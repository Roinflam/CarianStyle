package pers.roinflam.kaliastyle.utils.util;

import net.minecraft.enchantment.Enchantment;

public class EnchantmentUtil {

    public static Enchantment registerEnchantment(Enchantment enchantment, String name) {
        enchantment.setName(name);
        enchantment.setRegistryName(name);
        return enchantment;
    }

}
