package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * PORT-STUB(E3)
 *
 * 黑焰刃附魔
 * <p>
 * 攻击时施加灭绝火焰燃烧效果
 * 持续伤害：伤害×等级×0.15/100 每tick，持续100tick
 * </p>
 * <p>
 * 性能优化 v3.0：使用 DamageOverTimeManager 替代 SynchronizationTask(5, 1)
 * 黑焰刃是RARE级非宝藏附魔，使用频率最高的DoT附魔之一，优化效果显著
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@Deprecated
public class EnchantmentBlackFlameBlade extends EnchantmentBase {
    private static final String VALUE_ID = "black_flame_blade";
    private static final EnchantmentValues.Handle DOT_DURATION = EnchantmentValues.define(VALUE_ID, "dot_duration",
                    100, 1, 1200);
    private static final EnchantmentValues.Handle DOT_DELAY = EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle BURNING_SECONDS = EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    5, 1, 120);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.15D, 0.0D, 5.0D);
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

