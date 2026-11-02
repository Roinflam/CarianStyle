package pers.roinflam.carianstyle.annotation.data;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔数据管理器
 * <p>
 * 提供线程安全的附魔运行时数据管理，包括冷却系统、计数器系统和通用数据存储
 * 所有数据都支持自动过期，避免内存泄漏
 * </p>
 * <p>
 * 修复记录：
 * - v2.1：将 System.currentTimeMillis()/50 替换为服务器tick计数器，确保与游戏tick同步
 * </p>
 * <p>
 * 性能优化记录 v2.2：
 * - buildKey()：UUID.toString() 每次调用创建新的36字符String，
 *   加上字符串拼接 id + ":" + uuid.toString() 又创建中间String和最终String，
 *   每次 buildKey 产生2-3个临时String对象。
 *   50人服务器每tick大量调用 isOnCooldown/getCounter/getData 等方法。
 *   优化：使用 ThreadLocal StringBuilder 复用缓冲区，临时字符串分配从3次降为1次（最终的toString()）。
 * - cleanupExpiredData()：频率从200tick(10秒)降为600tick(30秒)。
 *   过期数据在被访问时已被惰性清除（get/isOnCooldown时检查），
 *   定期清理只是兜底防止长期未访问的数据堆积，30秒间隔足够。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@Mod.EventBusSubscriber
public class EnchantmentDataManager {

    // ==================== 服务器Tick计数器 ====================

    /**
     * 服务器tick计数器，每个服务器tick递增1
     * <p>
     * 替代原来的 System.currentTimeMillis()/50，确保与游戏tick完全同步
     * </p>
     */
    private static long serverTickCount = 0;

    // ==================== Key构建优化（v2.2新增） ====================

    /**
     * ThreadLocal StringBuilder 复用缓冲区
     * <p>
     * 避免 buildKey() 中的字符串拼接产生临时对象。
     * 每个线程独享一个 StringBuilder，线程安全且无锁。
     * 初始容量80：典型key长度约为 enchantId(~30) + ":" + UUID(36) ≈ 67字符
     * </p>
     */
    private static final ThreadLocal<StringBuilder> KEY_BUILDER =
            ThreadLocal.withInitial(() -> new StringBuilder(80));

    // ==================== 冷却系统 ====================

    /**
     * 冷却数据存储
     * <p>
     * 键格式："enchantmentId:uuid"
     * 值：冷却结束的游戏时间
     * </p>
     */
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    /**
     * 设置冷却时间
     *
     * @param enchantmentId 附魔ID
     * @param entityUuid    实体UUID
     * @param durationTicks 持续时间（tick）
     */
    public static void setCooldown(@NotNull String enchantmentId, @NotNull UUID entityUuid, int durationTicks) {
        String key = buildKey(enchantmentId, entityUuid);
        long expireTime = serverTickCount + durationTicks;
        COOLDOWNS.put(key, expireTime);
    }

    /**
     * 检查是否在冷却中
     *
     * @param enchantmentId 附魔ID
     * @param entityUuid    实体UUID
     * @return 是否在冷却中
     */
    public static boolean isOnCooldown(@NotNull String enchantmentId, @NotNull UUID entityUuid) {
        String key = buildKey(enchantmentId, entityUuid);
        Long expireTime = COOLDOWNS.get(key);

        if (expireTime == null) {
            return false;
        }

        if (serverTickCount >= expireTime) {
            COOLDOWNS.remove(key);
            return false;
        }

        return true;
    }

    /**
     * 获取剩余冷却时间
     *
     * @param enchantmentId 附魔ID
     * @param entityUuid    实体UUID
     * @return 剩余冷却时间（tick），0表示无冷却
     */
    public static int getRemainingCooldown(@NotNull String enchantmentId, @NotNull UUID entityUuid) {
        String key = buildKey(enchantmentId, entityUuid);
        Long expireTime = COOLDOWNS.get(key);

        if (expireTime == null) {
            return 0;
        }

        if (serverTickCount >= expireTime) {
            COOLDOWNS.remove(key);
            return 0;
        }

        return (int) (expireTime - serverTickCount);
    }

