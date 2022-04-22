package pers.roinflam.kaliastyle.init;

import net.minecraft.potion.Potion;
import pers.roinflam.kaliastyle.potion.PotionBadOmen;
import pers.roinflam.kaliastyle.potion.PotionFrostbite;
import pers.roinflam.kaliastyle.potion.PotionScarletCorruption;
import pers.roinflam.kaliastyle.potion.PotionSleeping;

import java.util.ArrayList;
import java.util.List;

public class KaliaStylePotion {
    public static final List<Potion> POTIONS = new ArrayList<Potion>();

    public static final PotionScarletCorruption SCARLET_CORRUPTION = new PotionScarletCorruption(true, 0xbf2000);
    public static final PotionBadOmen BAD_OMEN = new PotionBadOmen(true, 0x74581f);
    public static final PotionSleeping SLEEPING = new PotionSleeping(true, 0x7038a1);
    public static final PotionFrostbite FROSTBITE = new PotionFrostbite(true, 0x2a76a9);

}