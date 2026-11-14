package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.LivingEntity;

/** 和某个生物有关的事件。模组自己发的生物 tick、伤害三段也继承它。 */
public abstract class LivingEvent extends EntityEvent {

    @Override
    public abstract LivingEntity getEntity();
}
