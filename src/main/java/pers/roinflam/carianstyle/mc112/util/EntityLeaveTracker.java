package pers.roinflam.carianstyle.mc112.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 「实体离开世界」回调：1.20.1 {@code EntityLeaveLevelEvent} 的 1.12.2 等价物
 * <p>
 * 1.12.2 的 Forge 没有实体离开世界的事件。原版的 {@link World#onEntityRemoved} 会通知世界上挂的每个
 * {@link IWorldEventListener}，这里实现这个接口，只用 {@link #onEntityRemoved}，其余 11 个方法空着。
 * {@code World.onEntityRemoved} 在这几处被调用（字节码核过）：
 * </p>
 * <ul>
 *     <li>{@code removeEntity}：玩家下线、死亡移除（玩家立即调，非玩家在下一次 {@code updateEntities} 里调）；</li>
 *     <li>{@code removeEntityDangerously}：换维度；</li>
 *     <li>{@code updateEntities}：区块卸载、死亡实体清理。</li>
 * </ul>
 *
 * <h3>怎么挂到世界上</h3>
 * <ul>
 *     <li>{@code WorldEvent.Load} 时挂（服务端、客户端的世界都挂）；</li>
 *     <li>兜底：{@code EntityJoinWorldEvent} 里看这个世界挂过没有，没挂过就补挂。Mohist 系混合端上插件建的世界
 *         会不会发 {@code WorldEvent.Load} 没有核实过，有这个兜底就不依赖它。</li>
 * </ul>
 * <p>
 * <b>整个世界卸载</b>（{@code WorldEvent.Unload}，包括停服、单人退出存档）不会逐个触发 {@code onEntityRemoved}：
 * 卸载时把世界上还在的实体逐个派发给全部监听器，然后从世界上摘掉自己。
 * </p>
 * <p>
 * 监听器只管清理内存里的东西，不要在里面改实体、生成实体：派发时世界正在遍历实体表。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class EntityLeaveTracker implements IWorldEventListener {

    /** 唯一的实例：同一个对象挂到每个世界上 */
    private static final EntityLeaveTracker INSTANCE = new EntityLeaveTracker();

    /** 全部监听器。注册很少、派发很多，用写时复制 */
    private static final List<Consumer<Entity>> LISTENERS = new CopyOnWriteArrayList<>();

    /** 已经挂过的世界。弱引用：世界卸载后不拦着回收 */
    private static final Set<World> ATTACHED = Collections.newSetFromMap(new WeakHashMap<World, Boolean>());

    private EntityLeaveTracker() {
    }

    /**
     * 登记一个「实体离开世界」监听器
     * <p>服务端、客户端的实体都会派发过来，要哪一边自己判断 {@code entity.world.isRemote}。</p>
     *
     * @param listener 监听器
     */
    public static void addListener(@Nonnull Consumer<Entity> listener) {
        LISTENERS.add(listener);
    }

    /**
     * 把实体派发给全部监听器，单个监听器出错不影响别的
     *
     * @param entity 离开世界的实体
     */
    private static void dispatch(@Nullable Entity entity) {
        if (entity == null) {
            return;
        }
        for (Consumer<Entity> listener : LISTENERS) {
            try {
                listener.accept(entity);
            } catch (Throwable t) {
                LogUtil.error("实体离开世界的回调出错: " + entity, t);
            }
        }
    }

    /**
     * 挂到世界上（挂过的不重复挂）
     *
     * @param world 世界
     */
    private static void attach(@Nonnull World world) {
        synchronized (ATTACHED) {
            if (!ATTACHED.add(world)) {
                return;
            }
        }
        world.addEventListener(INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorldLoad(@Nonnull WorldEvent.Load event) {
        attach(event.getWorld());
    }

    /**
     * 兜底补挂：没发过 {@code WorldEvent.Load} 的世界，第一个实体进来时挂上
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent event) {
        World world = event.getWorld();
        boolean attached;
        synchronized (ATTACHED) {
            attached = ATTACHED.contains(world);
        }
        if (!attached) {
            attach(world);
        }
    }

    /**
     * 世界卸载：剩下的实体逐个派发，再把自己摘掉
     * <p>放在最低优先级，别的模组在卸载事件里还能读到完整的内存数据。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWorldUnload(@Nonnull WorldEvent.Unload event) {
        World world = event.getWorld();
        boolean attached;
        synchronized (ATTACHED) {
            attached = ATTACHED.remove(world);
        }
        if (!attached) {
            return;
        }
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            dispatch(entity);
        }
        world.removeEventListener(INSTANCE);
    }

    // ==================== IWorldEventListener ====================

    @Override
    public void onEntityRemoved(@Nonnull Entity entityIn) {
        dispatch(entityIn);
    }

    @Override
    public void onEntityAdded(@Nonnull Entity entityIn) {
    }

    @Override
    public void notifyBlockUpdate(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState oldState,
                                  @Nonnull IBlockState newState, int flags) {
    }

    @Override
    public void notifyLightSet(@Nonnull BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    @Override
    public void playSoundToAllNearExcept(@Nullable EntityPlayer player, @Nonnull SoundEvent soundIn,
                                         @Nonnull SoundCategory category, double x, double y, double z,
                                         float volume, float pitch) {
    }

    @Override
    public void playRecord(@Nonnull SoundEvent soundIn, @Nonnull BlockPos pos) {
    }

    @Override
    public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord,
                              double xSpeed, double ySpeed, double zSpeed, @Nonnull int... parameters) {
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, boolean minimiseParticleLevel, double x, double y,
                              double z, double xSpeed, double ySpeed, double zSpeed, @Nonnull int... parameters) {
    }

    @Override
    public void broadcastSound(int soundID, @Nonnull BlockPos pos, int data) {
    }

    @Override
    public void playEvent(@Nullable EntityPlayer player, int type, @Nonnull BlockPos blockPosIn, int data) {
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, @Nonnull BlockPos pos, int progress) {
    }
}
