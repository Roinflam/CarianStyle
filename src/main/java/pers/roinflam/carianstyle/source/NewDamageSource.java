package pers.roinflam.carianstyle.source;

import net.minecraft.util.DamageSource;

public class NewDamageSource {
    public static final DamageSource EPILEPSY_FIRE = new DamageSource("epilepsyFire").setDamageBypassesArmor().setDamageAllowedInCreativeMode();
    public static final DamageSource FROSTBITE = new DamageSource("frostbite");
    public static final DamageSource SCARLET_CORRUPTION = new DamageSource("scarletCorruption").setMagicDamage().setDamageBypassesArmor();
    public static final DamageSource WAVE_STONE_MAGIC = new DamageSource("wave_stone_magic");
}
