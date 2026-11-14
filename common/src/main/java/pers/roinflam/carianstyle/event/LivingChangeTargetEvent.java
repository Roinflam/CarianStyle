package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.bus.ICancellableEvent;

/** 怪物将要换攻击目标（{@code Mob#setTarget}、大脑写攻击目标记忆之前）。取消即保持原目标。 */
public abstract class LivingChangeTargetEvent extends LivingEvent implements ICancellableEvent {

    /** 新目标；清除目标时为空。 */
    public abstract LivingEntity getNewAboutToBeSetTarget();
}
