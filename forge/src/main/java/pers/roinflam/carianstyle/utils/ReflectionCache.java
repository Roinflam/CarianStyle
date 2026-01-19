package pers.roinflam.carianstyle.utils;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射缓存工具类
 * 缓存反射对象以提高性能
 */
public class ReflectionCache {

    // ===== 字段缓存 =====
    private static Field noJumpDelayField = null;
    private static Field useItemRemainingField = null;
    private static EntityDataAccessor<Float> dataHealthId = null;  // ⭐ 新增

    // ===== 方法缓存 =====
    private static Method updatingUsingItemMethod = null;

    // ===== 初始化状态 =====
    private static boolean initialized = false;
    private static boolean noJumpDelayAvailable = false;
    private static boolean useItemRemainingAvailable = false;
    private static boolean updatingUsingItemAvailable = false;
    private static boolean dataHealthIdAvailable = false;  // ⭐ 新增

    static {
        initialize();
    }

    /**
     * 初始化反射缓存
     */
    private static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        LogUtil.info("开始初始化反射缓存...");

        // 获取 noJumpDelay 字段
        // MCP名: noJumpDelay, SRG名: f_20954_
        try {
            noJumpDelayField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20954_");
            noJumpDelayField.setAccessible(true);
            noJumpDelayAvailable = true;
            LogUtil.info("✓ 成功获取 noJumpDelay 字段");
        } catch (Exception e) {
            LogUtil.error("✗ 无法获取 noJumpDelay 字段", e);
            noJumpDelayAvailable = false;
        }

        // 获取 useItemRemaining 字段
        // MCP名: useItemRemaining, SRG名: f_20936_
        try {
            useItemRemainingField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20936_");
            useItemRemainingField.setAccessible(true);
            useItemRemainingAvailable = true;
            LogUtil.info("✓ 成功获取 useItemRemaining 字段");
        } catch (Exception e) {
            LogUtil.error("✗ 无法获取 useItemRemaining 字段", e);
            useItemRemainingAvailable = false;
        }

        // ⭐ 获取 DATA_HEALTH_ID 字段
        // MCP名: DATA_HEALTH_ID, SRG名: f_20961_
        try {
            Field healthField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20961_");
            healthField.setAccessible(true);
            dataHealthId = (EntityDataAccessor<Float>) healthField.get(null);
            dataHealthIdAvailable = true;
            LogUtil.info("✓ 成功获取 DATA_HEALTH_ID 字段");
        } catch (Exception e) {
            LogUtil.error("✗ 无法获取 DATA_HEALTH_ID 字段", e);
            dataHealthIdAvailable = false;
        }

        // 获取 updatingUsingItem 方法
        // MCP名: updatingUsingItem, SRG名: m_21329_
        try {
            updatingUsingItemMethod = ObfuscationReflectionHelper.findMethod(
                    LivingEntity.class,
                    "m_21329_"
            );
            updatingUsingItemMethod.setAccessible(true);
            updatingUsingItemAvailable = true;
            LogUtil.info("✓ 成功获取 updatingUsingItem 方法");
        } catch (Exception e) {
            LogUtil.error("✗ 无法获取 updatingUsingItem 方法", e);
            updatingUsingItemAvailable = false;
        }

        LogUtil.info("反射缓存初始化完成 - 可用功能: " +
                (noJumpDelayAvailable ? "跳跃 " : "") +
                (useItemRemainingAvailable ? "物品使用 " : "") +
                (updatingUsingItemAvailable ? "物品更新 " : "") +
                (dataHealthIdAvailable ? "真伤扣血" : ""));
    }

    /**
     * 设置实体的跳跃延迟
     */
    public static void setJumpTicks(@Nonnull LivingEntity livingEntity, int ticks) {
        if (!noJumpDelayAvailable || noJumpDelayField == null) {
            return;
        }

        try {
            noJumpDelayField.setInt(livingEntity, ticks);
        } catch (Exception e) {
            LogUtil.error("设置跳跃延迟失败: " + livingEntity.getName().getString(), e);
        }
    }

    /**
     * 获取实体的跳跃延迟
     */
    public static int getJumpTicks(@Nonnull LivingEntity livingEntity) {
        if (!noJumpDelayAvailable || noJumpDelayField == null) {
            return 0;
        }

        try {
            return noJumpDelayField.getInt(livingEntity);
        } catch (Exception e) {
            LogUtil.error("获取跳跃延迟失败: " + livingEntity.getName().getString(), e);
        }
        return 0;
    }

    /**
     * 获取物品使用剩余时间
     */
    public static int getUseItemRemaining(@Nonnull LivingEntity livingEntity) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return -1;
        }

        try {
            return useItemRemainingField.getInt(livingEntity);
        } catch (Exception e) {
            LogUtil.error("获取物品使用剩余时间失败: " + livingEntity.getName().getString(), e);
        }
        return -1;
    }

    /**
     * 设置物品使用剩余时间
     */
    public static void setUseItemRemaining(@Nonnull LivingEntity livingEntity, int ticks) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return;
        }

        try {
            useItemRemainingField.setInt(livingEntity, Math.max(0, ticks));
        } catch (Exception e) {
            LogUtil.error("设置物品使用剩余时间失败: " + livingEntity.getName().getString(), e);
        }
    }

    /**
     * 减少物品使用剩余时间
     */
    public static boolean reduceUseItemRemaining(@Nonnull LivingEntity livingEntity, int ticksToReduce) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return false;
        }

        try {
            int current = useItemRemainingField.getInt(livingEntity);
            int newValue = Math.max(0, current - ticksToReduce);
            useItemRemainingField.setInt(livingEntity, newValue);
            return true;
        } catch (Exception e) {
            LogUtil.error("减少物品使用剩余时间失败: " + livingEntity.getName().getString(), e);
        }
        return false;
    }

    /**
     * 调用 updatingUsingItem 方法
     */
    public static boolean invokeUpdatingUsingItem(@Nonnull LivingEntity livingEntity) {
        if (!updatingUsingItemAvailable || updatingUsingItemMethod == null) {
            return false;
        }

        try {
            updatingUsingItemMethod.invoke(livingEntity);
            return true;
        } catch (Exception e) {
            LogUtil.error("调用 updatingUsingItem 失败: " + livingEntity.getName().getString(), e);
        }
        return false;
    }

    /**
     * 获取 DATA_HEALTH_ID
     * 用于直接操作实体血量,绕过 setHealth 方法
     */
    public static EntityDataAccessor<Float> getDataHealthId() {
        return dataHealthId;
    }

    // ===== 状态检查方法 =====

    public static boolean isNoJumpDelayAvailable() {
        return noJumpDelayAvailable;
    }

    public static boolean isUseItemRemainingAvailable() {
        return useItemRemainingAvailable;
    }

    public static boolean isUpdatingUsingItemAvailable() {
        return updatingUsingItemAvailable;
    }

    public static boolean isDataHealthIdAvailable() {
        return dataHealthIdAvailable;
    }
}