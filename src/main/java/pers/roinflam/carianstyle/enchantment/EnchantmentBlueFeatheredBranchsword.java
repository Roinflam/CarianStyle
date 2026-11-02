package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>蓝羽枝剑附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "blue_feathered_branchsword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentCorruptedWingSword.class
        }
)
public class EnchantmentBlueFeatheredBranchsword extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blue_feathered_branchsword";

    /**
     * 参与减伤计算的等级上限
     * <p>默认 9，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle MAX_EFFECTIVE_LEVEL =
            EnchantmentValues.define(VALUE_ID, "max_effective_level",
                    9, 1, 100);

    /**
     * 每级的减伤比例
     * <p>默认 0.1，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.1D, 0.0D, 0.5D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 触发所需的剩余生命比例阈值
     * <p>默认 0.2，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEALTH_THRESHOLD =
            EnchantmentValues.define(VALUE_ID, "health_threshold",
                    0.2D, 0.0D, 1.0D);

    /**
     * 参与减伤计算的最高等级。
     * <p>
     * 减伤 = 等级 × 10%，故 {@value} 级对应 90% 减伤上限。
     * <b>这个上限比 {@code ConfigLoader.levelLimit} 的 10 级更严格，且不受该开关影响</b>——
     * 它的作用不是「限制附魔等级」，而是「保证减伤系数恒为正」，
     * 属于公式本身的安全边界，不该由配置开关来决定要不要生效。
     * </p>
     */
    public EnchantmentBlueFeatheredBranchsword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
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
