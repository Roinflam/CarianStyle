package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>先祖之魂附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "ancestral_spirits",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST}
)
public class EnchantmentAncestralSpirits extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "ancestral_spirits";

    /**
     * 触发时受到伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.5D, 0.0D, 1.0D);

    /**
     * 持续治疗的总时长（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle HEAL_DURATION_TICKS =
            EnchantmentValues.define(VALUE_ID, "heal_duration_ticks",
                    200, 20, 6000);

    /**
     * 每秒回复已损失生命的比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "heal_ratio_per_second",
                    0.05D, 0.0D, 2.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentAncestralSpirits() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