    /**
     * 清除冷却
     *
     * @param enchantmentId 附魔ID
     * @param entityUuid    实体UUID
     */
    public static void clearCooldown(@NotNull String enchantmentId, @NotNull UUID entityUuid) {
        String key = buildKey(enchantmentId, entityUuid);
        COOLDOWNS.remove(key);
    }

    /**
     * 清除实体的所有冷却
     *
     * @param entityUuid 实体UUID
     */
    public static void clearAllCooldowns(@NotNull UUID entityUuid) {
        String suffix = ":" + entityUuid;
        COOLDOWNS.entrySet().removeIf(entry -> entry.getKey().endsWith(suffix));
    }

    // ==================== 计数器系统 ====================

    /**
     * 计数器数据存储
     */
    private static final Map<String, Integer> COUNTERS = new ConcurrentHashMap<>();

    /**
     * 计数器过期时间存储
     */
    private static final Map<String, Long> COUNTER_EXPIRY = new ConcurrentHashMap<>();

    /**
     * 获取计数器值
     *
     * @param counterId  计数器ID
     * @param entityUuid 实体UUID
     * @return 计数器值，0表示无计数或已过期
     */
    public static int getCounter(@NotNull String counterId, @NotNull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);

        Long expireTime = COUNTER_EXPIRY.get(key);
        if (expireTime != null && serverTickCount >= expireTime) {
            COUNTERS.remove(key);
            COUNTER_EXPIRY.remove(key);
            return 0;
        }

