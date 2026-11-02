package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>排斥附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "exclude",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR_LEGS,
        slots = {EntityEquipmentSlot.LEGS}
)
public class EnchantmentExclude extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "exclude";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 10.0，允许范围 1.0 ~ 64.0。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10.0D, 1.0D, 64.0D);

    /**
     * 单次触发最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 基础作用半径（格）
     * <p>默认 5.0，允许范围 0.5 ~ 64.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_RANGE =
            EnchantmentValues.define(VALUE_ID, "base_range",
                    5.0D, 0.5D, 64.0D);

    /**
     * 每超过一级增加的作用半径（格）
     * <p>默认 0.75，允许范围 0.0 ~ 16.0。</p>
     */
    private static final EnchantmentValues.Handle RANGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "range_per_level",
                    0.75D, 0.0D, 16.0D);

    /**
     * 排斥的击退强度
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "knockback_strength",
                    0.5D, 0.0D, 5.0D);

    public EnchantmentExclude() {
        super(EnumEnchantmentType.ARMOR_LEGS, new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
