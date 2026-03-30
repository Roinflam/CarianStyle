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
 * - 火焰视觉残留：增加每100tick定期重同步，修正任何因网络波动导致的状态不一致
 * - 包量爆炸：改为增量式广播（add/remove只发1个实体ID），全量同步仅在登录/切维度/定期校正时使用
 * - 客户端查询O(n)：List改为Set，shouldRenderEffect从O(n)降为O(1)
 * </p>
 *
 * @version 2.1
 */
@Mod.EventBusSubscriber
public class ClientSyncEffectManager {

    /** 服务端：维度 -> 序列号 -> 实体ID集合 */
    private static final Map<ResourceKey<Level>, Map<Integer, Set<Integer>>> SERVER_ACTIVATED_ENTITIES
            = new ConcurrentHashMap<>();

    /** 重同步计数器 */
    private static int resyncCounter = 0;

    /** 重同步间隔（100tick = 5秒） */
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
     */
    public static void addEntity(@Nonnull LivingEntity entity, int serialNumber) {
        if (entity.level().isClientSide) return;
        ResourceKey<Level> dimension = entity.level().dimension();
        int entityId = entity.getId();

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

    /** 增量广播：只发送1个实体的ADD/REMOVE */
    private static void broadcastDelta(@Nonnull ServerLevel level, int serialNumber,
                                       ClientSyncEffectPacket.Action action, int entityId) {
        ClientSyncEffectPacket packet = ClientSyncEffectPacket.delta(serialNumber, action, entityId);
        for (ServerPlayer player : level.players()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /** 全量同步给单个玩家（登录/切维度/重生） */
    public static void syncDimensionToPlayer(@Nonnull ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        Map<Integer, Set<Integer>> dimMap = SERVER_ACTIVATED_ENTITIES.get(dimension);
        if (dimMap == null) return;

        for (Map.Entry<Integer, Set<Integer>> entry : dimMap.entrySet()) {
            int serialNumber = entry.getKey();
            Set<Integer> entityIds = entry.getValue();

            // 清理无效实体
            entityIds.removeIf(id -> {
                var e = player.level().getEntity(id);
                return e == null || !e.isAlive();
            });

            // 全量同步包
            ClientSyncEffectPacket packet = ClientSyncEffectPacket.fullSync(
                    serialNumber, new ArrayList<>(entityIds));
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /**
     * 定期重同步：每5秒向所有维度的所有玩家发送全量状态
     * <p>
     * 修复火焰视觉残留的核心：即使增量包丢失，5秒后也会被全量包修正
     * </p>
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

            Map<Integer, Set<Integer>> serialMap = dimEntry.getValue();
            for (Map.Entry<Integer, Set<Integer>> entry : serialMap.entrySet()) {
                int serialNumber = entry.getKey();
                Set<Integer> entityIds = entry.getValue();

                // 清理无效实体
                entityIds.removeIf(id -> {
                    var e = level.getEntity(id);
                    return e == null || !e.isAlive();
                });

                // 全量同步给该维度所有玩家
                if (!level.players().isEmpty()) {
                    ClientSyncEffectPacket packet = ClientSyncEffectPacket.fullSync(
                            serialNumber, new ArrayList<>(entityIds));
                    for (ServerPlayer player : level.players()) {
                        NetworkHandler.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> player), packet);
                    }
                }
            }
        }
    }

    // ==================== 清理方法 ====================

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

    public static void clearServerCache() { SERVER_ACTIVATED_ENTITIES.clear(); }
    public static void clearClientCache() { CLIENT_PROXY.clearClientCache(); }

    // ==================== 客户端：包处理 ====================

    /** 客户端处理收到的包（由Packet.handle调用） */
    public static void handlePacket(int serialNumber, ClientSyncEffectPacket.Action action,
                                    @Nonnull List<Integer> entityIds) {
        CLIENT_PROXY.handlePacket(serialNumber, action, entityIds);
    }

    /** 客户端：检查实体是否应该渲染效果（O(1)查询） */
    @OnlyIn(Dist.CLIENT)
    public static boolean shouldRenderEffect(int serialNumber, int entityId) {
        return CLIENT_PROXY.shouldRenderEffect(serialNumber, entityId);
    }

    // 兼容旧API
    public static void updateClientCache(int serialNumber, @Nonnull List<Integer> entityIds) {
        CLIENT_PROXY.handlePacket(serialNumber, ClientSyncEffectPacket.Action.FULL_SYNC, entityIds);
    }

    // ==================== 代理接口和实现 ====================

    private interface IClientCacheProxy {
        void handlePacket(int serialNumber, ClientSyncEffectPacket.Action action, List<Integer> entityIds);
        boolean shouldRenderEffect(int serialNumber, int entityId);
        void clearClientCache();
    }

    /** 客户端实现：使用Set保证O(1)查询 */
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
        public void clearClientCache() { cache.clear(); }
    }

    /** 服务端空实现 */
    private static class ServerCacheProxyImpl implements IClientCacheProxy {
        @Override public void handlePacket(int sn, ClientSyncEffectPacket.Action a, List<Integer> ids) {}
        @Override public boolean shouldRenderEffect(int sn, int eid) { return false; }
        @Override public void clearClientCache() {}
    }
}
