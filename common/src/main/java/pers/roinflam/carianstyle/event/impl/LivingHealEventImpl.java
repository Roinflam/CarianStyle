package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.LivingHealEvent;

/** {@link LivingHealEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class LivingHealEventImpl extends LivingHealEvent {

    private final LivingEntity entity;
    private float amount;

    public LivingHealEventImpl(LivingEntity entity, float amount) {
        this.entity = entity;
        this.amount = amount;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public float getAmount() {
        return amount;
    }

    @Override
    public void setAmount(float amount) {
        this.amount = amount;
    }
}
