package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>凶兆附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "bad_omen",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentBadOmen extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "bad_omen";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 追加伤害占本次伤害的比例
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "extra_damage_ratio",
                    0.5D, 0.0D, 5.0D);

    /**
     * 本体伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.5D, 0.0D, 5.0D);

    /**
     * 施加不祥预感的持续时间（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_DURATION =
            EnchantmentValues.define(VALUE_ID, "effect_duration",
                    200, 20, 6000);

    public EnchantmentBadOmen() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
