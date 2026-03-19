// LootingLevelCache.java
package com.flameforge.mixinfixserver.util;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Looting等级缓存工具类
 * <p>
 * 在同一次掉落物生成过程中，LootTable的每个LootPool都会通过
 * ForgeHooks.getLootingLevel → Curios遍历所有饰品槽位计算附魔等级，
 * 此缓存避免同一次掉落事件中的重复计算。
 * </p>
 *
 * @author RoinFlam
 */
public final class LootingLevelCache {

    /** 缓存的被击杀实体引用 */
    private static final ThreadLocal<Entity> CACHED_TARGET = new ThreadLocal<>();

    /** 缓存的击杀者实体引用 */
    private static final ThreadLocal<Entity> CACHED_KILLER = new ThreadLocal<>();

    /** 缓存的looting等级 */
    private static final ThreadLocal<Integer> CACHED_LEVEL = new ThreadLocal<>();

    private LootingLevelCache() {
    }

    /**
     * 尝试从缓存获取looting等级
     *
     * @param target 被击杀的实体
     * @param killer 击杀者（可能为null）
     * @return 缓存的looting等级，如果缓存未命中则返回null
     */
    @Nullable
    public static Integer get(Entity target, @Nullable Entity killer) {
        // 引用相等判断，确保是同一次掉落事件的重复调用
        if (CACHED_TARGET.get() == target
                && CACHED_KILLER.get() == killer
                && CACHED_LEVEL.get() != null) {
            return CACHED_LEVEL.get();
        }
        return null;
    }

    /**
     * 存储looting等级到缓存
     *
     * @param target 被击杀的实体
     * @param killer 击杀者
     * @param level  计算出的looting等级
     */
    public static void put(Entity target, @Nullable Entity killer, int level) {
        CACHED_TARGET.set(target);
        CACHED_KILLER.set(killer);
        CACHED_LEVEL.set(level);
    }

    /**
     * 清除缓存，防止实体引用泄漏
     * 应在掉落计算完成后调用
     */
    public static void clear() {
        CACHED_TARGET.remove();
        CACHED_KILLER.remove();
        CACHED_LEVEL.remove();
    }
}