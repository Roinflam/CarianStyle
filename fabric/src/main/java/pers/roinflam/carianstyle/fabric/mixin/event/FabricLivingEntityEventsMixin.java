package pers.roinflam.carianstyle.fabric.mixin.event;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;
import pers.roinflam.carianstyle.event.LivingHealEvent;
import pers.roinflam.carianstyle.event.LivingKnockBackEvent;
import pers.roinflam.carianstyle.event.impl.LivingDeathEventImpl;
import pers.roinflam.carianstyle.event.impl.LivingExperienceDropEventImpl;
import pers.roinflam.carianstyle.event.impl.LivingHealEventImpl;
import pers.roinflam.carianstyle.event.impl.LivingKnockBackEventImpl;
import pers.roinflam.carianstyle.event.impl.MobEffectAddedEventImpl;
import pers.roinflam.carianstyle.event.impl.MobEffectExpiredEventImpl;
import pers.roinflam.carianstyle.event.impl.MobEffectRemoveEventImpl;

/**
 * {@code LivingEntity} 上的逻辑事件：死亡、治疗、击退、药水效果三种（施加 / 移除 / 到期）、死亡经验。
 * <p>
 * 位置照 NeoForge 1.21.1 打补丁的同一处（伤害三段另见 {@code mixin/damage}）。这几个事件除死亡和治疗外，
 * 本模组的监听都不取消，所以不写取消分支；NeoForge 那边取消了会提前返回的地方，这里照原版往下走。
 * 注入点的字节码偏移按 1.21.1 原版（Mojmap）{@code javap -c -p} 核过，写在各方法注释里。
 */
@Mixin(LivingEntity.class)
public abstract class FabricLivingEntityEventsMixin {

    @Shadow
    protected Player lastHurtByPlayer;

    @Shadow
    public abstract MobEffectInstance getEffect(Holder<MobEffect> effect);

    /**
     * 死亡：{@code die} 第一行，可取消（NeoForge {@code CommonHooks.onLivingDeath} 同位置）。
     * <p>
     * 玩家不在这里跳过：{@code Player.die} 先发一次再调 super，非服务端玩家会发两次，和 NeoForge 一样；
     * {@code ServerPlayer.die} 不调 super，只走它自己那一处（{@code FabricServerPlayerEventsMixin}）。
     * 取消后 Fabric API 在 {@code die} 末尾的 {@code AFTER_DEATH} 也不会发。
     * </p>
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void carianstyle$livingDeath(DamageSource source, CallbackInfo ci) {
        LivingDeathEvent event = CarianBus.post(new LivingDeathEventImpl((LivingEntity) (Object) this, source));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    /**
     * 治疗：{@code heal} 第一行改回复量；取消按 0 算，结果 ≤ 0 直接返回（原版没有这个判断，照 NeoForge 补上）。
     */
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void carianstyle$livingHeal(float ignored, CallbackInfo ci,
                                        @Local(argsOnly = true) LocalFloatRef amount) {
        LivingHealEvent event = CarianBus.post(new LivingHealEventImpl((LivingEntity) (Object) this, amount.get()));
        float result = event.isCanceled() ? 0.0F : event.getAmount();
        amount.set(result);
        if (result <= 0.0F) {
            ci.cancel();
        }
    }

    /**
     * 击退：{@code knockback(DDD)} 第一行（击退抗性在偏移 6 才读），改的是力度。
     * NeoForge 发事件时把力度转成 float 再写回参数，这里同样一律写回，精度和它一致。
     */
    @Inject(method = "knockback", at = @At("HEAD"))
    private void carianstyle$knockBack(double ignoredStrength, double x, double z, CallbackInfo ci,
                                       @Local(argsOnly = true, ordinal = 0) LocalDoubleRef strength) {
        LivingKnockBackEvent event = CarianBus.post(
                new LivingKnockBackEventImpl((LivingEntity) (Object) this, (float) strength.get()));
        strength.set(event.getStrength());
    }

    /**
     * 施加效果：{@code addEffect(MobEffectInstance, Entity)} 里 {@code canBeAffected} 判断通过之后、
     * 第一次 {@code activeEffects.get} 之前（偏移 18），即 NeoForge 发 {@code MobEffectEvent.Added} 的位置。
     * {@code forceAddEffect} 不经过这里，NeoForge 也不发。
     */
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void carianstyle$effectAdded(MobEffectInstance instance, Entity source, CallbackInfoReturnable<Boolean> cir) {
        CarianBus.post(new MobEffectAddedEventImpl((LivingEntity) (Object) this, instance));
    }

    /**
     * 移除单个效果：{@code removeEffect(Holder)} 第一行，身上没有这个效果也发（实例为 null），同 NeoForge。
     * 蜂蜜瓶走这里。
     */
    @Inject(method = "removeEffect", at = @At("HEAD"))
    private void carianstyle$effectRemove(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        CarianBus.post(new MobEffectRemoveEventImpl((LivingEntity) (Object) this, effect, getEffect(effect)));
    }

    /**
     * 清空效果：{@code removeAllEffects} 循环里每个效果 {@code onEffectRemoved} 之前（偏移 48）各发一次。
     * 实例这时只在操作数栈上、没进局部变量，所以借 {@code @ModifyArg} 拿到它、原样返回。牛奶、不死图腾走这里。
     */
    @ModifyArg(method = "removeAllEffects",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;onEffectRemoved(Lnet/minecraft/world/effect/MobEffectInstance;)V"))
    private MobEffectInstance carianstyle$effectRemoveAll(MobEffectInstance instance) {
        CarianBus.post(new MobEffectRemoveEventImpl((LivingEntity) (Object) this, instance.getEffect(), instance));
        return instance;
    }

    /**
     * 效果到期：{@code tickEffects} 里到期分支、只在服务端、{@code Iterator.remove} 之前（偏移 74，端判断在偏移 63～70）。
     * 这个方法里 {@code MobEffectInstance} 类型的局部只有槽 3 一个。
     */
    @Inject(method = "tickEffects",
            at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V", ordinal = 0))
    private void carianstyle$effectExpired(CallbackInfo ci, @Local MobEffectInstance instance) {
        CarianBus.post(new MobEffectExpiredEventImpl((LivingEntity) (Object) this, instance));
    }

    /**
     * 死亡经验：{@code dropExperience(Entity)} 里 {@code ExperienceOrb.award} 的第 3 个参数（偏移 72），
     * 攻击者取 {@code lastHurtByPlayer}，同 NeoForge {@code EventHooks.getExperienceDrop}。
     */
    @ModifyArg(method = "dropExperience",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
            index = 2)
    private int carianstyle$experienceDrop(int experience) {
        LivingExperienceDropEvent event = CarianBus.post(
                new LivingExperienceDropEventImpl((LivingEntity) (Object) this, lastHurtByPlayer, experience));
        return event.getDroppedExperience();
    }
}
