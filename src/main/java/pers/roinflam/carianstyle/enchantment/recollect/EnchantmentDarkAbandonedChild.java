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
 * <p>暗弃子附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "dark_abandoned_child",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentDarkAbandonedChild extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "dark_abandoned_child";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 夜间受到伤害的倍率
     * <p>默认 0.9，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle NIGHT_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "night_damage_multiplier",
                    0.9D, 0.0D, 2.0D);

    /**
     * 夜间每秒回复的最大生命占比
     * <p>默认 0.015，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "heal_ratio_per_second",
                    0.015D, 0.0D, 1.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentDarkAbandonedChild() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
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
