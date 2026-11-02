package pers.roinflam.carianstyle.utils;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.datasync.DataParameter;
import pers.roinflam.carianstyle.mc112.util.Reflect;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射缓存工具类
 * 缓存反射对象以提高性能
 * <p>
 * 1.12.2 版：字段和方法的名字换成 1.12.2 的，一律经 {@link Reflect} 先找开发环境名、再找正式环境名：
 * {@code jumpTicks}（1.20.1 叫 noJumpDelay）、{@code activeItemStackUseCount}（useItemRemaining）、
 * {@code HEALTH}（DATA_HEALTH_ID）、{@code updateActiveHand()}（updatingUsingItem）。对外的方法名不变。
 * </p>
 */
public class ReflectionCache {

    // ===== 字段缓存 =====
    private static Field noJumpDelayField = null;
    private static Field useItemRemainingField = null;
    private static DataParameter<Float> dataHealthId = null;

    // ===== 方法缓存 =====
    private static Method updatingUsingItemMethod = null;

    // ===== 初始化状态 =====
    private static boolean initialized = false;
    private static boolean noJumpDelayAvailable = false;
    private static boolean useItemRemainingAvailable = false;
    private static boolean updatingUsingItemAvailable = false;
    private static boolean dataHealthIdAvailable = false;

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

        // 获取跳跃冷却字段
        // MCP名: jumpTicks, SRG名: field_70773_bE
        try {
            noJumpDelayField = Reflect.field(EntityLivingBase.class, "jumpTicks", "field_70773_bE");
            noJumpDelayAvailable = true;
            LogUtil.info("成功获取 noJumpDelay 字段");
        } catch (Exception e) {
            LogUtil.error("无法获取 noJumpDelay 字段", e);
            noJumpDelayAvailable = false;
        }

        // 获取物品使用剩余时间字段
        // MCP名: activeItemStackUseCount, SRG名: field_184628_bn
        try {
            useItemRemainingField = Reflect.field(EntityLivingBase.class, "activeItemStackUseCount", "field_184628_bn");
            useItemRemainingAvailable = true;
            LogUtil.info("成功获取 useItemRemaining 字段");
        } catch (Exception e) {
            LogUtil.error("无法获取 useItemRemaining 字段", e);
            useItemRemainingAvailable = false;
        }

        // 获取血量同步参数
        // MCP名: HEALTH, SRG名: field_184632_c
        try {
            Field healthField = Reflect.field(EntityLivingBase.class, "HEALTH", "field_184632_c");
            @SuppressWarnings("unchecked")
            DataParameter<Float> health = (DataParameter<Float>) healthField.get(null);
            dataHealthId = health;
            dataHealthIdAvailable = true;
            LogUtil.info("成功获取 DATA_HEALTH_ID 字段");
        } catch (Exception e) {
            LogUtil.error("无法获取 DATA_HEALTH_ID 字段", e);
            dataHealthIdAvailable = false;
        }

        // 获取每 tick 推进物品使用的方法
        // MCP名: updateActiveHand, SRG名: func_184608_ct
        try {
            updatingUsingItemMethod = Reflect.method(EntityLivingBase.class, "updateActiveHand", "func_184608_ct");
            updatingUsingItemAvailable = true;
            LogUtil.info("成功获取 updatingUsingItem 方法");
        } catch (Exception e) {
            LogUtil.error("无法获取 updatingUsingItem 方法", e);
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
    public static void setJumpTicks(@Nonnull EntityLivingBase livingEntity, int ticks) {
        if (!noJumpDelayAvailable || noJumpDelayField == null) {
            return;
        }

        try {
            noJumpDelayField.setInt(livingEntity, ticks);
        } catch (Exception e) {
            LogUtil.error("设置跳跃延迟失败: " + livingEntity.getName(), e);
        }
    }

    /**
     * 获取实体的跳跃延迟
     */
    public static int getJumpTicks(@Nonnull EntityLivingBase livingEntity) {
        if (!noJumpDelayAvailable || noJumpDelayField == null) {
            return 0;
        }

        try {
            return noJumpDelayField.getInt(livingEntity);
        } catch (Exception e) {
            LogUtil.error("获取跳跃延迟失败: " + livingEntity.getName(), e);
        }
        return 0;
    }

    /**
     * 获取物品使用剩余时间
     */
    public static int getUseItemRemaining(@Nonnull EntityLivingBase livingEntity) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return -1;
        }

        try {
            return useItemRemainingField.getInt(livingEntity);
        } catch (Exception e) {
            LogUtil.error("获取物品使用剩余时间失败: " + livingEntity.getName(), e);
        }
        return -1;
    }

    /**
     * 设置物品使用剩余时间
     */
    public static void setUseItemRemaining(@Nonnull EntityLivingBase livingEntity, int ticks) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return;
        }

        try {
            useItemRemainingField.setInt(livingEntity, Math.max(0, ticks));
        } catch (Exception e) {
            LogUtil.error("设置物品使用剩余时间失败: " + livingEntity.getName(), e);
        }
    }

    /**
     * 减少物品使用剩余时间
     */
    public static boolean reduceUseItemRemaining(@Nonnull EntityLivingBase livingEntity, int ticksToReduce) {
        if (!useItemRemainingAvailable || useItemRemainingField == null) {
            return false;
        }

        try {
            int current = useItemRemainingField.getInt(livingEntity);
            int newValue = Math.max(0, current - ticksToReduce);
            useItemRemainingField.setInt(livingEntity, newValue);
            return true;
        } catch (Exception e) {
            LogUtil.error("减少物品使用剩余时间失败: " + livingEntity.getName(), e);
        }
        return false;
    }

    /**
     * 调用 updatingUsingItem 方法
     */
    public static boolean invokeUpdatingUsingItem(@Nonnull EntityLivingBase livingEntity) {
        if (!updatingUsingItemAvailable || updatingUsingItemMethod == null) {
            return false;
        }

        try {
            updatingUsingItemMethod.invoke(livingEntity);
            return true;
        } catch (Exception e) {
            LogUtil.error("调用 updatingUsingItem 失败: " + livingEntity.getName(), e);
        }
        return false;
    }

    /**
     * 获取 DATA_HEALTH_ID
     * 用于直接操作实体血量,绕过 setHealth 方法
     */
    public static DataParameter<Float> getDataHealthId() {
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