package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

public abstract class EnchantmentBase extends Enchantment {

    protected EnchantmentBase(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, name);
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }
}
