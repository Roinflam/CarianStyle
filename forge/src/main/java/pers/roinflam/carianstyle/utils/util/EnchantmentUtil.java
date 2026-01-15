package pers.roinflam.carianstyle.utils.util;

import net.minecraft.enchantment.Enchantment;

import javax.annotation.Nonnull;

public class EnchantmentUtil {

    @Nonnull
    public static Enchantment registerEnchantment(@Nonnull Enchantment enchantment, @Nonnull String name) {
        enchantment.setName(name);
        enchantment.setRegistryName(name);
        return enchantment;
    }

}
