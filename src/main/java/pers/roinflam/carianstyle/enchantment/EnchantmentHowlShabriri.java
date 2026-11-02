package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * PORT-STUB(E4)
 *
 * 沙布里里嚎叫附魔
 * <p>
 * 武器附魔，叠层诅咒系统
 * 攻击时：
 * - 给目标叠加沙布里里嚎叫效果（持续 = 等级 × 3秒，层数+1，最高5层）
 * - 目标满5层时，伤害增加 15% × 等级
 * - 攻击者自身受到癫火伤害（3秒内共5%最大生命值，创造模式免疫）
 * </p>
 * <p>
 * 性能优化 v3.0：自损持续伤害改用 DamageOverTimeManager
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@Deprecated
public class EnchantmentHowlShabriri extends EnchantmentBase {
    private static final String VALUE_ID = "howl_shabriri";
    private static final EnchantmentValues.Handle SELF_DOT_DURATION = EnchantmentValues.define(VALUE_ID, "self_dot_duration",
                    60, 1, 1200);
    private static final EnchantmentValues.Handle SELF_DOT_DELAY = EnchantmentValues.define(VALUE_ID, "self_dot_delay",
                    5, 0, 200);
    private static final EnchantmentValues.Handle MAX_AMPLIFIER = EnchantmentValues.define(VALUE_ID, "max_amplifier",
                    5, 0, 127);
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.15D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO = EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.05D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle STACK_SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "stack_seconds_per_level",
                    3, 1, 120);
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

