package pers.roinflam.carianstyle.visual.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 世界特效细节层级（LOD）裁剪（纯客户端）。
 * <p>
 * <b>解决的问题：</b>{@link VisualBatch} 已把八个渲染器合并为一次 GL 状态切换 + 一次 draw call，
 * {@link SharedEntityQuery} 已把每帧范围查询从 6 次降为 1 次——这两项之后，
 * 客户端剩下的真正瓶颈是<b>顶点量</b>。
 * </p>
 * <p>
 * 以出血为例，单个患者每帧要写 <b>948</b> 个顶点（血泊 120 + 血滴 624 + 垂落血线 36 + 血雾 168），
 * 心跳爆发窗口内（占周期的 40%）更达 <b>1512</b>。十人团战每人挂着两三种效果，
 * 顶点量轻松上万——而这些顶点绝大多数属于<b>远处看不清、或被同屏其它特效盖住</b>的实体。
 * </p>
 *
 * <h3>两个降级维度</h3>
 * <ol>
 *     <li><b>距离</b>——{@link #FULL_DETAIL_RANGE} 格内保持全细节；
 *         之外线性衰减到 {@link #MIN_DETAIL_RANGE} 格处的 {@link #MIN_DETAIL}。
 *         远处实体在屏幕上只占几个像素，26 颗血滴和 8 颗血滴的观感差别为零。</li>
 *     <li><b>拥挤度</b>——同屏特效实例越多，整体细节越低。
 *         {@link #CROWD_FREE} 个以内不降级；超过后线性衰减到 {@link #CROWD_HEAVY} 个时的
 *         {@link #CROWD_MIN}。这正是团战最需要帧率的时候。</li>
 * </ol>
 * <p>
 * 两者相乘得到最终细节系数。单人单效果场景（最常见的观赏场景）系数恒为 1.0，
 * <b>视觉与优化前逐像素一致</b>。
 * </p>
 *
 * <h3>拥挤度为什么用「上一帧」的计数</h3>
 * <p>
 * 本帧的实例总数要等八个渲染器全部跑完才知道，而第一个渲染器开画时就需要这个系数。
 * 因此改用上一帧的总数作估算——相邻帧的同屏特效数量高度连续，
 * 这是实时渲染里的标准做法（时间连贯性）。
 * 代价是特效突然大量出现的那一帧不会降级，下一帧才跟上，肉眼无法察觉。
 * </p>
 *
 * <h3>降级为什么不会「闪烁」</h3>
 * <p>
 * 各渲染器的元素都用 {@code seedFor(entityId, index)} 生成确定性随机参数——
 * 减少数量时只是<b>少画尾部几个</b>，保留下来的那些种子不变、位置不变。
 * 因此靠近实体时是「逐渐多出几颗血滴」，而不是整片重新洗牌。
 * </p>
 * <p>
 * 仅在客户端渲染线程访问，无并发问题。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class VisualLod {

    // ==================== 距离维度 ====================

    /** 全细节距离（格）：此距离内细节系数恒为 1.0 */
    public static final double FULL_DETAIL_RANGE = 12.0;

    /** 最简距离（格）：此距离外细节系数恒为 {@link #MIN_DETAIL} */
    public static final double MIN_DETAIL_RANGE = 40.0;

    /** 距离维度的细节下限 */
    public static final float MIN_DETAIL = 0.30f;

    private static final double FULL_DETAIL_SQR = FULL_DETAIL_RANGE * FULL_DETAIL_RANGE;
    private static final double MIN_DETAIL_SQR = MIN_DETAIL_RANGE * MIN_DETAIL_RANGE;

    // ==================== 拥挤度维度 ====================

    /** 同屏特效实例数在此以内不做拥挤降级 */
    public static final int CROWD_FREE = 6;

    /** 同屏特效实例数达到此值时，拥挤系数降至 {@link #CROWD_MIN} */
    public static final int CROWD_HEAVY = 24;

    /** 拥挤维度的细节下限 */
    public static final float CROWD_MIN = 0.45f;

    /**
     * 近距离保底系数。
     * <p>
     * 拥挤降级是按「同屏总量」一刀切的，但玩家真正盯着看的是<b>眼前那一两个</b>实体。
     * 若不设保底，24 人混战时贴脸的目标也会被压到 {@link #CROWD_MIN}，
     * 血滴从 26 颗砍到 12 颗——这个距离上是看得出来的。
     * </p>
     * <p>
     * 因此 {@link #FULL_DETAIL_RANGE} 格以内的实体细节不低于本值。
     * 代价很小：混战中处于该距离的实体本就只有少数几个，
     * 大头仍由距离维度削减。
     * </p>
     */
    public static final float NEAR_FLOOR = 0.70f;

    // ==================== 帧级状态 ====================

    /** 本帧已绘制的特效实例数（由各渲染器调用 {@link #countInstance()} 累加） */
    private static int currentFrameInstances = 0;

    /** 上一帧的特效实例总数（拥挤系数的计算依据，也供调试查看） */
    private static int previousFrameInstances = 0;

    /** 本帧的拥挤系数，在 {@link #beginFrame()} 根据上一帧实例数算好 */
    private static float crowdFactor = 1f;

    private VisualLod() {
    }

    /**
     * 帧开始：用上一帧的实例数算出本帧拥挤系数，并复位计数器。
     * <p>由 {@link VisualBatch#onBatchBegin} 在批次开启时调用，各渲染器不需要管。</p>
     */
    static void beginFrame() {
        previousFrameInstances = currentFrameInstances;
        crowdFactor = crowdFactorFor(previousFrameInstances);
        currentFrameInstances = 0;
    }

    /**
     * 登记一个特效实例（每个渲染器每绘制一个实体调用一次）。
     * <p>只用于估算下一帧的拥挤度，不影响本帧任何绘制。</p>
     */
    public static void countInstance() {
        currentFrameInstances++;
    }

    /**
     * 取某实体本帧的细节系数。
     *
     * @param distSqr 实体到相机的<b>平方</b>距离（渲染器裁剪时已经算过，直接传进来即可）
     * @return 细节系数，范围 [{@link #MIN_DETAIL} × {@link #CROWD_MIN}, 1.0]
     */
    public static float detail(double distSqr) {
        float result = distanceFactor(distSqr) * crowdFactor;
        // 近距离保底：不让拥挤降级把玩家眼前的实体也砍到看得出来（详见 NEAR_FLOOR）
        if (distSqr <= FULL_DETAIL_SQR && result < NEAR_FLOOR) {
            return NEAR_FLOOR;
        }
        return result;
    }

    /**
     * 距离维度的细节系数。
     *
     * @param distSqr 平方距离
     * @return [{@link #MIN_DETAIL}, 1.0]
     */
    public static float distanceFactor(double distSqr) {
        if (distSqr <= FULL_DETAIL_SQR) {
            return 1f;
        }
        if (distSqr >= MIN_DETAIL_SQR) {
            return MIN_DETAIL;
        }
        // 在实际距离（而非平方距离）上做线性插值，衰减手感更均匀
        double dist = Math.sqrt(distSqr);
        float t = (float) ((dist - FULL_DETAIL_RANGE) / (MIN_DETAIL_RANGE - FULL_DETAIL_RANGE));
        return 1f + (MIN_DETAIL - 1f) * t;
    }

    /**
     * 拥挤维度的细节系数。
     *
     * @param instances 同屏特效实例数
     * @return [{@link #CROWD_MIN}, 1.0]
     */
    public static float crowdFactorFor(int instances) {
        if (instances <= CROWD_FREE) {
            return 1f;
        }
        if (instances >= CROWD_HEAVY) {
            return CROWD_MIN;
        }
        float t = (float) (instances - CROWD_FREE) / (float) (CROWD_HEAVY - CROWD_FREE);
        return 1f + (CROWD_MIN - 1f) * t;
    }

    /**
     * 按细节系数缩放元素数量，<b>至少保留 1 个</b>。
     * <p>用于血滴、雾团、射线这类「少画几个看不出来」的重复元素。</p>
     *
     * @param baseCount 全细节下的数量
     * @param detail    细节系数
     * @return 缩放后的数量，最小为 1
     */
    public static int scale(int baseCount, float detail) {
        if (baseCount <= 1 || detail >= 1f) {
            return baseCount;
        }
        int n = Math.round(baseCount * detail);
        return n < 1 ? 1 : n;
    }

    /**
     * 按细节系数缩放<b>环 / 圆盘的分段数</b>，并保证不低于给定下限。
     * <p>
     * 分段数低于 6 时圆形会明显变成多边形，因此这类元素需要单独的下限，
     * 不能直接用 {@link #scale}。
     * </p>
     *
     * @param baseSegments 全细节下的分段数
     * @param minSegments  分段数下限（建议 6 起，柔光块可低至 4）
     * @param detail       细节系数
     * @return 缩放后的分段数
     */
    public static int scaleSegments(int baseSegments, int minSegments, float detail) {
        if (detail >= 1f || baseSegments <= minSegments) {
            return baseSegments;
        }
        int n = Math.round(baseSegments * detail);
        return n < minSegments ? minSegments : n;
    }

    /**
     * 判断某个「可选层」是否还值得画。
     * <p>
     * 用于雾团、外圈光晕这类<b>远处完全看不出、但顶点开销不小</b>的装饰层：
     * 细节系数低于阈值时整层跳过。
     * </p>
     *
     * @param detail    细节系数
     * @param threshold 保留阈值（建议 0.4~0.6）
     * @return 应当绘制返回 true
     */
    public static boolean keepLayer(float detail, float threshold) {
        return detail >= threshold;
    }

    /**
     * @return 上一帧统计到的同屏特效实例总数（调试 / 性能观察用）
     */
    public static int lastFrameInstances() {
        return previousFrameInstances;
    }

    /**
     * @return 本帧生效的拥挤系数（调试 / 性能观察用）
     */
    public static float currentCrowdFactor() {
        return crowdFactor;
    }
}
