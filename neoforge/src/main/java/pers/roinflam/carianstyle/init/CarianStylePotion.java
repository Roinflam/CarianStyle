package pers.roinflam.carianstyle.init;

import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import pers.roinflam.carianstyle.potion.*;
import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.core.registries.Registries;

/**
 * 模组药水效果注册类
 */
public class CarianStylePotion {

    /**
     * 药水效果延迟注册器
     */
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Reference.MOD_ID);

    // ==================== 显示图标的药水效果 ====================

    public static final DeferredHolder<MobEffect, MobEffectScarletRot> SCARLET_ROT =
            MOB_EFFECTS.register("scarlet_rot", () -> new MobEffectScarletRot(true, 0xbf2000));

    public static final DeferredHolder<MobEffect, MobEffectBadOmen> BAD_OMEN =
            MOB_EFFECTS.register("bad_omen", () -> new MobEffectBadOmen(true, 0x74581f));

    public static final DeferredHolder<MobEffect, MobEffectSleep> SLEEP =
            MOB_EFFECTS.register("sleep", () -> new MobEffectSleep(true, 0x7038a1));

    public static final DeferredHolder<MobEffect, MobEffectFrostbite> FROSTBITE =
            MOB_EFFECTS.register("frostbite", () -> new MobEffectFrostbite(true, 0x2a76a9));

    public static final DeferredHolder<MobEffect, MobEffectGoldenVow> GOLDEN_VOW =
            MOB_EFFECTS.register("golden_vow", () -> new MobEffectGoldenVow(false, 0xffd700));

    public static final DeferredHolder<MobEffect, MobEffectBlessingOfTheErdtree> BLESSING_OF_THE_ERDTREE =
            MOB_EFFECTS.register("blessing_of_the_erdtree", () -> new MobEffectBlessingOfTheErdtree(false, 0xffd700));

    public static final DeferredHolder<MobEffect, MobEffectProtectionOfTheErdtree> PROTECTION_OF_THE_ERDTREE =
            MOB_EFFECTS.register("protection_of_the_erdtree", () -> new MobEffectProtectionOfTheErdtree(false, 0xffd700));

    public static final DeferredHolder<MobEffect, MobEffectHemorrhage> HEMORRHAGE =
            MOB_EFFECTS.register("hemorrhage", () -> new MobEffectHemorrhage(true, 0xc70000));

    public static final DeferredHolder<MobEffect, MobEffectGravitas> GRAVITAS =
            MOB_EFFECTS.register("gravitas", () -> new MobEffectGravitas(true, 0x7200c5));

    public static final DeferredHolder<MobEffect, MobEffectIncision> INCISION =
            MOB_EFFECTS.register("incision", () -> new MobEffectIncision(false, 0xff2a00));
}