package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.MobEffectEvent;

/** {@link MobEffectEvent.Expired} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class MobEffectExpiredEventImpl extends MobEffectEvent.Expired {

    private final LivingEntity entity;
    private final MobEffectInstance effectInstance;

    public MobEffectExpiredEventImpl(LivingEntity entity, MobEffectInstance effectInstance) {
        this.entity = entity;
        this.effectInstance = effectInstance;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public MobEffectInstance getEffectInstance() {
        return effectInstance;
    }
}
