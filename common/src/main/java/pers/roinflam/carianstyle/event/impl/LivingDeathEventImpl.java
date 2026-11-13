package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.LivingDeathEvent;

/** {@link LivingDeathEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class LivingDeathEventImpl extends LivingDeathEvent {

    private final LivingEntity entity;
    private final DamageSource source;

    public LivingDeathEventImpl(LivingEntity entity, DamageSource source) {
        this.entity = entity;
        this.source = source;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public DamageSource getSource() {
        return source;
    }
}
