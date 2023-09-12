package pers.roinflam.carianstyle.base.enchantment.rarity;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;

public abstract class UncommonBase extends EnchantmentBase {

    protected UncommonBase(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(Rarity.UNCOMMON, typeIn, slots, name);
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public boolean isTreasureEnchantment() {
        if (ConfigLoader.isTreasureUncommonEnchantment) {
            return true;
        } else {
            return super.isTreasureEnchantment();
        }
    }
}
