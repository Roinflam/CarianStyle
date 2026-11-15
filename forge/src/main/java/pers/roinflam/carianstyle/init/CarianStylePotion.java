package pers.roinflam.carianstyle.init;

import net.minecraft.core.registries.Registries;
import pers.roinflam.carianstyle.CarianContent;
import pers.roinflam.carianstyle.potion.*;

import java.util.function.Supplier;

/**
 * 模组药水效果注册类
 */
public class CarianStylePotion {

    // ==================== 显示图标的药水效果 ====================

    public static final Supplier<MobEffectScarletRot> SCARLET_ROT =
            CarianContent.register(Registries.MOB_EFFECT, "scarlet_rot", () -> new MobEffectScarletRot(true, 0xbf2000));

    public static final Supplier<MobEffectBadOmen> BAD_OMEN =
            CarianContent.register(Registries.MOB_EFFECT, "bad_omen", () -> new MobEffectBadOmen(true, 0x74581f));

    public static final Supplier<MobEffectSleep> SLEEP =
            CarianContent.register(Registries.MOB_EFFECT, "sleep", () -> new MobEffectSleep(true, 0x7038a1));

    public static final Supplier<MobEffectFrostbite> FROSTBITE =
            CarianContent.register(Registries.MOB_EFFECT, "frostbite", () -> new MobEffectFrostbite(true, 0x2a76a9));

    public static final Supplier<MobEffectGoldenVow> GOLDEN_VOW =
            CarianContent.register(Registries.MOB_EFFECT, "golden_vow", () -> new MobEffectGoldenVow(false, 0xffd700));

    public static final Supplier<MobEffectBlessingOfTheErdtree> BLESSING_OF_THE_ERDTREE =
            CarianContent.register(Registries.MOB_EFFECT, "blessing_of_the_erdtree", () -> new MobEffectBlessingOfTheErdtree(false, 0xffd700));

    public static final Supplier<MobEffectProtectionOfTheErdtree> PROTECTION_OF_THE_ERDTREE =
            CarianContent.register(Registries.MOB_EFFECT, "protection_of_the_erdtree", () -> new MobEffectProtectionOfTheErdtree(false, 0xffd700));

    public static final Supplier<MobEffectHemorrhage> HEMORRHAGE =
            CarianContent.register(Registries.MOB_EFFECT, "hemorrhage", () -> new MobEffectHemorrhage(true, 0xc70000));

    public static final Supplier<MobEffectGravitas> GRAVITAS =
            CarianContent.register(Registries.MOB_EFFECT, "gravitas", () -> new MobEffectGravitas(true, 0x7200c5));

    public static final Supplier<MobEffectIncision> INCISION =
            CarianContent.register(Registries.MOB_EFFECT, "incision", () -> new MobEffectIncision(false, 0xff2a00));
}