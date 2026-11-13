package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import java.util.UUID;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 祈祷一击附魔
 * <p>v2.2：LivingDamage双向入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，onLivingDeath 是清理类，onPlayerRespawn 是清理类，均无需检查。</p>
 * <p>v2.3 视觉：{@link #triggerPrayerfulStrike} 内广播一发自绘圣光特效
 * （{@link CarianStyleCombatArtEffects#prayerStrike}——自天而降的金色圣光柱 + 落地金环 +
 * 地面十字圣徽 + 升腾金光丝）。此前蓄力完成的那一击只有 {@code PLAYER_LEVELUP} 音效，
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
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {EnchantmentScarletCorruption.class},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@EventBusSubscriber
public class EnchantmentPrayerfulStrike extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.prayerful_strike.desc，
    //   否则玩家看到的描述会与实际效果不符。

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

    /**
     * 最大生命加成修饰符的 id。
     * <p>1.20.1 用的是 UUID {@code b55a7c8a-df03-bca7-b5ea-ec703b261525}，旧存档里的那份由
     * {@code LegacyModifierMigration} 进世界时按同数值改成这个 id，两边必须一致。</p>
     */
    private static final ResourceLocation MAX_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "prayerful_strike/max_health");
    private static final String CHARGING_COOLDOWN_KEY = "prayerful_strike_charging";
    private static final String NOT_READY_COOLDOWN_KEY = "prayerful_strike_not_ready";

    public EnchantmentPrayerfulStrike() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        EnchantmentBase prayerfulStrike = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPrayerfulStrike.class);
        if (prayerfulStrike == null) {
            return;
        }

        // 攻击者视角
        // v2.2：怪物附魔触发开关（攻击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
            ItemStack attackerHeld = attacker.getItemInHand(InteractionHand.MAIN_HAND);
            if (!attackerHeld.isEmpty()) {
                int level = EnchantmentLevels.get(prayerfulStrike, attackerHeld);
                if (level > 0) {
                    handleAttackerLogic(evt, attacker, victim);
                }
            }
        }

        // 受击者视角
        // v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack victimHeld = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (!victimHeld.isEmpty()) {
            int level = EnchantmentLevels.get(prayerfulStrike, victimHeld);
            if (level > 0) {
                handleVictimLogic(victim);
            }
        }
    }

    private static void handleAttackerLogic(LivingDamageEvent evt, LivingEntity attacker, LivingEntity victim) {
        UUID uuid = attacker.getUUID();
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

    private static void handleVictimLogic(LivingEntity victim) {
        UUID uuid = victim.getUUID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod && !stillWaiting) {
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN.getInt());
        } else if (!inChargingPeriod) {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, CHARGING_COOLDOWN.getInt());
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, NOT_READY_COOLDOWN.getInt());
        }
    }

    private static void triggerPrayerfulStrike(LivingDamageEvent evt, LivingEntity attacker, LivingEntity victim) {
        float bonusDamage = Math.min(
                attacker.getMaxHealth() * (float) SELF_HEALTH_RATIO.get()
                        + victim.getHealth() * (float) VICTIM_HEALTH_RATIO.get(),
                victim.getMaxHealth() * (float) DAMAGE_CAP_RATIO.get()
        );
        evt.setAmount(evt.getAmount() + bonusDamage);
        applyMaxHealthBonus(attacker, bonusDamage);
        attacker.heal(bonusDamage);
        playLevelUpSound(attacker, 0);
        victim.playSound(SoundEvents.PLAYER_LEVELUP, 1, 0);

        // v2.3 视觉：蓄力完成那一击的金色圣光柱（纯服务端广播，不影响任何机制）
        // 落在攻击者脚下——这一击的语义是「祈祷得到回应」，光应该降在祈祷者身上而非目标身上
        if (attacker.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.prayerStrike(serverLevel, attacker);
        }
    }

    /**
     * 播提示音，持有者自己也听得到。
     * <p>{@code Player#playSound} 在服务端排除本人（默认由客户端自己播），这里不能走它。</p>
     */
    private static void playLevelUpSound(LivingEntity holder, float pitch) {
        holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.PLAYER_LEVELUP, holder.getSoundSource(), 1, pitch);
    }

    private static void applyMaxHealthBonus(LivingEntity attacker, float bonusDamage) {
        AttributeInstance attribute = attacker.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        float healthBonus = (float) (bonusDamage / HEAL_DIVISOR.get());
        float maxBonus = attacker.getMaxHealth() * (float) HEAL_CAP_RATIO.get();
        AttributeModifier existing = attribute.getModifier(MAX_HEALTH_MODIFIER_ID);

        // 第一次也受单次上限限制，没有修饰符时按 0 起算
        double currentAmount = existing == null ? 0 : existing.amount();
        double newAmount = Math.min(
                currentAmount + Math.min(healthBonus, maxBonus),
                ConfigLoader.prayerfulStrikeMaxHealth
        );
        attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
        attribute.addPermanentModifier(new AttributeModifier(
                MAX_HEALTH_MODIFIER_ID,
                newAmount, AttributeModifier.Operation.ADD_VALUE
        ));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(@NotNull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.isEndConquered()) {
            return;
        }
        AttributeInstance attribute = evt.getEntity().getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        // 清理类，无需开关检查
        UUID uuid = evt.getEntity().getUUID();
        EnchantmentDataManager.clearCooldown(CHARGING_COOLDOWN_KEY, uuid);
        EnchantmentDataManager.clearCooldown(NOT_READY_COOLDOWN_KEY, uuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull PlayerTickEvent.Pre evt) {
        if (evt.getEntity().level().isClientSide
                || evt.getEntity().tickCount % TICK_INTERVAL.getInt() != 0) {
            return;
        }
        Player player = evt.getEntity();
        if (!player.isAlive()) {
            return;
        }
        UUID uuid = player.getUUID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);
        if (inChargingPeriod && stillWaiting) {
            playLevelUpSound(player, 3);
        }
    }

}
