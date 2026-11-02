package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>隐形武器附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "invisible_weapon",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        customType = "ARMS",
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentInvisibleWeapon extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "invisible_weapon";

    /**
     * 箭矢命中时额外增加的倍率（基础为 1）
     * <p>默认 2，允许范围 0 ~ 20。</p>
     */
    private static final EnchantmentValues.Handle ARROW_BONUS_MAGNIFICATION =
            EnchantmentValues.define(VALUE_ID, "arrow_bonus_magnification",
                    2, 0, 20);

    /**
     * 每级提供的潜行时长（tick）
     * <p>默认 20，允许范围 1 ~ 400。</p>
     */
    private static final EnchantmentValues.Handle STEALTH_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "stealth_ticks_per_level",
                    20, 1, 400);

    public EnchantmentInvisibleWeapon() {
        // v2.1：EnumEnchantmentType.WEAPON → ARMS 自定义类型（剑+弓），与注解一致
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("ARMS"),
                new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentHypnoticArrow.class));
    }
}
