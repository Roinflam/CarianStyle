package pers.roinflam.carianstyle.network;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端同步效果管理器
 * <p>
 * 修复记录：
 * - 火焰视觉残留：增加定期重同步，修正任何因网络波动导致的状态不一致
 * - 包量爆炸：改为增量式广播（add/remove 只发 1 个实体 ID），全量同步仅在登录/切维度/定期校正时使用
 * - 客户端查询 O(n)：List 改为 Set，shouldRenderEffect 从 O(n) 降为 O(1)
 * </p>
 *
 * <h3>v3.1 修复：实体死亡后特效残留（玩家复活后仍显示）</h3>
 * <p>
 * <b>现象：</b>切腹（俗称「大灭」）触发期间玩家被自己耗死，复活之后腹部刀痕、上升血刃碎片、
 * 疾走涟漪等视觉<b>一直挂在身上不消失</b>，直到下一次正常触发并过期才恢复。
 * 猩红腐败(5)、冻伤(7)、出血(8)、切腹(9)、睡眠(10)、噩兆(11) 这些
 * <b>基于 MobEffect 的同步全部存在同一个问题</b>。
 * </p>
 * <p>
 * <b>根因：</b>各 SyncHandler 靠 {@code MobEffectEvent.Added / Remove / Expired} 维护集合，
 * 而<b>实体死亡时这三个事件一个都不会触发</b>——效果是随旧实体实例一起消失的，Forge 不会补发
 * Remove/Expired。于是服务端集合里留下了该实体 ID。
 * </p>
 * <p>
 * 唯一的兜底是 {@link #onServerTick} 里每 5 秒一次全量重同步的剪枝
 * （{@code e == null || !e.isAlive()}），但 <b>Minecraft 玩家复活后实体网络 ID 不变</b>
 * （{@code PlayerList.respawn} 里 {@code serverplayer.setId(player.getId())}），
 * 复活后该 ID 又能解析到一个<b>存活</b>的实体，剪枝判定直接失效，
 * 残留条目于是永久留在集合里，并被每次全量同步反复广播出去。
 * </p>
 * <p>
 * <b>修复：</b>新增 {@link #removeAllForEntity}，由
 * {@code ClientSyncEffectEventHandler} 在<b>死亡 / 复活 / 登出</b>三处调用，
 * 把该实体从当前维度下的<b>全部序列号</b>中移除并增量广播。
 * 死亡是主修复点；复活与登出是兜底——覆盖死亡事件被
 * 满月 / 死诞者 / 时间逆转 拦截取消等路径下漏清的情况。
 * </p>
 *
 * <h3>定期重同步为什么要跳过空集合</h3>
 * <p>
 * {@link #onServerTick} 的 5 秒全量重同步早期<b>不管集合是否为空都照发</b>。
 * 该机制诞生时只有 4 个序列号（3 个自定义火焰 + 隐身），开销可以忽略；
 * 但随着效果同步链路不断扩充，现已达 <b>11 个</b>（火焰 1~3、隐身 4、猩红腐败 5、
 * 重力力场 6、冻伤 7、出血 8、切腹 9、睡眠 10、噩兆 11）。
 * </p>
 * <p>
 * 于是包量变成 <b>序列号数 × 在线人数 ÷ 5 秒</b>：60 人同维度时约
 * {@code 11 × 60 ÷ 5 ≈ 132 包/秒}，而其中绝大多数是<b>空包</b>——
 * 和平时期或小规模战斗中同时存在的效果通常只有一两种，
 * 剩下九种的集合恒为空，却仍在稳定地对全服广播「我这里什么都没有」。
 * </p>
 * <p>
 * <b>但不能无脑跳过</b>——若某实体的出血结束、增量 REMOVE 包恰好丢失，
 * 客户端缓存里会残留该实体 ID、视觉一直挂着；此时服务端集合已空，
 * 直接跳过重同步会让这个残留<b>永远得不到修正</b>，兜底机制就失效了。
 * </p>
 * <p>
 * 因此引入 {@link #LAST_NON_EMPTY} 记录「上一轮重同步时哪些序列号非空」，判定规则：
 * </p>
 * <ul>
 *     <li>集合非空 → 照常广播，并标记该序列号为非空；</li>
 *     <li>集合为空 <b>且上一轮非空</b> → 广播<b>一次</b>空包做收尾修正，然后清除标记；</li>
 *     <li>集合为空 <b>且上一轮也为空</b> → 跳过，一个包都不发。</li>
 * </ul>
 * <p>
 * 「从有到无」的那一次修正机会被完整保留，长期闲置的序列号彻底静默。
 * 按前述 60 人场景估算，稳态包量从约 132 包/秒降至<b>仅活跃效果</b>的十余包/秒。
 * </p>
 *
 * <h3>切维度为什么要逐个覆盖序列号</h3>
 * <p>
 * {@link #syncDimensionToPlayer}（登录 / 切维度 / 重生时调用）
 * 早期只遍历<b>新维度</b>的 {@code dimMap}。考虑这条链路：
 * </p>
 * <ol>
 *     <li>玩家在主世界，附近有带猩红腐败（序列号 5）的怪，客户端缓存里存着这些实体 ID；</li>
 *     <li>玩家传送到下界，下界从未有过腐败实体，其 {@code dimMap} 里<b>根本没有序列号 5 这一项</b>；</li>
 *     <li>循环遍历不到序列号 5 → <b>不发任何包</b> → 客户端缓存中主世界的那批 ID 原封不动。</li>
 * </ol>
 * <p>
 * 实际危害通常不大：那些实体 ID 在新维度多半解析不到实体，渲染器取不到坐标就不会画东西。
 * 但 Minecraft 的实体网络 ID 是<b>按世界递增分配、跨维度可能重复</b>的——
 * 一旦下界某个实体恰好分到了与主世界残留 ID 相同的编号，
 * 它就会凭空挂上一层它根本没有的效果特效（例如无缘无故冒红雾），
 * 且要等到 5 秒后的定期重同步才被纠正。
 * </p>
 * <p>
 * 因此引入 {@link #KNOWN_SERIALS} 记录「本次服务器运行期间实际被使用过的全部序列号」
 * （在 {@link #addEntity} 时登记）。{@link #syncDimensionToPlayer} 改为遍历该集合而非新维度的
 * {@code dimMap}：新维度有数据就发实际数据，<b>没有数据就发一个空包</b>，
 * 强制覆盖客户端对应序列号的缓存，残留即刻清除。
 * </p>
 * <p>
 * 代价可以忽略：切维度 / 登录是低频事件，一次至多发 11 个包（且多为空包，每个约 10 字节）。
 * 用「实际用过的序列号」而非硬编码 1~11 的好处是：新增效果同步时无需回来改这里，
 * 且从未启用过的序列号不会产生无意义的空包。
 * </p>
 *
 * @version 3.1
 */
@Mod.EventBusSubscriber
public class ClientSyncEffectManager {

    /** 服务端：维度 -> 序列号 -> 实体ID集合 */
    private static final Map<ResourceKey<Level>, Map<Integer, Set<Integer>>> SERVER_ACTIVATED_ENTITIES
            = new ConcurrentHashMap<>();

    /**
     * 服务端：维度 -> 「上一轮定期重同步时非空」的序列号集合。
     * <p>
     * 仅用于 {@link #onServerTick} 判断是否需要为「刚变空」的序列号补发一次收尾空包，
     * 详见类注释。
     * </p>
     */
    private static final Map<ResourceKey<Level>, Set<Integer>> LAST_NON_EMPTY
            = new ConcurrentHashMap<>();

    /**
     * 服务端：本次运行期间<b>实际被使用过</b>的全部效果序列号（跨维度全局）。
     * <p>
     * 供 {@link #syncDimensionToPlayer} 在切维度时逐个覆盖客户端缓存，
     * 清除旧维度残留（详见类注释）。
     * </p>
     * <p>
     * 之所以不硬编码 1~11：新增效果同步时不必回来改这里，
     * 且从未启用的序列号不会产生无意义的空包。
     * 该集合只增不减，但上限就是模组定义的序列号总数（十余个），无内存风险。
     * </p>
     */
    private static final Set<Integer> KNOWN_SERIALS = ConcurrentHashMap.newKeySet();

    /** 重同步计数器 */
    private static int resyncCounter = 0;

    /** 重同步间隔（100 tick = 5 秒） */
    private static final int RESYNC_INTERVAL = 100;

    // ==================== 客户端缓存（使用Set） ====================

    /** 客户端缓存代理 */
    private static final IClientCacheProxy CLIENT_PROXY;

    static {
        CLIENT_PROXY = FMLEnvironment.dist == Dist.CLIENT
                ? new ClientCacheProxyImpl()
                : new ServerCacheProxyImpl();
    }

    // ==================== 服务端：增量式广播 ====================

    /**
     * 服务端：添加实体到激活列表（增量广播）
     *
     * @param entity       目标实体
     * @param serialNumber 效果序列号
     */
    public static void addEntity(@Nonnull LivingEntity entity, int serialNumber) {
        if (entity.level().isClientSide) return;
        ResourceKey<Level> dimension = entity.level().dimension();
        int entityId = entity.getId();

        // 登记序列号，供切维度时逐个覆盖客户端缓存
        KNOWN_SERIALS.add(serialNumber);

        Map<Integer, Set<Integer>> dimMap = SERVER_ACTIVATED_ENTITIES
                .computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        Set<Integer> entitySet = dimMap
                .computeIfAbsent(serialNumber, k -> ConcurrentHashMap.newKeySet());

        if (entitySet.add(entityId)) {
            // 只广播增量：ADD单个实体
            broadcastDelta((ServerLevel) entity.level(), serialNumber,
                    ClientSyncEffectPacket.Action.ADD, entityId);
        }
    }

    /**
     * 服务端：从激活列表移除实体（增量广播）
     *
     * @param entity       目标实体
     * @param serialNumber 效果序列号
     */
    public static void removeEntity(@Nonnull LivingEntity entity, int serialNumber) {
        if (entity.level().isClientSide) return;
        ResourceKey<Level> dimension = entity.level().dimension();
        int entityId = entity.getId();

        Map<Integer, Set<Integer>> dimMap = SERVER_ACTIVATED_ENTITIES.get(dimension);
        if (dimMap == null) return;
        Set<Integer> entitySet = dimMap.get(serialNumber);
        if (entitySet == null) return;

        if (entitySet.remove(entityId)) {
            // 只广播增量：REMOVE单个实体
            broadcastDelta((ServerLevel) entity.level(), serialNumber,
                    ClientSyncEffectPacket.Action.REMOVE, entityId);
        }
    }

    /**
     * 服务端：把某实体从<b>当前维度下的全部效果序列号</b>中移除（逐个增量广播）。
     * <p>
     * <b>用途：</b>修复「实体死亡后特效残留」——基于 MobEffect 的同步
     * （猩红腐败 5 / 冻伤 7 / 出血 8 / 切腹 9 / 睡眠 10 / 噩兆 11）在实体死亡时
     * <b>收不到任何 {@code MobEffectEvent.Remove / Expired}</b>，
     * 因为效果是随旧实体实例一起消失的，Forge 不会补发事件。
     * 而玩家复活后实体网络 ID 不变且重新存活，
     * {@link #onServerTick} 里 {@code !e.isAlive()} 的剪枝也拦不住，
     * 导致残留条目永久生效（详见类注释「v3.1 修复」小节）。
     * </p>
     * <p>
     * 由 {@code ClientSyncEffectEventHandler} 在<b>死亡 / 复活 / 登出</b>三处调用。
     * 对本就不在集合中的实体是无开销的空操作（{@code Set.remove} 返回 false 即不广播），
     * 因此重复调用安全、可与 {@code DynamicAttributeManager.clearAll} 的移除回调叠加。
     * </p>
     * <p>
     * <b>只处理实体当前所在维度：</b>跨维度的残留（例如在下界死亡、在主世界复活）
     * 由旧维度的定期重同步剪枝自愈——那边 {@code level.getEntity(id)} 解析为 null，
     * 会被正常剔除。
     * </p>
     *
     * @param entity 目标实体
     */
    public static void removeAllForEntity(@Nonnull LivingEntity entity) {
        if (entity.level().isClientSide) return;

        Map<Integer, Set<Integer>> dimMap = SERVER_ACTIVATED_ENTITIES.get(entity.level().dimension());
        if (dimMap == null || dimMap.isEmpty()) return;

        ServerLevel level = (ServerLevel) entity.level();
        int entityId = entity.getId();

        for (Map.Entry<Integer, Set<Integer>> entry : dimMap.entrySet()) {
            if (entry.getValue().remove(entityId)) {
                broadcastDelta(level, entry.getKey(),
                        ClientSyncEffectPacket.Action.REMOVE, entityId);
            }
        }
    }

    /**
     * 增量广播：只发送 1 个实体的 ADD/REMOVE
     *
     * @param level        服务端世界
     * @param serialNumber 效果序列号
     * @param action       操作类型
     * @param entityId     实体网络 ID
     */
    private static void broadcastDelta(@Nonnull ServerLevel level, int serialNumber,
                                       ClientSyncEffectPacket.Action action, int entityId) {
        ClientSyncEffectPacket packet = ClientSyncEffectPacket.delta(serialNumber, action, entityId);
        for (ServerPlayer player : level.players()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /**
     * 全量同步给单个玩家（登录 / 切维度 / 重生）。
     * <p>
     * <b>遍历 {@link #KNOWN_SERIALS} 而非新维度的 dimMap。</b>
     * 新维度对某序列号没有数据时会发一个<b>空包</b>，强制覆盖客户端缓存，
     * 从而清除旧维度残留的实体 ID（详见类注释）。
     * </p>
     *
     * @param player 目标玩家
     */
    public static void syncDimensionToPlayer(@Nonnull ServerPlayer player) {
        if (KNOWN_SERIALS.isEmpty()) {
            // 本次运行还没有任何效果被登记过，客户端也不可能有缓存，无需同步
            return;
        }

        ResourceKey<Level> dimension = player.level().dimension();
        Map<Integer, Set<Integer>> dimMap = SERVER_ACTIVATED_ENTITIES.get(dimension);

        for (int serialNumber : KNOWN_SERIALS) {
            Set<Integer> entityIds = (dimMap == null) ? null : dimMap.get(serialNumber);

            List<Integer> payload;
            if (entityIds == null || entityIds.isEmpty()) {
                // 新维度无此效果 → 发空包覆盖，清除客户端旧维度残留
                payload = Collections.emptyList();
            } else {
                // 清理无效实体后再下发
                entityIds.removeIf(id -> {
                    var e = player.level().getEntity(id);
                    return e == null || !e.isAlive();
                });
                payload = new ArrayList<>(entityIds);
            }

            ClientSyncEffectPacket packet = ClientSyncEffectPacket.fullSync(serialNumber, payload);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /**
     * 定期重同步：每 5 秒向所有维度的所有玩家发送全量状态。
     * <p>
     * 这是修复视觉残留的兜底机制：即使增量包丢失，5 秒后也会被全量包修正。
     * </p>
     * <p>
     * <b>跳过恒为空的序列号。</b>清理无效实体后，
     * 若集合为空且上一轮也为空则一个包都不发；
     * 若集合为空但上一轮非空，则补发一次空包做收尾修正。详见类注释。
     * </p>
     * <p>
     * <b>注意：</b>此处的剪枝条件 {@code e == null || !e.isAlive()}
     * <b>拦不住「死亡后复活」的场景</b>（复活后实体 ID 不变且重新存活），
     * 那条路径必须由 {@link #removeAllForEntity} 在死亡时主动清除。
     * </p>
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++resyncCounter < RESYNC_INTERVAL) return;
        resyncCounter = 0;

        for (Map.Entry<ResourceKey<Level>, Map<Integer, Set<Integer>>> dimEntry
                : SERVER_ACTIVATED_ENTITIES.entrySet()) {
            // 获取该维度的ServerLevel
            ServerLevel level = event.getServer().getLevel(dimEntry.getKey());
            if (level == null) continue;

            // 该维度没有玩家：无人可发，同时清空「上轮非空」标记，
            // 避免玩家回来后凭旧标记补发一堆无意义的空包
            if (level.players().isEmpty()) {
                LAST_NON_EMPTY.remove(dimEntry.getKey());
                continue;
            }

            Set<Integer> lastNonEmpty = LAST_NON_EMPTY
                    .computeIfAbsent(dimEntry.getKey(), k -> ConcurrentHashMap.newKeySet());

            Map<Integer, Set<Integer>> serialMap = dimEntry.getValue();
            for (Map.Entry<Integer, Set<Integer>> entry : serialMap.entrySet()) {
                int serialNumber = entry.getKey();
                Set<Integer> entityIds = entry.getValue();

                // 清理无效实体（必须保留：这是防止服务端集合无限增长的唯一出口）
                entityIds.removeIf(id -> {
                    var e = level.getEntity(id);
                    return e == null || !e.isAlive();
                });

                // 空集合的跳过判定
                if (entityIds.isEmpty()) {
                    if (!lastNonEmpty.remove(serialNumber)) {
                        // 上一轮也是空的 → 长期闲置，彻底静默，一个包都不发
                        continue;
                    }
                    // 上一轮非空、这一轮空了 → 补发一次空包做收尾修正，
                    // 覆盖「增量 REMOVE 丢包导致客户端残留」的场景
                } else {
                    lastNonEmpty.add(serialNumber);
                }

                ClientSyncEffectPacket packet = ClientSyncEffectPacket.fullSync(
                        serialNumber, new ArrayList<>(entityIds));
                for (ServerPlayer player : level.players()) {
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
        }
    }

    // ==================== 清理方法 ====================

    /**
     * 清理指定维度中某序列号下的无效实体。
     *
     * @param level        服务端世界
     * @param serialNumber 效果序列号
     */
    public static void cleanupDimension(@Nonnull ServerLevel level, int serialNumber) {
        ResourceKey<Level> dimension = level.dimension();
        Map<Integer, Set<Integer>> dimMap = SERVER_ACTIVATED_ENTITIES.get(dimension);
        if (dimMap == null) return;
        Set<Integer> entityIds = dimMap.get(serialNumber);
        if (entityIds == null) return;
        entityIds.removeIf(id -> {
            var e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
    }

    /**
     * 清空服务端全部缓存（含重同步标记与已知序列号登记）。
     */
    public static void clearServerCache() {
        SERVER_ACTIVATED_ENTITIES.clear();
        LAST_NON_EMPTY.clear();
        KNOWN_SERIALS.clear();
    }

    /**
     * 清空客户端缓存。
     */
    public static void clearClientCache() {
        CLIENT_PROXY.clearClientCache();
    }

    // ==================== 客户端：包处理 ====================

    /**
     * 客户端处理收到的包（由 Packet.handle 调用）。
     *
     * @param serialNumber 效果序列号
     * @param action       操作类型
     * @param entityIds    实体 ID 列表
     */
    public static void handlePacket(int serialNumber, ClientSyncEffectPacket.Action action,
                                    @Nonnull List<Integer> entityIds) {
        CLIENT_PROXY.handlePacket(serialNumber, action, entityIds);
    }

    /**
     * 客户端：检查实体是否应该渲染效果（O(1) 查询）。
     *
     * @param serialNumber 效果序列号
     * @param entityId     实体网络 ID
     * @return 应渲染返回 true
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean shouldRenderEffect(int serialNumber, int entityId) {
        return CLIENT_PROXY.shouldRenderEffect(serialNumber, entityId);
    }

    /**
     * 兼容旧 API：以全量方式更新客户端缓存。
     *
     * @param serialNumber 效果序列号
     * @param entityIds    实体 ID 列表
     */
    public static void updateClientCache(int serialNumber, @Nonnull List<Integer> entityIds) {
        CLIENT_PROXY.handlePacket(serialNumber, ClientSyncEffectPacket.Action.FULL_SYNC, entityIds);
    }

    // ==================== 代理接口和实现 ====================

    /**
     * 客户端缓存代理接口：让服务端环境下不加载任何客户端专有逻辑。
     */
    private interface IClientCacheProxy {
        void handlePacket(int serialNumber, ClientSyncEffectPacket.Action action, List<Integer> entityIds);

        boolean shouldRenderEffect(int serialNumber, int entityId);

        void clearClientCache();
    }

    /** 客户端实现：使用 Set 保证 O(1) 查询 */
    @OnlyIn(Dist.CLIENT)
    private static class ClientCacheProxyImpl implements IClientCacheProxy {
        private final Map<Integer, Set<Integer>> cache = new ConcurrentHashMap<>();

        @Override
        public void handlePacket(int serialNumber, ClientSyncEffectPacket.Action action,
                                 List<Integer> entityIds) {
            switch (action) {
                case ADD -> cache.computeIfAbsent(serialNumber, k -> ConcurrentHashMap.newKeySet())
                        .addAll(entityIds);
                case REMOVE -> {
                    Set<Integer> set = cache.get(serialNumber);
                    if (set != null) entityIds.forEach(set::remove);
                }
                case FULL_SYNC -> {
                    Set<Integer> newSet = ConcurrentHashMap.newKeySet();
                    newSet.addAll(entityIds);
                    cache.put(serialNumber, newSet);
                }
            }
        }

        @Override
        public boolean shouldRenderEffect(int serialNumber, int entityId) {
            Set<Integer> set = cache.get(serialNumber);
            return set != null && set.contains(entityId);
        }

        @Override
        public void clearClientCache() {
            cache.clear();
        }
    }

    /** 服务端空实现 */
    private static class ServerCacheProxyImpl implements IClientCacheProxy {
        @Override
        public void handlePacket(int sn, ClientSyncEffectPacket.Action a, List<Integer> ids) {
        }

        @Override
        public boolean shouldRenderEffect(int sn, int eid) {
            return false;
        }

        @Override
        public void clearClientCache() {
        }
    }
}
