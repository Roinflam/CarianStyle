package pers.roinflam.carianstyle.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * 碎岩连带挖的格子：标上的格子这一 tick 里掉的方块经验减半（{@code FabricBlockMixin}）。
 * <p>
 * Forge 1.20.1 上连挖的格子是模组自己按 {@code getExpDrop / 2} 放经验；Fabric 上原版 {@code playerDestroy}
 * 直接放全额（精准采集为 0），所以在 {@code popExperience} 里按格子减半，结果一样。
 * 按格子记、不按调用栈记：标记和放经验不在同一个方法里。精准采集时不放经验、标记用不掉，每 tick 末尾清空。
 * 只在服务端线程读写。
 * </p>
 */
public final class RockBlasterExperience {

    private static final Set<GlobalPos> MARKED = new HashSet<>();

    private RockBlasterExperience() {
    }

    public static void mark(ServerLevel level, BlockPos pos) {
        MARKED.add(GlobalPos.of(level.dimension(), pos.immutable()));
    }

    /** 这一格标过就用掉标记、返回 true。 */
    public static boolean consume(ServerLevel level, BlockPos pos) {
        return !MARKED.isEmpty() && MARKED.remove(GlobalPos.of(level.dimension(), pos));
    }

    static void clear() {
        MARKED.clear();
    }
}
