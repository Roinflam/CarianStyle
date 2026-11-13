package pers.roinflam.carianstyle.event.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import pers.roinflam.carianstyle.event.BlockEvent;

/** {@link BlockEvent.BreakEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class BreakEventImpl extends BlockEvent.BreakEvent {

    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;
    private final Player player;

    public BreakEventImpl(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.player = player;
    }

    @Override
    public LevelAccessor getLevel() {
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
    public Player getPlayer() {
        return player;
    }
}
