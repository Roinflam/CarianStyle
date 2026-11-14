package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.MobEffectEvent;

/** {@link MobEffectEvent.Remove} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class MobEffectRemoveEventImpl extends MobEffectEvent.Remove {

    private final LivingEntity entity;
    private final MobEffect effect;
    private final MobEffectInstance effectInstance;

    public MobEffectRemoveEventImpl(LivingEntity entity, MobEffect effect, MobEffectInstance effectInstance) {
        this.entity = entity;
        this.effect = effect;
        this.effectInstance = effectInstance;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public MobEffect getEffect() {
        return effect;
    }

    @Override
    public MobEffectInstance getEffectInstance() {
        return effectInstance;
    }
}
