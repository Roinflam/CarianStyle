package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.impl.LivingDeathEventImpl;
import pers.roinflam.carianstyle.event.impl.PlayerChangedDimensionEventImpl;

/** {@code ServerPlayer} 上的逻辑事件：死亡、切维度。 */
@Mixin(ServerPlayer.class)
public abstract class FabricServerPlayerEventsMixin {

    // ServerPlayer.die 不调 super；Forge 在 gameEvent(ENTITY_DIE) 之后发（偏移 4）
    @Inject(method = "die",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;)V",
                    ordinal = 0, shift = At.Shift.AFTER),
            cancellable = true)
    private void carianstyle$serverPlayerDeath(DamageSource source, CallbackInfo ci) {
        LivingDeathEvent event = CarianBus.post(new LivingDeathEventImpl((ServerPlayer) (Object) this, source));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    /*
     * 1.20.1 有两处调用（changeDimension、跨世界 teleportTo），TAIL 都盖住。比 Forge 发得早（Forge 在发完能力、
     * 效果包之后），唯一的监听只发新维度的效果同步包，这时玩家已在新世界，不受影响。
     */
    @Inject(method = "triggerDimensionChangeTriggers", at = @At("TAIL"))
    private void carianstyle$changedDimension(ServerLevel origin, CallbackInfo ci) {
        CarianBus.post(new PlayerChangedDimensionEventImpl((ServerPlayer) (Object) this));
    }
}
