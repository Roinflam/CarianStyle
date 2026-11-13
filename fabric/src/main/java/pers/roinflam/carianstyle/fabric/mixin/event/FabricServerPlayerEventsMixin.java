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

/**
 * {@code ServerPlayer} 上的逻辑事件：死亡、切维度。
 */
@Mixin(ServerPlayer.class)
public abstract class FabricServerPlayerEventsMixin {

    /**
     * 服务端玩家死亡：{@code ServerPlayer.die} 不调 super，要单独挂。位置照 NeoForge：
     * 偏移 4 的 {@code gameEvent(ENTITY_DIE)} 之后、死亡消息之前，可取消。
     * 取消时 {@code ENTITY_DIE} 游戏事件已经发出（NeoForge 一样）。
     */
    @Inject(method = "die",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/core/Holder;)V",
                    ordinal = 0, shift = At.Shift.AFTER),
            cancellable = true)
    private void carianstyle$serverPlayerDeath(DamageSource source, CallbackInfo ci) {
        LivingDeathEvent event = CarianBus.post(new LivingDeathEventImpl((ServerPlayer) (Object) this, source));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    /**
     * 切维度：{@code triggerDimensionChangeTriggers} 末尾。它只在 {@code changeDimension} 里调一次（偏移 323），
     * 这时玩家已经在新世界、重生包已发。
     * <p>
     * 比 NeoForge 早：NeoForge 在 {@code changeDimension} 最后，能力 / 世界信息 / 玩家信息 / 药水效果包和
     * {@code postDimensionTransition} 之后才发。现有唯一的监听（给新维度发效果同步包）不受影响；
     * 以后有监听要求客户端已经收到效果包，再挪到 {@code changeDimension} 的 RETURN（要滤掉返回 null 的失败路径）。
     * 不用 Fabric API 的 {@code AFTER_PLAYER_CHANGE_WORLD}：它在跨维度重生时多发一次。
     * </p>
     */
    @Inject(method = "triggerDimensionChangeTriggers", at = @At("TAIL"))
    private void carianstyle$changedDimension(ServerLevel origin, CallbackInfo ci) {
        CarianBus.post(new PlayerChangedDimensionEventImpl((ServerPlayer) (Object) this));
    }
}
