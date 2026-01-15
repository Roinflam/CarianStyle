package pers.roinflam.carianstyle.utils.util;

import net.minecraft.potion.Potion;

import javax.annotation.Nonnull;

public class PotionUtil {

    @Nonnull
    public static Potion registerPotion(@Nonnull Potion potion, @Nonnull String name) {
        potion.setPotionName("effect." + name);
        potion.setRegistryName(name);
        return potion;
    }

}
