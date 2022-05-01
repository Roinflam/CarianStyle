package pers.roinflam.kaliastyle.init;

import net.minecraft.potion.Potion;
import pers.roinflam.kaliastyle.potion.MobEffectBadOmen;
import pers.roinflam.kaliastyle.potion.MobEffectFrostbite;
import pers.roinflam.kaliastyle.potion.MobEffectScarletCorruption;
import pers.roinflam.kaliastyle.potion.MobEffectSleeping;
import pers.roinflam.kaliastyle.potion.hide.MobEffectDestructionFireBurning;
import pers.roinflam.kaliastyle.potion.hide.MobEffectDoomedDeath;
import pers.roinflam.kaliastyle.potion.hide.MobEffectDoomedDeathBurning;
import pers.roinflam.kaliastyle.potion.hide.MobEffectEpilepsyFireBurning;

import java.util.ArrayList;
import java.util.List;

public class KaliaStylePotion {
    public static final List<Potion> POTIONS = new ArrayList<Potion>();

    public static final MobEffectScarletCorruption SCARLET_CORRUPTION = new MobEffectScarletCorruption(true, 0xbf2000);
    public static final MobEffectBadOmen BAD_OMEN = new MobEffectBadOmen(true, 0x74581f);
    public static final MobEffectSleeping SLEEPING = new MobEffectSleeping(true, 0x7038a1);
    public static final MobEffectFrostbite FROSTBITE = new MobEffectFrostbite(true, 0x2a76a9);

    public static final MobEffectDoomedDeath DOOMED_DEATH = new MobEffectDoomedDeath(true, 0x380000);

    public static final MobEffectDoomedDeathBurning DOOMED_DEATH_BURNING = new MobEffectDoomedDeathBurning(true, 0);
    public static final MobEffectDestructionFireBurning DESTRUCTION_FIRE_BURNING = new MobEffectDestructionFireBurning(true, 0);
    public static final MobEffectEpilepsyFireBurning EPILEPSY_FIRE_BURNING = new MobEffectEpilepsyFireBurning(true, 0);

}