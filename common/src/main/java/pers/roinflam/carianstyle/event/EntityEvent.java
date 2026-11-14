package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.Entity;
import pers.roinflam.carianstyle.bus.Event;

/** 和某个实体有关的事件。 */
public abstract class EntityEvent extends Event {

    public abstract Entity getEntity();
}
