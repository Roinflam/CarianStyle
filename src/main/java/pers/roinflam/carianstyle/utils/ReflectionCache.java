package pers.roinflam.carianstyle.utils;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.mixin.accessor.LivingEntityAccessor;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;

/**
 * 访问 {@link LivingEntity} 私有成员的工具类（跳跃延迟、物品使用剩余时间、血量同步键、更新物品使用）。
 * <p>
 * <b>问题：</b>1.20.1 这里按 SRG 名（{@code f_20954_} 等）反射。1.21.1 的 NeoForge 运行期就是 Mojang 映射，
 * 没有 SRG 名，FML 的 {@code ObfuscationReflectionHelper} 也只是普通反射、不做名字映射，原写法全部找不到成员。
 * </p>
 * <p>
 * <b>做法：</b>改走访问器 mixin {@link LivingEntityAccessor}：名字写错在编译期 / 挂载期就会报出来，
 * 不用访问转换器，也没有反射的开销。对外的方法签名和返回值约定（不可用时返回 -1 / false / 0）保持不变，
 * 调用方不用改；因为访问器在 mixin 挂载时就确定了，各「是否可用」一律为 true。
 * </p>
 */
public class ReflectionCache {

    private static LivingEntityAccessor accessor(@Nonnull LivingEntity livingEntity) {
        return (LivingEntityAccessor) livingEntity;
    }

    /**
     * 设置实体的跳跃延迟
     */
    public static void setJumpTicks(@Nonnull LivingEntity livingEntity, int ticks) {
        accessor(livingEntity).carianstyle$setNoJumpDelay(ticks);
    }

    /**
     * 获取实体的跳跃延迟
     */
    public static int getJumpTicks(@Nonnull LivingEntity livingEntity) {
        return accessor(livingEntity).carianstyle$getNoJumpDelay();
    }

    /**
     * 获取物品使用剩余时间
     */
    public static int getUseItemRemaining(@Nonnull LivingEntity livingEntity) {
        return accessor(livingEntity).carianstyle$getUseItemRemaining();
    }

    /**
     * 设置物品使用剩余时间
     */
    public static void setUseItemRemaining(@Nonnull LivingEntity livingEntity, int ticks) {
        accessor(livingEntity).carianstyle$setUseItemRemaining(Math.max(0, ticks));
    }

    /**
     * 减少物品使用剩余时间
     */
    public static boolean reduceUseItemRemaining(@Nonnull LivingEntity livingEntity, int ticksToReduce) {
        LivingEntityAccessor acc = accessor(livingEntity);
        acc.carianstyle$setUseItemRemaining(Math.max(0, acc.carianstyle$getUseItemRemaining() - ticksToReduce));
        return true;
    }

    /**
     * 调用 updatingUsingItem 方法
     */
    public static boolean invokeUpdatingUsingItem(@Nonnull LivingEntity livingEntity) {
        try {
            accessor(livingEntity).carianstyle$updatingUsingItem();
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
        return LivingEntityAccessor.carianstyle$getDataHealthId();
    }

    // ===== 状态检查方法 =====

    public static boolean isNoJumpDelayAvailable() {
        return true;
    }

    public static boolean isUseItemRemainingAvailable() {
        return true;
    }

    public static boolean isUpdatingUsingItemAvailable() {
        return true;
    }

    public static boolean isDataHealthIdAvailable() {
        return true;
    }
}