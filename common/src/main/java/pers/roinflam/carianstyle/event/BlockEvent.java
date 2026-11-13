package pers.roinflam.carianstyle.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import pers.roinflam.carianstyle.bus.Event;
import pers.roinflam.carianstyle.bus.ICancellableEvent;

/** 和某个方块有关的事件。 */
public abstract class BlockEvent extends Event {

    public abstract LevelAccessor getLevel();

    public abstract BlockPos getPos();

    public abstract BlockState getState();

    /**
     * 玩家将要破坏方块（{@code destroyBlock} 开头），取消即不破坏。原版本来就会拒绝的情况平台会先预取消或不发。
     * 碎岩连挖的权限探测也经过这里，同样能被领地模组取消。
     */
    public abstract static class BreakEvent extends BlockEvent implements ICancellableEvent {

        public abstract Player getPlayer();
    }
}
