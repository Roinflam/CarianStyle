package pers.roinflam.carianstyle.enchantment.combatskill;

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
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;
import java.util.UUID;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * PORT-STUB(E1)
 *
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
@Deprecated
public class EnchantmentPrayerfulStrike extends EnchantmentBase {
    private static final String VALUE_ID = "prayerful_strike";
    private static final EnchantmentValues.Handle CHARGING_COOLDOWN = EnchantmentValues.define(VALUE_ID, "charging_cooldown",
                    160, 1, 12000);
    private static final EnchantmentValues.Handle NOT_READY_COOLDOWN = EnchantmentValues.define(VALUE_ID, "not_ready_cooldown",
                    80, 1, 12000);
    private static final EnchantmentValues.Handle SELF_HEALTH_RATIO = EnchantmentValues.define(VALUE_ID, "self_health_ratio",
                    0.025D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle VICTIM_HEALTH_RATIO = EnchantmentValues.define(VALUE_ID, "victim_health_ratio",
                    0.075D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle DAMAGE_CAP_RATIO = EnchantmentValues.define(VALUE_ID, "damage_cap_ratio",
                    0.1D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEAL_DIVISOR = EnchantmentValues.define(VALUE_ID, "heal_divisor",
                    2.0D, 0.1D, 100.0D);
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO = EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.05D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle TICK_INTERVAL = EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");
    private static final String MAX_HEALTH_MODIFIER_NAME = "enchantment.prayerful_strike";
    private static final String CHARGING_COOLDOWN_KEY = "prayerful_strike_charging";
    private static final String NOT_READY_COOLDOWN_KEY = "prayerful_strike_not_ready";
    public EnchantmentPrayerfulStrike() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
}

