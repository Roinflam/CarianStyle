package pers.roinflam.carianstyle.base.enchantment.rarity;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

public abstract class VeryRaryBase extends EnchantmentBase {

    protected VeryRaryBase(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(Rarity.VERY_RARE, typeIn, slots, name);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

}
