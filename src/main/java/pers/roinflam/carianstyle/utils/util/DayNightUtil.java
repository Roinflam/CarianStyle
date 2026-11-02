package pers.roinflam.carianstyle.utils.util;

import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * 昼夜判定（两端通用）。
 * <p>
 * 原版 {@link World#isDaytime()} 读的是天光减弱值 {@code skylightSubtracted}，这个值只有服务端每 tick 刷新。
 * 客户端世界只在建世界时算过一次——那时世界时间还没从服务端同步过来——之后就不再更新，
 * 所以在客户端直接调 {@code isDaytime()} 一直是旧值。
 * </p>
 * <p>
 * 这里在客户端用同一条公式现算（{@code calculateSkylightSubtracted(1.0F)}），结果与服务端一致；
 * 服务端仍然直接用原版的值。只读世界时间与雨雷强度，不改任何原版状态。
 * </p>
 * <p>
 * 1.12.2 版：下界、末地这类非地表维度两端都恒为 false，和 1.20.1「固定时间的维度不算白天」一致
 * （1.12.2 的末地天体角度固定在正午，不拦的话服务端会一直算白天）。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
public final class DayNightUtil {

    private DayNightUtil() {
    }

    /**
     * 是否处于白天，口径与 1.20.1 服务端的 {@code isDay()} 相同。
     *
     * @param level 任意一端的世界
     * @return 白天返回 true；非地表维度（下界、末地）恒为 false
     */
    public static boolean isDay(@Nonnull World level) {
        if (!level.provider.isSurfaceWorld()) {
            return false;
        }
        if (!level.isRemote) {
            return level.isDaytime();
        }
        return level.calculateSkylightSubtracted(1.0F) < 4;
    }
}
