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
 * <p>回归法则附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "regressive_principle",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET},
        forceTreasure = true
)
public class EnchantmentRegressivePrinciple extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "regressive_principle";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 8，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    8, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 16，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    16, 1, 200);

    /**
     * 每次触发的概率（百分比）
     * <p>默认 5.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_CHANCE =
            EnchantmentValues.define(VALUE_ID, "trigger_chance",
                    5.0D, 0.0D, 100.0D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    public EnchantmentRegressivePrinciple() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
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
