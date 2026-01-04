package pers.roinflam.carianstyle.utils.util;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/**
 * 实体工具类
 * <p>
 * 提供实体查询、属性访问等常用方法
 * </p>
 */
public class EntityUtil {

    /** 缓存的fire字段 */
    private static Field fireField = null;

    /** fire字段初始化标记 */
    private static boolean fireFieldInitialized = false;

    /**
     * 获取指定范围内的实体（立方体范围）
     *
     * @param clazz  实体类型
     * @param entity 中心实体
     * @param range  范围（各方向相同）
     * @return 实体列表
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<? extends T> clazz,
            @Nonnull Entity entity,
            double range) {
        return getNearbyEntities(clazz, entity, range, range, range, null);
    }

    /**
     * 获取指定范围内的实体（带过滤器）
     *
     * @param clazz     实体类型
     * @param entity    中心实体
     * @param range     范围（各方向相同）
     * @param predicate 过滤器
     * @return 实体列表
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<? extends T> clazz,
            @Nonnull Entity entity,
            double range,
            @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, range, range, range, predicate);
    }

    /**
     * 获取指定范围内的实体（圆柱体范围）
     *
     * @param clazz     实体类型
     * @param entity    中心实体
     * @param width     水平范围
     * @param height    垂直范围
     * @param predicate 过滤器
     * @return 实体列表
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<? extends T> clazz,
            @Nonnull Entity entity,
            double width,
            double height,
            @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, width, width, height, predicate);
    }

    /**
     * 获取指定范围内的实体（自定义各方向范围）
     *
     * @param clazz     实体类型
     * @param entity    中心实体
     * @param x         X轴范围
     * @param z         Z轴范围
     * @param y         Y轴范围
     * @param predicate 过滤器
     * @return 实体列表
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<? extends T> clazz,
            @Nonnull Entity entity,
            double x,
            double z,
            double y,
            @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity.world, entity.getPosition(), x, z, y, predicate);
    }

    /**
     * 获取指定范围内的实体（基于世界和坐标）
     *
     * @param clazz     实体类型
     * @param world     世界
     * @param blockPos  中心坐标
     * @param x         X轴范围
     * @param z         Z轴范围
     * @param y         Y轴范围
     * @param predicate 过滤器
     * @return 实体列表
     */
    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(
            @Nonnull Class<? extends T> clazz,
            @Nonnull World world,
            @Nonnull BlockPos blockPos,
            double x,
            double z,
            double y,
            @Nullable Predicate<? super T> predicate) {
        if (world.isRemote) {
            return Collections.emptyList();
        }

        AxisAlignedBB aabb = new AxisAlignedBB(
                blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z,
                blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z
        );
        return world.getEntitiesWithinAABB(clazz, aabb, predicate);
    }

    /**
     * 获取实体的燃烧时间
     * <p>
     * 使用缓存的反射字段，避免重复查找
     * </p>
     *
     * @param entity 实体
     * @return 燃烧时间（tick），-999表示获取失败
     */
    public static int getFire(@Nonnull Entity entity) {
        try {
            if (!fireFieldInitialized) {
                initFireField();
            }

            if (fireField != null) {
                return fireField.getInt(entity);
            }
        } catch (Exception e) {
            // 忽略异常，返回默认值
        }
        return -999;
    }

    /**
     * 初始化fire字段（线程安全）
     */
    private static synchronized void initFireField() {
        if (fireFieldInitialized) {
            return;
        }

        fireFieldInitialized = true;

        try {
            // 尝试开发环境名称
            fireField = Entity.class.getDeclaredField("fire");
            fireField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                // 尝试混淆后名称
                fireField = ObfuscationReflectionHelper.findField(Entity.class, "field_190534_ay");
            } catch (Exception e1) {
                LogUtil.error("无法获取Entity.fire字段", e1);
                fireField = null;
            }
        }
    }
}