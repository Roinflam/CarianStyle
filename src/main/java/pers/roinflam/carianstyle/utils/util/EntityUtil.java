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
     * 获取指定范围内的实体（<b>圆形范围</b>）。
     * Get entities within specified range (<b>circular range</b>).
     * <p>
     * 改动说明：本「单一半径」重载已由旧的「方块坐标 + 轴对齐立方体（AABB.inflate）」
     * 改为「精确小数坐标 + 水平圆 + 垂直 ±range 的圆柱」判定（见 {@link #getNearbyEntitiesCircular}）。
     * <ul>
     *     <li>中心取实体<b>精确坐标</b>（{@code entity.getX/Y/Z}），不再向下取整，过渡平滑无方块吸附；</li>
     *     <li>水平方向按实体<b>中心点</b>距离 {@code dx^2 + dz^2 <= range^2} 判定（真正的圆）；</li>
     *     <li>垂直方向保留 ±range（与旧立方体一致，避免改变竖直触及范围）。</li>
     * </ul>
     * 注意：相比旧立方体，对角线触及范围由 range×1.41 收为 range；多轴（width/height、x/z/y）
     * 重载仍为盒形，未改动。
     *
     * @param clazz  实体类型 / entity class
     * @param entity 中心实体 / center entity
     * @param range  半径（格）/ radius (blocks)
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<T> clazz,
            @Nonnull Entity entity,
            double range) {
        return getNearbyEntitiesCircular(clazz, entity, range, null);
    }

    /**
     * 获取指定范围内的实体（<b>圆形范围</b>，带过滤器）。
     * Get entities within specified range (<b>circular range</b>, with filter).
     * <p>
     * 同 {@link #getNearbyEntities(Class, Entity, double)}，使用精确坐标 + 圆柱判定。
     *
     * @param clazz     实体类型 / entity class
     * @param entity    中心实体 / center entity
     * @param range     半径（格）/ radius (blocks)
     * @param predicate 过滤器 / filter
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<T> clazz,
            @Nonnull Entity entity,
            double range,
            @Nullable Predicate<? super T> predicate) {
        return getNearbyEntitiesCircular(clazz, entity, range, predicate);
    }

    /**
     * 圆形范围实体查询的核心实现（精确小数坐标 + 水平圆 + 垂直 ±radius 的圆柱）。
     * Core implementation of circular range query (precise coordinates + horizontal circle + vertical cylinder).
     * <p>
     * 行为与旧实现保持一致的部分：仅服务端有效（客户端返回空列表）；
     * 不主动排除中心实体本身（与 {@code getEntitiesOfClass} 一致，由调用方自行决定是否过滤自身）。
     *
     * @param clazz     实体类型 / entity class
     * @param entity    中心实体 / center entity
     * @param radius    半径（格）/ radius (blocks)
     * @param predicate 附加过滤器（可空）/ extra filter (nullable)
     * @return 实体列表 / entity list
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntitiesCircular(
            @Nonnull Class<T> clazz,
            @Nonnull Entity entity,
            double radius,
            @Nullable Predicate<? super T> predicate) {
        Level level = entity.level();
        if (level.isClientSide) {
            return Collections.emptyList();
        }

        // 精确中心坐标（不取整）
        final double centerX = entity.getX();
        final double centerY = entity.getY();
        final double centerZ = entity.getZ();
        final double radiusSqr = radius * radius;

        // 宽相位预筛：以精确中心 ±radius 建立 AABB（垂直同样 ±radius，保留旧立方体的竖直范围）
        AABB box = new AABB(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius, centerY + radius, centerZ + radius
        );

        // 精确圆柱过滤：按实体中心点判定，水平圆 + 垂直 ±radius
        Predicate<T> circleFilter = candidate -> {
            double dx = candidate.getX() - centerX;
            double dz = candidate.getZ() - centerZ;
            // 水平圆外：剔除
            if (dx * dx + dz * dz > radiusSqr) {
                return false;
            }
            // 垂直范围外：剔除
            if (Math.abs(candidate.getY() - centerY) > radius) {
                return false;
            }
            // 通过附加过滤器
            return predicate == null || predicate.test(candidate);
        };

        return level.getEntitiesOfClass(clazz, box, circleFilter);
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
