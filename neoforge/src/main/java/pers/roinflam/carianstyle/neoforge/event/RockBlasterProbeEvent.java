package pers.roinflam.carianstyle.neoforge.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 碎岩连带挖相连方块之前，以玩家身份问「这一格能不能动」的破坏事件。<b>只用来问，方块不会因为它被破坏</b>，被取消就跳过这一格。
 * <p>
 * 它是 {@link BlockEvent.BreakEvent} 的子类，在 NeoForge 总线上发：只认 {@code BreakEvent} 的领地 / 保护模组照样收得到，取消它就算「不许」。
 * 别的模组挂在 NeoForge 总线上的监听器想区分探测和真挖，可以判 {@code event instanceof RockBlasterProbeEvent}。
 * 本模组自己的监听挂在模组自带总线上，拿到的是转发过来的包装事件，{@code instanceof} 认不出它；
 * 碎岩自己的 {@code onBreak} 不靠这个判断，挡探测的有两道：第一道是重入保护，发这个事件时这一格已经记进正在连带挖的坐标集，
 * {@code onBreak} 一进来查到就返回；第二道是发事件时拿镐的那只手被临时清空，看到空手也直接返回
 * （这一道也让别的按手上工具范围挖掘的监听器不会再挖一片）。
 * <p>
 * 类名以 {@code ProbeEvent} 结尾是有意的：Mohist 系混合端可以按「{@code BreakEvent} 的子类、简单类名以 {@code ProbeEvent} 结尾」认出权限探测，
 * 只问领地插件、不当成玩家真挖了一格。改名会让这种识别失效。
 */
public class RockBlasterProbeEvent extends BlockEvent.BreakEvent {
    public RockBlasterProbeEvent(Level level, BlockPos pos, BlockState state, Player player) {
        super(level, pos, state, player);
    }
}
