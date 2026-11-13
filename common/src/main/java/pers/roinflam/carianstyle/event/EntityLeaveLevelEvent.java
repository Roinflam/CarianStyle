package pers.roinflam.carianstyle.event;

import net.minecraft.world.level.Level;

/** 实体离开世界（卸载、死亡移除、玩家下线 / 切维度）。Fabric 侧只在服务端发，同 {@link EntityJoinLevelEvent}。 */
public abstract class EntityLeaveLevelEvent extends EntityEvent {

    public abstract Level getLevel();
}
