package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;

/** {@link LivingExperienceDropEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class LivingExperienceDropEventImpl extends LivingExperienceDropEvent {

    private final LivingEntity entity;
    private final Player attackingPlayer;
    private int droppedExperience;

    public LivingExperienceDropEventImpl(LivingEntity entity, Player attackingPlayer, int droppedExperience) {
        this.entity = entity;
        this.attackingPlayer = attackingPlayer;
        this.droppedExperience = droppedExperience;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    @Override
    public Player getAttackingPlayer() {
        return attackingPlayer;
    }

    @Override
    public int getDroppedExperience() {
        return droppedExperience;
    }

    @Override
    public void setDroppedExperience(int droppedExperience) {
        this.droppedExperience = droppedExperience;
    }
}
