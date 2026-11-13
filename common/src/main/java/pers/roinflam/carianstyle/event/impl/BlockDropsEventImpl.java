package pers.roinflam.carianstyle.event.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import pers.roinflam.carianstyle.event.BlockDropsEvent;

/** {@link BlockDropsEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class BlockDropsEventImpl extends BlockDropsEvent {

    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private int droppedExperience;

    public BlockDropsEventImpl(ServerLevel level, BlockPos pos, BlockState state, int droppedExperience) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.droppedExperience = droppedExperience;
    }

    @Override
    public ServerLevel getLevel() {
        return level;
    }

    @Override
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public BlockState getState() {
        return state;
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
