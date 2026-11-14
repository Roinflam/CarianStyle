package pers.roinflam.carianstyle.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.nio.file.Path;

/**
 * 两端都要用的平台能力：common 里的代码需要加载器提供的东西时只经这里取，不直接 import 加载器的类。
 * <p>
 * 实现由各平台模块在 {@code META-INF/services/pers.roinflam.carianstyle.platform.Platform} 里登记，
 * 用 {@link Services#platform()} 取。只放「common 调平台」的方法；平台调 common 的（入口、配置登记、
 * 挂数据包、事件转发）由平台直接调 common 的静态方法，不进接口。
 * </p>
 */
public interface Platform {

    /** 当前进程是不是客户端（单人游戏的内置服务端也算客户端进程）。 */
    boolean isClient();

    /** 是否开发环境（开发时才做的自检用它判断，正式包里一律 false）。 */
    boolean isDevelopment();

    /** 配置目录（游戏目录下的 {@code config}），本模组自己读写的 JSON 放这里。 */
    Path configDir();

    /** 是不是加载器自己的假玩家（假玩家没有真连接，不发包、不算真人）。 */
    boolean isFakePlayer(ServerPlayer player);

    /**
     * 多部位生物的某个部位所属的本体；不是部位就返回 null。
     * 命中末影龙、下界合金巨兽这类生物时，伤害要转给本体才算数。
     */
    Entity partParent(Entity entity);

    /** 实体上跟着存档走的自定义数据，本模组只在里面放自己的键。 */
    CompoundTag persistentData(Entity entity);

    /**
     * 以玩家身份问一格方块能不能破坏，不真的破坏。
     * 领地、保护类模组在这一步拒绝就返回 false。
     */
    boolean probeBlockBreak(Level level, BlockPos pos, BlockState state, Player player);

    /**
     * 按「玩家破坏」移除一格方块，返回是否真的移除了。
     * <p>
     * <b>不含 {@code playerWillDestroy}</b>：破坏前的那一步（粒子、音效、方块自己的善后）由调用方先调原版的
     * {@code Block#playerWillDestroy}，两个平台一样；这里只做真正移除那一步（Forge 上是 {@code onDestroyedByPlayer}）。
     * 移除后的 {@code destroy}、掉落也由调用方照旧做。
     * </p>
     *
     * @param willHarvest 之后还要不要掉落（抑制掉落时传 false）
     * @param fluid       这一格原来的流体，移除后要留下的就是它
     */
    boolean destroyByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                            boolean willHarvest, FluidState fluid);
}
