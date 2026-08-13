package pers.roinflam.carianstyle.visual.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 世界特效颜色工具（纯客户端）——<b>零堆分配</b>的颜色解包与插值。
 * <p>
 * <b>解决的问题：</b>各渲染器此前统一使用 {@code unpack(int)} 把 {@code 0xRRGGBB}
 * 拆成 {@code float[3]}，而该方法<b>每次调用都 {@code new float[3]}</b>。
 * 它出现在最深的热路径里——以出血为例，单个患者每帧的调用次数：
 * </p>
 * <pre>
 * 喷溅血滴（26 基础 + 18 脉冲）每颗 lerpRgb→unpack   44
 * 血雾（7 团）每团 lerpRgb→unpack                     7
 * 垂落血线（6 条）verticalLine 内部 unpack            6
 * 迸溅射线 hot / dark / flash                          3
 * 血泊（2 层）                                          2
 * ────────────────────────────────────────────────────
 * 合计                              ~62 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 十个患者 × 60fps ≈ <b>每秒 3.7 万次小数组分配</b>，全部朝生夕死。
 * Eden 区回收单次很便宜，但这个量级会实打实推高客户端 GC 频率——
 * 而且这是<b>纯浪费</b>：每个数组的存活期只有紧随其后的那几行。
 * </p>
 *
 * <h3>两条替代路径</h3>
 * <ol>
 *     <li><b>常量颜色 → 类加载时预解包一次</b>。用 {@link #constant(int)} 建出
 *         {@code private static final float[]}，此后永久复用、零分配。
 *         配色表里的固定色（血红、冰蓝、墨黑……）全部属于这一类；</li>
 *     <li><b>动态颜色 → 写入调用方自备的复用缓冲</b>。{@link #lerpInto} /
 *         {@link #unpackInto} / {@link #mixInto} 不返回新数组，而是把结果写进传入的
 *         {@code dst}。缓冲由各渲染器自己持有为 {@code private static final float[]}。</li>
 * </ol>
 *
 * <h3>为什么复用缓冲由各渲染器自己持有，而不是本类集中提供</h3>
 * <p>
 * 集中提供 {@code scratch0()/scratch1()} 看似更省事，但会埋下<b>跨渲染器互相踩</b>的隐患：
 * 九个世界渲染器都在同一个 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段跑，
 * 一旦某处把「借来的」缓冲存下来跨方法使用，问题会以「颜色偶尔闪错」的形式出现，
 * 极难定位。让每个渲染器持有自己命名的缓冲，作用域一眼可见，也便于评审。
 * </p>
 *
 * <h3>⚠ 使用约束（违反会导致颜色错乱）</h3>
 * <ul>
 *     <li><b>{@link #constant(int)} 产出的数组必须当成只读</b>。Java 没有不可变数组，
 *         若不慎把常量数组当作 {@code dst} 传给 {@code *Into} 方法，
 *         会<b>永久污染该配色</b>，且之后每一帧都是错的。
 *         本模组约定这类字段一律以 {@code C_} 前缀命名，便于人眼与搜索识别；</li>
 *     <li><b>写入缓冲后必须立即消费</b>，不能存起来跨调用使用。
 *         典型正确用法是「写入 → 紧接着 {@code b.vertex(...).color(dst[0], dst[1], dst[2], a)}」；</li>
 *     <li><b>两个动态颜色同时存活时，必须用两个不同的缓冲</b>。这是最容易踩的一处——
 *         例如渐变线段需要同时持有起点色与终点色：
 *         <pre>
 * // ❌ 错误：两次写同一个缓冲，colPrev 会被 col 覆盖，整条线变成纯色
 * mixInto(SCRATCH, petal, mist, uPrev);  float[] colPrev = SCRATCH;
 * mixInto(SCRATCH, petal, mist, u);      float[] col     = SCRATCH;
 * lineGradient(..., colPrev, a1, col, a2);
 *
 * // ✅ 正确：各用各的缓冲
 * mixInto(SCRATCH_A, petal, mist, uPrev);
 * mixInto(SCRATCH_B, petal, mist, u);
 * lineGradient(..., SCRATCH_A, a1, SCRATCH_B, a2);
 *         </pre>
 *     </li>
 * </ul>
 *
 * <h3>为什么 {@link #lerpInto} 保留了 0~255 域的取整</h3>
 * <p>
 * 直接在归一化 float 域插值会更快一点，但那样<b>结果与旧的
 * {@code lerpRgb(int,int,float)} → {@code unpack} 链路不再逐位相同</b>——
 * 旧链路先在 0~255 整数域插值并 {@link Math#round}、再除以 255，存在量化到
 * 1/255 网格的行为。差异虽然最大只有约 0.4%、肉眼绝无可能分辨，
 * 但本次改造的承诺是<b>视觉零变化</b>，故照搬取整逻辑，把「零变化」做到字面意义上成立。
 * 三次 {@link Math#round} 的开销相对于省下的一次堆分配完全可以忽略。
 * </p>
 * <p>
 * 仅在客户端渲染线程访问，无并发问题。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class VisualColor {

    /** 颜色分量数（R、G、B；alpha 一律由调用方单独传，不进本类） */
    public static final int RGB = 3;

    private VisualColor() {
    }

    /**
     * 把 {@code 0xRRGGBB} 解包成一个<b>新建</b>的 {@code float[3]}。
     * <p>
     * <b>仅供类加载时初始化常量使用</b>（{@code private static final float[] C_XXX = constant(XXX);}），
     * 绝不可在渲染循环里调用——那正是本类要消除的分配。
     * </p>
     * <p>
     * 返回的数组请当作<b>只读</b>：它会被后续每一帧反复读取，一旦被写入就永久污染该配色。
     * </p>
     *
     * @param color 0xRRGGBB
     * @return 新建的 {@code [r, g, b]}，各分量归一化到 0~1
     */
    public static float[] constant(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f
        };
    }

    /**
     * 把 {@code 0xRRGGBB} 解包写入调用方提供的缓冲，<b>不分配</b>。
     *
     * @param dst   目标缓冲，长度至少 {@link #RGB}；调用后其内容被完全覆盖
     * @param color 0xRRGGBB
     */
    public static void unpackInto(float[] dst, int color) {
        dst[0] = ((color >> 16) & 0xFF) / 255f;
        dst[1] = ((color >> 8) & 0xFF) / 255f;
        dst[2] = (color & 0xFF) / 255f;
    }

    /**
     * 在两个 {@code 0xRRGGBB} 之间插值并写入缓冲，<b>不分配</b>。
     * <p>
     * 等价于旧代码的 {@code unpack(lerpRgb(from, to, t))}，但省掉中间的 int 与新数组。
     * 取整行为与旧链路逐位一致（0~255 域插值 + {@link Math#round}，详见类注释）。
     * </p>
     *
     * @param dst  目标缓冲，长度至少 {@link #RGB}
     * @param from 起始色 0xRRGGBB
     * @param to   目标色 0xRRGGBB
     * @param t    插值系数，自动夹取到 0~1
     */
    public static void lerpInto(float[] dst, int from, int to, float t) {
        float u = clamp01(t);
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        dst[0] = Math.round(fr + (tr - fr) * u) / 255f;
        dst[1] = Math.round(fg + (tg - fg) * u) / 255f;
        dst[2] = Math.round(fb + (tb - fb) * u) / 255f;
    }

    /**
     * 在两个<b>已解包</b>的颜色之间插值并写入缓冲，<b>不分配</b>。
     * <p>等价于旧代码的 {@code mix(a, b, t)}。在归一化域直接插值，与旧实现逐位一致。</p>
     * <p>
     * {@code dst} 允许与 {@code a} 或 {@code b} 是同一个数组（就地插值），
     * 因为三个分量各自读完立即写、互不干扰。但<b>切勿把常量数组当作 {@code dst}</b>。
     * </p>
     *
     * @param dst 目标缓冲，长度至少 {@link #RGB}
     * @param a   起始色 {@code [r, g, b]}
     * @param b   目标色 {@code [r, g, b]}
     * @param t   插值系数，自动夹取到 0~1
     */
    public static void mixInto(float[] dst, float[] a, float[] b, float t) {
        float u = clamp01(t);
        dst[0] = a[0] + (b[0] - a[0]) * u;
        dst[1] = a[1] + (b[1] - a[1]) * u;
        dst[2] = a[2] + (b[2] - a[2]) * u;
    }

    /**
     * 把一个已解包颜色整体向白色提亮（保留色相，只增亮），写入缓冲，<b>不分配</b>。
     * <p>供黄金树祝福等需要「核心线比主体略亮」的渲染器使用。</p>
     *
     * @param dst    目标缓冲，长度至少 {@link #RGB}；可与 {@code src} 同一数组
     * @param src    源色 {@code [r, g, b]}
     * @param amount 提亮比例 0~1（0 为原色，1 为纯白）
     */
    public static void brightenInto(float[] dst, float[] src, float amount) {
        float u = clamp01(amount);
        dst[0] = src[0] + (1f - src[0]) * u;
        dst[1] = src[1] + (1f - src[1]) * u;
        dst[2] = src[2] + (1f - src[2]) * u;
    }

    /**
     * 把源缓冲的三个分量复制到目标缓冲，<b>不分配</b>。
     * <p>
     * 用于「需要把某个动态色暂存下来、稍后与另一个动态色同时使用」的场景——
     * 与其在渲染循环里再开一个数组，不如显式复制到第二个复用缓冲。
     * </p>
     *
     * @param dst 目标缓冲，长度至少 {@link #RGB}
     * @param src 源缓冲，长度至少 {@link #RGB}
     */
    public static void copyInto(float[] dst, float[] src) {
        dst[0] = src[0];
        dst[1] = src[1];
        dst[2] = src[2];
    }

    /**
     * 夹取到 0~1。
     *
     * @param v 输入值
     * @return 夹取结果
     */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
