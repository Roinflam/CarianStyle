package pers.roinflam.carianstyle.base.enchantment.rarity;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;

public abstract class RaryBase extends EnchantmentBase {

    protected RaryBase(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(Rarity.RARE, typeIn, slots, name);
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public boolean isTreasureEnchantment() {
        if (ConfigLoader.isTreasureRaryEnchantment) {
            return true;
        } else {
            return super.isTreasureEnchantment();
        }
    }
}
