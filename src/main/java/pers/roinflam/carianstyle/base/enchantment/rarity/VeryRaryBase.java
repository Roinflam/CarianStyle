package pers.roinflam.carianstyle.base.enchantment.rarity;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;

public abstract class VeryRaryBase extends EnchantmentBase {

    protected VeryRaryBase(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(Rarity.VERY_RARE, typeIn, slots, name);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isTreasureEnchantment() {
        if (ConfigLoader.isTreasureVeryRaryEnchantment) {
            return true;
        } else {
            return super.isTreasureEnchantment();
        }
    }
}
