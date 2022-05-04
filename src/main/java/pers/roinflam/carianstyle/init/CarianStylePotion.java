package pers.roinflam.carianstyle.init;

import net.minecraft.potion.Potion;
import pers.roinflam.carianstyle.potion.MobEffectBadOmen;
import pers.roinflam.carianstyle.potion.MobEffectFrostbite;
import pers.roinflam.carianstyle.potion.MobEffectScarletCorruption;
import pers.roinflam.carianstyle.potion.MobEffectSleeping;
import pers.roinflam.carianstyle.potion.hide.MobEffectDestructionFireBurning;
import pers.roinflam.carianstyle.potion.hide.MobEffectDoomedDeath;
import pers.roinflam.carianstyle.potion.hide.MobEffectDoomedDeathBurning;
import pers.roinflam.carianstyle.potion.hide.MobEffectEpilepsyFireBurning;

import java.util.ArrayList;
import java.util.List;

public class CarianStylePotion {
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