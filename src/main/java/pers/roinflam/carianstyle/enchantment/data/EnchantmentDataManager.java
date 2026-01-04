package pers.roinflam.carianstyle.enchantment.data;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔数据管理器
 *
 * 提供线程安全的附魔运行时数据管理，包括冷却系统、计数器系统和通用数据存储
 * 所有数据都支持自动过期，避免内存泄漏
 */
@Mod.EventBusSubscriber
public class EnchantmentDataManager {

    // ==================== 冷却系统 ====================

    /**
     * 冷却数据存储
     * 键格式："enchantmentId:uuid"
     * 值：冷却结束的游戏时间
     */
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    /**
     * 设置冷却时间
     */
    public static void setCooldown(@Nonnull String enchantmentId, @Nonnull UUID entityUuid, int durationTicks) {
        String key = buildKey(enchantmentId, entityUuid);
        long expireTime = getCurrentGameTime() + durationTicks;
        COOLDOWNS.put(key, expireTime);
    }

    /**
     * 检查是否在冷却中
     */
    public static boolean isOnCooldown(@Nonnull String enchantmentId, @Nonnull UUID entityUuid) {
        String key = buildKey(enchantmentId, entityUuid);
        Long expireTime = COOLDOWNS.get(key);

        if (expireTime == null) {
            return false;
        }

        if (getCurrentGameTime() >= expireTime) {
            COOLDOWNS.remove(key);
            return false;
        }

        return true;
    }

    /**
     * 获取剩余冷却时间
     */
    public static int getRemainingCooldown(@Nonnull String enchantmentId, @Nonnull UUID entityUuid) {
        String key = buildKey(enchantmentId, entityUuid);
        Long expireTime = COOLDOWNS.get(key);

        if (expireTime == null) {
            return 0;
        }

        long currentTime = getCurrentGameTime();
        if (currentTime >= expireTime) {
            COOLDOWNS.remove(key);
            return 0;
        }

        return (int) (expireTime - currentTime);
    }

    /**
     * 清除冷却
     */
    public static void clearCooldown(@Nonnull String enchantmentId, @Nonnull UUID entityUuid) {
        String key = buildKey(enchantmentId, entityUuid);
        COOLDOWNS.remove(key);
    }

