package pers.roinflam.kaliastyle.utils.util;

import net.minecraft.potion.Potion;

public class PotionUtil {

    public static Potion registerPotion(Potion potion, String name) {
        potion.setPotionName("effect." + name);
        potion.setRegistryName(name);
        return potion;
    }

}
