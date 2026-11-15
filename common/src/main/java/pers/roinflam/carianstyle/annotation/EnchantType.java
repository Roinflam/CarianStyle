package pers.roinflam.carianstyle.annotation;

import net.minecraft.world.item.enchantment.EnchantmentCategory;

// 和 EnchantSlot 同一个原因：注解里写原版 EnchantmentCategory 的话，Fabric 出包会把常量名映射成 field_9074 这类名字，
// 运行期一读注解就抛 AnnotationFormatError。这里的常量名和原版逐个对应，name() 也照旧（百科按它取文字键）。
public enum EnchantType {
    ARMOR(EnchantmentCategory.ARMOR),
    ARMOR_FEET(EnchantmentCategory.ARMOR_FEET),
    ARMOR_LEGS(EnchantmentCategory.ARMOR_LEGS),
    ARMOR_CHEST(EnchantmentCategory.ARMOR_CHEST),
    ARMOR_HEAD(EnchantmentCategory.ARMOR_HEAD),
    WEAPON(EnchantmentCategory.WEAPON),
    DIGGER(EnchantmentCategory.DIGGER),
    FISHING_ROD(EnchantmentCategory.FISHING_ROD),
    TRIDENT(EnchantmentCategory.TRIDENT),
    BREAKABLE(EnchantmentCategory.BREAKABLE),
    BOW(EnchantmentCategory.BOW),
    WEARABLE(EnchantmentCategory.WEARABLE),
    CROSSBOW(EnchantmentCategory.CROSSBOW),
    VANISHABLE(EnchantmentCategory.VANISHABLE);

    private final EnchantmentCategory vanilla;

    EnchantType(EnchantmentCategory vanilla) {
        this.vanilla = vanilla;
    }

    public EnchantmentCategory vanilla() {
        return vanilla;
    }
}
