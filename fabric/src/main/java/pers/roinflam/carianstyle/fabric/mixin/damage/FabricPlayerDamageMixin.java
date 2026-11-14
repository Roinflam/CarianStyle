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
import pers.roinflam.carianstyle.base.entity.AbsorptionShield;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;

/**
 * 玩家的伤害三段，和攻击蓄力重置挪到 {@code attack} 末尾。
 * <p>
 * {@code Player.actuallyHurt} 不调 super，结构和 {@code LivingEntity} 那份一样（槽位同 {@link FabricLivingEntityDamageMixin}），
 * 但只有一次 {@code setAbsorptionAmount}、统计记的是受击者自己的 {@code DAMAGE_ABSORBED}；
 * 方法里调用指令的所有者写的是 {@code Player}，以后要加 INVOKE 注入点必须写 {@code Player}。
 * 服务端玩家的 {@code ServerPlayer.hurt} 先做出生保护 / PVP 检查再调 super，不另挂；
 * 假玩家对任何伤害 {@code isInvulnerableTo} 都为真，三段都不发，和 NeoForge 一样。
 * </p>
 */
@Mixin(Player.class)
public abstract class FabricPlayerDamageMixin {

    /** 攻击阶段：{@code Player.hurt} 第一行（无敌 / 创造 / 已死 / 难度缩放之前），和 Forge 的 {@code onPlayerAttack} 同位置。 */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$attackStage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (CarianBus.post(new LivingAttackEvent((Player) (Object) this, source, amount)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    /** 受伤阶段：同 {@link FabricLivingEntityDamageMixin} 的受伤阶段。 */
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

    /**
     * 伤害阶段：同 {@link FabricLivingEntityDamageMixin} 的伤害阶段（槽 4 存完时吸收已扣、统计已定），
     * 发完同样调 {@link AbsorptionShield#afterAbsorb}。
     */
    @ModifyVariable(method = "actuallyHurt", at = @At("STORE"), ordinal = 2)
    private float carianstyle$damageStage(float absorbed,
                                          @Local(argsOnly = true) DamageSource source,
                                          @Local(argsOnly = true) LocalFloatRef amount) {
        Player self = (Player) (Object) this;
        LivingDamageEvent event = CarianBus.post(new LivingDamageEvent(self, source, amount.get()));
        amount.set(event.isCanceled() ? 0.0F : event.getAmount());
        AbsorptionShield.afterAbsorb(self);
        return absorbed;
    }

    /**
     * 攻击蓄力重置挪到 {@code attack} 末尾，和 Forge / NeoForge 一致。
     * <p>
     * 原版 {@code attack} 在算完伤害倍率后、{@code hurt} 之前就 {@code resetAttackStrengthTicker()}（偏移 100，全方法唯一一次），
     * 整条伤害事件链里读到的蓄力都是重置后的值（约 0.1）；Forge / NeoForge 把这一句挪到方法最后，
     * 本模组的满蓄力判断都按「事件里读到的是出手前的蓄力」写。
     * </p>
     * <p>
     * 做法：包住那次调用，先不执行，把「原操作」存进 {@code @Share} 局部；{@code attack} 每个返回处存过就补调。
     * 补调走 {@link Operation#call}，别的模组对同一调用的包装链照样执行，只是时机后移。
     * 原版在重置之前就返回的两条路（目标不可攻击、{@code skipAttackInteraction}）本来就不重置，这里也不重置；
     * 伤害为 0 那条照样重置。弹射物被反弹那条（偏移 170 返回）原版在重置之后，这里照原版补调，
     * NeoForge 那条路径反而不重置（反弹成功直接返回），这一处的差别是有意的。
     * 每次调用各有自己的 {@code @Share} 局部，攻击事件里再让同一个玩家出手互不串。
     * </p>
     * <p>
     * 兜底：别的模组照原版时机写，可能在 {@code hurt} 之后自己 {@code ci.cancel()} 中止 {@code attack}，
     * 或者中途抛异常，这时走不到 RETURN 补调，蓄力就一直不重置（等于能连点满伤害）。
     * 所以推迟时记一笔欠账，补调时销账；下一次玩家 tick 开头还欠着就直接重置。
     * 原版这种情况下重置早已做过，tick 里随后的 {@code ++attackStrengthTicker} 结果和原版一样。
     * 欠账按次数记，嵌套出手时内层先销自己那笔，不会把外层的也销掉。
     * </p>
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

    /**
     * 欠账兜底：{@code attack} 被中途打断时，最晚到这个玩家下一次 tick 开头重置蓄力。
     * 直接调 {@code resetAttackStrengthTicker()}，不走那次调用上别的模组的包装链（那条链已经被打断了）。
     */
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
