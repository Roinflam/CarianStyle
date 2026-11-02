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
 * <p>巨人火焰附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "giant_flame",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST}
)
public class EnchantmentGiantFlame extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "giant_flame";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 燃烧时反弹给攻击者的伤害比例
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle REFLECT_RATIO =
            EnchantmentValues.define(VALUE_ID, "reflect_ratio",
                    0.5D, 0.0D, 5.0D);

    /**
     * 按已损失生命比例提供的减伤系数
     * <p>默认 0.25，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_RATIO =
            EnchantmentValues.define(VALUE_ID, "reduction_ratio",
                    0.25D, 0.0D, 1.0D);

    /**
     * 燃烧治疗的结算冷却（tick）
     * <p>默认 10，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle HEAL_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "heal_cooldown",
                    10, 1, 600);

    private static final String FLAME_HEAL_COOLDOWN_KEY = "giant_flame_heal_cooldown";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGiantFlame() {
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
