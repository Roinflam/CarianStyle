package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>连射附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "continuous_shooting",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentContinuousShooting extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "continuous_shooting";

    /**
     * 连射箭矢的伤害倍率
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    0.5D, 0.0D, 5.0D);

    /**
     * 每次触发额外射出的箭矢数
     * <p>默认 4，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_ARROWS =
            EnchantmentValues.define(VALUE_ID, "extra_arrows",
                    4, 1, 32);

    public EnchantmentContinuousShooting() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.INFINITY) {
            return false;
        }
        return super.checkCompatibility(ench);
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
