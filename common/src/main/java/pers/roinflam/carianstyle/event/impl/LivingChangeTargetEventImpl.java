package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;

/** {@link LivingChangeTargetEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class LivingChangeTargetEventImpl extends LivingChangeTargetEvent {

    private final LivingEntity entity;
    private final LivingEntity newAboutToBeSetTarget;

    public LivingChangeTargetEventImpl(LivingEntity entity, LivingEntity newAboutToBeSetTarget) {
        this.entity = entity;
        this.newAboutToBeSetTarget = newAboutToBeSetTarget;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public LivingEntity getNewAboutToBeSetTarget() {
        return newAboutToBeSetTarget;
    }
}
