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
        if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            int level = EnchantmentHelper.getEnchantmentLevel(
                    prayerfulStrike,
                    attacker.getHeldItem(attacker.getActiveHand()));

            if (level > 0) {
                handleAttackerLogic(evt, attacker, victim);
            }
        }

        // 受击者视角
        if (!victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
            int level = EnchantmentHelper.getEnchantmentLevel(
                    prayerfulStrike,
                    victim.getHeldItem(victim.getActiveHand()));

            if (level > 0) {
                handleVictimLogic(victim);
            }
        }
    }

    private static void handleAttackerLogic(LivingDamageEvent evt, EntityLivingBase attacker, EntityLivingBase victim) {
        UUID uuid = attacker.getUniqueID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod) {
            if (!stillWaiting) {
                triggerPrayerfulStrike(evt, attacker, victim);
                EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, 160);
                EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
            }
        } else {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, 160);
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
        }
    }

    private static void handleVictimLogic(EntityLivingBase victim) {
        UUID uuid = victim.getUniqueID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod && !stillWaiting) {
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
        } else if (!inChargingPeriod) {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, 160);
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
        }
    }

    private static void triggerPrayerfulStrike(LivingDamageEvent evt, EntityLivingBase attacker, EntityLivingBase victim) {
        float bonusDamage = Math.min(
                attacker.getMaxHealth() * 0.025f + victim.getHealth() * 0.075f,
                victim.getMaxHealth() * 0.1f
        );
        evt.setAmount(evt.getAmount() + bonusDamage);

        applyMaxHealthBonus(attacker, bonusDamage);
        attacker.heal(bonusDamage);

        attacker.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 0);
        victim.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 0);
    }

    private static void applyMaxHealthBonus(EntityLivingBase attacker, float bonusDamage) {
        IAttributeInstance attribute = attacker.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        float healthBonus = bonusDamage / 2;
        float maxBonus = attacker.getMaxHealth() * 0.05f;
        AttributeModifier existing = attribute.getModifier(MAX_HEALTH_MODIFIER_ID);

        if (existing == null) {
            attribute.applyModifier(new AttributeModifier(
                    MAX_HEALTH_MODIFIER_ID,
                    MAX_HEALTH_MODIFIER_NAME,
                    healthBonus,
                    0
            ));
        } else {
            double newAmount = Math.min(
                    existing.getAmount() + Math.min(healthBonus, maxBonus),
                    ConfigLoader.prayerfulStrikeMaxHealth
            );
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
            attribute.applyModifier(new AttributeModifier(
                    MAX_HEALTH_MODIFIER_ID,
                    MAX_HEALTH_MODIFIER_NAME,
                    newAmount,
                    0
            ));
        }
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
        if (evt.phase != TickEvent.Phase.START || evt.player.ticksExisted % 20 != 0) {
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
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 3);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}