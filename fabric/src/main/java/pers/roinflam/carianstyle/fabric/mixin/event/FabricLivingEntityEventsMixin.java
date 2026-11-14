package pers.roinflam.carianstyle.fabric.mixin.event;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
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
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.event.impl.LivingDeathEventImpl;
import pers.roinflam.carianstyle.event.impl.LivingExperienceDropEventImpl;
import pers.roinflam.carianstyle.event.impl.LivingHealEventImpl;
import pers.roinflam.carianstyle.event.impl.LivingKnockBackEventImpl;
import pers.roinflam.carianstyle.event.impl.MobEffectAddedEventImpl;
import pers.roinflam.carianstyle.event.impl.MobEffectExpiredEventImpl;
import pers.roinflam.carianstyle.event.impl.MobEffectRemoveEventImpl;

/**
 * {@code LivingEntity} 上的逻辑事件，位置照 Forge 1.20.1 打补丁的同一处（伤害三段在 {@code mixin/damage}）。
 * 本模组只对死亡、治疗、生物 tick 取消，别的不写取消分支。
 */
@Mixin(LivingEntity.class)
public abstract class FabricLivingEntityEventsMixin {

    @Shadow
    protected Player lastHurtByPlayer;

    @Shadow
    public abstract MobEffectInstance getEffect(MobEffect effect);

    // Forge 在 tick 第一行发 LivingTickEvent，取消就整个 tick 跳过
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void carianstyle$livingTick(CallbackInfo ci) {
        if (CarianBus.post(new LivingTickEvent((LivingEntity) (Object) this)).isCanceled()) {
            ci.cancel();
        }
    }

    // 玩家不跳过：Player.die 先发再调 super，非服务端玩家会发两次，同 Forge；ServerPlayer.die 不调 super
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void carianstyle$livingDeath(DamageSource source, CallbackInfo ci) {
        LivingDeathEvent event = CarianBus.post(new LivingDeathEventImpl((LivingEntity) (Object) this, source));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    // 原版没有 ≤ 0 的判断，照 Forge 补上
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

    // Forge 把力度转成 float 发事件再写回，这里一律写回，精度一致
    @Inject(method = "knockback", at = @At("HEAD"))
    private void carianstyle$knockBack(double ignoredStrength, double x, double z, CallbackInfo ci,
                                       @Local(argsOnly = true, ordinal = 0) LocalDoubleRef strength) {
        LivingKnockBackEvent event = CarianBus.post(
                new LivingKnockBackEventImpl((LivingEntity) (Object) this, (float) strength.get()));
        strength.set(event.getStrength());
    }

    // canBeAffected 之后、第一次 activeEffects.get 之前（偏移 18）；forceAddEffect 不经过这里，Forge 也不发
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0))
    private void carianstyle$effectAdded(MobEffectInstance instance, Entity source, CallbackInfoReturnable<Boolean> cir) {
        CarianBus.post(new MobEffectAddedEventImpl((LivingEntity) (Object) this, instance));
    }

    // 身上没有这个效果也发（实例为 null），同 Forge
    @Inject(method = "removeEffect", at = @At("HEAD"))
    private void carianstyle$effectRemove(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        CarianBus.post(new MobEffectRemoveEventImpl((LivingEntity) (Object) this, effect, getEffect(effect)));
    }

    // 实例只在操作数栈上、没进局部变量，借 @ModifyArg 拿到再原样返回
    @ModifyArg(method = "removeAllEffects",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;onEffectRemoved(Lnet/minecraft/world/effect/MobEffectInstance;)V"))
    private MobEffectInstance carianstyle$effectRemoveAll(MobEffectInstance instance) {
        CarianBus.post(new MobEffectRemoveEventImpl((LivingEntity) (Object) this, instance.getEffect(), instance));
        return instance;
    }

    // 到期分支、服务端、Iterator.remove 之前（偏移 74）；方法里 MobEffectInstance 局部只有槽 3 一个
    @Inject(method = "tickEffects",
            at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V", ordinal = 0))
    private void carianstyle$effectExpired(CallbackInfo ci, @Local MobEffectInstance instance) {
        CarianBus.post(new MobEffectExpiredEventImpl((LivingEntity) (Object) this, instance));
    }

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
