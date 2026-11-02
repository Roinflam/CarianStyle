package pers.roinflam.carianstyle.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.event.LivingTickEvent;

/**
 * 在 {@code LivingEntity.tick()} 开头发出本模组的 {@link LivingTickEvent}。
 * <p>
 * <b>为什么在这里：</b>1.20.1 Forge 的 {@code LivingTickEvent} 就是在 {@code LivingEntity.tick()} 第一行发的。
 * 1.21.1 的 NeoForge 没有这个事件，{@code EntityTickEvent.Pre} 的位置在世界实体循环里，对服务端玩家会挪到
 * 玩家 tick 前置事件之前，持续伤害、动态属性、光环的先后顺序都会变（详见 {@link LivingTickEvent}）。
 * 头部注入和 Forge 补丁的位置完全一样：子类覆写 {@code tick} 后调 {@code super.tick()} 时才走到这里。
 * </p>
 * <p>
 * 取消事件则整个 {@code tick} 跳过，和 Forge 的语义相同；本模组现有的监听都不取消。
 * 目标是 1.21.1 {@code LivingEntity#tick()}（Mojang 名，运行期不重映射）。
 * </p>
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityTick {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void carianstyle$fireLivingTick(CallbackInfo ci) {
        if (NeoForge.EVENT_BUS.post(new LivingTickEvent((LivingEntity) (Object) this)).isCanceled()) {
            ci.cancel();
        }
    }
}
