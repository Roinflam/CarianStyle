package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.List;

/**
 * 定点 AOE 自绘渲染器（纯客户端）。
 * <p>
 * 与光环 {@link AuraGroundRenderer} 采用同款管线：订阅 {@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，用 {@code POSITION_COLOR} 纯顶点绘制——无贴图、无原版粒子。
 * </p>
 * <p>
 * 多数演出为<b>水平地面法阵</b>；而<b>猩红罗妮亚 {@link #drawScarletBloom}</b> 为还原玛莲妮亚的
 * 「猩红艾奥尼亚」开花，额外绘制一朵<b>竖直 3D 立体绽放花</b>（{@link #drawAeoniaFlower}）。其余演出
 * （含癫火 {@link #drawFrenziedFlame}）保持平面。
 * </p>
 * <p>
 * <b>龙雷红色闪电 {@link #drawRedLightning}（v4）：</b>另一类<b>竖直</b>演出——还原艾尔登法环「龙雷」的
 * 红色雷击：一道自天而降的红色之字电柱劈在目标脚下，白热核 + 红辉光 + 浓红外晕三层电流，沿途分出数条
 * 短分叉，落地处掀起红色冲击环与地面强闪，整体瞬间炸亮后缓慢庄重明灭、较慢消散。电柱每段用「十字双面」
 * 绘制（沿世界 X、Z 轴各一个四边形），从任意水平视角皆可见、无需 billboard。外形由管理器创建时确定的
 * 固定种子决定，每道闪电形态各异、同一道闪电在其生命周期内形态稳定。古龙雷击等高频重复降雷由管理器的
 * 「同位置合并」收敛为一道持续的雷，避免叠加成「鬼畜」。
 * </p>
 * <p>
 * <b>v5（性能，视觉零变化）：</b>接入 {@link VisualBatch}——不再自行设置 / 恢复 GL 状态、
 * 不再自行 {@code begin/end} 顶点缓冲，改为向共享缓冲写顶点，由 {@link VisualBatch} 在本帧末
 * 统一提交（本模组七个世界渲染器合并为一次 GL 状态切换与一次 draw call）。
 * <b>空缓冲兜底</b>（零面积全透明退化三角形）原先由本类在自己的 {@code begin()} 后追加，
 * 现已上移至 {@link VisualBatch} 统一处理，本类不再需要。
 * 本渲染器不做范围实体查询（数据来自 {@link AoeEffectManager} 的存活特效列表），故不涉及
 * {@link SharedEntityQuery}；仅在绑定跟随实体时按 id 精确查一次实体，与优化前一致。
 * 距离裁剪、进度映射、分发顺序与全部几何参数均未改动。
 * </p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class AoeEffectRenderer {

    /** 离地高度偏移，避免与地面 z-fighting */
    private static final float Y_OFFSET = 0.02f;
    /** 距离裁剪（格）：相机太远的特效本帧不绘制。已调大以容纳更大的爆发冲击环与立体花。 */
    private static final double CULL = 96.0;
    private static final float TAU = (float) (Math.PI * 2.0);

    // ===== 主题配色（0xRRGGBB）=====
    private static final int CAUSALITY_GOLD = 0xFFD24A;
    private static final int CAUSALITY_VIOLET = 0xB47BFF;
    private static final int FROST_ICE = 0x9ED8FF;
    private static final int FROST_WHITE = 0xEAF6FF;
    private static final int REPULSE_WHITE = 0xF0F4FF;
    private static final int SCARLET = 0xE0244A;
    private static final int SCARLET_DEEP = 0x800018;
    /** 猩红血核白热（炸裂/蓄力顶点用） */
    private static final int SCARLET_HOT = 0xFFD0D8;
    private static final int FRENZY_YELLOW = 0xFFE020;
    private static final int FRENZY_ORANGE = 0xFF6A1A;
    /** 癫火白热（顶点冲击强闪用） */
    private static final int FRENZY_WHITE = 0xFFF4C0;
    private static final int GENERIC_BLUE = 0xCFE0FF;

    // ===== 龙雷红色闪电配色（0xRRGGBB）=====
    /** 龙雷电柱核心：近白热（仅微带红），原作龙雷核心极亮发白 */
    private static final int LIGHTNING_CORE = 0xFFF0F0;
    /** 龙雷电柱中层辉光：亮红 */
    private static final int LIGHTNING_GLOW = 0xFF2A36;
    /** 龙雷电柱外层光晕 / 落地冲击 / 分叉末端：浓深红 */
    private static final int LIGHTNING_DEEP = 0xC81022;

    // ===== 龙雷红色闪电几何参数 =====
    /** 闪电柱总高度（格，自落地点向上延伸） */
    private static final double LIGHTNING_HEIGHT = 28.0;
    /** 主干段数 */
    private static final int LIGHTNING_SEGMENTS = 18;
    /** 每段水平蜿蜒幅度（格，越大电柱越曲折） */
    private static final double LIGHTNING_WANDER = 0.65;
    /** 主干水平最大偏移（格，限制电柱不漂离落地点太远） */
    private static final double LIGHTNING_MAX_OFFSET = 3.0;
    /** 电柱核心半宽（格，白热炽核） */
    private static final double LIGHTNING_CORE_HALF = 0.16;
    /** 电柱中层辉光半宽（格，红色主体，包裹核心） */
    private static final double LIGHTNING_GLOW_HALF = 0.52;
    /** 电柱外层光晕半宽（格，浓红体量光晕，最宽最淡） */
    private static final double LIGHTNING_HALO_HALF = 1.05;
    /** 分叉数量 */
    private static final int LIGHTNING_BRANCHES = 6;
    /** 每条分叉段数 */
    private static final int LIGHTNING_BRANCH_SEGMENTS = 5;
    /** 分叉每段步长（格） */
    private static final double LIGHTNING_BRANCH_STEP = 1.7;

    private AoeEffectRenderer() {
    }

    /**
     * 渲染回调：遍历全部存活特效，按类型分发到对应自绘演出。
     * <p>
     * v5：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理；本方法只负责裁剪与写顶点。
     * </p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        List<AoeEffectManager.AoeEffect> list = AoeEffectManager.getActive();
        if (list.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // 共享批次未开启：直接跳过
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        long now = System.currentTimeMillis();
        double cullSqr = CULL * CULL;

        boolean anyVisible = false;
        for (AoeEffectManager.AoeEffect fx : list) {
            double dx = fx.x - cam.x;
            double dy = fx.y - cam.y;
            double dz = fx.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > cullSqr) {
                continue;
            }
            float progress = (now - fx.birthMs) / (float) fx.durationMs;
            if (progress > 0.0005f && progress < 1f) {
                anyVisible = true;
                break;
            }
        }

        if (!anyVisible) {
            return;
        }

        Matrix4f matrix = VisualBatch.matrix();
        float partial = VisualBatch.partialTick();

        for (AoeEffectManager.AoeEffect fx : list) {
            double fxX = fx.x;
            double fxY = fx.y;
            double fxZ = fx.z;
            if (fx.entityId >= 0) {
                Entity bound = mc.level.getEntity(fx.entityId);
                if (bound != null && bound.isAlive()) {
                    Vec3 pos = bound.getPosition(partial);
                    fxX = pos.x;
                    fxY = pos.y;
                    fxZ = pos.z;
                    fx.x = fxX;
                    fx.y = fxY;
                    fx.z = fxZ;
                }
            }
            double dx = fxX - cam.x;
            double dy = fxY - cam.y;
            double dz = fxZ - cam.z;
            if (dx * dx + dy * dy + dz * dz > cullSqr) {
                continue;
            }
            float progress = AoeEffectManager.progressFor(fx, now);
            double rx = fxX - cam.x;
            double ry = fxY - cam.y + Y_OFFSET;
            double rz = fxZ - cam.z;
            // fx.seed 为该特效创建时确定的固定外形种子（仅红色闪电用到），整段生命周期恒定，
            // 即便红闪被「同位置合并」反复续命也不跳变外形。
            dispatch(builder, matrix, fx.type, rx, ry, rz, fx.radius, progress, fx.seed);
        }
    }

    /**
     * 按类型分发到具体演出。
     *
     * @param seed 该特效的固定外形种子（创建时确定、生命周期不变，当前仅红色闪电使用）
     */
    private static void dispatch(BufferBuilder b, Matrix4f m, int type,
                                 double cx, double cy, double cz, double radius, float p, long seed) {
        switch (type) {
            case AoeEffectPacket.TYPE_CAUSALITY -> drawCausality(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_FROST_QUAKE -> drawFrostQuake(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_REPULSION -> drawRepulsion(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_SCARLET_BLOOM -> drawScarletBloom(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_FRENZIED_FLAME -> drawFrenziedFlame(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_RED_LIGHTNING -> drawRedLightning(b, m, cx, cy, cz, radius, p, seed);
            default -> drawGeneric(b, m, cx, cy, cz, radius, p);
        }
    }

    // ============================== 各附魔专属演出 ==============================

    /**
     * 因果律：地面金色六芒星法阵（旋转）+ 内六边形 + 外发光环（脉冲扩张）+ 紫色「因果之线」放射抽射
     * + 六芒星顶点火花。整体淡入淡出。
     */
    private static void drawCausality(BufferBuilder b, Matrix4f m,
                                      double cx, double cy, double cz, double radius, float p) {
        float fade = fadeInOut(p, 0.15f, 0.80f);
        if (fade <= 0f) {
            return;
        }
        float rot = p * 1.2f * TAU;
        double expand = easeOutCubic(clamp01(p / 0.30f));
        double rOuter = radius * (0.85 + 0.15 * expand);
        float[] gold = unpack(CAUSALITY_GOLD);
        float[] violet = unpack(CAUSALITY_VIOLET);
        double hw = lineHalf(radius);
        int segs = segmentsFor(rOuter);

        glowRing(b, m, cx, cz, cy, rOuter, segs, gold, 0.85f * fade, 0.32f * fade, 0.07, 0.50);
        starPolygon(b, m, cx, cz, cy, radius * 0.62, 6, 2, rot, hw, gold, 0.70f * fade);
        polygonRing(b, m, cx, cz, cy, radius * 0.40, 6, rot, hw, gold, 0.55f * fade);
        float lash = clamp01((p - 0.30f) / 0.20f);
        if (lash > 0f) {
            double rl = radius * 0.20 + (radius - radius * 0.20) * easeOutCubic(lash);
            rays(b, m, cx, cz, cy, radius * 0.20, rl, 8, rot * 0.5f, hw,
                    violet, 0.75f * fade * lash, 0.05f * fade * lash);
        }
        for (int i = 0; i < 6; i++) {
            double ang = rot + TAU * i / 6.0;
            double px = cx + radius * 0.62 * Math.cos(ang);
            double pz = cz + radius * 0.62 * Math.sin(ang);
            float tw = 0.5f + 0.5f * (float) Math.sin(p * 18.0 + i);
            spark(b, m, px, pz, cy, (float) (radius * 0.035 + 0.07), gold, 0.8f * fade * tw);
        }
    }

    /**
     * 冻结地震：12 条放射地裂纹由内向外生长 + 2 道霜环扩张外滚 + 中心冰花（八角星 + 六边形反向旋转）
     * + 起手中心闪光。
     */
    private static void drawFrostQuake(BufferBuilder b, Matrix4f m,
                                       double cx, double cy, double cz, double radius, float p) {
        float fade = fadeInOut(p, 0.12f, 0.70f);
        if (fade <= 0f) {
            return;
        }
        float[] ice = unpack(FROST_ICE);
        float[] white = unpack(FROST_WHITE);
        double hw = lineHalf(radius);

        double grow = easeOutCubic(clamp01(p / 0.45f));
        double crackLen = radius * grow;
        rays(b, m, cx, cz, cy, radius * 0.10, crackLen, 12, 0f, hw, ice, 0.80f * fade, 0.08f * fade);
        rays(b, m, cx, cz, cy, radius * 0.10, crackLen * 0.70, 12, 0f, hw * 0.7,
                white, 0.90f * fade, 0.10f * fade);

        for (int i = 0; i < 2; i++) {
            float rp = clamp01((p - i * 0.12f) / 0.60f);
            if (rp <= 0f || rp >= 1f) {
                continue;
            }
            double rr = radius * easeOutCubic(rp);
            float a = (1f - rp) * fade;
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr), ice, 0.50f * a, 0.20f * a, 0.05, 0.35);
        }

        float rot = p * 0.6f * TAU;
        starPolygon(b, m, cx, cz, cy, radius * 0.18, 8, 3, rot, hw, white, 0.70f * fade);
        polygonRing(b, m, cx, cz, cy, radius * 0.12, 6, -rot, hw, ice, 0.60f * fade);

        if (p < 0.20f) {
            spark(b, m, cx, cz, cy, (float) (radius * 0.10 + 0.20), white, (0.20f - p) / 0.20f * fade);
        }
    }

    /**
     * 排斥：双发光环从中心猛烈外推扩张并快速淡出（短促的「砰」一下冲击波）。
     */
    private static void drawRepulsion(BufferBuilder b, Matrix4f m,
                                      double cx, double cy, double cz, double radius, float p) {
        float fade = 1f - smoothstep(0.40f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float[] w = unpack(REPULSE_WHITE);
        double expand = easeOutCubic(clamp01(p / 0.70f));
        double r1 = radius * expand;
        double r2 = radius * 0.6 * expand;
        glowRing(b, m, cx, cz, cy, r1, segmentsFor(r1), w, 0.85f * fade, 0.35f * fade, 0.06, 0.45);
        glowRing(b, m, cx, cz, cy, r2, segmentsFor(r2), w, 0.50f * fade, 0.20f * fade, 0.05, 0.30);
    }

    /**
     * 猩红艾奥尼亚（还原玛莲妮亚开花，含竖直 3D 立体绽放花）。
     */
    private static void drawScarletBloom(BufferBuilder b, Matrix4f m,
                                         double cx, double cy, double cz, double radius, float p) {
        float[] red = unpack(SCARLET);
        float[] deep = unpack(SCARLET_DEEP);
        float[] hot = unpack(SCARLET_HOT);
        double hw = lineHalf(radius);
        float appear = clamp01(p / 0.015f);
        float rot = p * 0.35f * TAU;

        float open;
        if (p < 0.40f) {
            open = lerp(0.08f, 0.72f, (float) easeOutCubic(p / 0.40f));
        } else if (p < 0.44f) {
            open = lerp(0.72f, 1.0f, (p - 0.40f) / 0.04f);
        } else {
            open = 1.0f + 0.6f * smoothstep(0.44f, 1.0f, p);
        }
        float riseH = clamp01(p / 0.04f);
        if (p > 0.62f) {
            riseH *= (1f - 0.55f * smoothstep(0.62f, 1f, p));
        }
        float flowerAlpha = appear * (1f - smoothstep(0.65f, 1.0f, p));

        float baseA = appear * (1f - smoothstep(0.48f, 0.56f, p));
        if (baseA > 0f) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(p * 12.0);
            band(b, m, cx, cz, cy, 0.0, radius * 1.05, segmentsFor(radius),
                    deep[0], deep[1], deep[2], 0.04f * baseA * pulse, 0.09f * baseA);
            polygonRing(b, m, cx, cz, cy, radius * 0.92, 6, rot * 0.5f, hw, red, 0.35f * baseA);
            tickRing(b, m, cx, cz, cy, radius * 0.74, 0.28, 36, p * 0.4f * TAU, 0.04, red, 0.32f * baseA);
            tickRing(b, m, cx, cz, cy, radius * 0.58, 0.20, 24, -p * 0.5f * TAU, 0.035, deep, 0.28f * baseA);
            for (int i = 0; i < 3; i++) {
                float ph = frac(p * 0.9f + i / 3f);
                double rr = radius * ph;
                if (rr > 0.3) {
                    glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr), red, 0f, 0.13f * baseA * (1f - ph), 0.05, 0.40);
                }
            }
            starField(b, m, cx, cz, cy, radius * 0.95, 16, p, (float) (radius * 0.022 + 0.035), red, 0.4f * baseA);
        }

        drawAeoniaFlower(b, m, cx, cy, cz, radius, open, riseH, flowerAlpha, rot);

        if (p >= 0.40f && p < 0.52f) {
            float kf = clamp01((p - 0.40f) / 0.04f) * (1f - smoothstep(0.46f, 0.52f, p));
            double coreH = radius * 0.95 * riseH * 0.16;
            drawOrb(b, m, cx, cy + coreH, cz, radius * (0.10 + 0.18 * kf), mix(red, hot, 0.7f), 0.9f * appear);
        }

        float burst = clamp01((p - 0.42f) / 0.30f);
        if (burst > 0f) {
            float bfade = 1f - burst;
            double rr = radius * 1.9 * easeOutCubic(burst);
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr), red, 0.85f * bfade, 0.42f * bfade, 0.10, 0.70);
            glowRing(b, m, cx, cz, cy, rr * 0.74, segmentsFor(rr), deep, 0.6f * bfade, 0.28f * bfade, 0.07, 0.50);
            float b2 = clamp01((p - 0.47f) / 0.28f);
            if (b2 > 0f) {
                double rr2 = radius * 1.45 * easeOutCubic(b2);
                glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2), red, 0.45f * (1f - b2), 0.2f * (1f - b2), 0.06, 0.45);
            }
            if (burst < 0.55f) {
                float rf = 1f - burst / 0.55f;
                rays(b, m, cx, cz, cy, radius * 0.2, radius * 1.7 * easeOutCubic(burst), 16, p * 0.2f, hw, deep, 0.6f * rf, 0f);
            }
            if (burst < 0.15f) {
                float flash = (0.15f - burst) / 0.15f;
                band(b, m, cx, cz, cy, 0.0, radius * 0.9, segmentsFor(radius), hot[0], hot[1], hot[2], 0.5f * flash, 0.0f);
            }
        }

        float ember = smoothstep(0.65f, 0.75f, p) * (1f - smoothstep(0.95f, 1f, p));
        if (ember > 0f) {
            double rr = radius * 2.2;
            band(b, m, cx, cz, cy, 0.0, rr, segmentsFor(rr), deep[0], deep[1], deep[2], 0.0f, 0.15f * ember);
            for (int i = 0; i < 2; i++) {
                float ph = frac(p * 2.5f + i * 0.5f);
                double rrr = radius * 2.2 * ph;
                if (rrr > 0.3) {
                    glowRing(b, m, cx, cz, cy, rrr, segmentsFor(rrr), deep, 0f, 0.10f * ember * (1f - ph), 0.04, 0.28);
                }
            }
            starField(b, m, cx, cz, cy, radius * 1.8, 12, p, (float) (radius * 0.02 + 0.03), deep, 0.32f * ember);
        }
    }

    // ============================== 猩红艾奥尼亚 · 3D 立体花专用几何 ==============================

    /**
     * 绘制整朵猩红艾奥尼亚之花：四层曼陀罗式层叠花瓣 + 花蕊小瓣 + 花心白热球。
     */
    private static void drawAeoniaFlower(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                         double radius, float open, float riseH, float flowerAlpha, float rot) {
        if (flowerAlpha <= 0.01f || riseH <= 0.01f) {
            return;
        }
        float[] deep = unpack(SCARLET_DEEP);
        float[] red = unpack(SCARLET);
        float[] hot = unpack(SCARLET_HOT);
        double l = radius * 0.95 * riseH;

        drawPetalLayer(b, m, cx, cy, cz, 8, l * 1.00, radius * 0.24, deg2rad(82), open, rot,
                deep, red, red, flowerAlpha);
        drawPetalLayer(b, m, cx, cy, cz, 7, l * 0.78, radius * 0.21, deg2rad(64), open, rot + 0.34f,
                deep, red, hot, flowerAlpha * 0.96f);
        drawPetalLayer(b, m, cx, cy, cz, 6, l * 0.56, radius * 0.18, deg2rad(48), open, rot + 0.66f,
                red, red, hot, flowerAlpha * 0.92f);
        drawPetalLayer(b, m, cx, cy, cz, 5, l * 0.38, radius * 0.14, deg2rad(34), open, rot + 0.95f,
                red, hot, hot, flowerAlpha * 0.90f);
        drawPetalLayer(b, m, cx, cy, cz, 6, l * 0.24, radius * 0.08, deg2rad(20), open * 0.5f, rot + 0.15f,
                red, hot, hot, flowerAlpha);
        drawOrb(b, m, cx, cy + l * 0.12, cz, radius * 0.06, mix(red, hot, 0.5f), 0.7f * flowerAlpha);
    }

    /**
     * 绘制一层花瓣：{@code petals} 片均布，方位以 {@code baseRot} 为起点。
     */
    private static void drawPetalLayer(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                       int petals, double length, double maxWidth,
                                       float fullAngle, float open, float baseRot,
                                       float[] deep, float[] mid, float[] tip, float alphaMul) {
        float budAngle = deg2rad(12);
        float baseAngle = budAngle + open * (fullAngle - budAngle);
        float curlAngle = deg2rad(12) + deg2rad(28) * open;
        for (int i = 0; i < petals; i++) {
            float az = baseRot + TAU * i / petals;
            drawPetal(b, m, cx, cy, cz, az, baseAngle, curlAngle, length, maxWidth, deep, mid, tip, alphaMul);
        }
    }

    /**
     * 绘制一片 3D 花瓣：沿一条「向上弯曲」的脊线积分采样，左右按宽度轮廓展开成三角形带曲面。
     */
    private static void drawPetal(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                  float azimuth, float baseAngle, float curlAngle,
                                  double length, double maxWidth,
                                  float[] deep, float[] mid, float[] tip, float alphaMul) {
        final int seg = 8;
        double cosA = Math.cos(azimuth), sinA = Math.sin(azimuth);
        double wx = -sinA, wz = cosA;

        double[] hor = new double[seg + 1];
        double[] ver = new double[seg + 1];
        double h = 0, v = 0;
        double ds = length / seg;
        for (int i = 0; i <= seg; i++) {
            hor[i] = h;
            ver[i] = v;
            double u = (double) i / seg;
            double ang = baseAngle + curlAngle * u;
            h += Math.sin(ang) * ds;
            v += Math.cos(ang) * ds;
        }

        for (int i = 0; i < seg; i++) {
            double u0 = (double) i / seg;
            double u1 = (double) (i + 1) / seg;
            double w0 = petalWidth(u0) * maxWidth * 0.5;
            double w1 = petalWidth(u1) * maxWidth * 0.5;

            double mx0 = cx + hor[i] * cosA, mz0 = cz + hor[i] * sinA, my0 = cy + ver[i];
            double mx1 = cx + hor[i + 1] * cosA, mz1 = cz + hor[i + 1] * sinA, my1 = cy + ver[i + 1];

            float l0x = (float) (mx0 - wx * w0), l0y = (float) my0, l0z = (float) (mz0 - wz * w0);
            float r0x = (float) (mx0 + wx * w0), r0y = (float) my0, r0z = (float) (mz0 + wz * w0);
            float l1x = (float) (mx1 - wx * w1), l1y = (float) my1, l1z = (float) (mz1 - wz * w1);
            float r1x = (float) (mx1 + wx * w1), r1y = (float) my1, r1z = (float) (mz1 + wz * w1);

            float[] c0 = petalColor(u0, deep, mid, tip);
            float[] c1 = petalColor(u1, deep, mid, tip);
            float a0 = petalAlpha(u0) * alphaMul;
            float a1 = petalAlpha(u1) * alphaMul;

            b.vertex(m, l0x, l0y, l0z).color(c0[0], c0[1], c0[2], a0).endVertex();
            b.vertex(m, r0x, r0y, r0z).color(c0[0], c0[1], c0[2], a0).endVertex();
            b.vertex(m, r1x, r1y, r1z).color(c1[0], c1[1], c1[2], a1).endVertex();

            b.vertex(m, l0x, l0y, l0z).color(c0[0], c0[1], c0[2], a0).endVertex();
            b.vertex(m, r1x, r1y, r1z).color(c1[0], c1[1], c1[2], a1).endVertex();
            b.vertex(m, l1x, l1y, l1z).color(c1[0], c1[1], c1[2], a1).endVertex();
        }
    }

    /**
     * 绘制一个小亮球（上下两个四棱锥拼成的八面体近似），用作花心白热核。
     */
    private static void drawOrb(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                double size, float[] col, float alpha) {
        float r = col[0], g = col[1], bb = col[2];
        float cxf = (float) cx, cyf = (float) cy, czf = (float) cz, s = (float) size;
        float[] top = {cxf, cyf + s, czf};
        float[] bot = {cxf, cyf - s, czf};
        float[][] eq = {
                {cxf + s, cyf, czf}, {cxf, cyf, czf + s}, {cxf - s, cyf, czf}, {cxf, cyf, czf - s}
        };
        for (int i = 0; i < 4; i++) {
            float[] a = eq[i];
            float[] d = eq[(i + 1) % 4];
            b.vertex(m, top[0], top[1], top[2]).color(r, g, bb, alpha).endVertex();
            b.vertex(m, a[0], a[1], a[2]).color(r, g, bb, alpha * 0.55f).endVertex();
            b.vertex(m, d[0], d[1], d[2]).color(r, g, bb, alpha * 0.55f).endVertex();
            b.vertex(m, bot[0], bot[1], bot[2]).color(r, g, bb, alpha).endVertex();
            b.vertex(m, d[0], d[1], d[2]).color(r, g, bb, alpha * 0.55f).endVertex();
            b.vertex(m, a[0], a[1], a[2]).color(r, g, bb, alpha * 0.55f).endVertex();
        }
    }

    /** 花瓣宽度轮廓（根窄、中宽、尖收的叶形）。 */
    private static double petalWidth(double u) {
        return Math.sin(Math.PI * Math.pow(u, 0.65));
    }

    /** 花瓣颜色：根部 deep → 中部 mid → 尖端偏向 tip。 */
    private static float[] petalColor(double u, float[] deep, float[] mid, float[] tip) {
        if (u < 0.5) {
            return mix(deep, mid, (float) (u / 0.5));
        }
        return mix(mid, tip, (float) ((u - 0.5) / 0.5) * 0.7f);
    }

    /** 花瓣 alpha：中段最实的驼峰。 */
    private static float petalAlpha(double u) {
        return 0.5f + 0.45f * (float) Math.sin(Math.PI * u);
    }

    /**
     * 癫火蔓延（大型多段平面演出，开场即满状态）。
     */
    private static void drawFrenziedFlame(BufferBuilder b, Matrix4f m,
                                          double cx, double cy, double cz, double radius, float p) {
        float[] yellow = unpack(FRENZY_YELLOW);
        float[] orange = unpack(FRENZY_ORANGE);
        float[] hot = unpack(FRENZY_WHITE);
        double hw = lineHalf(radius);
        float appear = clamp01(p / 0.015f);

        float pre = appear * (1f - smoothstep(0.40f, 0.46f, p));
        if (pre > 0f) {
            float spin = p * 5f * TAU;
            float flick = 0.55f + 0.45f * (float) Math.sin(p * 60.0);

            band(b, m, cx, cz, cy, 0.0, radius * 1.0, segmentsFor(radius),
                    orange[0] * 0.6f, orange[1] * 0.45f, orange[2] * 0.35f, 0.03f * pre, 0.08f * pre);
            float wobble = (float) (Math.sin(p * 35.0) * 0.05);
            polygonRing(b, m, cx, cz, cy, radius * (0.30 + wobble), 7, spin * 0.5f, hw, yellow, 0.4f * pre * flick);

            int n = 18;
            for (int i = 0; i < n; i++) {
                double base = TAU * i / n;
                double jitter = Math.sin(p * 48.0 + i * 1.7) * 0.16 + Math.sin(p * 29.0 + i * 3.1) * 0.10;
                double ang = base + jitter;
                double lenPulse = 0.55 + 0.45 * Math.abs(Math.sin(p * 8.0 + i * 0.9));
                double len = radius * lenPulse;
                double cos = Math.cos(ang), sin = Math.sin(ang);
                double ix = cx + cos * radius * 0.08, iz = cz + sin * radius * 0.08;
                double mx = cx + cos * len * 0.55, mz = cz + sin * len * 0.55;
                double ox = cx + cos * len, oz = cz + sin * len;
                line(b, m, ix, iz, mx, mz, cy, hw, yellow[0], yellow[1], yellow[2], 0.90f * pre, 0.70f * pre);
                line(b, m, mx, mz, ox, oz, cy, hw * 0.8, orange[0], orange[1], orange[2], 0.70f * pre, 0.06f * pre);
                if (len > radius * 0.45) {
                    for (int s = -1; s <= 1; s += 2) {
                        double fang = ang + s * 0.38;
                        double fox = ox + Math.cos(fang) * len * 0.28;
                        double foz = oz + Math.sin(fang) * len * 0.28;
                        line(b, m, ox, oz, fox, foz, cy, hw * 0.6, orange[0], orange[1], orange[2], 0.5f * pre, 0f);
                    }
                }
            }
            starPolygon(b, m, cx, cz, cy, radius * 0.30, 6, 2, spin, hw, yellow, 0.65f * pre * flick);
            starPolygon(b, m, cx, cz, cy, radius * 0.22, 5, 2, -spin * 1.3f, hw, orange, 0.55f * pre * flick);
            starPolygon(b, m, cx, cz, cy, radius * 0.15, 7, 3, spin * 1.6f, hw, hot, 0.45f * pre * flick);
            starField(b, m, cx, cz, cy, radius * 0.95, 22, p * 1.5f, (float) (radius * 0.025 + 0.035), yellow, 0.5f * pre);
            spark(b, m, cx, cz, cy, (float) (radius * 0.12 + 0.05), mix(yellow, hot, 0.4f + 0.3f * flick), 0.8f * pre);
        }

        if (p >= 0.38f && p < 0.46f) {
            float kf = clamp01((p - 0.38f) / 0.06f);
            float coreSize = (float) (radius * (0.12 + 0.28 * easeOutCubic(kf)));
            spark(b, m, cx, cz, cy, coreSize, mix(yellow, hot, kf), 0.95f * appear);
            rays(b, m, cx, cz, cy, radius * (1.0 - 0.6 * kf), radius * 0.2, 14, p * 2.5f, hw, hot, 0.1f, 0.6f * kf * appear);
        }

        float burst = clamp01((p - 0.42f) / 0.28f);
        if (burst > 0f) {
            float cfade = 1f - burst;
            double rr = radius * 1.75 * easeOutCubic(burst);
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr), orange, 0.85f * cfade, 0.42f * cfade, 0.09, 0.60);
            glowRing(b, m, cx, cz, cy, rr * 0.72, segmentsFor(rr), yellow, 0.60f * cfade, 0.28f * cfade, 0.06, 0.45);
            float b2 = clamp01((p - 0.47f) / 0.26f);
            if (b2 > 0f) {
                double rr2 = radius * 1.35 * easeOutCubic(b2);
                glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2), orange, 0.5f * (1f - b2), 0.22f * (1f - b2), 0.05, 0.40);
            }
            if (burst < 0.55f) {
                float rf = 1f - burst / 0.55f;
                rays(b, m, cx, cz, cy, radius * 0.2, radius * 1.6 * easeOutCubic(burst), 18, p * 0.3f, hw, orange, 0.6f * rf, 0f);
            }
            if (burst < 0.16f) {
                float flash = (0.16f - burst) / 0.16f;
                spark(b, m, cx, cz, cy, (float) (radius * 0.55 + 0.4), mix(yellow, hot, 0.7f), 0.95f * flash);
                glowRing(b, m, cx, cz, cy, radius * 0.7, segmentsFor(radius), hot, 0.75f * flash, 0.35f * flash, 0.12, 0.5);
            }
        }

        float emberOut = smoothstep(0.65f, 0.75f, p) * (1f - smoothstep(0.93f, 1f, p));
        if (emberOut > 0f) {
            int n = 18;
            for (int i = 0; i < n; i++) {
                double ang = TAU * i / n + Math.sin(i * 1.7) * 0.1;
                double len = radius * 0.85;
                double cos = Math.cos(ang), sin = Math.sin(ang);
                line(b, m, cx + cos * radius * 0.08, cz + sin * radius * 0.08,
                        cx + cos * len, cz + sin * len, cy, hw * 0.7,
                        orange[0] * 0.5f, orange[1] * 0.4f, orange[2] * 0.35f, 0.3f * emberOut, 0f);
            }
            float flick = 0.4f + 0.6f * (float) Math.abs(Math.sin(p * 28.0));
            starField(b, m, cx, cz, cy, radius * 0.8, 10, p, (float) (radius * 0.02 + 0.03), orange, 0.3f * emberOut * flick);
        }
    }

    /**
     * 通用回退：中性蓝白双环扩张（兼容层未匹配到专属类型时使用）。
     */
    private static void drawGeneric(BufferBuilder b, Matrix4f m,
                                    double cx, double cy, double cz, double radius, float p) {
        float fade = fadeInOut(p, 0.15f, 0.60f);
        if (fade <= 0f) {
            return;
        }
        float[] c = unpack(GENERIC_BLUE);
        double expand = easeOutCubic(clamp01(p / 0.60f));
        glowRing(b, m, cx, cz, cy, radius * expand, segmentsFor(radius), c, 0.70f * fade, 0.30f * fade, 0.06, 0.40);
        glowRing(b, m, cx, cz, cy, radius * 0.55 * expand, segmentsFor(radius), c, 0.40f * fade, 0.18f * fade, 0.05, 0.30);
    }

    // ============================== 龙雷 · 红色闪电专属演出（竖直） ==============================

    /**
     * 古龙雷击 / 维克的龙雷：垂直红色之字闪电柱（含蜿蜒主干 + 分叉 + 落地红色冲击环）。
     * <p>还原艾尔登法环「龙雷」的红色雷击意象：一道自天而降的红色电柱劈在目标脚下，
     * 粉白红核 + 红辉光双层电流，沿途分出数条短分叉，落地处掀起红色冲击环与地面强闪；
     * 整体瞬间炸亮后持续强烈明灭、较慢消散（约 1.4 秒），还原原作龙雷的巨大亮眼与余震重闪。
     * 电柱外形由管理器在创建时确定的固定种子决定，
     * 每道闪电形态各异、同一道闪电（含被「同位置合并」续命的）在整段生命周期内形态稳定，不逐帧 / 逐次抖动。</p>
     * <p>电柱为竖直几何，每段用「十字双面」绘制（沿世界 X、Z 轴各一个四边形），
     * 从任意水平视角都可见、无需 billboard 计算。{@code radius} 仅用于落地冲击环尺寸，
     * 闪电柱粗细 / 高度由本类顶部的 {@code LIGHTNING_*} 常量控制。</p>
     *
     * @param seed 该闪电的固定外形种子（由管理器创建时生成、生命周期不变）
     */
    private static void drawRedLightning(BufferBuilder b, Matrix4f m,
                                         double cx, double cy, double cz, double radius, float p, long seed) {
        float intensity = lightningIntensity(p, seed);
        if (intensity <= 0f) {
            return;
        }
        float[] core = unpack(LIGHTNING_CORE);
        float[] glow = unpack(LIGHTNING_GLOW);
        float[] deep = unpack(LIGHTNING_DEEP);

        // ===== 主电柱节点：自落地点向上蜿蜒（底部两段保持接地不偏移，使落点精确居中）=====
        int segs = LIGHTNING_SEGMENTS;
        double[] nx = new double[segs + 1];
        double[] ny = new double[segs + 1];
        double[] nz = new double[segs + 1];
        long s = seed;
        double walkX = 0, walkZ = 0;
        for (int i = 0; i <= segs; i++) {
            double frac = (double) i / segs;
            if (i >= 2) {
                s = rngNext(s);
                walkX += rngUnit(s) * LIGHTNING_WANDER;
                s = rngNext(s);
                walkZ += rngUnit(s) * LIGHTNING_WANDER;
                walkX = clampAbs(walkX, LIGHTNING_MAX_OFFSET);
                walkZ = clampAbs(walkZ, LIGHTNING_MAX_OFFSET);
            }
            nx[i] = cx + walkX;
            ny[i] = cy + LIGHTNING_HEIGHT * frac;
            nz[i] = cz + walkZ;
        }

        // ===== 主干：三层叠绘（外层浓红光晕 → 中层亮红主体 → 白热炽核），顶端略淡没入天空 =====
        for (int i = 0; i < segs; i++) {
            float aTop = 1f - 0.40f * (float) i / segs;
            lightningSegment(b, m, nx[i], ny[i], nz[i], nx[i + 1], ny[i + 1], nz[i + 1],
                    LIGHTNING_HALO_HALF, deep, 0.30f * intensity, 0.30f * intensity * aTop);
        }
        for (int i = 0; i < segs; i++) {
            float aTop = 1f - 0.35f * (float) i / segs;
            lightningSegment(b, m, nx[i], ny[i], nz[i], nx[i + 1], ny[i + 1], nz[i + 1],
                    LIGHTNING_GLOW_HALF, glow, 0.72f * intensity, 0.72f * intensity * aTop);
        }
        for (int i = 0; i < segs; i++) {
            float aTop = 1f - 0.28f * (float) i / segs;
            lightningSegment(b, m, nx[i], ny[i], nz[i], nx[i + 1], ny[i + 1], nz[i + 1],
                    LIGHTNING_CORE_HALF, core, 1.0f * intensity, 1.0f * intensity * aTop);
        }

        // ===== 分叉：在中上部若干节点分出蜿蜒短支（逐段变细、淡出、收尖）=====
        for (int k = 0; k < LIGHTNING_BRANCHES; k++) {
            s = rngNext(s);
            int anchor = 4 + (int) (rngFloat01(s) * (segs - 6));
            if (anchor < 1 || anchor >= segs) {
                continue;
            }
            s = rngNext(s);
            drawLightningBranch(b, m, nx[anchor], ny[anchor], nz[anchor], s, intensity, core, glow);
        }

        // ===== 落地冲击：红色扩张环 + 适度地面强闪 + 白热落地核（尺寸收敛，更贴近原作）=====
        // 主冲击环（随进度向外扩张，幅度收敛）
        double rr = radius * (0.40 + 0.55 * easeOutCubic(clamp01(p / 0.70f)));
        glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr), glow, 0.85f * intensity, 0.5f * intensity, 0.12, 0.70);
        glowRing(b, m, cx, cz, cy, rr * 0.70, segmentsFor(rr), deep, 0.6f * intensity, 0.32f * intensity, 0.09, 0.50);
        // 第二道追赶环（错相扩张，连续冲击感，幅度收敛）
        float w2 = clamp01((p - 0.10f) / 0.55f);
        if (w2 > 0f && w2 < 1f) {
            double rr2 = radius * 1.05 * easeOutCubic(w2);
            glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2), glow,
                    0.45f * (1f - w2) * intensity, 0.22f * (1f - w2) * intensity, 0.07, 0.45);
        }
        // 地面血色铺底（持续红晕，范围收敛）
        band(b, m, cx, cz, cy, 0.0, radius * 0.85, segmentsFor(radius),
                deep[0], deep[1], deep[2], 0.0f, 0.16f * intensity);
        // 开场白热强闪（落地瞬间炸亮，范围收敛）
        if (p < 0.22f) {
            float flash = (0.22f - p) / 0.22f;
            band(b, m, cx, cz, cy, 0.0, radius * 0.95, segmentsFor(radius),
                    core[0], core[1], core[2], 0.50f * flash, 0.0f);
            glowRing(b, m, cx, cz, cy, radius * 0.70, segmentsFor(radius), core,
                    0.65f * flash, 0.35f * flash, 0.14, 0.55);
        }
        // 落地白热核（随强度明灭的中心亮点）
        spark(b, m, cx, cz, cy, (float) (radius * 0.16 + 0.22), core, 1.0f * intensity);
    }

    /**
     * 绘制一条闪电分叉：从锚点出发的几段蜿蜒短支（随机水平方向 + 略偏向下），逐段变细、变暗、末端收尖。
     *
     * @param ax        锚点世界坐标 X
     * @param ay        锚点世界坐标 Y
     * @param az        锚点世界坐标 Z
     * @param seed      分叉随机种子
     * @param intensity 整体强度（继承主干）
     * @param core      核心色
     * @param glow      辉光色
     */
    private static void drawLightningBranch(BufferBuilder b, Matrix4f m,
                                            double ax, double ay, double az,
                                            long seed, float intensity, float[] core, float[] glow) {
        int segs = LIGHTNING_BRANCH_SEGMENTS;
        long s = seed;
        s = rngNext(s);
        double ang = rngFloat01(s) * TAU;        // 随机水平方向
        s = rngNext(s);
        double vy = (rngFloat01(s) - 0.3) * 0.9; // 竖直分量，略偏向下
        double dirX = Math.cos(ang), dirZ = Math.sin(ang);
        double step = LIGHTNING_BRANCH_STEP;

        double px = ax, py = ay, pz = az;
        for (int i = 0; i < segs; i++) {
            s = rngNext(s);
            double jitterX = rngUnit(s) * 0.4;
            s = rngNext(s);
            double jitterZ = rngUnit(s) * 0.4;
            double qx = px + dirX * step + jitterX;
            double qy = py + vy * step;
            double qz = pz + dirZ * step + jitterZ;
            float fade = 1f - (float) i / segs;   // 末端淡出
            float a = intensity * fade;
            lightningSegment(b, m, px, py, pz, qx, qy, qz, LIGHTNING_GLOW_HALF * 0.6, glow, 0.45f * a, 0.0f);
            lightningSegment(b, m, px, py, pz, qx, qy, qz, LIGHTNING_CORE_HALF * 0.7, core, 0.85f * a, 0.1f * a);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    /**
     * 竖直 / 任意朝向的「十字双面」线段：沿世界 X、Z 轴各画一个四边形，使线段从任意水平视角皆可见。
     * <p>用于闪电电柱 / 分叉。两端 alpha 可不同（a1 起点、a2 终点）。双面绘制已开启，缠绕方向无所谓。</p>
     *
     * @param hw 线半宽（格）
     */
    private static void lightningSegment(BufferBuilder b, Matrix4f m,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2,
                                         double hw, float[] col, float a1, float a2) {
        float r = col[0], g = col[1], bb = col[2];
        // 面1：沿世界 X 轴加宽
        quad(b, m,
                x1 - hw, y1, z1, x1 + hw, y1, z1,
                x2 + hw, y2, z2, x2 - hw, y2, z2,
                r, g, bb, a1, a2);
        // 面2：沿世界 Z 轴加宽
        quad(b, m,
                x1, y1, z1 - hw, x1, y1, z1 + hw,
                x2, y2, z2 + hw, x2, y2, z2 - hw,
                r, g, bb, a1, a2);
    }

    /**
     * 画一个四边形（拆成两三角形）：顶点 a→b 用 alpha {@code aAB}，c→d 用 alpha {@code aCD}。
     */
    private static void quad(BufferBuilder b, Matrix4f m,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cxp, double cyp, double czp, double dx, double dy, double dz,
                             float r, float g, float bb, float aAB, float aCD) {
        b.vertex(m, (float) ax, (float) ay, (float) az).color(r, g, bb, aAB).endVertex();
        b.vertex(m, (float) bx, (float) by, (float) bz).color(r, g, bb, aAB).endVertex();
        b.vertex(m, (float) cxp, (float) cyp, (float) czp).color(r, g, bb, aCD).endVertex();

        b.vertex(m, (float) ax, (float) ay, (float) az).color(r, g, bb, aAB).endVertex();
        b.vertex(m, (float) cxp, (float) cyp, (float) czp).color(r, g, bb, aCD).endVertex();
        b.vertex(m, (float) dx, (float) dy, (float) dz).color(r, g, bb, aCD).endVertex();
    }

    /**
     * 闪电强度包络（还原原作龙雷：缓慢庄重明灭 + 慢消散，刻意低频以避免抽搐）。
     * <ul>
     *     <li>主体 body：前 60% 维持满亮，后 40% 平方渐隐——消散慢，能看清整个过程；</li>
     *     <li>缓慢大明灭 pulse：整段约 2 次温和的明暗起伏（0.72~1.0），庄重而不闪烁抽搐。</li>
     * </ul>
     * <b>刻意不叠高频抖动</b>——上一版的高频 jitter 是「抽搐」的元凶，已移除。明灭节奏由 pulse 的
     * 频率 {@code p * 6.3} 决定（一个完整 sin 周期取绝对值 ≈ 2 次明灭，很慢）；要更慢就调小该系数。
     * seed 仅用于让不同闪电的明灭相位错开。
     *
     * @param p    归一化进度
     * @param seed 闪电种子（错开明灭相位）
     * @return 强度系数（0~1）
     */
    private static float lightningIntensity(float p, long seed) {
        if (p >= 1f) {
            return 0f;
        }
        float body;
        if (p < 0.60f) {
            body = 1f;
        } else {
            float t = (p - 0.60f) / 0.40f;
            body = 1f - t * t;
        }
        float pulse = 0.72f + 0.28f * (float) Math.abs(Math.sin(p * 6.3 + (seed & 0x7)));
        return body * pulse;
    }

    // ============================== 几何基元（水平面） ==============================

    /**
     * 发光圆环：外辉 + 内辉 + 核心亮带，三层叠出柔和光环。
     */
    private static void glowRing(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                 double radius, int segs, float[] col,
                                 float coreA, float glowA, double coreHalf, double glowSpread) {
        if (radius < 0.05) {
            return;
        }
        float r = col[0], g = col[1], bb = col[2];
        band(b, m, cx, cz, y, radius + coreHalf, radius + coreHalf + glowSpread, segs, r, g, bb, glowA, 0f);
        band(b, m, cx, cz, y, Math.max(0.0, radius - coreHalf - glowSpread), radius - coreHalf, segs,
                r, g, bb, 0f, glowA);
        band(b, m, cx, cz, y, Math.max(0.0, radius - coreHalf), radius + coreHalf, segs, r, g, bb, coreA, coreA);
    }

    /**
     * 圆环带（annulus），内/外边缘可分别指定 alpha；{@code rInner=0} 时退化为渐变圆盘。
     */
    private static void band(BufferBuilder builder, Matrix4f m, double cx, double cz, double cy,
                             double rInner, double rOuter, int segments,
                             float r, float g, float b, float alphaInner, float alphaOuter) {
        float y = (float) cy;
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2 * i) / segments;
            double a1 = (Math.PI * 2 * (i + 1)) / segments;
            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

            float ox0 = (float) (cx + rOuter * cos0), oz0 = (float) (cz + rOuter * sin0);
            float ox1 = (float) (cx + rOuter * cos1), oz1 = (float) (cz + rOuter * sin1);
            float ix0 = (float) (cx + rInner * cos0), iz0 = (float) (cz + rInner * sin0);
            float ix1 = (float) (cx + rInner * cos1), iz1 = (float) (cz + rInner * sin1);

            builder.vertex(m, ox0, y, oz0).color(r, g, b, alphaOuter).endVertex();
            builder.vertex(m, ox1, y, oz1).color(r, g, b, alphaOuter).endVertex();
            builder.vertex(m, ix1, y, iz1).color(r, g, b, alphaInner).endVertex();

            builder.vertex(m, ox0, y, oz0).color(r, g, b, alphaOuter).endVertex();
            builder.vertex(m, ix1, y, iz1).color(r, g, b, alphaInner).endVertex();
            builder.vertex(m, ix0, y, iz0).color(r, g, b, alphaInner).endVertex();
        }
    }

    /**
     * 带宽度的线段（两点之间的细长四边形，两端 alpha 可不同）。水平面（y 固定）。
     */
    private static void line(BufferBuilder builder, Matrix4f m,
                             double x1, double z1, double x2, double z2, double y,
                             double hw, float r, float g, float b, float a1, float a2) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        double nx = -dz / len * hw;
        double nz = dx / len * hw;
        float yf = (float) y;
        float ax1 = (float) (x1 + nx), az1 = (float) (z1 + nz);
        float ax2 = (float) (x1 - nx), az2 = (float) (z1 - nz);
        float bx1 = (float) (x2 + nx), bz1 = (float) (z2 + nz);
        float bx2 = (float) (x2 - nx), bz2 = (float) (z2 - nz);

        builder.vertex(m, ax1, yf, az1).color(r, g, b, a1).endVertex();
        builder.vertex(m, bx1, yf, bz1).color(r, g, b, a2).endVertex();
        builder.vertex(m, bx2, yf, bz2).color(r, g, b, a2).endVertex();

        builder.vertex(m, ax1, yf, az1).color(r, g, b, a1).endVertex();
        builder.vertex(m, bx2, yf, bz2).color(r, g, b, a2).endVertex();
        builder.vertex(m, ax2, yf, az2).color(r, g, b, a1).endVertex();
    }

    /**
     * 小菱形光点（火花），中心最亮、四角渐隐。水平面。
     */
    private static void spark(BufferBuilder builder, Matrix4f m, double px, double pz, double y,
                              float size, float[] col, float alpha) {
        float r = col[0], g = col[1], b = col[2];
        float yf = (float) y;
        float cxF = (float) px, czF = (float) pz;
        float[][] pts = {{cxF, czF - size}, {cxF + size, czF}, {cxF, czF + size}, {cxF - size, czF}};
        for (int i = 0; i < 4; i++) {
            float[] a = pts[i];
            float[] c = pts[(i + 1) % 4];
            builder.vertex(m, cxF, yf, czF).color(r, g, b, alpha).endVertex();
            builder.vertex(m, a[0], yf, a[1]).color(r, g, b, 0f).endVertex();
            builder.vertex(m, c[0], yf, c[1]).color(r, g, b, 0f).endVertex();
        }
    }

    /**
     * 放射线：count 条由内向外的径向线（尖端 alpha 可渐隐）。水平面。
     */
    private static void rays(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                             double rInner, double rOuter, int count, float rotation, double hw,
                             float[] col, float alphaInner, float alphaOuter) {
        for (int i = 0; i < count; i++) {
            double ang = rotation + (Math.PI * 2 * i) / count;
            double cos = Math.cos(ang), sin = Math.sin(ang);
            double ix = cx + rInner * cos, iz = cz + rInner * sin;
            double ox = cx + rOuter * cos, oz = cz + rOuter * sin;
            line(b, m, ix, iz, ox, oz, y, hw, col[0], col[1], col[2], alphaInner, alphaOuter);
        }
    }

    /**
     * 正多边形外框（N 条边首尾相连）。水平面。
     */
    private static void polygonRing(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                    double radius, int sides, float rotation, double hw,
                                    float[] col, float alpha) {
        double prevX = 0, prevZ = 0;
        for (int i = 0; i <= sides; i++) {
            double ang = rotation + (Math.PI * 2 * i) / sides;
            double x = cx + radius * Math.cos(ang);
            double z = cz + radius * Math.sin(ang);
            if (i > 0) {
                line(b, m, prevX, prevZ, x, z, y, hw, col[0], col[1], col[2], alpha, alpha);
            }
            prevX = x;
            prevZ = z;
        }
    }

    /**
     * 星形多边形：把 points 个顶点按步距 step 互连（如 points=6,step=2 即六芒星两叠三角）。水平面。
     */
    private static void starPolygon(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                    double radius, int points, int step, float rotation, double hw,
                                    float[] col, float alpha) {
        for (int i = 0; i < points; i++) {
            double a1 = rotation + (Math.PI * 2 * i) / points;
            double a2 = rotation + (Math.PI * 2 * ((i + step) % points)) / points;
            double x1 = cx + radius * Math.cos(a1), z1 = cz + radius * Math.sin(a1);
            double x2 = cx + radius * Math.cos(a2), z2 = cz + radius * Math.sin(a2);
            line(b, m, x1, z1, x2, z2, y, hw, col[0], col[1], col[2], alpha, alpha);
        }
    }

    /**
     * 旋转符文刻度环：沿圆周均布的 count 个短径向小段（内端略暗、外端亮）。水平面。
     */
    private static void tickRing(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                 double rStart, double length, int count, float rotation, double hw,
                                 float[] col, float alpha) {
        double rEnd = rStart + length;
        for (int k = 0; k < count; k++) {
            double ang = rotation + (Math.PI * 2 * k) / count;
            double cos = Math.cos(ang), sin = Math.sin(ang);
            double ix = cx + rStart * cos, iz = cz + rStart * sin;
            double ox = cx + rEnd * cos, oz = cz + rEnd * sin;
            line(b, m, ix, iz, ox, oz, y, hw, col[0], col[1], col[2], alpha * 0.5f, alpha);
        }
    }

    /**
     * 闪烁星屑场：count 个确定性分布的小光点，各自正弦闪烁。水平面。
     */
    private static void starField(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                  double radius, int count, float time, float size,
                                  float[] col, float baseAlpha) {
        for (int i = 0; i < count; i++) {
            double ang = i * 2.399963;
            double fr = (i * 0.6180339) - Math.floor(i * 0.6180339);
            double rr = radius * (0.15 + 0.82 * fr);
            double px = cx + rr * Math.cos(ang);
            double pz = cz + rr * Math.sin(ang);
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 6.0 + i * 1.7));
            spark(b, m, px, pz, y, size, col, baseAlpha * tw);
        }
    }

    // ============================== 数学 / 颜色辅助 ==============================

    /** 角度转弧度。 */
    private static float deg2rad(double deg) {
        return (float) (deg * Math.PI / 180.0);
    }

    /** 线性插值（t 自动夹取到 0~1）。 */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    /** 线半宽随半径缩放（最小 0.04 格）。 */
    private static double lineHalf(double radius) {
        return Math.max(0.04, radius * 0.012);
    }

    /** 环分段数（随半径，夹取 36~72）。 */
    private static int segmentsFor(double radius) {
        int v = (int) (radius * 3);
        if (v < 36) {
            return 36;
        }
        return Math.min(v, 72);
    }

    /** 淡入淡出包络：progress 在 [0,inEnd] 线性淡入、在 [outStart,1] 平滑淡出。 */
    private static float fadeInOut(float p, float inEnd, float outStart) {
        float fi = clamp01(p / inEnd);
        float fo = 1f - smoothstep(outStart, 1f, p);
        return fi * fo;
    }

    /** 平滑阶跃（Hermite）。 */
    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    /** 缓出（cubic）。 */
    private static double easeOutCubic(double t) {
        double inv = 1.0 - t;
        return 1.0 - inv * inv * inv;
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

    /**
     * 两个 [r,g,b] 颜色按 t 线性插值（t∈[0,1]，0 取 a、1 取 b）。
     */
    private static float[] mix(float[] a, float[] b, float t) {
        float u = clamp01(t);
        return new float[]{
                a[0] + (b[0] - a[0]) * u,
                a[1] + (b[1] - a[1]) * u,
                a[2] + (b[2] - a[2]) * u
        };
    }

    /** 0xRRGGBB 拆为 [r,g,b]（0~1）。 */
    private static float[] unpack(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f
        };
    }

    // ============================== 闪电用无分配伪随机（xorshift64） ==============================

    /**
     * xorshift64 推进一步。
     *
     * @param s 当前状态（非 0）
     * @return 下一状态
     */
    private static long rngNext(long s) {
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        return s;
    }

    /**
     * 由状态取 [0,1) 浮点。
     *
     * @param s 已推进的状态
     * @return [0,1)
     */
    private static float rngFloat01(long s) {
        return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    /**
     * 由状态取 [-1,1) 浮点。
     *
     * @param s 已推进的状态
     * @return [-1,1)
     */
    private static double rngUnit(long s) {
        return rngFloat01(s) * 2.0 - 1.0;
    }

    /**
     * 绝对值夹取到 ±max。
     *
     * @param v   输入
     * @param max 上限（正）
     * @return 夹取结果
     */
    private static double clampAbs(double v, double max) {
        if (v > max) {
            return max;
        }
        if (v < -max) {
            return -max;
        }
        return v;
    }
}
