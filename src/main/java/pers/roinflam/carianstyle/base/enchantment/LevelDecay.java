package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.enchantment.Enchantment;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 高等级衰减：附魔等级超过阈值后，按配置的曲线折算成「实际生效等级」。
 *
 * <h3>为什么要有</h3>
 * <p>
 * 整合包里附魔来源很多，指令、升级类模组都能把等级堆到几十上百级。本模组的数值大多是「每级 +X」的线性公式，
 * 100 级就是 1 级的 100 倍，远超设计范围。{@code levelLimit} 只能一刀切到 10 级，
 * 这里给服主一个折中：高等级照样比低等级强，只是越往上每多一级带来的提升越少。
 * </p>
 *
 * <h3>曲线怎么算</h3>
 * <ul>
 *   <li>阈值 T：{@code threshold = 0} 时取附魔自己的等级上限（{@link Enchantment#getMaxLevel()}，1 / 3 / 5），
 *       大于 0 时所有附魔统一用这个值。等级 ≤ T 原样生效。</li>
 *   <li>曲线是若干个「倍数:百分比」点（{@code byMultiple = true}，默认），倍数指「阈值的几倍」，
 *       意思是「等级到了 T × 倍数时，实际生效等级 = 等级 × 百分比」。
 *       {@code byMultiple = false} 时点直接写实际等级（「等级:百分比」），所有附魔共用同一组等级。
 *       起点固定是 (T, 100%)，不超过 T 的点忽略。</li>
 *   <li>两点之间对<b>实际生效等级</b>做线性插值（不是对百分比插值），这样只要求每个点的生效等级不比前一个点低，
 *       整条曲线就单调不降——多一级永远不会变弱。配错了（后一个点反而更低）会被抬平到前一个点，并打一条警告。</li>
 *   <li>超过最后一个点后，按最后一段的斜率继续涨（每多一级加的生效等级和最后一段相同），
 *       所以最后两点挑得平缓，极端等级（指令刷的 255、32767）也涨得很慢。最后一段斜率为 0 就等于封顶。</li>
 *   <li>{@code maxLevel > 0} 时生效等级最后再封一次顶。</li>
 *   <li>结果四舍五入成整数，并且保证 T ≤ 结果 ≤ 原等级。上限只有 1 级、没有成长的附魔，
 *       衰减后依然 ≥ 1，判定「有没有这个附魔」的地方不受影响。</li>
 * </ul>
 *
 * <h3>为什么默认按倍数</h3>
 * <p>
 * 本模组附魔的上限有 1 / 3 / 5 三档。按实际等级写曲线时，10 级不管哪个附魔都折成 9 级：
 * 上限 5 的附魔折完是满级的 1.8 倍，上限 3 的是 3 倍，上限 1 的是 9 倍——上限越低削得越少。
 * 按倍数写，「堆到满级的 10 倍」对哪个附魔都折成满级的 5 倍，各档削弱幅度一致。
 * </p>
 *
 * <p>
 * 默认曲线：满级 2 倍 → 1.8 倍，4 → 3，6 → 3.9，10 → 5，15 → 6，20 → 6.6，40 → 8，再往上每多 1 倍只加 0.07 倍。
 * 上限 5 的附魔即 10 级 → 9，50 → 25，100 → 33，200 → 40；上限 1 的附魔 10 级 → 5，100 → 12。
 * 只影响本模组自己的附魔，原版和其他模组的附魔不管。
 * </p>
 *
 * <p>
 * 配置是 COMMON，不会同步给客户端：百科、提示框、HUD 读的是客户端本地的配置。
 * 整合包分发时客户端和服务端用同一份配置即可。
 * </p>
 */
public final class LevelDecay {

    /** 配置界面、配置文件的默认曲线（倍数:百分比） */
    public static final List<String> DEFAULT_CURVE = Collections.unmodifiableList(Arrays.asList(
            "2:90", "4:75", "6:65", "10:50", "15:40", "20:33", "40:20"));

    private static volatile boolean enabled = false;
    private static volatile boolean byMultiple = true;
    private static volatile boolean showInName = true;
    private static volatile int fixedThreshold = 0;
    private static volatile int maxLevel = 0;
    /** 升序排好的曲线点，[i][0] 倍数或等级、[i][1] 百分比（0~1） */
    private static volatile double[][] points = new double[0][];

    private LevelDecay() {
    }

    /**
     * 配置重新加载时由 {@code ConfigLoader.bake()} 调用。
     */
    public static void bake(boolean enable, int threshold, boolean multiple, int cap,
                            @Nonnull List<? extends String> curve, boolean showName) {
        List<double[]> parsed = new ArrayList<>();
        for (String raw : curve) {
            double[] point = parsePoint(raw);
            if (point == null) {
                LogUtil.warn("卡利亚式附魔 - 高等级衰减曲线里有一项看不懂，已跳过：\"%s\"（写法：倍数或等级:百分比，例如 10:50）", raw);
                continue;
            }
            parsed.add(point);
        }
        parsed.sort((a, b) -> Double.compare(a[0], b[0]));

        // 同一等级写了多次只留最后一个
        List<double[]> dedup = new ArrayList<>();
        for (double[] p : parsed) {
            if (!dedup.isEmpty() && dedup.get(dedup.size() - 1)[0] == p[0]) {
                dedup.set(dedup.size() - 1, p);
            } else {
                dedup.add(p);
            }
        }

        // 只在配置层面检查一次「后一个点的生效等级比前一个点低」，阈值不同的附魔在 compute 里还会再抬平
        double prev = 0;
        for (double[] p : dedup) {
            double eff = p[0] * p[1];
            if (eff < prev) {
                LogUtil.warn("卡利亚式附魔 - 高等级衰减曲线 %s 那一点折算后（%.1f）比前一个点（%.1f）低，"
                        + "已按前一个点处理，否则多一级反而变弱", trim(p[0]), eff, prev);
            }
            prev = Math.max(prev, eff);
        }

        points = dedup.toArray(new double[0][]);
        fixedThreshold = Math.max(0, threshold);
        byMultiple = multiple;
        maxLevel = Math.max(0, cap);
        showInName = showName;
        enabled = enable;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /**
     * 解析「倍数或等级:百分比」。容忍 {@code =}、全角冒号、末尾的 %、倍数后面的 x / 倍、两边的空格；倍数可以是小数（1.5）。
     *
     * @return {倍数或等级, 比例(0~1)}；写错返回 null
     */
    private static double[] parsePoint(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace('：', ':').replace('=', ':').replace("%", "")
                .replace("倍", "").replace("x", "").replace("X", "");
        int sep = s.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        try {
            double level = Double.parseDouble(s.substring(0, sep).trim());
            double percent = Double.parseDouble(s.substring(sep + 1).trim().toLowerCase(Locale.ROOT));
            if (!(level > 0) || Double.isInfinite(level) || !(percent > 0) || percent > 100) {
                return null;
            }
            return new double[]{level, percent / 100.0};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 附魔名后面要不要补「（生效 N 级）」。
     */
    public static boolean showInName() {
        return enabled && showInName;
    }

    /**
     * 这个附魔从哪一级开始衰减。
     */
    public static int threshold(Enchantment enchantment) {
        return fixedThreshold > 0 ? fixedThreshold : Math.max(1, enchantment.getMaxLevel());
    }

    /**
     * 本模组附魔的实际生效等级。衰减没开、不是本模组的附魔、等级没超过阈值时原样返回。
     *
     * @param enchantment 附魔
     * @param level       原始等级（护甲类是几件加起来的总和）
     */
    public static int apply(Enchantment enchantment, int level) {
        if (!enabled || !(enchantment instanceof EnchantmentBase)) {
            return level;
        }
        int threshold = threshold(enchantment);
        return compute(level, threshold, byMultiple ? threshold : 1, points, maxLevel);
    }

    /**
     * 纯计算，便于单独验证曲线。
     *
     * @param scale 曲线点的横坐标乘上它才是实际等级：按倍数写时是阈值，按等级写时是 1
     */
    static int compute(int level, int threshold, double scale, double[][] curve, int cap) {
        if (level <= threshold) {
            return level;
        }
        double prevLevel = threshold;
        double prevEff = threshold;
        double slope = -1;
        for (double[] p : curve) {
            double at = p[0] * scale;
            if (at <= threshold) {
                continue;
            }
            double eff = Math.max(prevEff, at * p[1]);
            if (level <= at) {
                double v = prevEff + (eff - prevEff) * (level - prevLevel) / (at - prevLevel);
                return clamp(v, threshold, level, cap);
            }
            slope = (eff - prevEff) / (at - prevLevel);
            prevLevel = at;
            prevEff = eff;
        }
        if (slope < 0) {
            // 曲线里没有比阈值更高的点：没得衰减
            return clamp(level, threshold, level, cap);
        }
        return clamp(prevEff + slope * (level - prevLevel), threshold, level, cap);
    }

    private static int clamp(double v, int threshold, int level, int cap) {
        int r = (int) Math.min(Math.round(v), level);
        r = Math.max(r, threshold);
        if (cap > 0) {
            r = Math.min(r, Math.max(cap, 1));
        }
        return r;
    }
}
