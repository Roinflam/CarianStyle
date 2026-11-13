package pers.roinflam.carianstyle.init;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import pers.roinflam.carianstyle.CarianContent;
import pers.roinflam.carianstyle.potion.*;

/**
 * 模组药水效果注册类
 * <p>
 * 字段是注册表里的 {@link Holder}：加效果、查效果直接传。声明交给 {@link CarianContent}，
 * 由它在模组加载时触发本类、交给平台注册。
 * </p>
 */
public class CarianStylePotion {

    // ==================== 显示图标的药水效果 ====================

    public static final Holder<MobEffect> SCARLET_ROT =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "scarlet_rot", () -> new MobEffectScarletRot(true, 0xbf2000));

    public static final Holder<MobEffect> BAD_OMEN =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "bad_omen", () -> new MobEffectBadOmen(true, 0x74581f));

    public static final Holder<MobEffect> SLEEP =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "sleep", () -> new MobEffectSleep(true, 0x7038a1));

    public static final Holder<MobEffect> FROSTBITE =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "frostbite", () -> new MobEffectFrostbite(true, 0x2a76a9));

    public static final Holder<MobEffect> GOLDEN_VOW =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "golden_vow", () -> new MobEffectGoldenVow(false, 0xffd700));

    public static final Holder<MobEffect> BLESSING_OF_THE_ERDTREE =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "blessing_of_the_erdtree",
                    () -> new MobEffectBlessingOfTheErdtree(false, 0xffd700));

    public static final Holder<MobEffect> PROTECTION_OF_THE_ERDTREE =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "protection_of_the_erdtree",
                    () -> new MobEffectProtectionOfTheErdtree(false, 0xffd700));

    public static final Holder<MobEffect> HEMORRHAGE =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "hemorrhage", () -> new MobEffectHemorrhage(true, 0xc70000));

    public static final Holder<MobEffect> GRAVITAS =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "gravitas", () -> new MobEffectGravitas(true, 0x7200c5));

    public static final Holder<MobEffect> INCISION =
            CarianContent.registerHolder(Registries.MOB_EFFECT, "incision", () -> new MobEffectIncision(false, 0xff2a00));
}
