package pers.roinflam.carianstyle.init;

import net.minecraft.potion.Potion;
import pers.roinflam.carianstyle.potion.*;
import pers.roinflam.carianstyle.potion.hide.*;

import java.util.ArrayList;
import java.util.List;

public class CarianStylePotion {
    public static final List<Potion> POTIONS = new ArrayList<Potion>();

    public static final MobEffectScarletRot SCARLET_ROT = new MobEffectScarletRot(true, 0xbf2000);
    public static final MobEffectBadOmen BAD_OMEN = new MobEffectBadOmen(true, 0x74581f);
    public static final MobEffectSleep SLEEP = new MobEffectSleep(true, 0x7038a1);
    public static final MobEffectFrostbite FROSTBITE = new MobEffectFrostbite(true, 0x2a76a9);
    public static final MobEffectGoldenVow GOLDEN_VOW = new MobEffectGoldenVow(false, 0xffd700);
    public static final MobEffectBlessingOfTheErdtree BLESSING_OF_THE_ERDTREE = new MobEffectBlessingOfTheErdtree(false, 0xffd700);
    public static final MobEffectProtectionOfTheErdtree PROTECTION_OF_THE_ERDTREE = new MobEffectProtectionOfTheErdtree(false, 0xffd700);
    public static final MobEffectHemorrhage HEMORRHAGE = new MobEffectHemorrhage(true, 0xc70000);

    public static final MobEffectDoomedDeath DOOMED_DEATH = new MobEffectDoomedDeath(true, 0);
    public static final MobEffectStealth STEALTH = new MobEffectStealth(false, 0);

    public static final MobEffectDoomedDeathBurning DOOMED_DEATH_BURNING = new MobEffectDoomedDeathBurning(true, 0);
    public static final MobEffectDestructionFireBurning DESTRUCTION_FIRE_BURNING = new MobEffectDestructionFireBurning(true, 0);
    public static final MobEffectEpilepsyFireBurning EPILEPSY_FIRE_BURNING = new MobEffectEpilepsyFireBurning(true, 0);

}