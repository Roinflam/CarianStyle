package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 祈祷一击附魔
 *
 * 攻击后开始蓄力（8秒），4秒后进入准备状态
 * 准备状态下攻击触发：额外伤害、增加最大生命值、治疗自身
 * 蓄力期间每秒播放音效提示
 */
@AutoRegisterEnchantment(
        id = "prayerful_strike",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        forceTreasure = true,
        conflictsWith = {EnchantmentScarletCorruption.class}
)
@Mod.EventBusSubscriber
public class EnchantmentPrayerfulStrike extends EnchantmentBase {

    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");
    private static final String MAX_HEALTH_MODIFIER_NAME = "enchantment.prayerful_strike";

    private static final String CHARGING_COOLDOWN_KEY = "prayerful_strike_charging";
    private static final String NOT_READY_COOLDOWN_KEY = "prayerful_strike_not_ready";
    // 蓄力周期（tick）：触发或开始蓄力后重新计时
    private static final int CHARGING_COOLDOWN = 160;
    // 进入就绪前的等待（tick）
    private static final int NOT_READY_COOLDOWN = 80;
    // 额外伤害：自身最大生命值的比例
    private static final float SELF_HEALTH_RATIO = 0.025F;
    // 额外伤害：目标当前生命值的比例
    private static final float VICTIM_HEALTH_RATIO = 0.075F;
    // 额外伤害上限：目标最大生命值的比例
    private static final float DAMAGE_CAP_RATIO = 0.1F;
    // 最大生命值提升 = 额外伤害 ÷ 该值
    private static final float HEAL_DIVISOR = 2.0F;
    // 单次最大生命值提升上限：当前最大生命值的比例
    private static final float HEAL_CAP_RATIO = 0.05F;
    // 就绪提示音的检查间隔（tick）
    private static final int TICK_INTERVAL = 20;

    public EnchantmentPrayerfulStrike() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 伤害事件处理（LOWEST优先级）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();

        Enchantment prayerfulStrike = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPrayerfulStrike.class);
        if (prayerfulStrike == null) {
            return;
        }

        // 攻击者视角
        if (!shouldBlockMobTrigger(attacker, false)) {
            ItemStack attackerHeld = attacker.getHeldItemMainhand();
            if (!attackerHeld.isEmpty() && EnchantmentHelper.getEnchantmentLevel(prayerfulStrike, attackerHeld) > 0) {
                handleAttackerLogic(evt, attacker, victim);
            }
        }

        // 受击者视角
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }
        ItemStack victimHeld = victim.getHeldItemMainhand();
        if (!victimHeld.isEmpty() && EnchantmentHelper.getEnchantmentLevel(prayerfulStrike, victimHeld) > 0) {
            handleVictimLogic(victim);
        }
    }

    private static void handleAttackerLogic(LivingDamageEvent evt, EntityLivingBase attacker, EntityLivingBase victim) {
        UUID uuid = attacker.getUniqueID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod) {
            if (!stillWaiting) {
                triggerPrayerfulStrike(evt, attacker, victim);
                EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, CHARGING_COOLDOWN);
                EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN);
            }
        } else {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, CHARGING_COOLDOWN);
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN);
        }
    }

    private static void handleVictimLogic(EntityLivingBase victim) {
        UUID uuid = victim.getUniqueID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod && !stillWaiting) {
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN);
        } else if (!inChargingPeriod) {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, CHARGING_COOLDOWN);
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN);
        }
    }

    private static void triggerPrayerfulStrike(LivingDamageEvent evt, EntityLivingBase attacker, EntityLivingBase victim) {
        float bonusDamage = Math.min(
                attacker.getMaxHealth() * SELF_HEALTH_RATIO + victim.getHealth() * VICTIM_HEALTH_RATIO,
                victim.getMaxHealth() * DAMAGE_CAP_RATIO
        );
        evt.setAmount(evt.getAmount() + bonusDamage);

        applyMaxHealthBonus(attacker, bonusDamage);
        attacker.heal(bonusDamage);

        playLevelUpSound(attacker, 0);
        victim.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 0);
    }

    /**
     * 播提示音，持有者自己也听得到
     * <p>
     * {@code EntityPlayer#playSound} 在服务端会把本人排除（默认由客户端自己播），以前持有者听不到触发音和就绪提示。
     * </p>
     */
    private static void playLevelUpSound(@Nonnull EntityLivingBase holder, float pitch) {
        holder.world.playSound(null, holder.posX, holder.posY, holder.posZ,
                SoundEvents.ENTITY_PLAYER_LEVELUP, holder.getSoundCategory(), 1, pitch);
    }

    private static void applyMaxHealthBonus(EntityLivingBase attacker, float bonusDamage) {
        IAttributeInstance attribute = attacker.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        float healthBonus = bonusDamage / HEAL_DIVISOR;
        float maxBonus = attacker.getMaxHealth() * HEAL_CAP_RATIO;
        AttributeModifier existing = attribute.getModifier(MAX_HEALTH_MODIFIER_ID);

        // 第一次也受单次 5% 和总上限的限制，没有修饰符时按 0 起算（以前第一次直接加满额外伤害的一半）
        double currentAmount = existing == null ? 0 : existing.getAmount();
        double newAmount = Math.min(
                currentAmount + Math.min(healthBonus, maxBonus),
                ConfigLoader.prayerfulStrikeMaxHealth
        );
        if (existing != null) {
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
        attribute.applyModifier(new AttributeModifier(
                MAX_HEALTH_MODIFIER_ID,
                MAX_HEALTH_MODIFIER_NAME,
                newAmount,
                0
        ));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(@Nonnull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.player.world.isRemote || evt.isEndConquered()) {
            return;
        }

        IAttributeInstance attribute = evt.player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        UUID uuid = evt.getEntityLiving().getUniqueID();
        EnchantmentDataManager.clearCooldown(CHARGING_COOLDOWN_KEY, uuid);
        EnchantmentDataManager.clearCooldown(NOT_READY_COOLDOWN_KEY, uuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START
                || evt.player.ticksExisted % TICK_INTERVAL != 0) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        UUID uuid = player.getUniqueID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod && stillWaiting) {
            playLevelUpSound(player, 3);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}