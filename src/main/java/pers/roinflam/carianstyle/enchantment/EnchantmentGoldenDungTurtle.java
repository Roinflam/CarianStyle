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
 * <p>黄金粪金龟附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "golden_dung_turtle", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentGoldenDungTurtle extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "golden_dung_turtle";

    /**
     * 每级额外掉落的经验比例
     * <p>默认 0.3，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle EXP_BONUS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "exp_bonus_per_level",
                    0.3D, 0.0D, 10.0D);

    public EnchantmentGoldenDungTurtle() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
