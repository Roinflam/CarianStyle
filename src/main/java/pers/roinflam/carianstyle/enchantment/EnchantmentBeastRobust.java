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
 * <p>野兽强健附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "beast_robust",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST},
        forceTreasure = true
)
public class EnchantmentBeastRobust extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "beast_robust";

    /**
     * 转化后效果等级的上限
     * <p>默认 100，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle MAX_AMPLIFIER =
            EnchantmentValues.define(VALUE_ID, "max_amplifier",
                    100, 0, 127);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 转化后效果时长相对原时长的比例
     * <p>默认 0.4，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DURATION_RATIO =
            EnchantmentValues.define(VALUE_ID, "duration_ratio",
                    0.4D, 0.0D, 5.0D);

    /**
     * 转化后效果等级的倍数（结果再 +1）
     * <p>默认 2，允许范围 1 ~ 20。</p>
     */
    private static final EnchantmentValues.Handle AMPLIFIER_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "amplifier_multiplier",
                    2, 1, 20);

    /**
     * 药水效果等级的最大上限
     */
    public EnchantmentBeastRobust() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
