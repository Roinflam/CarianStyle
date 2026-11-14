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

/** {@code Player} 上的逻辑事件：玩家 tick 前、死亡、暴击。 */
@Mixin(Player.class)
public abstract class FabricPlayerEventsMixin {

    // 两端都发，客户端只有本地玩家走到
    @Inject(method = "tick", at = @At("HEAD"))
    private void carianstyle$playerTickPre(CallbackInfo ci) {
        CarianBus.post(new PlayerTickPreEventImpl((Player) (Object) this));
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void carianstyle$playerDeath(DamageSource source, CallbackInfo ci) {
        LivingDeathEvent event = CarianBus.post(new LivingDeathEventImpl((Player) (Object) this, source));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    /*
     * 1.20.1 的暴击布尔在槽 8、存两次：先存「蓄力满 + 下落 + 不在地上…」（偏移 250），再 && !isSprinting()（偏移 269）。
     * 只改第二次，才是 Forge 发 CriticalHitEvent 时拿到的那个值。直接按槽号选，1.21.1 那份的 ordinal 在这里对不上。
     * 倍率经 @Share 传给下面换 1.5F 的那个处理器，嵌套出手互不串。
     */
    @ModifyVariable(method = "attack", at = @At(value = "STORE", ordinal = 1), index = 8)
    private boolean carianstyle$criticalHit(boolean vanillaCritical,
                                            @Local(argsOnly = true) Entity target,
                                            @Share("carianstyle$critMultiplier") LocalFloatRef multiplier) {
        CriticalHitEventImpl event = CarianBus.post(new CriticalHitEventImpl((Player) (Object) this, target,
                vanillaCritical ? 1.5F : 1.0F, vanillaCritical));
        multiplier.set(event.getDamageMultiplier());
        return event.isCriticalHit();
    }

    // 方法里只有这一个 1.5F（偏移 277）
    @ModifyExpressionValue(method = "attack", at = @At(value = "CONSTANT", args = "floatValue=1.5F"))
    private float carianstyle$criticalMultiplier(float vanilla,
                                                 @Share("carianstyle$critMultiplier") LocalFloatRef multiplier) {
        return multiplier.get();
    }
}
