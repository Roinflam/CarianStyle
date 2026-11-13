package pers.roinflam.carianstyle.fabric.mixin.event;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.impl.CriticalHitEventImpl;
import pers.roinflam.carianstyle.event.impl.LivingDeathEventImpl;
import pers.roinflam.carianstyle.event.impl.PlayerTickPreEventImpl;

/**
 * {@code Player} 上的逻辑事件：玩家 tick 前置、死亡、暴击。攻击蓄力重置挪后和伤害三段在 {@code mixin/damage}。
 */
@Mixin(Player.class)
public abstract class FabricPlayerEventsMixin {

    /** 玩家 tick 前置：{@code Player.tick} 第一行（super.tick 在偏移 135），双端都发，同 NeoForge。 */
    @Inject(method = "tick", at = @At("HEAD"))
    private void carianstyle$playerTickPre(CallbackInfo ci) {
        CarianBus.post(new PlayerTickPreEventImpl((Player) (Object) this));
    }

    /**
     * 死亡：{@code Player.die} 第一行（偏移 2 才调 super），可取消。
     * 非服务端玩家这里一次、super 进 {@code LivingEntity.die} 又一次，共两次，和 NeoForge 一样；
     * 服务端玩家的 {@code ServerPlayer.die} 不调 super，不经过这里。
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void carianstyle$playerDeath(DamageSource source, CallbackInfo ci) {
        LivingDeathEvent event = CarianBus.post(new LivingDeathEventImpl((Player) (Object) this, source));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    /**
     * 暴击判定：{@code attack} 里暴击布尔（槽 9，偏移 335）存进去的那一刻发 {@code CriticalHitEvent}。
     * 这时可见的 boolean 局部依次是槽 7（蓄力够不够）、槽 8（疾跑击退）、槽 9，所以 ordinal = 2。
     * <p>
     * 倍率初值照平台：原版判成暴击给 1.5，否则 1.0（不一律写 1.5，否则监听把非暴击改成暴击时倍率和平台对不上）。
     * 事件改过的倍率存进 {@code @Share} 局部，紧接着偏移 343 那个 1.5F 常量换成它。
     * 后面横扫判断、暴击音效粒子都读这个布尔，改它就等于改「这一下算不算暴击」，同 NeoForge。
     * </p>
     */
    @ModifyVariable(method = "attack", at = @At("STORE"), ordinal = 2)
    private boolean carianstyle$criticalHit(boolean vanillaCritical,
                                            @Local(argsOnly = true) Entity target,
                                            @Share("carianstyle$critMultiplier") LocalFloatRef multiplier) {
        CriticalHitEventImpl event = CarianBus.post(new CriticalHitEventImpl((Player) (Object) this, target,
                vanillaCritical ? 1.5F : 1.0F, vanillaCritical));
        multiplier.set(event.getDamageMultiplier());
        return event.isCriticalHit();
    }

    /**
     * 暴击倍率：{@code attack} 里唯一的 1.5F（偏移 343，只在暴击分支里读），换成事件给的倍率。
     * 这个常量总在上面的布尔存储之后才走到，{@code @Share} 里一定已经有值。
     */
    @ModifyExpressionValue(method = "attack", at = @At(value = "CONSTANT", args = "floatValue=1.5F"))
    private float carianstyle$criticalMultiplier(float vanilla,
                                                 @Share("carianstyle$critMultiplier") LocalFloatRef multiplier) {
        return multiplier.get();
    }
}
