package pers.roinflam.carianstyle.fabric.mixin.damage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;

/**
 * 玩家的伤害三段，和攻击蓄力重置挪到 {@code attack} 末尾。
 * <p>
 * {@code Player.actuallyHurt} 不调 super，槽位同 {@link FabricLivingEntityDamageMixin}，只有一次 {@code setAbsorptionAmount}；
 * 方法里调用指令的所有者是 {@code Player}，以后加 INVOKE 注入点要写 {@code Player}。
 * {@code ServerPlayer.hurt} 先做出生保护 / PVP 检查再调 super，不另挂；假玩家对任何伤害都无敌，三段都不发，同 Forge。
 * </p>
 */
@Mixin(Player.class)
public abstract class FabricPlayerDamageMixin {

    // 创造 / 已死 / 难度缩放之前，同 Forge onPlayerAttack
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$attackStage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (CarianBus.post(new LivingAttackEvent((Player) (Object) this, source, amount)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$hurtStage(DamageSource source, float ignored, CallbackInfo ci,
                                       @Local(argsOnly = true) LocalFloatRef amount) {
        Player self = (Player) (Object) this;
        if (self.isInvulnerableTo(source)) {
            return;
        }
        LivingHurtEvent event = CarianBus.post(new LivingHurtEvent(self, source, amount.get()));
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            ci.cancel();
            return;
        }
        amount.set(event.getAmount());
    }

    @ModifyVariable(method = "actuallyHurt", at = @At("STORE"), ordinal = 2)
    private float carianstyle$damageStage(float absorbed,
                                          @Local(argsOnly = true) DamageSource source,
                                          @Local(argsOnly = true) LocalFloatRef amount) {
        LivingDamageEvent event = CarianBus.post(new LivingDamageEvent((Player) (Object) this, source, amount.get()));
        amount.set(event.isCanceled() ? 0.0F : event.getAmount());
        return absorbed;
    }

    /*
     * 原版在 hurt 之前就重置攻击蓄力（偏移 93，全方法唯一一次），整条伤害事件链读到的都是约 0.1；Forge 挪到了方法最后，
     * 本模组的满蓄力判断按出手前的蓄力写。这里先不执行，把原操作存进 @Share，attack 返回时补调，别的模组对这次调用的
     * 包装链照样执行。在重置之前就返回的两条路（目标不可攻击、skipAttackInteraction）本来就不重置，这里也不重置。
     *
     * 别的模组可能在 hurt 之后 ci.cancel() 掉 attack 或中途抛异常，走不到 RETURN，蓄力就一直不重置（能连点满伤害）。
     * 所以推迟时记一笔欠账、补调时销账，下一次玩家 tick 开头还欠着就直接重置。按次数记，嵌套出手内层只销自己那笔。
     */
    @WrapOperation(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V"))
    private void carianstyle$deferAttackStrengthReset(Player self, Operation<Void> original,
                                                      @Share("carianstyle$deferredReset") LocalRef<Operation<Void>> deferred) {
        deferred.set(original);
        carianstyle$owedResets++;
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void carianstyle$runDeferredReset(Entity target, CallbackInfo ci,
                                              @Share("carianstyle$deferredReset") LocalRef<Operation<Void>> deferred) {
        Operation<Void> op = deferred.get();
        if (op != null) {
            deferred.set(null);
            if (carianstyle$owedResets > 0) {
                carianstyle$owedResets--;
            }
            op.call((Player) (Object) this);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void carianstyle$settleOwedReset(CallbackInfo ci) {
        if (carianstyle$owedResets > 0) {
            carianstyle$owedResets = 0;
            ((Player) (Object) this).resetAttackStrengthTicker();
        }
    }

    @Unique
    private int carianstyle$owedResets;
}
