package pers.roinflam.carianstyle.source;

import net.minecraft.util.DamageSource;

public class NewDamageSource {
    public static final DamageSource EPILEPSY_FIRE = new DamageSource("unendurableFrenzy").setDamageBypassesArmor();
    public static final DamageSource FROSTBITE = new DamageSource("frostbite");
    public static final DamageSource SCARLET_ROT = new DamageSource("scarletRot").setMagicDamage().setDamageBypassesArmor();
    public static final DamageSource WAVE_STONE_MAGIC = new DamageSource("wave_stone_magic");
    public static final DamageSource HEMORRHAGE = new DamageSource("hemorrhage").setDamageBypassesArmor();
}
