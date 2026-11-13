package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import pers.roinflam.carianstyle.event.EntityLeaveLevelEvent;

/** {@link EntityLeaveLevelEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class EntityLeaveLevelEventImpl extends EntityLeaveLevelEvent {

    private final Entity entity;
    private final Level level;

    public EntityLeaveLevelEventImpl(Entity entity, Level level) {
        this.entity = entity;
        this.level = level;
    }

    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public Level getLevel() {
        return level;
    }
}