        return COUNTERS.getOrDefault(key, 0);
    }

    /**
     * 设置计数器值（不过期）
     *
     * @param counterId  计数器ID
     * @param entityUuid 实体UUID
     * @param value      计数器值
     */
    public static void setCounter(@NotNull String counterId, @NotNull UUID entityUuid, int value) {
        String key = buildKey(counterId, entityUuid);
        COUNTERS.put(key, value);
    }

    /**
     * 设置计数器值（带过期时间）
     *
     * @param counterId   计数器ID
     * @param entityUuid  实体UUID
     * @param value       计数器值
     * @param expiryTicks 过期时间（tick）
     */
    public static void setCounter(@NotNull String counterId, @NotNull UUID entityUuid, int value, int expiryTicks) {
        String key = buildKey(counterId, entityUuid);
        COUNTERS.put(key, value);
        COUNTER_EXPIRY.put(key, serverTickCount + expiryTicks);
    }

    /**
     * 递增计数器
     *
     * @param counterId  计数器ID
     * @param entityUuid 实体UUID
     * @return 递增后的值
     */
    public static int incrementCounter(@NotNull String counterId, @NotNull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);
        int newValue = getCounter(counterId, entityUuid) + 1;
        COUNTERS.put(key, newValue);
        return newValue;
    }

    /**
     * 递增计数器（带过期时间）
     *
     * @param counterId   计数器ID
     * @param entityUuid  实体UUID
     * @param expiryTicks 过期时间（tick）
     * @return 递增后的值
     */
    public static int incrementCounter(@NotNull String counterId, @NotNull UUID entityUuid, int expiryTicks) {
        String key = buildKey(counterId, entityUuid);
        int newValue = getCounter(counterId, entityUuid) + 1;
        COUNTERS.put(key, newValue);
        COUNTER_EXPIRY.put(key, serverTickCount + expiryTicks);
        return newValue;
    }

    /**
     * 递减计数器
     *
     * @param counterId  计数器ID
     * @param entityUuid 实体UUID
     * @return 递减后的值（最小为0）
     */
    public static int decrementCounter(@NotNull String counterId, @NotNull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);
        int newValue = Math.max(0, getCounter(counterId, entityUuid) - 1);
        COUNTERS.put(key, newValue);
        return newValue;
    }

    /**
     * 重置计数器
     *
     * @param counterId  计数器ID
     * @param entityUuid 实体UUID
     */
    public static void resetCounter(@NotNull String counterId, @NotNull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);
        COUNTERS.remove(key);
        COUNTER_EXPIRY.remove(key);
    }

    /**
     * 清除实体的所有计数器
     *
     * @param entityUuid 实体UUID
     */
    public static void clearAllCounters(@NotNull UUID entityUuid) {
        String suffix = ":" + entityUuid;
        COUNTERS.entrySet().removeIf(entry -> entry.getKey().endsWith(suffix));
        COUNTER_EXPIRY.entrySet().removeIf(entry -> entry.getKey().endsWith(suffix));
    }

    // ==================== 通用数据存储系统 ====================

    /**
     * 通用数据存储
     */
    private static final Map<String, Object> GENERIC_DATA = new ConcurrentHashMap<>();

    /**
     * 通用数据过期时间存储
     */
    private static final Map<String, Long> GENERIC_DATA_EXPIRY = new ConcurrentHashMap<>();

    /**
     * 存储数据（默认5分钟过期，防止内存泄漏）
     *
     * @param dataId     数据ID
     * @param entityUuid 实体UUID
     * @param data       要存储的数据，null表示删除
     * @param <T>        数据类型
     */
    public static <T> void setData(@NotNull String dataId, @NotNull UUID entityUuid, @Nullable T data) {
        // 默认6000tick = 5分钟
        setData(dataId, entityUuid, data, 6000);
    }

    /**
     * 存储数据（带过期时间）
     *
     * @param dataId      数据ID
     * @param entityUuid  实体UUID
     * @param data        要存储的数据，null表示删除
     * @param expiryTicks 过期时间（tick）
     * @param <T>         数据类型
     */
    public static <T> void setData(@NotNull String dataId, @NotNull UUID entityUuid, @Nullable T data, int expiryTicks) {
        String key = buildKey(dataId, entityUuid);
        if (data == null) {
            GENERIC_DATA.remove(key);
            GENERIC_DATA_EXPIRY.remove(key);
        } else {
            GENERIC_DATA.put(key, data);
            GENERIC_DATA_EXPIRY.put(key, serverTickCount + expiryTicks);
        }
    }

    /**
     * 获取数据（自动检查过期）
     *
     * @param dataId     数据ID
     * @param entityUuid 实体UUID
     * @param <T>        数据类型
     * @return 存储的数据，null表示不存在或已过期
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getData(@NotNull String dataId, @NotNull UUID entityUuid) {
        String key = buildKey(dataId, entityUuid);

        // 检查是否过期
        Long expireTime = GENERIC_DATA_EXPIRY.get(key);
        if (expireTime != null && serverTickCount >= expireTime) {
            GENERIC_DATA.remove(key);
            GENERIC_DATA_EXPIRY.remove(key);
            return null;
        }

        return (T) GENERIC_DATA.get(key);
    }

    /**
     * 检查数据是否存在且未过期
     *
     * @param dataId     数据ID
     * @param entityUuid 实体UUID
     * @return 数据是否存在
     */
    public static boolean hasData(@NotNull String dataId, @NotNull UUID entityUuid) {
        return getData(dataId, entityUuid) != null;
    }

    /**
     * 刷新数据的过期时间
     *
     * @param dataId      数据ID
     * @param entityUuid  实体UUID
     * @param expiryTicks 新的过期时间（tick）
     */
    public static void refreshDataExpiry(@NotNull String dataId, @NotNull UUID entityUuid, int expiryTicks) {
        String key = buildKey(dataId, entityUuid);
        if (GENERIC_DATA.containsKey(key)) {
            GENERIC_DATA_EXPIRY.put(key, serverTickCount + expiryTicks);
        }
    }

    /**
     * 移除数据
     *
     * @param dataId     数据ID
     * @param entityUuid 实体UUID
     */
    public static void removeData(@NotNull String dataId, @NotNull UUID entityUuid) {
        String key = buildKey(dataId, entityUuid);
        GENERIC_DATA.remove(key);
        GENERIC_DATA_EXPIRY.remove(key);
    }

    /**
     * 清除实体的所有通用数据
     *
     * @param entityUuid 实体UUID
     */
    public static void clearAllData(@NotNull UUID entityUuid) {
        String suffix = ":" + entityUuid;
        GENERIC_DATA.entrySet().removeIf(entry -> entry.getKey().endsWith(suffix));
        GENERIC_DATA_EXPIRY.entrySet().removeIf(entry -> entry.getKey().endsWith(suffix));
    }

    // ==================== 工具方法 ====================

    /**
     * 构建存储键（v2.2优化版）
     * <p>
     * 使用 ThreadLocal StringBuilder 复用缓冲区，
     * 避免字符串拼接产生的临时 String 对象。
     * 原实现 id + ":" + uuid.toString() 每次产生2-3个临时String，
     * 优化后只有最终 sb.toString() 产生1个String。
     * </p>
     *
     * @param id   数据ID
     * @param uuid 实体UUID
     * @return 格式化的键
     */
    private static String buildKey(@NotNull String id, @NotNull UUID uuid) {
        StringBuilder sb = KEY_BUILDER.get();
        sb.setLength(0); // 重置长度而非创建新实例
        sb.append(id).append(':').append(uuid);
        return sb.toString();
    }

    /**
     * 获取当前游戏时间
     * <p>
     * 使用服务器tick计数器，而非 System.currentTimeMillis()/50
     * 这确保了冷却时间与服务器TPS完全同步
     * </p>
     *
     * @return 游戏时间（以tick为单位）
     */
    private static long getCurrentGameTime() {
        return serverTickCount;
    }

    // ==================== 自动清理系统 ====================

    /**
     * 服务器Tick事件处理器
     * <p>
     * 递增tick计数器，定期自动清理过期数据。
     * v2.2优化：清理频率从200tick(10秒)降为600tick(30秒)。
     * 过期数据在被访问时已被惰性清除（get/isOnCooldown时检查），
     * 定期清理只是兜底防止长期未访问的数据堆积。
     * </p>
     *
     * @param event 服务器Tick事件
     */
    @SubscribeEvent
    public static void onServerTick(@NotNull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // 递增tick计数器
        serverTickCount++;

        // v2.2优化：每600tick（30秒）清理一次
        if (serverTickCount % 600 == 0) {
            cleanupExpiredData();
        }
    }

    /**
     * 清理所有过期数据
     */
    private static void cleanupExpiredData() {
        long currentTime = serverTickCount;
        int removedCount = 0;

        // 清理过期的冷却
        Iterator<Map.Entry<String, Long>> cooldownIterator = COOLDOWNS.entrySet().iterator();
        while (cooldownIterator.hasNext()) {
            if (cooldownIterator.next().getValue() <= currentTime) {
                cooldownIterator.remove();
                removedCount++;
            }
        }

        // 清理过期的计数器
        Iterator<Map.Entry<String, Long>> counterExpiryIterator = COUNTER_EXPIRY.entrySet().iterator();
        while (counterExpiryIterator.hasNext()) {
            Map.Entry<String, Long> entry = counterExpiryIterator.next();
            if (entry.getValue() <= currentTime) {
                COUNTERS.remove(entry.getKey());
                counterExpiryIterator.remove();
                removedCount++;
            }
        }

        // 清理过期的通用数据
        Iterator<Map.Entry<String, Long>> dataExpiryIterator = GENERIC_DATA_EXPIRY.entrySet().iterator();
        while (dataExpiryIterator.hasNext()) {
            Map.Entry<String, Long> entry = dataExpiryIterator.next();
            if (entry.getValue() <= currentTime) {
                GENERIC_DATA.remove(entry.getKey());
                dataExpiryIterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            LogUtil.debug("清理过期数据: %d 条", removedCount);
        }
    }

    /**
     * 清空所有数据
     */
    public static void clearAll() {
        COOLDOWNS.clear();
        COUNTERS.clear();
        COUNTER_EXPIRY.clear();
        GENERIC_DATA.clear();
        GENERIC_DATA_EXPIRY.clear();
        serverTickCount = 0;
        LogUtil.info("已清空所有附魔数据");
    }

    /**
     * 清除指定实体的所有数据
     *
     * @param entityUuid 实体UUID
     */
    public static void clearAllForEntity(@NotNull UUID entityUuid) {
        clearAllCooldowns(entityUuid);
        clearAllCounters(entityUuid);
        clearAllData(entityUuid);
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public static String getStatistics() {
        return String.format("附魔数据统计 - 冷却: %d, 计数器: %d, 通用数据: %d, 服务器Tick: %d",
                COOLDOWNS.size(), COUNTERS.size(), GENERIC_DATA.size(), serverTickCount);
    }
}
