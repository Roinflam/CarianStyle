package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.bus.ICancellableEvent;

/**
 * 生物 tick 事件：在 {@code LivingEntity.tick()} 开头发出，可取消（取消后本次 tick 整个跳过）。
 * 子类覆写 {@code tick} 后调 {@code super.tick()} 时才走到这里。
 * <p>
 * Forge 上直通转发 Forge 的 {@code LivingEvent.LivingTickEvent}（同一位置）；Fabric 上由 fabric 模块的 mixin
 * 在 {@code tick} 头部发。客户端和服务端都会发。
 * </p>
 */
public class LivingTickEvent extends LivingEvent implements ICancellableEvent {

    private final LivingEntity entity;

    public LivingTickEvent(LivingEntity entity) {
        this.entity = entity;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }
}
