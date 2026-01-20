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
        LOGGER.info("[ClientSyncEffectManager] 初始化完成，环境: {}", FMLEnvironment.dist);
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

        LOGGER.debug("[服务端] 添加客户端同步效果 - 实体ID: {}, 实体类型: {}, 序列号: {}, 维度: {}",
                entityId, entity.getType().getDescription().getString(), serialNumber, dimension.location());

        Map<Integer, Set<Integer>> dimensionMap = SERVER_ACTIVATED_ENTITIES
                .computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());

        Set<Integer> entitySet = dimensionMap
                .computeIfAbsent(serialNumber, k -> ConcurrentHashMap.newKeySet());

        // 如果是新添加的实体，广播给该维度的所有玩家
        if (entitySet.add(entityId)) {
            LOGGER.info("[服务端] 新实体添加成功，开始广播 - 实体ID: {}, 序列号: {}, 当前集合大小: {}",
                    entityId, serialNumber, entitySet.size());
            broadcastToDimension((ServerLevel) entity.level(), serialNumber, entitySet);
        } else {
            LOGGER.warn("[服务端] 实体已存在于集合中 - 实体ID: {}, 序列号: {}", entityId, serialNumber);
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

        LOGGER.debug("[服务端] 移除客户端同步效果 - 实体ID: {}, 序列号: {}", entityId, serialNumber);

        Map<Integer, Set<Integer>> dimensionMap = SERVER_ACTIVATED_ENTITIES.get(dimension);
        if (dimensionMap == null) {
            LOGGER.warn("[服务端] 维度映射不存在 - 维度: {}", dimension.location());
            return;
        }

        Set<Integer> entitySet = dimensionMap.get(serialNumber);
        if (entitySet == null) {
            LOGGER.warn("[服务端] 序列号集合不存在 - 序列号: {}", serialNumber);
            return;
        }

        // 如果成功移除，广播给该维度的所有玩家
        if (entitySet.remove(entityId)) {
            LOGGER.info("[服务端] 实体移除成功，开始广播 - 实体ID: {}, 序列号: {}", entityId, serialNumber);
            broadcastToDimension((ServerLevel) entity.level(), serialNumber, entitySet);
        } else {
            LOGGER.warn("[服务端] 实体不在集合中 - 实体ID: {}, 序列号: {}", entityId, serialNumber);
        }
    }

    /**
     * 服务端:向整个维度广播状态
     */
    private static void broadcastToDimension(@Nonnull ServerLevel level, int serialNumber,
                                             @Nonnull Set<Integer> entityIds) {
        List<Integer> idList = new ArrayList<>(entityIds);
        ClientSyncEffectPacket packet = new ClientSyncEffectPacket(serialNumber, idList);

        int playerCount = 0;
        // 发送给该维度的所有玩家
        for (ServerPlayer player : level.players()) {
            LOGGER.debug("[服务端] 发送网络包给玩家 - 玩家: {}, 序列号: {}, 实体列表: {}",
                    player.getName().getString(), serialNumber, idList);
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    packet
            );
            playerCount++;
        }

        LOGGER.info("[服务端] 广播完成 - 序列号: {}, 实体数: {}, 玩家数: {}",
                serialNumber, idList.size(), playerCount);
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

        LOGGER.info("[服务端] 同步维度状态给玩家 - 玩家: {}, 维度: {}",
                player.getName().getString(), dimension.location());

        if (dimensionMap == null) {
            LOGGER.debug("[服务端] 该维度没有客户端同步效果数据");
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
                LOGGER.info("[服务端] 同步序列号 {} 给玩家 {}, 实体数: {}",
                        serialNumber, player.getName().getString(), idList.size());
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
        LOGGER.info("[ClientSyncEffectManager] 更新客户端缓存 - 序列号: {}, 实体列表: {}", serialNumber, entityIds);
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
        LOGGER.info("[ClientSyncEffectManager] 清理客户端缓存");
        CLIENT_PROXY.clearClientCache();
    }

    /**
     * 服务端：清理所有缓存
     */
    public static void clearServerCache() {
        LOGGER.info("[服务端] 清理所有缓存");
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
            LOGGER.info("[客户端代理] 更新缓存 - 序列号: {}, 实体列表: {}", serialNumber, entityIds);

            List<Integer> oldList = CLIENT_ENTITY_CACHE.get(serialNumber);
            if (oldList != null) {
                LOGGER.debug("[客户端代理] 替换旧缓存 - 旧列表: {}, 新列表: {}", oldList, entityIds);
            }

            CLIENT_ENTITY_CACHE.put(serialNumber, new ArrayList<>(entityIds));

            LOGGER.info("[客户端代理] 缓存更新完成 - 序列号: {}, 当前缓存大小: {}",
                    serialNumber, CLIENT_ENTITY_CACHE.size());
        }

        @Override
        public boolean shouldRenderEffect(int serialNumber, int entityId) {
            List<Integer> entityIds = CLIENT_ENTITY_CACHE.get(serialNumber);
            boolean shouldRender = entityIds != null && entityIds.contains(entityId);

            if (entityIds == null) {
                LOGGER.debug("[客户端代理] 渲染检查失败：缓存中没有序列号 {} 的数据", serialNumber);
            } else if (!shouldRender) {
                LOGGER.debug("[客户端代理] 渲染检查失败：实体ID {} 不在列表中 - 当前列表: {}",
                        entityId, entityIds);
            }

            return shouldRender;
        }

        @Override
        public void clearClientCache() {
            LOGGER.info("[客户端代理] 清理所有缓存");
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
            LOGGER.debug("[服务端代理] 客户端缓存更新被调用但被忽略 - 序列号: {}, 实体数: {}", serialNumber, entityIds.size());
        }

        @Override
        public boolean shouldRenderEffect(int serialNumber, int entityId) {
            // 服务端永远返回false
            return false;
        }

        @Override
        public void clearClientCache() {
            // 服务端不执行任何操作
            LOGGER.debug("[服务端代理] 客户端缓存清理被调用但被忽略");
        }
    }
}