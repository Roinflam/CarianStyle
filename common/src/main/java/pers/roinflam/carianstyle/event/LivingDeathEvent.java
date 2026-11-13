package pers.roinflam.carianstyle.event;

import net.minecraft.world.damagesource.DamageSource;
import pers.roinflam.carianstyle.bus.ICancellableEvent;

/**
 * 生物将要死亡（{@code die} 第一行），取消即不死。
 * <p>
 * 和 NeoForge 一样，不是服务端玩家的 {@code Player} 会经 {@code Player#die} 和 {@code LivingEntity#die} 各发一次；
 * 服务端玩家在 {@code gameEvent} 之后发。
 * </p>
 */
public abstract class LivingDeathEvent extends LivingEvent implements ICancellableEvent {

    public abstract DamageSource getSource();
}
