package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.LivingKnockBackEvent;

/** {@link LivingKnockBackEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class LivingKnockBackEventImpl extends LivingKnockBackEvent {

    private final LivingEntity entity;
    private float strength;

    public LivingKnockBackEventImpl(LivingEntity entity, float strength) {
        this.entity = entity;
        this.strength = strength;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public float getStrength() {
        return strength;
    }

    @Override
    public void setStrength(float strength) {
        this.strength = strength;
    }
}
