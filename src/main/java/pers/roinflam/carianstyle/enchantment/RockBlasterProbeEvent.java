package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

/**
 * 碎岩连带挖相连方块之前，以玩家身份问「这一格能不能动」的破坏事件。<b>只用来问，方块不会因为它被破坏</b>，被取消就跳过这一格。
 * <p>
 * 它是 {@link BlockEvent.BreakEvent} 的子类，在 Forge 总线上发：只认 {@code BreakEvent} 的领地 / 保护模组照样收得到，取消它就算「不许」。
 * 按破坏发奖励、记日志的监听器想区分探测和真挖，判 {@code event instanceof RockBlasterProbeEvent} 就行。
 * <p>
 * 类名以 {@code ProbeEvent} 结尾是有意的：Mohist 系混合端可以按「{@code BreakEvent} 的子类、简单类名以 {@code ProbeEvent} 结尾」认出权限探测，
 * 只问领地插件、不当成玩家真挖了一格。改名会让这种识别失效。
 */
public class RockBlasterProbeEvent extends BlockEvent.BreakEvent {
    public RockBlasterProbeEvent(Level level, BlockPos pos, BlockState state, Player player) {
        super(level, pos, state, player);
    }
}
