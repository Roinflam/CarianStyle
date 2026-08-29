package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.network.CombatArtEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.List;

/**
 * 水鸟乱舞「连斩」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@code EnchantmentWaterfowlFlurry}：把一次攻击拆成 {@code level+1} 段、
 * 每 2 tick 落一段。这是玛莲妮亚最标志性的招式，在此之前<b>完全没有任何视觉</b>——
 * 玩家只看到伤害数字连着跳几下，感受不到「乱舞」。
 * </p>
 *
 * <h3>为什么单独一个渲染器，而不是加进 CombatArtEffectRenderer</h3>
 * <p>
 * {@code CombatArtEffectRenderer} 的类型分发 switch 末尾是：
 * </p>
 * <pre>
 * default -&gt; {
 *     // 未知类型：静默跳过，不画通用回退
 * }
 * </pre>
 * <p>
 * 也就是说它<b>会安静地忽略自己不认识的类型</b>。因此本渲染器只要订阅同一个
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段、自己过滤出
 * {@link CombatArtEffectPacket#TYPE_WATERFOWL_FLURRY}，
 * 就能与它并存，<b>那个一千多行的文件一行都不用改</b>。
 * 两者共同向 {@link VisualBatch} 的共享缓冲写顶点，最终仍是一次 GL 状态切换 + 一次 draw call。
 * </p>
 * <p>
 * 这个做法也给后续新增战技演出提供了模板：加类型常量 + 加时长 + 写一个独立渲染器，
 * 不必再去动那个越来越大的分发文件。
 * </p>
 *
 * <h3>连斩感来自「多个短命特效错相叠加」，而不是一个长特效</h3>
 * <p>
 * 附魔每一段攻击各发一个包，因此客户端会拿到 {@code level+1} 个
 * <b>各自独立、诞生时刻相差 2 tick</b> 的特效实例。每一道只活
 * {@code 380ms}（见 {@code CombatArtEffectManager}），
 * 于是前一道刚开始淡出、下一道就劈下来——这正是连斩该有的节奏。
 * </p>
 * <p>
 * <b>包里没有「第几段」字段，也不需要。</b>要让多道刀光交叉而非重叠，靠两样：
 * </p>
 * <ul>
 *     <li><b>服务端给每段的 yaw 加不同偏移</b>（见 {@code EnchantmentWaterfowlFlurry}），
 *         使各道弧的方位错开；</li>
 *     <li><b>本渲染器按 {@code birthMs} 派生扫向与高度</b>（{@link #hashOf}）——
 *         同一道特效整段生命周期内恒定，而相隔 2 tick 的两道会拿到不同的哈希，
 *         于是一上一下、一左一右地交叉。</li>
 * </ul>
 * <p>
 * <b>为什么用 birthMs 而不是加个字段：</b>加字段要改包格式，而包格式一改就得
 * 同时改编解码与所有既有类型的构造点；而「同一特效内恒定、不同特效间不同」
 * 这个需求，birthMs 已经完美满足了。
 * </p>
 *
 * <h3>配色：靠边缘色与形态和另两个战技区分</h3>
 * <p>
 * 居合是<b>银白 + 冷蓝影</b>、回旋是<b>银白 + 琥珀尘</b>，本演出取<b>银白 + 猩红边</b>。
 * 三者刀身同为银白（都是刀），靠边缘色分辨；形态上更是三种完全不同的东西：
 * </p>
 * <ul>
 *     <li>居合 —— 一道宽跨度（150°）的横斩，扫完就停；</li>
 *     <li>回旋 —— 绕自身整整一圈；</li>
 *     <li>水鸟 —— <b>多道窄跨度（{@value #SWEEP_SPAN_DEG}°）的短促交叉斩</b>。</li>
 * </ul>
 * <p>
 * 猩红取偏暗的 {@link #WATERFOWL_SCARLET}，与出血的鲜红 {@code 0xE0202F}、
 * 猩红腐败的 {@code 0xE0244A} 拉开一档——它只出现在刀光边缘的一线，
 * 不会被误读成「目标在流血」。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 单道刀光三层（3 × 24 段 × 6）   432
 * 前端火花                          12
 * ─────────────────────────────────
 * 单道合计                    ~444 顶点
 * 满级 4 道同屏峰值           ~1776 顶点
 * </pre>
 * <p>
 * 完整接入 {@link VisualLod}（含 {@link VisualLod#countInstance()}——
 * 少登记一个渲染器就会让全局 {@code crowdFactor} 被系统性高估、
 * 已接入的重量级渲染器削减不足）。
 * </p>
 * <p>
 * <b>细节系数按到刀光边缘的距离取</b>，而非到中心：刀光半径 3.6 格虽小于
 * {@link VisualLod#FULL_DETAIL_RANGE}(12)，按中心算本也够用，
 * 但统一走边缘口径可以避免将来有人把半径调大后出现
 * 「站在刀光里却被判定为远」的问题——这个坑本模组已经踩过两次
 * （圣域大圈、龙雷电柱）。
 * </p>
 * <p>
 * 三个配色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class WaterfowlFlurryRenderer {

    /** 距离裁剪（格） */
    private static final double CULL = 64.0;
    private static final double CULL_SQR = CULL * CULL;

    // ===== 配色（0xRRGGBB）=====
    /** 刀锋核心：纯白高光 */
    private static final int WATERFOWL_EDGE = 0xFFFFFF;
    /** 刀身银白：与居合 / 回旋同族（都是刀），靠边缘色区分 */
    private static final int WATERFOWL_STEEL = 0xDCE4EE;
    /**
     * 猩红边：刀光外缘的一线暗猩红。
     * <p>刻意比出血的 {@code 0xE0202F} 与猩红腐败的 {@code 0xE0244A} 暗一档，
     * 且只出现在最外层的窄边上，不会被误读成「目标在流血」。</p>
     */
    private static final int WATERFOWL_SCARLET = 0xB01A2E;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    private static final float[] C_EDGE = VisualColor.constant(WATERFOWL_EDGE);
    private static final float[] C_STEEL = VisualColor.constant(WATERFOWL_STEEL);
    private static final float[] C_SCARLET = VisualColor.constant(WATERFOWL_SCARLET);

    // ===== 几何参数 =====
    /**
     * 弧光横扫的角度跨度（度）。
     * <p>比居合的 150° 窄得多——水鸟是「一连串短促快斩」而不是「一记大横斩」，
     * 跨度做宽了每一道都像居合，连起来反而看不出是多段。</p>
     */
    private static final float SWEEP_SPAN_DEG = 110f;
    /** 弧光横扫的角度跨度（弧度） */
    private static final float SWEEP_SPAN = SWEEP_SPAN_DEG * ((float) Math.PI / 180f);
    /**
     * 弧光扫完所占的进度比例。
     * <p>取 0.3 —— 前 114ms 扫完、其余 266ms 用于消散。
     * 「扫得极快、散得稍慢」是快斩的观感基础；两段都快会看不清，两段都慢会显得黏。</p>
     */
    private static final float SWEEP_RATIO = 0.3f;
    /** 弧带细分段数 */
    private static final int ARC_SEGMENTS = 24;
    /**
     * 弧带的最少细分段数。
     * <p>跨度只有 110°，10 段即每段 11°，在 3.6 格半径下与真弧的偏离约 2cm；
     * 而刀光是「一闪而过」的高速演出，再低才会显出折线感。</p>
     */
    private static final int ARC_SEGMENTS_MIN = 10;

    /** 刀光离地高度基准（格）：约在腰腹，与居合一致 */
    private static final float SLASH_HEIGHT_BASE = 0.95f;
    /**
     * 刀光高度的错相幅度（格）。
     * <p>相邻两段各自派生出不同的高度偏移，使多道刀光<b>不共面</b>——
     * 共面的话即便方位错开，从侧面看仍是几条平行线，读不出「乱」。</p>
     */
    private static final float SLASH_HEIGHT_STAGGER = 0.45f;

    private WaterfowlFlurryRenderer() {
    }

    /**
     * 渲染回调：只处理 {@link CombatArtEffectPacket#TYPE_WATERFOWL_FLURRY}，
     * 其余类型交给 {@code CombatArtEffectRenderer}。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        List<CombatArtEffectManager.CombatArtEffect> list = CombatArtEffectManager.getActive();
        if (list.isEmpty()) {
            return;
        }
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        Matrix4f matrix = VisualBatch.matrix();
        long now = System.currentTimeMillis();

        for (CombatArtEffectManager.CombatArtEffect fx : list) {
            if (fx.type != CombatArtEffectPacket.TYPE_WATERFOWL_FLURRY) {
                continue;
            }

            double dx = fx.x - cam.x;
            double dy = fx.y - cam.y;
            double dz = fx.z - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue;
            }

            float p = CombatArtEffectManager.progressFor(fx, now);
            if (p <= 0.0005f || p >= 1f) {
                continue;
            }

            // 细节系数按「到刀光边缘的距离」取，而非到中心（详见类注释）
            double edge = Math.max(0.0, Math.sqrt(distSqr) - fx.radius);
            float detail = VisualLod.detail(edge * edge);
            VisualLod.countInstance();

            drawFlurrySlash(builder, matrix,
                    (float) dx, (float) dy, (float) dz,
                    fx.radius, fx.baseAngle, fx.birthMs, p, detail);
        }
    }

    /**
     * 绘制一道水鸟乱舞的刀光。
     * <p>
     * 时间轴：
     * </p>
     * <ul>
     *     <li>p ∈ [0, {@value #SWEEP_RATIO}]：弧光自一端极快扫向另一端（缓出，起手最快）；</li>
     *     <li>p ∈ [{@value #SWEEP_RATIO}, 1]：整道弧原地消散，尾端先散、前端后散。</li>
     * </ul>
     * <p>
     * 三层叠绘：外缘暗猩红晕 → 银白刀身 → 纯白刀锋，越靠外越宽越淡。
     * </p>
     * <p>
     * <b>扫向与高度由 {@code birthMs} 派生</b>（见 {@link #hashOf}）：
     * 同一道特效整段生命周期内恒定，相隔 2 tick 的两道则不同，
     * 于是一上一下、一左一右地交叉。这是「乱舞」二字的全部来源——
     * 若所有段都同向同高，连起来只是一道被切断的居合。
     * </p>
     *
     * @param cx        中心（持有者位置）相对相机 X
     * @param cyFoot    脚底高度相对相机 Y
     * @param cz        中心相对相机 Z
     * @param radius    刀光半径（格）
     * @param baseAngle 持有者正前方的极坐标角（弧度）
     * @param birthMs   该道特效的诞生墙钟（用于派生扫向与高度）
     * @param p         归一化进度
     * @param detail    本帧细节系数
     */
    private static void drawFlurrySlash(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz,
                                        float radius, float baseAngle, long birthMs,
                                        float p, float detail) {
        float sweep = easeOutCubic(clamp01(p / SWEEP_RATIO));
        float fade = 1f - smoothstep(SWEEP_RATIO, 1f, p);
        if (fade <= 0f || sweep <= 0f) {
            return;
        }

        long h = hashOf(birthMs);
        // 扫向：奇偶交替，使相邻两段一左一右
        boolean reversed = (h & 1L) != 0L;
        // 高度：在 [-0.5, +0.5] 区间内派生一个偏移，使相邻两段不共面
        float heightOffset = (((h >>> 8) & 0xFFL) / 255f - 0.5f) * SLASH_HEIGHT_STAGGER;
        float slashY = cyFoot + SLASH_HEIGHT_BASE + heightOffset;

        // 起点在跨度的一端，reversed 时从另一端起扫
        float startAngle = baseAngle + (reversed ? SWEEP_SPAN * 0.5f : -SWEEP_SPAN * 0.5f);
        float span = reversed ? -SWEEP_SPAN : SWEEP_SPAN;

        int segments = VisualLod.scaleSegments(ARC_SEGMENTS, ARC_SEGMENTS_MIN, detail);

        // 三层叠绘：外缘猩红 → 银白刀身 → 纯白刀锋
        drawSlashArc(b, m, cx, slashY, cz, radius * 1.0f, radius * 1.14f,
                startAngle, span, sweep, segments, C_SCARLET, 0.4f * fade);
        drawSlashArc(b, m, cx, slashY, cz, radius * 0.84f, radius * 1.02f,
                startAngle, span, sweep, segments, C_STEEL, 0.78f * fade);
        drawSlashArc(b, m, cx, slashY, cz, radius * 0.94f, radius * 0.99f,
                startAngle, span, sweep, segments, C_EDGE, 1.0f * fade);

        // 前端火花：标出刀尖此刻在哪。仅 12 顶点，不参与削减
        if (sweep < 1f) {
            float frontAngle = startAngle + span * sweep;
            float fx = cx + Mth.cos(frontAngle) * radius;
            float fz = cz + Mth.sin(frontAngle) * radius;
            spark(b, m, fx, fz, slashY, radius * 0.09f + 0.08f, C_EDGE, 0.92f * fade);
        }
    }

    /**
     * 由诞生墙钟派生一个稳定哈希。
     * <p>
     * 要求只有两条：<b>同一道特效整段生命周期内恒定</b>（否则刀光会逐帧跳变）、
     * <b>相隔 2 tick(≈100ms) 的两道拿到不同的值</b>（否则连斩不交叉）。
     * 一次 xorshift 混合足以同时满足。
     * </p>
     *
     * @param birthMs 诞生墙钟（毫秒）
     * @return 非 0 哈希
     */
    private static long hashOf(long birthMs) {
        long s = birthMs * 0x9E3779B97F4A7C15L;
        s ^= (s >>> 29);
        s *= 0xBF58476D1CE4E5B9L;
        s ^= (s >>> 32);
        return s == 0L ? 0x9E3779B97F4A7C15L : s;
    }

    // ==================== 几何基元 ====================

    /**
     * 绘制一段「刀光弧带」：以 (cx,cz) 为圆心、位于水平面高度 cy 的圆环扇段，
     * 从 {@code startAngle} 起沿 {@code span} 方向跨越 {@code span × sweep} 弧度。
     * <p>
     * <b>透明度沿弧长梯度：</b>越靠近前端（刚扫到的位置）越亮，越靠近起点越淡——
     * 这正是「刀光拖尾」的成因。梯度用 {@code u^1.6} 而非线性，
     * 使尾部衰减更快、前端更集中，更接近真实高速挥砍的残留。
     * </p>
     * <p>
     * {@code span} 允许为负（反向扫）；{@code sweep} 只控制已扫过的比例，
     * 因此负 span 时弧会从另一端反向长出来。
     * </p>
     *
     * @param rInner     弧带内半径
     * @param rOuter     弧带外半径
     * @param startAngle 起始角（弧度）
     * @param span       总跨度（弧度，可为负表示反向）
     * @param sweep      已扫过的比例（0~1）
     * @param segments   整段跨度对应的细分数（实际只绘制 sweep 部分）
     * @param col        颜色（只读）
     * @param alpha      前端峰值透明度
     */
    private static void drawSlashArc(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz,
                                     float rInner, float rOuter,
                                     float startAngle, float span, float sweep,
                                     int segments, float[] col, float alpha) {
        if (sweep <= 0f || alpha <= 0.002f || rOuter <= rInner) {
            return;
        }
        int drawn = Math.max(1, Math.round(segments * sweep));
        float segAngle = span * sweep / drawn;
        float r = col[0], g = col[1], bl = col[2];

        for (int i = 0; i < drawn; i++) {
            float a0 = startAngle + segAngle * i;
            float a1 = a0 + segAngle;
            // u：0=起点（最旧），1=前端（最新）
            float u0 = (float) i / drawn;
            float u1 = (float) (i + 1) / drawn;
            float alpha0 = alpha * (float) Math.pow(u0, 1.6);
            float alpha1 = alpha * (float) Math.pow(u1, 1.6);

            float cos0 = Mth.cos(a0), sin0 = Mth.sin(a0);
            float cos1 = Mth.cos(a1), sin1 = Mth.sin(a1);

            float ox0 = cx + rOuter * cos0, oz0 = cz + rOuter * sin0;
            float ox1 = cx + rOuter * cos1, oz1 = cz + rOuter * sin1;
            float ix0 = cx + rInner * cos0, iz0 = cz + rInner * sin0;
            float ix1 = cx + rInner * cos1, iz1 = cz + rInner * sin1;

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha0).endVertex();
            b.vertex(m, ox1, cy, oz1).color(r, g, bl, alpha1).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha1).endVertex();

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha0).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha1).endVertex();
            b.vertex(m, ix0, cy, iz0).color(r, g, bl, alpha0).endVertex();
        }
    }

    /**
     * 小菱形光点（水平面），中心最亮、四角渐隐。
     * <p>角点内联为标量，零分配（做法与 {@code AoeEffectRenderer} 的同名方法同源）。</p>
     */
    private static void spark(BufferBuilder b, Matrix4f m, float px, float pz, float y,
                              float size, float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float p0x = px, p0z = pz - size;
        float p1x = px + size, p1z = pz;
        float p2x = px, p2z = pz + size;
        float p3x = px - size, p3z = pz;

        sparkTri(b, m, px, y, pz, p0x, p0z, p1x, p1z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p1x, p1z, p2x, p2z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p2x, p2z, p3x, p3z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p3x, p3z, p0x, p0z, r, g, bl, alpha);
    }

    /**
     * 光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     */
    private static void sparkTri(BufferBuilder b, Matrix4f m,
                                 float cx, float y, float cz,
                                 float ax, float az, float bx, float bz,
                                 float r, float g, float bl, float alpha) {
        b.vertex(m, cx, y, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, y, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, y, bz).color(r, g, bl, 0f).endVertex();
    }

    // ==================== 数学辅助 ====================

    /** 缓出（cubic）。 */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** 平滑阶跃（Hermite）。 */
    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    /** 夹取到 0~1。 */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
