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
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.mc112.attr.Operation;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * 祈祷一击附魔
 * <p>
 * 命中后进入 160 tick 的蓄力期，其中前 80 tick 未就绪；就绪后的下一击额外造成
 * 自身最大生命 2.5% + 目标当前生命 7.5% 的伤害（至多目标最大生命 10%），回复同等生命，
 * 并把额外伤害的一半累加进最大生命上限（单次至多自身最大生命 5%，总量受 {@code prayerfulStrikeMaxHealth} 限制，死亡重生时清除）。
 * 期间被打中会重新进入未就绪。
 * </p>
 * <p>v2.2：LivingDamage双向入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，onLivingDeath 是清理类，onPlayerRespawn 是清理类，均无需检查。</p>
 * <p>v2.3 视觉：{@link #triggerPrayerfulStrike} 内广播一发自绘圣光特效
 * （{@link CarianStyleCombatArtEffects#prayerStrike}——自天而降的金色圣光柱 + 落地金环 +
 * 地面十字圣徽 + 升腾金光丝）。此前蓄力完成的那一击只有 {@code ENTITY_PLAYER_LEVELUP} 音效，
 * 没有任何画面，玩家难以确认「这一下是不是蓄满了的」。</p>
 * <p>只加视觉，机制不变：蓄力冷却、触发判定、伤害加成、生命上限累积、
 * 治疗与音效全部保持原样；特效为纯服务端广播，不生成实体、不触发任何事件。
 * 蓄力周期为 160tick（8 秒）、判定窗口 80tick，触发频率天然受控，不会刷屏。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "prayerful_strike",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {EnchantmentScarletCorruption.class}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentPrayerfulStrike extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "prayerful_strike";

    /**
     * 蓄力所需的时长（tick）
     * <p>默认 160，允许范围 1 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle CHARGING_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "charging_cooldown",
                    160, 1, 12000);

    /**
     * 未就绪状态的时长（tick）。应小于 charging_cooldown，二者共同决定可用节奏
     * <p>默认 80，允许范围 1 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle NOT_READY_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "not_ready_cooldown",
                    80, 1, 12000);

    /**
     * 按自身最大生命计算的额外伤害比例
     * <p>默认 0.025，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_health_ratio",
                    0.025D, 0.0D, 2.0D);

    /**
     * 按目标当前生命计算的额外伤害比例
     * <p>默认 0.075，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "victim_health_ratio",
                    0.075D, 0.0D, 2.0D);

    /**
     * 额外伤害的上限（占目标最大生命的比例）
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_cap_ratio",
                    0.1D, 0.0D, 2.0D);

    /**
     * 额外伤害转化为治疗的除数（2 表示一半）
     * <p>默认 2.0，允许范围 0.1 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "heal_divisor",
                    2.0D, 0.1D, 100.0D);

    /**
     * 单次治疗量的最大生命占比上限
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.05D, 0.0D, 2.0D);

    /**
     * 就绪提示音的结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");

    private static final String MAX_HEALTH_MODIFIER_NAME = "enchantment.prayerful_strike";

    private static final String CHARGING_COOLDOWN_KEY = "prayerful_strike_charging";

    private static final String NOT_READY_COOLDOWN_KEY = "prayerful_strike_not_ready";

    public EnchantmentPrayerfulStrike() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

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
        // v2.2：怪物附魔触发开关（攻击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
            ItemStack attackerHeld = attacker.getHeldItem(EnumHand.MAIN_HAND);
            if (!attackerHeld.isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(prayerfulStrike, attackerHeld);
                if (level > 0) {
                    handleAttackerLogic(evt, attacker, victim);
                }
            }
        }

        // 受击者视角
        // v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack victimHeld = victim.getHeldItem(EnumHand.MAIN_HAND);
        if (!victimHeld.isEmpty()) {
            int level = EnchantmentHelper.getEnchantmentLevel(prayerfulStrike, victimHeld);
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
                EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, CHARGING_COOLDOWN.getInt());
                EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN.getInt());
            }
        } else {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, CHARGING_COOLDOWN.getInt());
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN.getInt());
        }
    }

    private static void handleVictimLogic(EntityLivingBase victim) {
        UUID uuid = victim.getUniqueID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod && !stillWaiting) {
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN.getInt());
        } else if (!inChargingPeriod) {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, CHARGING_COOLDOWN.getInt());
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN.getInt());
        }
    }

    private static void triggerPrayerfulStrike(LivingDamageEvent evt, EntityLivingBase attacker, EntityLivingBase victim) {
        float bonusDamage = Math.min(
                attacker.getMaxHealth() * (float) SELF_HEALTH_RATIO.get()
                        + victim.getHealth() * (float) VICTIM_HEALTH_RATIO.get(),
                victim.getMaxHealth() * (float) DAMAGE_CAP_RATIO.get()
        );
        evt.setAmount(evt.getAmount() + bonusDamage);
        applyMaxHealthBonus(attacker, bonusDamage);
        attacker.heal(bonusDamage);
        playLevelUpSound(attacker, 0);
        victim.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 0);

        // v2.3 视觉：蓄力完成那一击的金色圣光柱（纯服务端广播，不影响任何机制）
        // 落在攻击者脚下——这一击的语义是「祈祷得到回应」，光应该降在祈祷者身上而非目标身上
        if (attacker.world instanceof WorldServer) {
            CarianStyleCombatArtEffects.prayerStrike((WorldServer) attacker.world, attacker);
        }
    }

    /**
     * 播提示音，持有者自己也听得到。
     * <p>{@code EntityPlayer#playSound} 在服务端排除本人（默认由客户端自己播），这里不能走它。</p>
     */
    private static void playLevelUpSound(EntityLivingBase holder, float pitch) {
        holder.world.playSound(null, holder.posX, holder.posY, holder.posZ,
                SoundEvents.ENTITY_PLAYER_LEVELUP, holder.getSoundCategory(), 1, pitch);
    }

    private static void applyMaxHealthBonus(EntityLivingBase attacker, float bonusDamage) {
        IAttributeInstance attribute = attacker.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        float healthBonus = (float) (bonusDamage / HEAL_DIVISOR.get());
        float maxBonus = attacker.getMaxHealth() * (float) HEAL_CAP_RATIO.get();
        AttributeModifier existing = attribute.getModifier(MAX_HEALTH_MODIFIER_ID);

        // 第一次也受单次上限限制，没有修饰符时按 0 起算
        double currentAmount = existing == null ? 0 : existing.getAmount();
        double newAmount = Math.min(
                currentAmount + Math.min(healthBonus, maxBonus),
                ConfigLoader.prayerfulStrikeMaxHealth
        );
        attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
        attribute.applyModifier(new AttributeModifier(
                MAX_HEALTH_MODIFIER_ID, MAX_HEALTH_MODIFIER_NAME,
                newAmount, Operation.ADDITION.id()
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
        // 清理类，无需开关检查
        UUID uuid = evt.getEntityLiving().getUniqueID();
        EnchantmentDataManager.clearCooldown(CHARGING_COOLDOWN_KEY, uuid);
        EnchantmentDataManager.clearCooldown(NOT_READY_COOLDOWN_KEY, uuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START
                || evt.player.ticksExisted % TICK_INTERVAL.getInt() != 0) {
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
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
