package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;

/**
 * 蓝羽枝剑附魔
 * <p>
 * 血量&lt;=20%时受击减伤（等级×10%），<b>最高 9 级生效</b>
 * </p>
 *
 * <h3>v2.1：减伤等级封顶到 {@value #MAX_EFFECTIVE_LEVEL} 级</h3>
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
 * 也无论 {@code levelLimit} 开关状态如何，参与计算的等级最多为 {@value #MAX_EFFECTIVE_LEVEL}</b>，
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
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentCorruptedWingSword.class
        }
)
public class EnchantmentBlueFeatheredBranchsword extends EnchantmentBase {

    /**
     * 参与减伤计算的最高等级。
     * <p>
     * 减伤 = 等级 × 10%，故 {@value} 级对应 90% 减伤上限。
     * <b>这个上限比 {@code ConfigLoader.levelLimit} 的 10 级更严格，且不受该开关影响</b>——
     * 它的作用不是「限制附魔等级」，而是「保证减伤系数恒为正」，
     * 属于公式本身的安全边界，不该由配置开关来决定要不要生效。
     * </p>
     */
    private static final int MAX_EFFECTIVE_LEVEL = 9;

    /** 每级减伤比例 */
    private static final float REDUCTION_PER_LEVEL = 0.1f;

    public EnchantmentBlueFeatheredBranchsword() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsVictim(@NotNull EnchantmentContext ctx, int level) {
        // 只处理有攻击者的伤害
        if (ctx.getAttacker() == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // ⭐ v2.1：再压一道公式自身的安全上限，保证减伤系数恒为正（详见类注释）
        effectiveLevel = Math.min(effectiveLevel, MAX_EFFECTIVE_LEVEL);

        // 血量 <= 20%时触发
        if (ctx.getHolder().getHealth() <= ctx.getHolder().getMaxHealth() * 0.2) {
            // 减伤 = 伤害 × 等级 × 10%，最高 9 级 → 最高 90%
            ctx.multiplyDamage(1 - effectiveLevel * REDUCTION_PER_LEVEL);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
