package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import pers.roinflam.carianstyle.event.EntityJoinLevelEvent;

/** {@link EntityJoinLevelEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class EntityJoinLevelEventImpl extends EntityJoinLevelEvent {

    private final Entity entity;
    private final Level level;
    private final boolean loadedFromDisk;

    public EntityJoinLevelEventImpl(Entity entity, Level level, boolean loadedFromDisk) {
        this.entity = entity;
        this.level = level;
        this.loadedFromDisk = loadedFromDisk;
    }

    @Override
    public Entity getEntity() {
        return entity;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }
}
