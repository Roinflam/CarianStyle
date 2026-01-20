package pers.roinflam.carianstyle.network;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端同步效果管理器
 * <p>
 * 服务端主类，客户端功能通过代理访问
 * </p>
 */
public class ClientSyncEffectManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSyncEffectManager.class);

    /**
     * 服务端：维度 -> 序列号 -> 实体ID集合
     */
    private static final Map<ResourceKey<Level>, Map<Integer, Set<Integer>>> SERVER_ACTIVATED_ENTITIES
            = new ConcurrentHashMap<>();

    /**
     * 客户端缓存代理
     */
    private static final IClientCacheProxy CLIENT_PROXY;

    // 静态初始化块：根据环境创建代理
    static {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // 客户端环境
            CLIENT_PROXY = new ClientCacheProxyImpl();
        } else {
            // 服务端环境
            CLIENT_PROXY = new ServerCacheProxyImpl();
        }
    }

    /**
     * 服务端：添加实体到激活列表
     *
     * @param entity 实体
     * @param serialNumber 序列号
     */
    public static void addEntity(@Nonnull LivingEntity entity, int serialNumber) {
        if (entity.level().isClientSide) {
            return;
        }

        ResourceKey<Level> dimension = entity.level().dimension();
        int entityId = entity.getId();

        Map<Integer, Set<Integer>> dimensionMap = SERVER_ACTIVATED_ENTITIES
                .computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());

        Set<Integer> entitySet = dimensionMap
                .computeIfAbsent(serialNumber, k -> ConcurrentHashMap.newKeySet());

        // 如果是新添加的实体，广播给该维度的所有玩家
        if (entitySet.add(entityId)) {
            broadcastToDimension((ServerLevel) entity.level(), serialNumber, entitySet);
        }
    }

    /**
     * 服务端：从激活列表移除实体
     *
     * @param entity 实体
     * @param serialNumber 序列号
     */
    public static void removeEntity(@Nonnull LivingEntity entity, int serialNumber) {
        if (entity.level().isClientSide) {
            return;
        }

        ResourceKey<Level> dimension = entity.level().dimension();
        int entityId = entity.getId();

        Map<Integer, Set<Integer>> dimensionMap = SERVER_ACTIVATED_ENTITIES.get(dimension);
        if (dimensionMap == null) {
            return;
        }

        Set<Integer> entitySet = dimensionMap.get(serialNumber);
        if (entitySet == null) {
            return;
        }

        // 如果成功移除，广播给该维度的所有玩家
        if (entitySet.remove(entityId)) {
            broadcastToDimension((ServerLevel) entity.level(), serialNumber, entitySet);
        }
    }

    /**
     * 服务端:向整个维度广播状态
     */
    private static void broadcastToDimension(@Nonnull ServerLevel level, int serialNumber,
                                             @Nonnull Set<Integer> entityIds) {
        List<Integer> idList = new ArrayList<>(entityIds);
        ClientSyncEffectPacket packet = new ClientSyncEffectPacket(serialNumber, idList);

        // 发送给该维度的所有玩家
        for (ServerPlayer player : level.players()) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    packet
            );
        }
    }

    /**
     * 服务端：同步维度所有状态给单个玩家
     * <p>
     * 用于玩家登录、切换维度、重生时
     * </p>
     */
    public static void syncDimensionToPlayer(@Nonnull ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        Map<Integer, Set<Integer>> dimensionMap = SERVER_ACTIVATED_ENTITIES.get(dimension);

        if (dimensionMap == null) {
            return;
        }

        // 遍历该维度所有序列号的效果
        for (Map.Entry<Integer, Set<Integer>> entry : dimensionMap.entrySet()) {
            int serialNumber = entry.getKey();
            Set<Integer> entityIds = entry.getValue();

            // 清理无效实体
            entityIds.removeIf(entityId -> {
                var entity = player.level().getEntity(entityId);
                return entity == null || !entity.isAlive();
            });

            // 发送同步包
            if (!entityIds.isEmpty()) {
                List<Integer> idList = new ArrayList<>(entityIds);
                ClientSyncEffectPacket packet = new ClientSyncEffectPacket(serialNumber, idList);
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        packet
                );
            }
        }
    }

    /**
     * 服务端：清理维度中的实体
     */
    public static void cleanupDimension(@Nonnull ServerLevel level, int serialNumber) {
        ResourceKey<Level> dimension = level.dimension();
        Map<Integer, Set<Integer>> dimensionMap = SERVER_ACTIVATED_ENTITIES.get(dimension);

        if (dimensionMap == null) {
            return;
        }

        Set<Integer> entityIds = dimensionMap.get(serialNumber);
        if (entityIds == null) {
            return;
        }

        // 移除已死亡或不存在的实体
        entityIds.removeIf(entityId -> {
            var entity = level.getEntity(entityId);
            return entity == null || !entity.isAlive();
        });

        // 广播更新
        broadcastToDimension(level, serialNumber, entityIds);
    }

    /**
     * 客户端：更新缓存
     */
    public static void updateClientCache(int serialNumber, @Nonnull List<Integer> entityIds) {
        CLIENT_PROXY.updateClientCache(serialNumber, entityIds);
    }

    /**
     * 客户端：检查实体是否应该渲染效果
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean shouldRenderEffect(int serialNumber, int entityId) {
        return CLIENT_PROXY.shouldRenderEffect(serialNumber, entityId);
    }

    /**
     * 客户端：清理缓存
     */
    public static void clearClientCache() {
        CLIENT_PROXY.clearClientCache();
    }

    /**
     * 服务端：清理所有缓存
     */
    public static void clearServerCache() {
        SERVER_ACTIVATED_ENTITIES.clear();
    }

    /**
     * 客户端缓存代理接口
     */
    private interface IClientCacheProxy {
        void updateClientCache(int serialNumber, @Nonnull List<Integer> entityIds);
        boolean shouldRenderEffect(int serialNumber, int entityId);
        void clearClientCache();
    }

    /**
     * 客户端代理实现
     */
    @OnlyIn(Dist.CLIENT)
    private static class ClientCacheProxyImpl implements IClientCacheProxy {
        /**
         * 客户端：序列号 -> 实体ID列表
         */
        private final Map<Integer, List<Integer>> CLIENT_ENTITY_CACHE = new ConcurrentHashMap<>();

        @Override
        public void updateClientCache(int serialNumber, @Nonnull List<Integer> entityIds) {
            CLIENT_ENTITY_CACHE.put(serialNumber, new ArrayList<>(entityIds));
        }

        @Override
        public boolean shouldRenderEffect(int serialNumber, int entityId) {
            List<Integer> entityIds = CLIENT_ENTITY_CACHE.get(serialNumber);
            return entityIds != null && entityIds.contains(entityId);
        }

        @Override
        public void clearClientCache() {
            CLIENT_ENTITY_CACHE.clear();
        }
    }

    /**
     * 服务端代理实现（空操作）
     */
    private static class ServerCacheProxyImpl implements IClientCacheProxy {
        @Override
        public void updateClientCache(int serialNumber, @Nonnull List<Integer> entityIds) {
            // 服务端不执行任何操作
        }

        @Override
        public boolean shouldRenderEffect(int serialNumber, int entityId) {
            // 服务端永远返回false
            return false;
        }

        @Override
        public void clearClientCache() {
            // 服务端不执行任何操作
        }
    }
}