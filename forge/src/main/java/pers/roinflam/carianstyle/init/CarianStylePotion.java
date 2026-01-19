package pers.roinflam.carianstyle.init;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.carianstyle.potion.*;
import pers.roinflam.carianstyle.potion.hide.*;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 模组药水效果注册类
 */
public class CarianStylePotion {

    /**
     * 药水效果延迟注册器
     */
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Reference.MOD_ID);

    // ==================== 显示图标的药水效果 ====================

    public static final RegistryObject<MobEffectScarletRot> SCARLET_ROT =
            MOB_EFFECTS.register("scarlet_rot", () -> new MobEffectScarletRot(true, 0xbf2000));

    public static final RegistryObject<MobEffectBadOmen> BAD_OMEN =
            MOB_EFFECTS.register("bad_omen", () -> new MobEffectBadOmen(true, 0x74581f));

    public static final RegistryObject<MobEffectSleep> SLEEP =
            MOB_EFFECTS.register("sleep", () -> new MobEffectSleep(true, 0x7038a1));

    public static final RegistryObject<MobEffectFrostbite> FROSTBITE =
            MOB_EFFECTS.register("frostbite", () -> new MobEffectFrostbite(true, 0x2a76a9));

    public static final RegistryObject<MobEffectGoldenVow> GOLDEN_VOW =
            MOB_EFFECTS.register("golden_vow", () -> new MobEffectGoldenVow(false, 0xffd700));

    public static final RegistryObject<MobEffectBlessingOfTheErdtree> BLESSING_OF_THE_ERDTREE =
            MOB_EFFECTS.register("blessing_of_the_erdtree", () -> new MobEffectBlessingOfTheErdtree(false, 0xffd700));

    public static final RegistryObject<MobEffectProtectionOfTheErdtree> PROTECTION_OF_THE_ERDTREE =
            MOB_EFFECTS.register("protection_of_the_erdtree", () -> new MobEffectProtectionOfTheErdtree(false, 0xffd700));

    public static final RegistryObject<MobEffectHemorrhage> HEMORRHAGE =
            MOB_EFFECTS.register("hemorrhage", () -> new MobEffectHemorrhage(true, 0xc70000));

    public static final RegistryObject<MobEffectGravitas> GRAVITAS =
            MOB_EFFECTS.register("gravitas", () -> new MobEffectGravitas(true, 0x7200c5));

    public static final RegistryObject<MobEffectIncision> INCISION =
            MOB_EFFECTS.register("incision", () -> new MobEffectIncision(false, 0xff2a00));

    // ==================== 隐藏的药水效果 ====================

    public static final RegistryObject<MobEffectDoomedDeath> DOOMED_DEATH =
            MOB_EFFECTS.register("doomed_death", () -> new MobEffectDoomedDeath(true, 0));

    public static final RegistryObject<MobEffectStealth> STEALTH =
            MOB_EFFECTS.register("stealth", () -> new MobEffectStealth(false, 0));

    public static final RegistryObject<MobEffectSpeedBoost> SPEED_BOOST =
            MOB_EFFECTS.register("speed_boost", () -> new MobEffectSpeedBoost(false, 0));

    public static final RegistryObject<MobEffectAttackBoost> ATTACK_BOOST =
            MOB_EFFECTS.register("attack_boost", () -> new MobEffectAttackBoost(false, 0));

    public static final RegistryObject<MobEffectHowlShabriri> HOWL_SHABRIRI =
            MOB_EFFECTS.register("howl_shabriri", () -> new MobEffectHowlShabriri(true, 0xe3c600));

    public static final RegistryObject<MobEffectDragoncrestGreatshield> DRAGONCREST_GREATSHIELD =
            MOB_EFFECTS.register("dragoncrest_greatshield", () -> new MobEffectDragoncrestGreatshield(false, 0));

    public static final RegistryObject<MobEffectCragblade> CRAGBLADE =
            MOB_EFFECTS.register("cragblade", () -> new MobEffectCragblade(true, 0));

    // ==================== 火焰燃烧效果 ====================

    public static final RegistryObject<MobEffectDoomedDeathBurning> DOOMED_DEATH_BURNING =
            MOB_EFFECTS.register("doomed_death_burning", () -> new MobEffectDoomedDeathBurning(true, 0));

    public static final RegistryObject<MobEffectDestructionFireBurning> DESTRUCTION_FIRE_BURNING =
            MOB_EFFECTS.register("destruction_fire_burning", () -> new MobEffectDestructionFireBurning(true, 0));

    public static final RegistryObject<MobEffectEpilepsyFireBurning> EPILEPSY_FIRE_BURNING =
            MOB_EFFECTS.register("epilepsy_fire_burning", () -> new MobEffectEpilepsyFireBurning(true, 0));
}