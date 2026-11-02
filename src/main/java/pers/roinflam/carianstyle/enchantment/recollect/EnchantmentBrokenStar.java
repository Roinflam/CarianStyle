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
 * <p>碎星附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "broken_star", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentBrokenStar extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "broken_star";

    /**
     * 触发所需的生命比例阈值
     * <p>默认 0.5，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEALTH_THRESHOLD =
            EnchantmentValues.define(VALUE_ID, "health_threshold",
                    0.5D, 0.0D, 1.0D);

    /**
     * 夜间的攻击倍率
     * <p>默认 2.0，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle ATTACK_MULTIPLIER_NIGHT =
            EnchantmentValues.define(VALUE_ID, "attack_multiplier_night",
                    2.0D, 0.0D, 10.0D);

    /**
     * 白天的攻击倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle ATTACK_MULTIPLIER_DAY =
            EnchantmentValues.define(VALUE_ID, "attack_multiplier_day",
                    1.5D, 0.0D, 10.0D);

    /**
     * 夜间受到伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DEFENSE_MULTIPLIER_NIGHT =
            EnchantmentValues.define(VALUE_ID, "defense_multiplier_night",
                    0.5D, 0.0D, 2.0D);

    /**
     * 白天受到伤害的倍率
     * <p>默认 0.75，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DEFENSE_MULTIPLIER_DAY =
            EnchantmentValues.define(VALUE_ID, "defense_multiplier_day",
                    0.75D, 0.0D, 2.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBrokenStar() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
