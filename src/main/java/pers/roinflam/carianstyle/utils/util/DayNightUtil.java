package pers.roinflam.carianstyle.utils.util;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

/**
 * 昼夜判定（两端通用）。
 * <p>
 * 原版 {@link Level#isDay()} 读的是 {@code skyDarken} 字段，而这个字段只有服务端每 tick
 * 调 {@code updateSkyBrightness()} 刷新。客户端只在 {@code ClientLevel} 构造时算过一次——
 * 那时世界时间还没从服务端同步过来，算出来是白天——之后就再也不更新，
 * 所以在客户端直接调 {@code isDay()} 永远得到「白天」。
 * </p>
 * <p>
 * 这里在客户端按 {@code updateSkyBrightness()} 的同一条公式现算，结果与服务端一致；
 * 服务端仍然直接用原版字段。只读世界时间与雨雷强度，不改任何原版状态。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
public final class DayNightUtil {

    private DayNightUtil() {
    }

    /**
     * 是否处于白天，口径与服务端 {@link Level#isDay()} 相同。
     *
     * @param level 任意一端的世界
     * @return 白天返回 true；固定时间的维度（下界、末地）恒为 false
     */
    public static boolean isDay(@Nonnull Level level) {
        if (!level.isClientSide) {
            return level.isDay();
        }
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        double rain = 1.0D - (double) (level.getRainLevel(1.0F) * 5.0F) / 16.0D;
        double thunder = 1.0D - (double) (level.getThunderLevel(1.0F) * 5.0F) / 16.0D;
        double sun = 0.5D + 2.0D * Mth.clamp((double) Mth.cos(level.getTimeOfDay(1.0F) * ((float) Math.PI * 2F)), -0.25D, 0.25D);
        int skyDarken = (int) ((1.0D - sun * rain * thunder) * 11.0D);
        return skyDarken < 4;
    }
}
