package pers.roinflam.carianstyle.base.enchantment.rarity;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

public abstract class UncommonBase extends EnchantmentBase {

    protected UncommonBase(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(Rarity.UNCOMMON, typeIn, slots, name);
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

}
