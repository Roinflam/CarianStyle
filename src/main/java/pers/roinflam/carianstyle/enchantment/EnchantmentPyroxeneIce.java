package pers.roinflam.carianstyle.enchantment;

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
 * PORT-STUB(E3)
 * <p>辉石冰附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "pyroxene_ice",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
        // v2.1修复：移除 conflictsWith = {Enchantments.class}，该配置无效
        // 与火矢(FLAMING_ARROWS)的互斥通过下方 checkCompatibility 方法实现
)
public class EnchantmentPyroxeneIce extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "pyroxene_ice";

    /**
     * 施加效果的持续秒数
     * <p>默认 10，允许范围 1 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_SECONDS =
            EnchantmentValues.define(VALUE_ID, "effect_seconds",
                    10, 1, 300);

    public EnchantmentPyroxeneIce() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        // 与火矢互斥
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.FLAME);
    }
}
