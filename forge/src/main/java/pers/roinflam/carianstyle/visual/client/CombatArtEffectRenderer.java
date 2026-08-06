package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
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
 * 战技自绘特效渲染器（纯客户端）。
 * <p>
 * 与 {@link AoeEffectRenderer} 并列的第二条瞬时特效渲染链路，专画<b>有朝向</b>的战技演出。
 * GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，{@code POSITION_COLOR} 纯顶点绘制，
 * 无贴图、无原版粒子。
 * </p>
 * <p>
 * <b>三套演出：</b>
 * <ol>
 *     <li><b>居合斩 {@link #drawIaiSlash}</b>——沿持有者正面扫出的一道极快水平弧形刀光。
 *         主刀光为银白高亮弧带（前端最亮、尾端拖曳渐隐），后随两道更细更暗的残影弧；
 *         起手瞬间在身前闪一道竖直刀锋（拔刀那一下），同时地面沿正前方裂开一条切割线。
 *         整道弧光在前 25% 时间内扫完，其余时间用于消散——「快」是这个战技的全部性格。</li>
 *     <li><b>回旋斩 {@link #drawSpinSlash}</b>——绕自身完整扫过 360° 的环形刀光。
 *         扫过节奏与附魔里玩家真实旋转 12tick(600ms) 大致同步；刀锋银白、外缘染琥珀，
 *         身后拖一圈低透明度的扬尘带，收尾时残留整圈光环快速淡出。</li>
 *     <li><b>祈祷一击 {@link #drawPrayerStrike}</b>——自天而降的金色圣光柱 + 落地扩散金环
 *         + 地面十字圣徽 + 升腾金光丝。光柱用「十字双面」建模（沿世界 X、Z 轴各一个四边形），
 *         从任意水平视角皆可见、无需 billboard 计算，手法与
 *         {@code AoeEffectRenderer#drawRedLightning} 的电柱一致。</li>
 * </ol>
 * </p>
 * <p>
 * <b>配色刻意与既有演出拉开距离</b>（同屏叠加时要能分辨）：
 * 因果律是金紫法阵、冻结地震是冰蓝晶体、猩红是深红、癫火是黄橙、龙雷是血红电柱——
 * 故居合取<b>金属银白</b>（刀锋反光，而非冰的晶莹）、回旋取<b>银白刀锋 + 琥珀扬尘</b>
 * （橙只出现在尘土上，不与癫火的火焰橙撞主色）、祈祷取<b>暖金 + 米白</b>
 * （形态是竖直光柱，与黄金树祝福的贴身光晕形态完全不同）。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatArtEffectRenderer {

    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;
    /** 距离裁剪（格）：相机太远的特效本帧不绘制 */
    private static final double CULL = 64.0;
    private static final float TAU = (float) (Math.PI * 2.0);

    // ===== 居合斩配色（0xRRGGBB）=====
    /** 刀锋核心：纯白高光 */
    private static final int IAI_EDGE = 0xFFFFFF;
    /** 刀身银灰：金属反光的主色，与冰蓝拉开距离 */
    private static final int IAI_STEEL = 0xC2D0E4;
    /** 刀影冷蓝：弧带外缘与残影的暗部 */
    private static final int IAI_SHADOW = 0x6B84AE;

    // ===== 回旋斩配色（0xRRGGBB）=====
    /** 刀锋核心：近白 */
    private static final int SPIN_EDGE = 0xFFF4E4;
    /** 琥珀边：刀光外缘的暖色描边 */
    private static final int SPIN_AMBER = 0xFFB347;
    /** 扬尘：土黄，低透明度铺在地面 */
    private static final int SPIN_DUST = 0xB08048;

    // ===== 祈祷一击配色（0xRRGGBB）=====
    /** 圣光核心：近白暖光 */
    private static final int PRAYER_CORE = 0xFFF8E0;
    /** 圣光主色：暖金 */
    private static final int PRAYER_GOLD = 0xFFD98A;
    /** 圣光深色：外缘与圣徽暗部 */
    private static final int PRAYER_DEEP = 0xC9962F;

    // ===== 居合斩几何参数 =====
    /** 弧光横扫的角度跨度（弧度）：约 150°，以正前方居中 */
    private static final float IAI_SWEEP_SPAN = 2.62f;
    /** 弧光扫完所占的进度比例（其余时间用于消散） */
    private static final float IAI_SWEEP_RATIO = 0.25f;
    /** 弧带细分段数 */
    private static final int IAI_ARC_SEGMENTS = 36;
    /** 残影弧数量 */
    private static final int IAI_AFTERIMAGE_COUNT = 2;

    // ===== 回旋斩几何参数 =====
    /** 完整 360° 扫过所占的进度比例 */
    private static final float SPIN_SWEEP_RATIO = 0.6f;
    /** 环形弧带细分段数（整圈，需比居合更细才不显棱角） */
    private static final int SPIN_ARC_SEGMENTS = 64;
    /** 刀光尾迹保留的角度长度（弧度）：约 200° */
    private static final float SPIN_TRAIL_SPAN = 3.5f;

    // ===== 祈祷一击几何参数 =====
    /** 光柱总高度（格） */
    private static final float PRAYER_COLUMN_HEIGHT = 14.0f;
    /** 光柱竖直细分段数（用于沿高度做透明度渐变） */
    private static final int PRAYER_COLUMN_SEGMENTS = 10;
    /** 光柱底部半宽（格） */
    private static final float PRAYER_COLUMN_HALF = 0.55f;
    /** 升腾金光丝数量 */
    private static final int PRAYER_THREAD_COUNT = 8;

    private CombatArtEffectRenderer() {
    }

    /**
     * 渲染回调：遍历全部存活战技特效，按类型分发到对应自绘演出。
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
        // 共享批次未开启（世界未加载等）：直接跳过
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
        double cullSqr = CULL * CULL;

        for (CombatArtEffectManager.CombatArtEffect fx : list) {
            double dx = fx.x - cam.x;
            double dy = fx.y - cam.y;
            double dz = fx.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > cullSqr) {
                continue;
            }

            float p = CombatArtEffectManager.progressFor(fx, now);
            if (p <= 0.0005f || p >= 1f) {
                continue;
            }

            float rx = (float) dx;
            float ry = (float) dy + Y_OFFSET;
            float rz = (float) dz;

            switch (fx.type) {
                case CombatArtEffectPacket.TYPE_IAI_SLASH ->
                        drawIaiSlash(builder, matrix, rx, ry, rz, fx.radius, fx.baseAngle, p);
                case CombatArtEffectPacket.TYPE_SPIN_SLASH ->
                        drawSpinSlash(builder, matrix, rx, ry, rz, fx.radius, fx.baseAngle, p);
                case CombatArtEffectPacket.TYPE_PRAYER_STRIKE ->
                        drawPrayerStrike(builder, matrix, rx, ry, rz, fx.radius, fx.baseAngle, p);
                default -> {
                    // 未知类型：静默跳过，不画通用回退——战技特效都是有明确语义的，
                    // 画个不相干的圈反而误导玩家
                }
            }
        }
    }

    // ============================== 居合斩 ==============================

    /**
     * 居合斩：沿持有者正面扫出的一道极快水平弧形刀光。
     * <p>
     * 时间轴：
     * <ul>
     *     <li>p ∈ [0, 0.25]：弧光从右侧起点快速扫向左侧终点（缓出，起手最快）；
     *         同时身前闪一道竖直刀锋（p &lt; 0.12），地面切割线向前延伸；</li>
     *     <li>p ∈ [0.25, 1]：整道弧光原地消散，尾端先散、前端后散。</li>
     * </ul>
     * 弧带分三层叠绘：外缘冷蓝光晕 → 银灰刀身 → 纯白刀锋，越靠外越宽越淡。
     * </p>
     *
     * @param cx        中心（持有者位置）相对相机 X
     * @param cy        地面高度相对相机 Y
     * @param cz        中心相对相机 Z
     * @param radius    刀光半径（格）
     * @param baseAngle 持有者正前方的极坐标角（弧度）
     * @param p         归一化进度
     */
    private static void drawIaiSlash(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz,
                                     float radius, float baseAngle, float p) {
        // 扫过进度：前 IAI_SWEEP_RATIO 完成，缓出让起手最快
        float sweep = easeOutCubic(clamp01(p / IAI_SWEEP_RATIO));
        // 整体淡出：扫完后开始消散
        float fade = 1f - smoothstep(IAI_SWEEP_RATIO, 1f, p);
        if (fade <= 0f || sweep <= 0f) {
            return;
        }

        float[] edge = unpack(IAI_EDGE);
        float[] steel = unpack(IAI_STEEL);
        float[] shadow = unpack(IAI_SHADOW);

        // 起点在右侧（-半跨度），扫向左侧（+半跨度）——居合为右手拔刀横斩
        float startAngle = baseAngle - IAI_SWEEP_SPAN * 0.5f;

        // 刀光离地高度：约在腰部（不贴地，否则像法阵而不像刀光）
        float slashY = cy + 1.0f;

        // ===== 主刀光弧带（三层叠绘）=====
        drawSlashArc(b, m, cx, slashY, cz, radius * 1.02f, radius * 1.18f,
                startAngle, IAI_SWEEP_SPAN, sweep, IAI_ARC_SEGMENTS, shadow, 0.30f * fade);
        drawSlashArc(b, m, cx, slashY, cz, radius * 0.86f, radius * 1.04f,
                startAngle, IAI_SWEEP_SPAN, sweep, IAI_ARC_SEGMENTS, steel, 0.72f * fade);
        drawSlashArc(b, m, cx, slashY, cz, radius * 0.95f, radius * 1.00f,
                startAngle, IAI_SWEEP_SPAN, sweep, IAI_ARC_SEGMENTS, edge, 1.00f * fade);

        // ===== 残影弧：更小半径、进度滞后、更暗，制造「刀走过的空气还没合拢」的观感 =====
        for (int i = 1; i <= IAI_AFTERIMAGE_COUNT; i++) {
            float lag = i * 0.16f;
            float ghostSweep = easeOutCubic(clamp01((p - lag * IAI_SWEEP_RATIO) / IAI_SWEEP_RATIO));
            if (ghostSweep <= 0f) {
                continue;
            }
            float shrink = 1f - i * 0.14f;
            float ghostAlpha = 0.34f / i * fade;
            drawSlashArc(b, m, cx, slashY - i * 0.12f, cz,
                    radius * 0.88f * shrink, radius * 0.98f * shrink,
                    startAngle, IAI_SWEEP_SPAN, ghostSweep, IAI_ARC_SEGMENTS, steel, ghostAlpha);
        }

        // ===== 起手竖直刀锋：拔刀那一瞬身前的一道白刃 =====
        if (p < 0.12f) {
            float flash = 1f - p / 0.12f;
            float fx = cx + (float) Math.cos(startAngle) * radius * 0.75f;
            float fz = cz + (float) Math.sin(startAngle) * radius * 0.75f;
            crossQuad(b, m, fx, cy + 0.15f, fz, fx, cy + 2.3f, fz,
                    0.10f, edge, 0.95f * flash, 0.15f * flash);
            crossQuad(b, m, fx, cy + 0.15f, fz, fx, cy + 2.3f, fz,
                    0.26f, steel, 0.45f * flash, 0.05f * flash);
        }

        // ===== 地面切割线：沿正前方裂开的一道细长白痕 =====
        float crackGrow = easeOutCubic(clamp01(p / 0.30f));
        float crackFade = 1f - smoothstep(0.35f, 0.85f, p);
        if (crackFade > 0f) {
            double fwdX = Math.cos(baseAngle);
            double fwdZ = Math.sin(baseAngle);
            float len = radius * 1.5f * crackGrow;
            line(b, m,
                    cx + (float) fwdX * radius * 0.2f, cz + (float) fwdZ * radius * 0.2f,
                    cx + (float) fwdX * len, cz + (float) fwdZ * len,
                    cy, 0.05f, edge, 0.75f * crackFade, 0f);
        }

        // ===== 弧光前端火花：标出「刀尖此刻在哪」 =====
        if (sweep < 1f) {
            float frontAngle = startAngle + IAI_SWEEP_SPAN * sweep;
            float fx = cx + (float) Math.cos(frontAngle) * radius;
            float fz = cz + (float) Math.sin(frontAngle) * radius;
            spark(b, m, fx, fz, slashY, radius * 0.10f + 0.10f, edge, 0.9f * fade);
        }
    }

    // ============================== 回旋斩 ==============================

    /**
     * 回旋斩：绕自身完整扫过 360° 的环形刀光。
     * <p>
     * 时间轴：
     * <ul>
     *     <li>p ∈ [0, 0.6]：刀光自持有者正前方起，匀速扫过整圈（匀速而非缓出——
     *         因为附魔里玩家是<b>匀速</b>转 12tick，缓出会与身体动作脱节）；
     *         尾迹只保留身后约 200°，更靠前的部分已消散；</li>
     *     <li>p ∈ [0.6, 1]：整圈残留光环 + 地面扬尘带一起淡出。</li>
     * </ul>
     * </p>
     *
     * @param baseAngle 起始角（持有者正前方）
     */
    private static void drawSpinSlash(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz,
                                      float radius, float baseAngle, float p) {
        float[] edge = unpack(SPIN_EDGE);
        float[] amber = unpack(SPIN_AMBER);
        float[] dust = unpack(SPIN_DUST);

        float slashY = cy + 0.85f;

        // ===== 扫过阶段：带尾迹的旋转弧 =====
        if (p < SPIN_SWEEP_RATIO) {
            // 匀速扫过，与玩家身体旋转同步
            float sweep = p / SPIN_SWEEP_RATIO;
            float frontAngle = baseAngle + TAU * sweep;
            // 尾迹起点：前端往回退 SPIN_TRAIL_SPAN，但不早于起始角
            float trailLen = Math.min(SPIN_TRAIL_SPAN, TAU * sweep);
            float trailStart = frontAngle - trailLen;

            drawSlashArc(b, m, cx, slashY, cz, radius * 1.02f, radius * 1.16f,
                    trailStart, trailLen, 1f, SPIN_ARC_SEGMENTS, amber, 0.34f);
            drawSlashArc(b, m, cx, slashY, cz, radius * 0.88f, radius * 1.04f,
                    trailStart, trailLen, 1f, SPIN_ARC_SEGMENTS, edge, 0.80f);

            // 前端火花：刀尖位置
            float fx = cx + (float) Math.cos(frontAngle) * radius;
            float fz = cz + (float) Math.sin(frontAngle) * radius;
            spark(b, m, fx, fz, slashY, radius * 0.12f + 0.10f, edge, 0.95f);

            // 扬尘：跟在刀光后面从地面扬起（低透明度宽带，只在已扫过的扇区）
            band(b, m, cx, cz, cy, radius * 0.45, radius * 1.25,
                    trailStart, trailLen, SPIN_ARC_SEGMENTS,
                    dust[0], dust[1], dust[2], 0.16f, 0f);
        }

        // ===== 收尾阶段：整圈残留光环 + 全周扬尘一起淡出 =====
        float tail = smoothstep(SPIN_SWEEP_RATIO - 0.1f, SPIN_SWEEP_RATIO, p)
                * (1f - smoothstep(SPIN_SWEEP_RATIO, 1f, p));
        if (tail > 0f) {
            glowRing(b, m, cx, cz, slashY, radius, SPIN_ARC_SEGMENTS, edge,
                    0.55f * tail, 0.22f * tail, 0.06, 0.35);
            glowRing(b, m, cx, cz, slashY, radius * 1.06f, SPIN_ARC_SEGMENTS, amber,
                    0.30f * tail, 0.14f * tail, 0.05, 0.30);
            // 地面全周扬尘，随收尾略微向外扩散
            double dustOuter = radius * (1.25 + 0.25 * smoothstep(SPIN_SWEEP_RATIO, 1f, p));
            band(b, m, cx, cz, cy, radius * 0.4, dustOuter, 0f, TAU, SPIN_ARC_SEGMENTS,
                    dust[0], dust[1], dust[2], 0.14f * tail, 0f);
        }
    }

    // ============================== 祈祷一击 ==============================

    /**
     * 祈祷一击：自天而降的金色圣光柱 + 落地扩散金环 + 地面十字圣徽 + 升腾金光丝。
     * <p>
     * 时间轴：
     * <ul>
     *     <li>p ∈ [0, 0.25]：光柱自上而下「落」到地面（底端 Y 从高处降至地面），
     *         同时快速增亮；</li>
     *     <li>p ∈ [0.15, 0.7]：落地金环向外扩散；地面十字圣徽闪现并脉动；</li>
     *     <li>p ∈ [0.2, 1]：金光丝自地面升腾；</li>
     *     <li>p ∈ [0.45, 1]：整体渐隐。</li>
     * </ul>
     * 光柱用「十字双面」建模（沿世界 X、Z 轴各展开一个四边形），任意水平视角皆可见。
     * </p>
     *
     * @param baseAngle 持有者朝向（此演出与朝向无关，仅用于让光丝分布逐次略有差异）
     */
    private static void drawPrayerStrike(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz,
                                         float radius, float baseAngle, float p) {
        float[] core = unpack(PRAYER_CORE);
        float[] gold = unpack(PRAYER_GOLD);
        float[] deep = unpack(PRAYER_DEEP);

        float fade = 1f - smoothstep(0.45f, 1f, p);

        // ===== 圣光柱：底端自上而下降落，落地后维持并渐隐 =====
        float drop = easeOutCubic(clamp01(p / 0.25f));
        float columnBottom = cy + PRAYER_COLUMN_HEIGHT * (1f - drop);
        float columnTop = cy + PRAYER_COLUMN_HEIGHT;
        if (columnTop > columnBottom && fade > 0f) {
            // 沿高度分段绘制，越往上越淡（融入天空）
            float segLen = (columnTop - columnBottom) / PRAYER_COLUMN_SEGMENTS;
            for (int i = 0; i < PRAYER_COLUMN_SEGMENTS; i++) {
                float y0 = columnBottom + segLen * i;
                float y1 = y0 + segLen;
                float u0 = (float) i / PRAYER_COLUMN_SEGMENTS;
                float u1 = (float) (i + 1) / PRAYER_COLUMN_SEGMENTS;
                // 上淡下实
                float a0 = (1f - u0 * 0.85f) * fade;
                float a1 = (1f - u1 * 0.85f) * fade;
                // 外层暖金光晕
                crossQuad(b, m, cx, y0, cz, cx, y1, cz,
                        PRAYER_COLUMN_HALF * 2.1f, gold, 0.20f * a0, 0.20f * a1);
                // 中层主体
                crossQuad(b, m, cx, y0, cz, cx, y1, cz,
                        PRAYER_COLUMN_HALF, gold, 0.55f * a0, 0.55f * a1);
                // 内层白热核心
                crossQuad(b, m, cx, y0, cz, cx, y1, cz,
                        PRAYER_COLUMN_HALF * 0.34f, core, 0.95f * a0, 0.95f * a1);
            }
        }

        // ===== 落地金环：随进度向外扩散并淡出 =====
        float ringP = clamp01((p - 0.15f) / 0.55f);
        if (ringP > 0f && ringP < 1f) {
            double rr = radius * easeOutCubic(ringP);
            float ringFade = 1f - ringP;
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr), gold,
                    0.80f * ringFade, 0.35f * ringFade, 0.08, 0.50);
            // 第二道追赶环，错相扩散
            float ring2 = clamp01((p - 0.28f) / 0.5f);
            if (ring2 > 0f && ring2 < 1f) {
                double rr2 = radius * 0.7 * easeOutCubic(ring2);
                glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2), deep,
                        0.45f * (1f - ring2), 0.20f * (1f - ring2), 0.06, 0.38);
            }
        }

        // ===== 地面十字圣徽：沿用「圣域」母题的两条垂直相交粗线，随心跳脉动 =====
        float emblem = smoothstep(0.05f, 0.2f, p) * fade;
        if (emblem > 0f) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(p * 18.0);
            float len = radius * 0.72f;
            double hw = Math.max(0.06, radius * 0.035);
            line(b, m, cx - len, cz, cx + len, cz, cy, hw, core, 0.75f * emblem * pulse, 0.75f * emblem * pulse);
            line(b, m, cx, cz - len, cx, cz + len, cy, hw, core, 0.75f * emblem * pulse, 0.75f * emblem * pulse);
            // 圣徽底色圆盘，让十字不至于孤零零浮在地上
            band(b, m, cx, cz, cy, 0.0, radius * 0.85, 0f, TAU, 28,
                    gold[0], gold[1], gold[2], 0.10f * emblem, 0f);
        }

        // ===== 升腾金光丝：自地面向上飘的细竖线，逐根错相 =====
        float threadPhase = clamp01((p - 0.2f) / 0.8f);
        if (threadPhase > 0f && fade > 0f) {
            for (int i = 0; i < PRAYER_THREAD_COUNT; i++) {
                // 用 baseAngle 参与相位，使不同次触发的光丝分布略有差异
                float ang = baseAngle + TAU * i / PRAYER_THREAD_COUNT + i * 0.37f;
                float rr = radius * (0.35f + 0.45f * frac(i * 0.6180339f));
                float tx = cx + (float) Math.cos(ang) * rr;
                float tz = cz + (float) Math.sin(ang) * rr;
                // 逐根错开起飞时刻
                float t = clamp01((threadPhase - i * 0.05f) / 0.7f);
                if (t <= 0f) {
                    continue;
                }
                float y0 = cy + t * 2.2f;
                float y1 = y0 + 0.75f;
                float a = (1f - t) * fade;
                crossQuad(b, m, tx, y0, tz, tx, y1, tz, 0.05f, core, 0.7f * a, 0f);
            }
        }
    }

    // ============================== 弧形刀光基元 ==============================

    /**
     * 绘制一段「刀光弧带」：以 (cx,cz) 为圆心、位于水平面高度 cy 的圆环扇段，
     * 从 {@code startAngle} 起沿正方向跨越 {@code span × sweep} 弧度。
     * <p>
     * <b>透明度沿弧长梯度：</b>越靠近前端（刚扫到的位置）越亮，越靠近起点（最早扫过的位置）
     * 越淡——这正是「刀光拖尾」的成因。梯度用 {@code u^1.6} 而非线性，使尾部衰减更快、
     * 前端更集中，观感更接近真实的高速挥砍残留。
     * </p>
     *
     * @param rInner     弧带内半径
     * @param rOuter     弧带外半径
     * @param startAngle 起始角（弧度）
     * @param span       总跨度（弧度）
     * @param sweep      已扫过的比例（0~1）
     * @param segments   整段跨度对应的细分数（实际只绘制 sweep 部分）
     * @param col        颜色
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

        for (int i = 0; i < drawn; i++) {
            float a0 = startAngle + segAngle * i;
            float a1 = a0 + segAngle;
            // u：0=起点（最旧），1=前端（最新）
            float u0 = (float) i / drawn;
            float u1 = (float) (i + 1) / drawn;
            float alpha0 = alpha * (float) Math.pow(u0, 1.6);
            float alpha1 = alpha * (float) Math.pow(u1, 1.6);

            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

            float ox0 = cx + rOuter * cos0, oz0 = cz + rOuter * sin0;
            float ox1 = cx + rOuter * cos1, oz1 = cz + rOuter * sin1;
            float ix0 = cx + rInner * cos0, iz0 = cz + rInner * sin0;
            float ix1 = cx + rInner * cos1, iz1 = cz + rInner * sin1;

            b.vertex(m, ox0, cy, oz0).color(col[0], col[1], col[2], alpha0).endVertex();
            b.vertex(m, ox1, cy, oz1).color(col[0], col[1], col[2], alpha1).endVertex();
            b.vertex(m, ix1, cy, iz1).color(col[0], col[1], col[2], alpha1).endVertex();

            b.vertex(m, ox0, cy, oz0).color(col[0], col[1], col[2], alpha0).endVertex();
            b.vertex(m, ix1, cy, iz1).color(col[0], col[1], col[2], alpha1).endVertex();
            b.vertex(m, ix0, cy, iz0).color(col[0], col[1], col[2], alpha0).endVertex();
        }
    }

    /**
     * 扇形圆环带（annulus 的一段），内 / 外边缘可分别指定 alpha；用于扬尘、圣徽底色等。
     * <p>与 {@link #drawSlashArc} 的区别：本方法沿弧长<b>不做</b>透明度梯度，
     * 而是沿径向（内→外）做梯度，用于铺底而非表现拖尾。</p>
     *
     * @param startAngle 起始角（弧度）
     * @param span       跨度（弧度）
     */
    private static void band(BufferBuilder b, Matrix4f m, float cx, float cz, float cy,
                             double rInner, double rOuter, float startAngle, float span,
                             int segments, float r, float g, float bl,
                             float alphaInner, float alphaOuter) {
        if (alphaInner <= 0.002f && alphaOuter <= 0.002f) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = startAngle + span * i / segments;
            double a1 = startAngle + span * (i + 1) / segments;
            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

            float ox0 = (float) (cx + rOuter * cos0), oz0 = (float) (cz + rOuter * sin0);
            float ox1 = (float) (cx + rOuter * cos1), oz1 = (float) (cz + rOuter * sin1);
            float ix0 = (float) (cx + rInner * cos0), iz0 = (float) (cz + rInner * sin0);
            float ix1 = (float) (cx + rInner * cos1), iz1 = (float) (cz + rInner * sin1);

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ox1, cy, oz1).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alphaInner).endVertex();

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alphaInner).endVertex();
            b.vertex(m, ix0, cy, iz0).color(r, g, bl, alphaInner).endVertex();
        }
    }

    /**
     * 发光圆环：外辉 + 内辉 + 核心亮带，三层叠出柔和光环（与 AOE 渲染器同款手法）。
     */
    private static void glowRing(BufferBuilder b, Matrix4f m, float cx, float cz, float cy,
                                 double radius, int segs, float[] col,
                                 float coreA, float glowA, double coreHalf, double glowSpread) {
        if (radius < 0.05) {
            return;
        }
        float r = col[0], g = col[1], bb = col[2];
        band(b, m, cx, cz, cy, radius + coreHalf, radius + coreHalf + glowSpread,
                0f, TAU, segs, r, g, bb, glowA, 0f);
        band(b, m, cx, cz, cy, Math.max(0.0, radius - coreHalf - glowSpread), radius - coreHalf,
                0f, TAU, segs, r, g, bb, 0f, glowA);
        band(b, m, cx, cz, cy, Math.max(0.0, radius - coreHalf), radius + coreHalf,
                0f, TAU, segs, r, g, bb, coreA, coreA);
    }

    /**
     * 带宽度的水平线段（两点之间的细长四边形，两端 alpha 可不同）。
     *
     * @param hw 线半宽（格）
     */
    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float z1, float x2, float z2, float y,
                             double hw, float[] col, float a1, float a2) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        double nx = -dz / len * hw;
        double nz = dx / len * hw;
        float ax1 = (float) (x1 + nx), az1 = (float) (z1 + nz);
        float ax2 = (float) (x1 - nx), az2 = (float) (z1 - nz);
        float bx1 = (float) (x2 + nx), bz1 = (float) (z2 + nz);
        float bx2 = (float) (x2 - nx), bz2 = (float) (z2 - nz);

        b.vertex(m, ax1, y, az1).color(col[0], col[1], col[2], a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(col[0], col[1], col[2], a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(col[0], col[1], col[2], a2).endVertex();

        b.vertex(m, ax1, y, az1).color(col[0], col[1], col[2], a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(col[0], col[1], col[2], a2).endVertex();
        b.vertex(m, ax2, y, az2).color(col[0], col[1], col[2], a1).endVertex();
    }

    /**
     * 小菱形光点（火花），中心最亮、四角渐隐。水平面。
     */
    private static void spark(BufferBuilder b, Matrix4f m, float px, float pz, float y,
                              float size, float[] col, float alpha) {
        float r = col[0], g = col[1], bl = col[2];
        float[][] pts = {{px, pz - size}, {px + size, pz}, {px, pz + size}, {px - size, pz}};
        for (int i = 0; i < 4; i++) {
            float[] a = pts[i];
            float[] c = pts[(i + 1) % 4];
            b.vertex(m, px, y, pz).color(r, g, bl, alpha).endVertex();
            b.vertex(m, a[0], y, a[1]).color(r, g, bl, 0f).endVertex();
            b.vertex(m, c[0], y, c[1]).color(r, g, bl, 0f).endVertex();
        }
    }

    /**
     * 竖直 / 任意朝向的「十字双面」线段：沿世界 X、Z 轴各画一个四边形，
     * 使线段从任意水平视角皆可见、无需 billboard 计算。
     * <p>用于圣光柱与升腾光丝，手法与 {@code AoeEffectRenderer} 的闪电电柱一致。</p>
     *
     * @param hw 线半宽（格）
     * @param a1 起点端 alpha
     * @param a2 终点端 alpha
     */
    private static void crossQuad(BufferBuilder b, Matrix4f m,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float hw, float[] col, float a1, float a2) {
        if (a1 <= 0.002f && a2 <= 0.002f) {
            return;
        }
        float r = col[0], g = col[1], bb = col[2];
        // 面1：沿世界 X 轴加宽
        quad(b, m, x1 - hw, y1, z1, x1 + hw, y1, z1, x2 + hw, y2, z2, x2 - hw, y2, z2, r, g, bb, a1, a2);
        // 面2：沿世界 Z 轴加宽
        quad(b, m, x1, y1, z1 - hw, x1, y1, z1 + hw, x2, y2, z2 + hw, x2, y2, z2 - hw, r, g, bb, a1, a2);
    }

    /**
     * 画一个四边形（拆成两三角形）：顶点 a→b 用 alpha {@code aAB}，c→d 用 alpha {@code aCD}。
     */
    private static void quad(BufferBuilder b, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cxp, float cyp, float czp, float dx, float dy, float dz,
                             float r, float g, float bb, float aAB, float aCD) {
        b.vertex(m, ax, ay, az).color(r, g, bb, aAB).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bb, aAB).endVertex();
        b.vertex(m, cxp, cyp, czp).color(r, g, bb, aCD).endVertex();

        b.vertex(m, ax, ay, az).color(r, g, bb, aAB).endVertex();
        b.vertex(m, cxp, cyp, czp).color(r, g, bb, aCD).endVertex();
        b.vertex(m, dx, dy, dz).color(r, g, bb, aCD).endVertex();
    }

    // ============================== 数学 / 颜色辅助 ==============================

    /** 环分段数（随半径，夹取 28~56）。 */
    private static int segmentsFor(double radius) {
        int v = (int) (radius * 4);
        if (v < 28) {
            return 28;
        }
        return Math.min(v, 56);
    }

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

    /** 取小数部分（结果恒在 [0,1)）。 */
    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /** 夹取到 0~1。 */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    /** 0xRRGGBB 拆为 [r,g,b]（0~1）。 */
    private static float[] unpack(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f
        };
    }
}
