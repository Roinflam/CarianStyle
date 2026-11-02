package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * 生物 tick 事件：在 {@code LivingEntity.tick()} 开头发出，可取消（取消后本次 tick 整个跳过）。
 * <p>
 * <b>问题：</b>1.21.1 的 NeoForge 去掉了 {@code LivingEvent.LivingTickEvent}，最接近的
 * {@code EntityTickEvent.Pre} 是在世界的实体循环里、{@code entity.tick()} 之前发的。对服务端玩家来说，
 * {@code LivingEntity.tick} 是在网络连接的 tick 里跑的，晚于所有世界 tick；换成 {@code EntityTickEvent.Pre}
 * 之后，玩家这一发会从「玩家 tick 前置事件之后、自己的 tick 开头」挪到「世界 tick 里、玩家 tick 前置事件之前」，
 * 持续伤害、动态属性、光环对其它生物的先后顺序都会变。
 * </p>
 * <p>
 * <b>做法：</b>自己建这个事件，由 {@code MixinLivingEntityTick} 在 {@code LivingEntity.tick()} 头部发出，
 * 位置和 1.20.1 Forge 的 {@code onLivingTick} 完全一样：子类覆写 {@code tick} 后调 {@code super.tick()} 时才走到这里。
 * 监听代码只改 import，用法和 1.20.1 相同（{@link #getEntity()}）。
 * </p>
 * <p>
 * 事件在 {@code NeoForge.EVENT_BUS} 上发，客户端和服务端都会发。
 * </p>
 */
public class LivingTickEvent extends LivingEvent implements ICancellableEvent {

    public LivingTickEvent(LivingEntity entity) {
        super(entity);
    }
}
