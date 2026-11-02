package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 *
 * 蓝羽枝剑附魔
 * <p>
 * 血量&lt;=20%时受击减伤（等级×10%），<b>最高 9 级生效</b>
 * </p>
 *
 * <h3>v2.1：减伤等级封顶到 {@link #MAX_EFFECTIVE_LEVEL} 级</h3>
 * <p>
 * 原实现是 {@code ctx.multiplyDamage(1 - effectiveLevel * 0.1f)}，只受
 * {@code ConfigLoader.levelLimit} 的 10 级限制约束，存在两个问题：
 * </p>
 * <ul>
 *     <li><b>10 级时系数正好为 0</b>——残血 20% 以下<b>完全免疫</b>。
 *         一个「减伤」附魔不该有能把伤害归零的档位；</li>
 *     <li><b>{@code levelLimit} 关闭时系数变负数</b>——等级 11 起为 {@code 1 - 1.1 = -0.1}，
 *         伤害乘以负值，视 {@code multiplyDamage} 的实现要么把伤害变成治疗、
 *         要么产生异常数值。这不是任何描述里写过的行为，纯属数值溢出。</li>
 * </ul>
 * <p>
 * 现在用一个独立的 {@link #MAX_EFFECTIVE_LEVEL} 常量兜住：<b>无论附魔等级多高、
 * 也无论 {@code levelLimit} 开关状态如何，参与计算的等级最多为 {@link #MAX_EFFECTIVE_LEVEL}</b>，
 * 即减伤上限 90%。这同时消灭了上面两个问题——不会归零，更不会变负。
 * </p>
 * <p>
 * 1~9 级的表现与原来完全一致，只有 10 级及以上被压回 9 级的效果。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "blue_feathered_branchsword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentCorruptedWingSword.class
        },
        baseEnchantability = 15,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentBlueFeatheredBranchsword extends EnchantmentBase {
    private static final String VALUE_ID = "blue_feathered_branchsword";
    private static final EnchantmentValues.Handle MAX_EFFECTIVE_LEVEL = EnchantmentValues.define(VALUE_ID, "max_effective_level",
                    9, 1, 100);
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.1D, 0.0D, 0.5D);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle HEALTH_THRESHOLD = EnchantmentValues.define(VALUE_ID, "health_threshold",
                    0.2D, 0.0D, 1.0D);
    public EnchantmentBlueFeatheredBranchsword() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsVictim(@NotNull EnchantmentContext ctx, int level) {
    }
}

