package pers.roinflam.carianstyle.event;

import net.minecraft.server.level.ServerLevel;

/**
 * 方块被破坏后结算掉落和经验。Forge 1.20.1 没有这个事件，1.20.1 两个平台都不发；类留着是为了两个游戏版本的 common 一致。
 * <p>
 * Fabric 侧发在放经验的那一处：经验为 0 时不发，也不提供掉落物列表；现有监听按坐标过滤、只改经验，结果一致。
 * </p>
 */
public abstract class BlockDropsEvent extends BlockEvent {

    @Override
    public abstract ServerLevel getLevel();

    public abstract int getDroppedExperience();

    public abstract void setDroppedExperience(int experience);
}
