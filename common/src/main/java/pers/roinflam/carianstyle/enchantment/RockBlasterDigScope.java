package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 碎岩者只从玩家亲手挖掉的那一格连带挖，外加每人每 tick 的连带格数上限。
 * <p>
 * 玩家自己挖方块在 {@code ServerPlayerGameMode} 里只有两处：收到挖掘包后的 {@code destroyAndAck}，
 * 和挖得比服务端算的快时 {@code tick()} 里补的那次 {@code destroyBlock}。{@code mixin.ServerPlayerGameModeMixin}
 * 在这两处前后进出作用域。别的模组的范围挖掘、连锁挖掘、机器代挖破坏的格子都不在作用域里；不拦的话，
 * 范围工具挖出的每一格又各连带一片，挖掘量成倍叠加。
 * </p>
 * <p>
 * 破坏事件里只登记要连带挖什么，等 {@code destroyBlock} 返回、中心那一格不在了才动手。
 * 有的模组为了问权限会发并不真正破坏方块的破坏事件，坐标可能正好是玩家在挖的这一格；
 * 中心那一格的事件被领地取消时也一样，这两种情况都不会连带挖。
 * </p>
 */
public final class RockBlasterDigScope {

    /** 最大范围配得小时，一次挖满也到不了这个数，同一 tick 里挖掘包和补挖各来一次，第二次也还能连带 */
    private static final int MIN_PER_TICK = 512;

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private static final Map<UUID, int[]> used = new HashMap<>();
    private static int usedTick = Integer.MIN_VALUE;

    private RockBlasterDigScope() {
    }

    private static final class Scope {
        final Player player;
        final BlockPos pos;
        final Scope outer;
        Runnable pending;

        Scope(Player player, BlockPos pos, Scope outer) {
            this.player = player;
            this.pos = pos;
            this.outer = outer;
        }
    }

    public static void enter(Player player, BlockPos pos) {
        CURRENT.set(new Scope(player, pos.immutable(), CURRENT.get()));
    }

    /**
     * 离开作用域，返回破坏事件里登记的连带挖掘；调用方在中心那一格确实挖掉之后再执行。
     */
    @Nullable
    public static Runnable exit() {
        Scope s = CURRENT.get();
        if (s == null) {
            return null;
        }
        if (s.outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(s.outer);
        }
        return s.pending;
    }

    /** 这次破坏是不是作用域里那个玩家正在亲手挖的那一格 */
    public static boolean isHandDig(Player player, BlockPos pos) {
        Scope s = CURRENT.get();
        return s != null && s.player == player && s.pos.equals(pos);
    }

    /** 同一格可能收到好几次破坏事件，只记第一次 */
    public static void defer(Runnable chain) {
        Scope s = CURRENT.get();
        if (s != null && s.pending == null) {
            s.pending = chain;
        }
    }

    /**
     * 按每人每 tick 的上限截断连带列表，超出的这一 tick 不挖，方块留在原地。
     * 上限取半径配置下一次挖满的格数和 {@link #MIN_PER_TICK} 里大的那个，正常挖一下不会被截。
     */
    public static List<BlockPos> limitPerTick(@Nullable MinecraftServer server, Player player, int maxRange, List<BlockPos> found) {
        if (found.isEmpty() || server == null || !server.isSameThread()) {
            return found;
        }
        int tick = server.getTickCount();
        if (tick != usedTick) {
            used.clear();
            usedTick = tick;
        }
        int cap = perTickCap(maxRange);
        int[] count = used.computeIfAbsent(player.getUUID(), id -> new int[1]);
        int left = cap - count[0];
        if (found.size() <= left) {
            count[0] += found.size();
            return found;
        }
        count[0] = cap;
        return left <= 0 ? new ArrayList<>() : new ArrayList<>(found.subList(0, left));
    }

    static int perTickCap(int maxRange) {
        long side = 2L * Math.max(0, maxRange) + 1;
        long full = side * side * side - 1;
        return (int) Math.max(MIN_PER_TICK, Math.min(full, Integer.MAX_VALUE));
    }
}
