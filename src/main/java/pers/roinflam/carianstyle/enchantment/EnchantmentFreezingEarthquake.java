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
 * <p>冻结地震附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "freezing_earthquake",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentFreezingEarthquake extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "freezing_earthquake";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 10，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 触发所需的单次伤害占最大生命的比例
     * <p>默认 0.25，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.25D, 0.0D, 1.0D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 基础作用半径（格）
     * <p>默认 3，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle BASE_RANGE =
            EnchantmentValues.define(VALUE_ID, "base_range",
                    3, 1, 64);

    /**
     * 每超过一级增加的作用半径（格）
     * <p>默认 2，允许范围 0 ~ 16。</p>
     */
    private static final EnchantmentValues.Handle RANGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "range_per_level",
                    2, 0, 16);

    /**
     * 每级冻结效果的持续秒数
     * <p>默认 5，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle SLOW_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "slow_seconds_per_level",
                    5, 1, 120);

    public EnchantmentFreezingEarthquake() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