    /**
     * 清除实体的所有冷却
     */
    public static void clearAllCooldowns(@Nonnull UUID entityUuid) {
        String uuidStr = entityUuid.toString();
        COOLDOWNS.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + uuidStr));
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
     */
    public static int getCounter(@Nonnull String counterId, @Nonnull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);

        Long expireTime = COUNTER_EXPIRY.get(key);
        if (expireTime != null && getCurrentGameTime() >= expireTime) {
            COUNTERS.remove(key);
            COUNTER_EXPIRY.remove(key);
            return 0;
        }

        return COUNTERS.getOrDefault(key, 0);
    }

    /**
     * 设置计数器值（不过期）
     */
    public static void setCounter(@Nonnull String counterId, @Nonnull UUID entityUuid, int value) {
        String key = buildKey(counterId, entityUuid);
        COUNTERS.put(key, value);
    }

    /**
     * 设置计数器值（带过期时间）
     */
    public static void setCounter(@Nonnull String counterId, @Nonnull UUID entityUuid, int value, int expiryTicks) {
        String key = buildKey(counterId, entityUuid);
        COUNTERS.put(key, value);
        COUNTER_EXPIRY.put(key, getCurrentGameTime() + expiryTicks);
    }

    /**
     * 递增计数器
     */
    public static int incrementCounter(@Nonnull String counterId, @Nonnull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);
        int newValue = getCounter(counterId, entityUuid) + 1;
        COUNTERS.put(key, newValue);
        return newValue;
    }

    /**
     * 递增计数器（带过期时间）
     */
    public static int incrementCounter(@Nonnull String counterId, @Nonnull UUID entityUuid, int expiryTicks) {
        String key = buildKey(counterId, entityUuid);
        int newValue = getCounter(counterId, entityUuid) + 1;
        COUNTERS.put(key, newValue);
        COUNTER_EXPIRY.put(key, getCurrentGameTime() + expiryTicks);
        return newValue;
    }

    /**
     * 递减计数器
     */
    public static int decrementCounter(@Nonnull String counterId, @Nonnull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);
        int newValue = Math.max(0, getCounter(counterId, entityUuid) - 1);
        COUNTERS.put(key, newValue);
        return newValue;
    }

    /**
     * 重置计数器
     */
    public static void resetCounter(@Nonnull String counterId, @Nonnull UUID entityUuid) {
        String key = buildKey(counterId, entityUuid);
        COUNTERS.remove(key);
        COUNTER_EXPIRY.remove(key);
    }

    /**
     * 清除实体的所有计数器
     */
    public static void clearAllCounters(@Nonnull UUID entityUuid) {
        String uuidStr = entityUuid.toString();
        COUNTERS.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + uuidStr));
        COUNTER_EXPIRY.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + uuidStr));
    }

    // ==================== 通用数据存储系统（已修复内存泄漏） ====================

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
     */
    public static <T> void setData(@Nonnull String dataId, @Nonnull UUID entityUuid, @Nullable T data) {
        // 默认6000tick = 5分钟
        setData(dataId, entityUuid, data, 6000);
    }

    /**
     * 存储数据（带过期时间）
     */
    public static <T> void setData(@Nonnull String dataId, @Nonnull UUID entityUuid, @Nullable T data, int expiryTicks) {
        String key = buildKey(dataId, entityUuid);
        if (data == null) {
            GENERIC_DATA.remove(key);
            GENERIC_DATA_EXPIRY.remove(key);
        } else {
            GENERIC_DATA.put(key, data);
            GENERIC_DATA_EXPIRY.put(key, getCurrentGameTime() + expiryTicks);
        }
    }

    /**
     * 获取数据（自动检查过期）
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getData(@Nonnull String dataId, @Nonnull UUID entityUuid) {
        String key = buildKey(dataId, entityUuid);

        // 检查是否过期
        Long expireTime = GENERIC_DATA_EXPIRY.get(key);
        if (expireTime != null && getCurrentGameTime() >= expireTime) {
            GENERIC_DATA.remove(key);
            GENERIC_DATA_EXPIRY.remove(key);
            return null;
        }

        return (T) GENERIC_DATA.get(key);
    }

    /**
     * 刷新数据的过期时间
     */
    public static void refreshDataExpiry(@Nonnull String dataId, @Nonnull UUID entityUuid, int expiryTicks) {
        String key = buildKey(dataId, entityUuid);
        if (GENERIC_DATA.containsKey(key)) {
            GENERIC_DATA_EXPIRY.put(key, getCurrentGameTime() + expiryTicks);
        }
    }

    /**
     * 移除数据
     */
    public static void removeData(@Nonnull String dataId, @Nonnull UUID entityUuid) {
        String key = buildKey(dataId, entityUuid);
        GENERIC_DATA.remove(key);
        GENERIC_DATA_EXPIRY.remove(key);
    }

    /**
     * 清除实体的所有通用数据
     */
    public static void clearAllData(@Nonnull UUID entityUuid) {
        String uuidStr = entityUuid.toString();
        GENERIC_DATA.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + uuidStr));
        GENERIC_DATA_EXPIRY.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + uuidStr));
    }

    // ==================== 工具方法 ====================

    private static String buildKey(@Nonnull String id, @Nonnull UUID uuid) {
        return id + ":" + uuid.toString();
    }

    private static long getCurrentGameTime() {
        return System.currentTimeMillis() / 50;
    }

    // ==================== 自动清理系统 ====================

    private static int cleanupCounter = 0;

    @SubscribeEvent
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // 每200 tick（10秒）清理一次
        if (++cleanupCounter >= 200) {
            cleanupCounter = 0;
            cleanupExpiredData();
        }
    }

    /**
     * 清理所有过期数据
     */
    private static void cleanupExpiredData() {
        long currentTime = getCurrentGameTime();
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
        LogUtil.info("已清空所有附魔数据");
    }

    /**
     * 清除指定实体的所有数据
     */
    public static void clearAllForEntity(@Nonnull UUID entityUuid) {
        clearAllCooldowns(entityUuid);
        clearAllCounters(entityUuid);
        clearAllData(entityUuid);
    }

    /**
     * 获取统计信息
     */
    public static String getStatistics() {
        return String.format("附魔数据统计 - 冷却: %d, 计数器: %d, 通用数据: %d",
                COOLDOWNS.size(), COUNTERS.size(), GENERIC_DATA.size());
    }
}