package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
 * 与光环 {@code AuraGroundRenderer} 采用同款管线：订阅 {@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，用 {@link Tesselator} + {@link DefaultVertexFormat#POSITION_COLOR}
 * + {@code GameRenderer::getPositionColorShader} 纯顶点绘制——无贴图、无原版粒子。
 * </p>
 * <p>
 * 多数演出为<b>水平地面法阵</b>；而<b>猩红罗妮亚 {@link #drawScarletBloom}</b> 为还原玛莲妮亚的
 * 「猩红艾奥尼亚」开花，额外绘制一朵<b>竖直 3D 立体绽放花</b>（{@link #drawAeoniaFlower}）：
 * 四层曼陀罗/兰花式层叠花瓣 + 花蕊 + 花心白热球，花瓣为真 3D 几何（沿向上弯曲脊线展开的曲面带，
 * 纯顶点色靠「根深尖亮」模拟体积），随进度从花苞聚拢 → 缓慢绽放（覆盖约 1.5 秒拉取无敌前摇）→
 * 猛地盛放 → 爆发喷腐败 → 外翻凋谢下沉。地面法阵保留作花根光环。其余演出（含癫火 {@link #drawFrenziedFlame}）
 * 保持平面。
 * </p>
 * <p>
 * <b>注意：</b>特效起播时刻取决于附魔实现类中调用
 * {@code CarianStyleBurstParticles.scarletBloom(...)} 的位置，不在本渲染器内。
 * </p>
 * <p>
 * <b>空缓冲兜底：</b>{@link #emitDegenerateTriangle} 在 {@code begin()} 后无条件追加一个零面积、
 * 全透明退化三角形，保证 {@code begin/end} 间永不空（避免 progress≈0 时整批零顶点在 Mohist 等
 * 环境下打断世界渲染）。
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

    private AoeEffectRenderer() {
    }

    /**
     * 渲染回调：遍历全部存活特效，按类型分发到对应自绘演出。
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

        Vec3 cam = event.getCamera().getPosition();
        long now = System.currentTimeMillis();
        double cullSqr = CULL * CULL;

        // 性能早退：本帧若不存在 progress∈(0,1) 且在裁剪范围内的特效，直接跳过 GL 状态设置与 begin/end。
        // 注意：anyVisible=true 并不保证下方循环一定产生顶点；空缓冲兜底由退化三角形负责（见下）。
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

        Matrix4f matrix = event.getPoseStack().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        // GL 状态：普通 alpha 混合、关闭深度写入、保留深度测试、双面绘制（与光环一致）。
        // 立体花同样在此批内绘制：关闭深度写入避免花瓣彼此遮挡产生硬边，双面绘制让花瓣正反皆可见。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        // 空缓冲兜底：无条件先追加一个零面积、全透明退化三角形（详见类注释与该方法注释）。
        emitDegenerateTriangle(builder, matrix);
        // 跟随特效需要实体的插值实时位置；partialTick 用于平滑（与世界渲染同步）
        float partial = event.getPartialTick();
        for (AoeEffectManager.AoeEffect fx : list) {
            // 解析特效中心：跟随实体（entityId>=0）则取实体插值实时位置并回写缓存坐标；
            // 实体不存在 / 已死则回退到缓存坐标（最后已知位置，死亡演出尾段残留原地）；
            // 定点特效（entityId=-1）直接用缓存坐标（恒为发包坐标）
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
                    // 回写缓存，供实体随后死亡 / 移除时继续在原地播放剩余演出
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
            // 进度改用 manager 的分段映射：死亡演出蓄能段恒对齐 30tick 爆发、加长只拉长凋谢余波；
            // 其余类型为线性。progressFor 已夹取到 [0,1]，无需再 clamp。
            float progress = AoeEffectManager.progressFor(fx, now);
            double rx = fxX - cam.x;
            double ry = fxY - cam.y + Y_OFFSET;
            double rz = fxZ - cam.z;
            dispatch(builder, matrix, fx.type, rx, ry, rz, fx.radius, progress);
        }
        BufferUploader.drawWithShader(builder.end());

        // 恢复 GL 状态
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 追加一个零面积、全透明的退化三角形（三个顶点重合于相机原点、alpha=0），保证缓冲永不空。
     *
     * @param b 顶点构建器
     * @param m 位姿矩阵
     */
    private static void emitDegenerateTriangle(BufferBuilder b, Matrix4f m) {
        for (int i = 0; i < 3; i++) {
            b.vertex(m, 0f, 0f, 0f).color(0f, 0f, 0f, 0f).endVertex();
        }
    }

    /**
     * 按类型分发到具体演出。
     */
    private static void dispatch(BufferBuilder b, Matrix4f m, int type,
                                 double cx, double cy, double cz, double radius, float p) {
        switch (type) {
            case AoeEffectPacket.TYPE_CAUSALITY -> drawCausality(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_FROST_QUAKE -> drawFrostQuake(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_REPULSION -> drawRepulsion(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_SCARLET_BLOOM -> drawScarletBloom(b, m, cx, cy, cz, radius, p);
            case AoeEffectPacket.TYPE_FRENZIED_FLAME -> drawFrenziedFlame(b, m, cx, cy, cz, radius, p);
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
     * 猩红艾奥尼亚（还原玛莲妮亚开花，含竖直 3D 立体绽放花）：
     * <ol>
     *     <li><b>地面法阵底座</b>(0~0.56)：血雾底盘 + 六边结界框 + 双向旋转符文环 + 腐败涟漪 + 星屑，
     *         作花根光环（较弱，不抢花的戏）；</li>
     *     <li><b>立体绽放花</b>(全程)：{@link #drawAeoniaFlower} 四层曼陀罗花瓣 + 花蕊 + 花心球，
     *         随 {@code open} 从花苞 → 缓慢绽放（覆盖约 1.5s 拉取无敌蓄能前摇）→ 盛放 → 外翻凋谢下沉；
     *         盛放主点 p≈0.42 正好对应附魔 30tick(1.5s) 的第二阶段爆发瞬间；</li>
     *     <li><b>盛放白热</b>(0.40~0.52)：花心白热球爆亮；</li>
     *     <li><b>爆发腐败</b>(0.42~0.74)：从花根喷出的多重地面炸裂环 + 放射爆纹 + 地面强闪；</li>
     *     <li><b>凋谢余波</b>(0.65~1.0)：地面血晕消退 + 余波细环 + 星屑下沉。</li>
     * </ol>
     */
    private static void drawScarletBloom(BufferBuilder b, Matrix4f m,
                                         double cx, double cy, double cz, double radius, float p) {
        float[] red = unpack(SCARLET);
        float[] deep = unpack(SCARLET_DEEP);
        float[] hot = unpack(SCARLET_HOT);
        double hw = lineHalf(radius);
        // 开场即满：前 0.015（约 60ms）内快速拉满，避免首帧硬跳，本质是"瞬间出现"
        float appear = clamp01(p / 0.015f);
        float rot = p * 0.35f * TAU; // 花与法阵缓慢同向自转

        // —— 绽放/拔起/凋谢参数 ——
        // open：0=花苞聚拢竖直，1=全开，>1=外翻下垂（凋谢）
        // 盛放主点 p≈0.42（3600ms 时长下约 1512ms），对齐附魔 30tick 第二阶段爆发
        float open;
        if (p < 0.40f) {
            open = lerp(0.08f, 0.72f, (float) easeOutCubic(p / 0.40f)); // 缓慢绽放（1.5s 蓄能期）
        } else if (p < 0.44f) {
            open = lerp(0.72f, 1.0f, (p - 0.40f) / 0.04f);              // 猛地盛放
        } else {
            open = 1.0f + 0.6f * smoothstep(0.44f, 1.0f, p);            // 持续外翻下垂
        }
        float riseH = clamp01(p / 0.04f);                              // 拔起
        if (p > 0.62f) {
            riseH *= (1f - 0.55f * smoothstep(0.62f, 1f, p));          // 凋谢下沉
        }
        float flowerAlpha = appear * (1f - smoothstep(0.65f, 1.0f, p));

        // ===== A. 地面法阵底座（0~0.62，减弱版，作花根光环）=====
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

        // ===== B. 立体绽放花（主角，全程）=====
        drawAeoniaFlower(b, m, cx, cy, cz, radius, open, riseH, flowerAlpha, rot);

        // ===== C. 盛放白热（0.40~0.52）：花心白热球爆亮 =====
        if (p >= 0.40f && p < 0.52f) {
            float kf = clamp01((p - 0.40f) / 0.04f) * (1f - smoothstep(0.46f, 0.52f, p));
            double coreH = radius * 0.95 * riseH * 0.16;
            drawOrb(b, m, cx, cy + coreH, cz, radius * (0.10 + 0.18 * kf), mix(red, hot, 0.7f), 0.9f * appear);
        }

        // ===== D. 爆发腐败（0.42~0.74）：从花根喷出的地面冲击 =====
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

        // ===== E. 凋谢余波（0.65~1.0）=====
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
     * 绘制整朵猩红艾奥尼亚之花：四层曼陀罗式层叠花瓣（外→内逐层更立、更短、更亮）+ 花蕊小瓣 + 花心白热球。
     * <p>花瓣为真 3D 几何（{@link #drawPetal}），随 {@code open} 绽放：层间方位错开（内层插在外层花瓣
     * 之间，模拟真花排布），整体随 {@code rot} 缓慢自转、{@code riseH} 控制高度（拔起/下沉）、
     * {@code flowerAlpha} 控制整朵透明度（凋谢淡出）。</p>
     *
     * @param radius      特效半径（决定花的尺度）
     * @param open        绽放程度（0 花苞 → 1 全开 → >1 外翻下垂）
     * @param riseH       高度系数（0~1，拔起与凋谢下沉）
     * @param flowerAlpha 整朵透明度系数
     * @param rot         整体自转角（弧度）
     */
    private static void drawAeoniaFlower(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                         double radius, float open, float riseH, float flowerAlpha, float rot) {
        if (flowerAlpha <= 0.01f || riseH <= 0.01f) {
            return;
        }
        float[] deep = unpack(SCARLET_DEEP);
        float[] red = unpack(SCARLET);
        float[] hot = unpack(SCARLET_HOT);
        double l = radius * 0.95 * riseH; // 花的高度量级

        // 四层花瓣：外→内（瓣数递减、更短、更立、更亮；方位逐层错开，内层插空）
        drawPetalLayer(b, m, cx, cy, cz, 8, l * 1.00, radius * 0.24, deg2rad(82), open, rot,
                deep, red, red, flowerAlpha);
        drawPetalLayer(b, m, cx, cy, cz, 7, l * 0.78, radius * 0.21, deg2rad(64), open, rot + 0.34f,
                deep, red, hot, flowerAlpha * 0.96f);
        drawPetalLayer(b, m, cx, cy, cz, 6, l * 0.56, radius * 0.18, deg2rad(48), open, rot + 0.66f,
                red, red, hot, flowerAlpha * 0.92f);
        drawPetalLayer(b, m, cx, cy, cz, 5, l * 0.38, radius * 0.14, deg2rad(34), open, rot + 0.95f,
                red, hot, hot, flowerAlpha * 0.90f);
        // 花蕊：一簇极短、近竖直的亮瓣（开合只取一半，使其始终较为聚拢）
        drawPetalLayer(b, m, cx, cy, cz, 6, l * 0.24, radius * 0.08, deg2rad(20), open * 0.5f, rot + 0.15f,
                red, hot, hot, flowerAlpha);
        // 花心白热球（盛放阶段另有更亮的爆亮球叠加，见 drawScarletBloom 的 C 段）
        drawOrb(b, m, cx, cy + l * 0.12, cz, radius * 0.06, mix(red, hot, 0.5f), 0.7f * flowerAlpha);
    }

    /**
     * 绘制一层花瓣：{@code petals} 片均布，方位以 {@code baseRot} 为起点。
     * <p>花瓣根部与 Y+ 的夹角随 {@code open} 从花苞角（约 12°）插值到该层全开角 {@code fullAngle}
     * （{@code open>1} 时自然超出 → 外翻下垂）；花瓣自身弧度（尖端外卷）也随 open 增强。</p>
     *
     * @param petals    花瓣数
     * @param length    花瓣长度（沿脊线弧长）
     * @param maxWidth  花瓣最大宽度
     * @param fullAngle 全开时花瓣根部与 Y+ 的夹角（弧度）
     * @param open      绽放程度
     * @param baseRot   本层方位起始角（弧度，用于层间错开）
     * @param deep      根部色
     * @param mid       中部色
     * @param tip       尖端色
     * @param alphaMul  整层透明度系数
     */
    private static void drawPetalLayer(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                       int petals, double length, double maxWidth,
                                       float fullAngle, float open, float baseRot,
                                       float[] deep, float[] mid, float[] tip, float alphaMul) {
        float budAngle = deg2rad(12);
        float baseAngle = budAngle + open * (fullAngle - budAngle);
        float curlAngle = deg2rad(12) + deg2rad(28) * open; // 尖端外卷，越开越卷
        for (int i = 0; i < petals; i++) {
            float az = baseRot + TAU * i / petals;
            drawPetal(b, m, cx, cy, cz, az, baseAngle, curlAngle, length, maxWidth, deep, mid, tip, alphaMul);
        }
    }

    /**
     * 绘制一片 3D 花瓣：沿一条「向上弯曲」的脊线积分采样，左右按宽度轮廓展开成三角形带曲面。
     * <p>脊线在参数 u∈[0,1]（根→尖）处与 Y+ 的夹角为 {@code baseAngle + curlAngle·u}：
     * 夹角 0 为竖直向上、90° 为水平外伸；沿脊线以固定步长积分得到每个截面的水平/竖直分量，
     * 再绕方位角 {@code azimuth} 展开到世界 xz 平面。宽度方向取水平面内垂直于方位角的方向。
     * 颜色沿 u 由根部 {@code deep} → 中部 {@code mid} → 尖端 {@code tip} 渐变，alpha 呈中段最实的
     * 驼峰，从而在无光照的纯顶点色下模拟花瓣的体积与发光感。</p>
     *
     * @param azimuth   花瓣水平朝向角（弧度）
     * @param baseAngle 根部脊线与 Y+ 的夹角（弧度）
     * @param curlAngle 根→尖额外增加的夹角（弧度，正值使尖端外卷）
     * @param length    花瓣长度（脊线弧长）
     * @param maxWidth  花瓣最大宽度
     */
    private static void drawPetal(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                  float azimuth, float baseAngle, float curlAngle,
                                  double length, double maxWidth,
                                  float[] deep, float[] mid, float[] tip, float alphaMul) {
        final int seg = 8;
        double cosA = Math.cos(azimuth), sinA = Math.sin(azimuth);
        // 宽度方向（水平，垂直于方位角）
        double wx = -sinA, wz = cosA;

        // 沿脊线积分得到每个截面的水平/竖直分量
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

            // 两个三角形构成本段曲面（双面绘制已开启，正反皆可见）
            b.vertex(m, l0x, l0y, l0z).color(c0[0], c0[1], c0[2], a0).endVertex();
            b.vertex(m, r0x, r0y, r0z).color(c0[0], c0[1], c0[2], a0).endVertex();
            b.vertex(m, r1x, r1y, r1z).color(c1[0], c1[1], c1[2], a1).endVertex();

            b.vertex(m, l0x, l0y, l0z).color(c0[0], c0[1], c0[2], a0).endVertex();
            b.vertex(m, r1x, r1y, r1z).color(c1[0], c1[1], c1[2], a1).endVertex();
            b.vertex(m, l1x, l1y, l1z).color(c1[0], c1[1], c1[2], a1).endVertex();
        }
    }

    /**
     * 绘制一个小亮球（上下两个四棱锥拼成的八面体近似，中心轴点最亮、赤道点略暗），用作花心白热核。
     *
     * @param size 球半尺寸（格）
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
            // 上半锥面
            b.vertex(m, top[0], top[1], top[2]).color(r, g, bb, alpha).endVertex();
            b.vertex(m, a[0], a[1], a[2]).color(r, g, bb, alpha * 0.55f).endVertex();
            b.vertex(m, d[0], d[1], d[2]).color(r, g, bb, alpha * 0.55f).endVertex();
            // 下半锥面
            b.vertex(m, bot[0], bot[1], bot[2]).color(r, g, bb, alpha).endVertex();
            b.vertex(m, d[0], d[1], d[2]).color(r, g, bb, alpha * 0.55f).endVertex();
            b.vertex(m, a[0], a[1], a[2]).color(r, g, bb, alpha * 0.55f).endVertex();
        }
    }

    /**
     * 花瓣宽度轮廓（u∈[0,1]，根 0 → 中部最宽 → 尖 0），用 {@code sin(π·u^0.65)} 得到根窄、中宽、尖收的叶形。
     *
     * @param u 沿花瓣长度的归一化参数
     * @return 宽度系数（0~1）
     */
    private static double petalWidth(double u) {
        return Math.sin(Math.PI * Math.pow(u, 0.65));
    }

    /**
     * 花瓣颜色：根部 {@code deep} → 中部 {@code mid} → 尖端偏向 {@code tip}，模拟根深尖亮的体积感。
     *
     * @param u 沿花瓣长度的归一化参数
     * @return [r,g,b]
     */
    private static float[] petalColor(double u, float[] deep, float[] mid, float[] tip) {
        if (u < 0.5) {
            return mix(deep, mid, (float) (u / 0.5));
        }
        return mix(mid, tip, (float) ((u - 0.5) / 0.5) * 0.7f);
    }

    /**
     * 花瓣 alpha：中段最实的驼峰（根尖略透发光），用 {@code 0.5 + 0.45·sin(π·u)}。
     *
     * @param u 沿花瓣长度的归一化参数
     * @return alpha 系数
     */
    private static float petalAlpha(double u) {
        return 0.5f + 0.45f * (float) Math.sin(Math.PI * u);
    }

    /**
     * 癫火蔓延（大型多段平面演出，开场即满状态）：狂乱蓄能（满裂纹网+三层乱星+火星场）→ 顶点压缩
     * → 双冲击环爆发 + 强闪 → 焦黑余烬。全程确定性正弦噪声抖动，不依赖随机数。
     */
    private static void drawFrenziedFlame(BufferBuilder b, Matrix4f m,
                                          double cx, double cy, double cz, double radius, float p) {
        float[] yellow = unpack(FRENZY_YELLOW);
        float[] orange = unpack(FRENZY_ORANGE);
        float[] hot = unpack(FRENZY_WHITE);
        double hw = lineHalf(radius);
        float appear = clamp01(p / 0.015f);

        // ===== A. 狂乱蓄能期（0~0.46，覆盖 1.5s 蓄能前摇）=====
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

        // ===== B. 顶点压缩（0.38~0.46）=====
        if (p >= 0.38f && p < 0.46f) {
            float kf = clamp01((p - 0.38f) / 0.06f);
            float coreSize = (float) (radius * (0.12 + 0.28 * easeOutCubic(kf)));
            spark(b, m, cx, cz, cy, coreSize, mix(yellow, hot, kf), 0.95f * appear);
            rays(b, m, cx, cz, cy, radius * (1.0 - 0.6 * kf), radius * 0.2, 14, p * 2.5f, hw, hot, 0.1f, 0.6f * kf * appear);
        }

        // ===== C. 大爆发冲击（0.42~0.70）：盛放主点 p≈0.42 对齐机制 30tick 爆发 =====
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

        // ===== D. 余烬（0.65~1.0）=====
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

    // ============================== 几何基元（水平面） ==============================

    /**
     * 发光圆环：外辉（向外渐隐）+ 内辉（向内渐隐）+ 核心亮带，三层叠出柔和光环。
     *
     * @param radius     环半径
     * @param coreA      核心 alpha
     * @param glowA      辉光峰值 alpha
     * @param coreHalf   核心半宽（格）
     * @param glowSpread 辉光向内外扩散宽度（格）
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
     * 圆环带（annulus），内/外边缘可分别指定 alpha；{@code rInner=0} 时退化为从中心到外缘的渐变圆盘。
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
     *
     * @param hw 线半宽（格）
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
     *
     * @param size 半尺寸（格）
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
     *
     * @param rStart   刻度内端半径
     * @param length   刻度长度（格）
     * @param count    刻度数量
     * @param rotation 旋转角（弧度）
     * @param hw       刻度线半宽
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
     * 闪烁星屑场：count 个确定性分布（黄金角铺角度、黄金比小数铺半径）的小光点，各自正弦闪烁。水平面。
     *
     * @param radius    分布最大半径（格）
     * @param count     星点数量
     * @param time      驱动闪烁的时间量（一般传 progress）
     * @param size      星点半尺寸（格）
     * @param baseAlpha 基础亮度（再乘以各自闪烁系数）
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
     * <p>用于濒死类低频特效的颜色过渡，每帧调用量有限，分配可忽略。
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
}
