package pers.roinflam.carianstyle.event;

import pers.roinflam.carianstyle.bus.ICancellableEvent;

/** 生物将要回血（{@code heal} 第一行）。可以改回血量；取消或改到 0 及以下就不回。 */
public abstract class LivingHealEvent extends LivingEvent implements ICancellableEvent {

    public abstract float getAmount();

    public abstract void setAmount(float amount);
}
