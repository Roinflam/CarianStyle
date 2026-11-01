package pers.roinflam.carianstyle.init;

import net.minecraft.potion.Potion;
import pers.roinflam.carianstyle.mc112.registry.DeferredRegister;
import pers.roinflam.carianstyle.mc112.registry.RegistryObject;
import pers.roinflam.carianstyle.potion.*;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 模组药水效果注册类
 * <p>
 * 1.12.2 版：以前的十个隐藏药水（攻速、移速、隐身、燃烧计时等）改成了内存里的动态属性，不再注册成药水；
 * 旧存档里残留的这些药水由迁移代码处理。
 * </p>
 */
public class CarianStylePotion {

    /**
     * 药水效果延迟注册器
     */
    public static final DeferredRegister<Potion> MOB_EFFECTS =
            DeferredRegister.create(Potion.class, Reference.MOD_ID);

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
}
