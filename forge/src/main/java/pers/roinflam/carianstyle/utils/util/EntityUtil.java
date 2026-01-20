package pers.roinflam.carianstyle.utils.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 实体工具类
 * Entity utility class
 *
 * 提供实体查询、属性访问等常用方法
 * Provides common methods for entity queries and attribute access
 */
public class EntityUtil {

    /**
     * 获取指定范围内的实体（立方体范围）
     * Get entities within specified range (cubic range)
     *
     * @param clazz 实体类型 / entity class
     * @param entity 中心实体 / center entity
     * @param range 范围（各方向相同）/ range (same for all directions)
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<T> clazz,
            @Nonnull Entity entity,
            double range) {
        return getNearbyEntities(clazz, entity, range, range, range, null);
    }

    /**
     * 获取指定范围内的实体（带过滤器）
     * Get entities within specified range (with filter)
     *
     * @param clazz 实体类型 / entity class
     * @param entity 中心实体 / center entity
     * @param range 范围（各方向相同）/ range (same for all directions)
     * @param predicate 过滤器 / filter
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<T> clazz,
            @Nonnull Entity entity,
            double range,
            @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, range, range, range, predicate);
    }

    /**
     * 获取指定范围内的实体（圆柱体范围）
     * Get entities within specified range (cylindrical range)
     *
     * @param clazz 实体类型 / entity class
     * @param entity 中心实体 / center entity
     * @param width 水平范围 / horizontal range
     * @param height 垂直范围 / vertical range
     * @param predicate 过滤器 / filter
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<T> clazz,
            @Nonnull Entity entity,
            double width,
            double height,
            @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, width, width, height, predicate);
    }

    /**
     * 获取指定范围内的实体（自定义各方向范围）
     * Get entities within specified range (custom range for each direction)
     *
     * @param clazz 实体类型 / entity class
     * @param entity 中心实体 / center entity
     * @param x X轴范围 / X-axis range
     * @param z Z轴范围 / Z-axis range
     * @param y Y轴范围 / Y-axis range
     * @param predicate 过滤器 / filter
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<T> clazz,
            @Nonnull Entity entity,
            double x,
            double z,
            double y,
            @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity.level(), entity.blockPosition(), x, z, y, predicate);
    }

    /**
     * 获取指定范围内的实体（基于世界和坐标）
     * Get entities within specified range (based on level and position)
     *
     * @param clazz 实体类型 / entity class
     * @param level 世界 / level
     * @param blockPos 中心坐标 / center position
     * @param x X轴范围 / X-axis range
     * @param z Z轴范围 / Z-axis range
     * @param y Y轴范围 / Y-axis range
     * @param predicate 过滤器 / filter
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<T> clazz,
            @Nonnull Level level,
            @Nonnull BlockPos blockPos,
            double x,
            double z,
            double y,
            @Nullable Predicate<? super T> predicate) {
        if (level.isClientSide) {
            return Collections.emptyList();
        }

        AABB aabb = new AABB(
                blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z,
                blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z
        );

        // 修复：正确处理 null predicate
        if (predicate == null) {
            // 使用不带 predicate 的方法
            return level.getEntitiesOfClass(clazz, aabb);
        } else {
            // 使用带 predicate 的方法
            return level.getEntitiesOfClass(clazz, aabb, predicate);
        }
    }
}