package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.HemorrhageSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 出血「飙血」客户端渲染器（纯客户端自绘）。
 * <p>
 * <b>判定采用双重冗余：</b>{@code entity.hasEffect(CarianStylePotion.HEMORRHAGE.get())}
 * （覆盖玩家自己、以及在观察者开始追踪前就已带出血的实体）<b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(HemorrhageSyncHandler.HEMORRHAGE_SERIAL, id)}
 * （覆盖战斗中途才被施加出血的怪物——原版不会把这次新增效果同步给已经在追踪该实体的观察者，
 * 见 {@link HemorrhageSyncHandler} 类注释。<b>这是此前「完全没有特效」的根因</b>：正在交战的目标
 * 几乎总是已被观察者追踪，导致 {@code hasEffect} 恒为 false，特效从未触发过，而不是强度不够）。
 * </p>
 * 视觉分两层：
 * <ol>
 *     <li><b>常驻氛围</b>——脚下血泊（两层渐变圆盘）、从躯干循环垂落的粗血线、
 *         多颗沿抛物线飞出的柔光血滴；</li>
 *     <li><b>心跳式迸溅爆发</b>——{@link #drawBurstRays}，按 {@link #PULSE_PERIOD} 周期性触发，
 *         在胸口炸开一圈放射状血线（内亮外暗，模拟血液甩出后迅速氧化变暗）+ 脚下同步的冲击
 *         闪光，让心跳喷发的瞬间有明确的「打击感」，而不只是持续飘落的血滴；</li>
 *     <li><b>血雾</b>（{@link #drawBloodMist}）——伤口附近喷出的红色雾团（原理与冻伤的冰雾
 *         相同，见 {@code FrostbiteMistRenderer#drawFrostFog}），配合血滴 / 射线一起构成
 *         「真正在喷血」的体积感，而不是零散血点各自往外飞。</li>
 * </ol>
 * </p>
 * <p>
 * <b>v3（修复同步后再加码）：</b>补上 {@link HemorrhageSyncHandler} 之后特效终于能正常触发，
 * 但反馈仍然「没有飙血的感觉」——血滴数量/体积/飞溅距离全面上调（基础血滴 16→26、尺寸
 * 0.11→0.18、初速度提升约 50%），心跳爆发窗口从周期的 24% 延长到 40%、间隔从 1.3 秒缩短到
 * 1 秒（喷得更频繁），迸溅射线更粗更长，再加上全新的血雾层，多管齐下确保观感是「正在喷血」
 * 而不是「偶尔滴几滴」。
 * </p>
 * <p>
 * <b>v4（性能，视觉零变化）：</b>接入 {@link VisualBatch} 与 {@link SharedEntityQuery}——
 * <ul>
 *     <li>不再自行设置 / 恢复 GL 状态、不再自行 {@code begin/end} 顶点缓冲，改为向
 *         {@link VisualBatch} 提供的共享缓冲写顶点，由其在本帧末统一提交（七个渲染器合并为一次
 *         GL 状态切换与一次 draw call）；</li>
 *     <li>不再自行做范围实体查询，改为遍历 {@link SharedEntityQuery} 的每帧共享列表，
 *         把原先的查询判定条件下沉为循环内的 {@code continue}（见 {@link #hasHemorrhage}）。</li>
 * </ul>
 * 判定条件、精确平方距离裁剪、绘制顺序与全部几何参数均未改动。
 * </p>
 * <p>
 * 渲染管线与 {@code ScarletRotMistRenderer} 同款：{@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，{@code POSITION_COLOR} 纯顶点绘制，无贴图、无原版粒子；
 * 顶点格式与着色器现由 {@link VisualBatch} 统一设置。
 * </p>
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class HemorrhageBloodRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int DROP_SEGMENTS = 8;
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== 配色（0xRRGGBB）=====
    private static final int BLOOD_BRIGHT = 0xE0202F;
    private static final int BLOOD_HOT = 0xFF6070;
    private static final int BLOOD_FLASH = 0xFF8090;
    private static final int BLOOD_MID = 0x8A0F18;
    private static final int BLOOD_DARK = 0x3A0508;

    // ===== 地面血泊 =====
    private static final int POOL_LAYERS = 2;
    private static final int POOL_SEGMENTS = 20;
    private static final float POOL_RADIUS_BASE = 1.05f;
    private static final float POOL_RADIUS_STEP = 0.5f;
    private static final float POOL_BASE_ALPHA = 0.6f;

    // ===== 喷溅血滴（抛物线飞溅，v3：全面加大）=====
    private static final int BASE_DROPLETS = 26;
    /** 每颗血滴的飞行循环速度（每秒推进的归一化进度） */
    private static final float DROPLET_RATE = 0.6f;
    private static final float DROPLET_SIZE = 0.18f;
    /** 喷溅起点高度系数（× 实体高度） */
    private static final float LAUNCH_HEIGHT_FACTOR = 0.55f;
    private static final float LAUNCH_SPEED_BASE = 1.05f;
    private static final float LAUNCH_VY = 2.4f;
    private static final float GRAVITY = 2.4f;

    // ===== 心跳式迸溅爆发（v3：更猛更频繁）=====
    private static final float PULSE_PERIOD = 1.0f;
    private static final float PULSE_WINDOW = 0.4f;
    private static final int PULSE_EXTRA_DROPLETS = 18;
    private static final float PULSE_SIZE_MULT = 2.4f;
    private static final int PULSE_RAY_COUNT = 14;

    // ===== 血雾（v3 新增：伤口处的喷射雾团，跟冻伤的冰雾同一原理，但更集中更红）=====
    private static final int MIST_COUNT = 7;
    private static final float MIST_SIZE_FACTOR = 0.32f;
    private static final float MIST_BASE_ALPHA = 0.4f;

    // ===== 滴落血线（从躯干垂落）=====
    private static final int DRIP_COUNT = 6;
    private static final float DRIP_RATE = 0.35f;
    private static final float DRIP_HALF_WIDTH = 0.03f;

    private HemorrhageBloodRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有出血生物的飙血视觉。
     * <p>
     * v4：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，实体列表取自
     * {@link SharedEntityQuery} 的每帧共享查询；本方法只负责筛选与写顶点。
     * </p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        // 共享批次未开启（世界未加载等）：直接跳过
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }
        List<LivingEntity> candidates = SharedEntityQuery.livingEntitiesNearCamera(mc, cam);
        if (candidates.isEmpty()) {
            return;
        }

        MobEffect hemorrhage = CarianStylePotion.HEMORRHAGE.get();

        Matrix4f matrix = VisualBatch.matrix();
        float rightX = VisualBatch.rightX();
        float rightY = VisualBatch.rightY();
        float rightZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            // v4：原先作为查询谓词的判定，现下沉为循环内筛选（共享列表已保证 isAlive）
            if (!hasHemorrhage(entity, hemorrhage)) {
                continue;
            }

            double ex = Mth.lerp((double) partial, entity.xo, entity.getX());
            double ey = Mth.lerp((double) partial, entity.yo, entity.getY());
            double ez = Mth.lerp((double) partial, entity.zo, entity.getZ());

            double dx = ex - cam.x;
            double dy = ey - cam.y;
            double dz = ez - cam.z;
            if (dx * dx + dy * dy + dz * dz > CULL_SQR) {
                continue;
            }

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawBloodPool(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId());
            drawDroplets(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    rightX, rightY, rightZ, upX, upY, upZ);
            drawDrips(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId());
            drawBurstRays(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId());
            drawBloodMist(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /**
     * 判断实体是否应显示出血视觉（双重冗余判定，与优化前的查询谓词逐条一致）。
     *
     * @param entity     待判定实体
     * @param hemorrhage 出血效果对象（可能为 null）
     * @return 应显示返回 true
     */
    private static boolean hasHemorrhage(LivingEntity entity, @Nullable MobEffect hemorrhage) {
        if (hemorrhage != null && entity.hasEffect(hemorrhage)) {
            return true;
        }
        return ClientSyncEffectManager.shouldRenderEffect(
                HemorrhageSyncHandler.HEMORRHAGE_SERIAL, entity.getId());
    }

    /** 脚下血泊：两层渐变圆盘，随时间轻微呼吸。 */
    private static void drawBloodPool(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz, float width,
                                      float time, int seedId) {
        float breath = 0.9f + 0.1f * Mth.sin(time * 1.1f + seedId * 0.4f);
        for (int layer = 0; layer < POOL_LAYERS; layer++) {
            float radius = width * (POOL_RADIUS_BASE + layer * POOL_RADIUS_STEP) * breath;
            float centerAlpha = POOL_BASE_ALPHA - layer * 0.14f;
            if (centerAlpha <= 0f || radius <= 0.05f) {
                continue;
            }
            int col = layer == 0 ? BLOOD_DARK : BLOOD_MID;
            float[] c = unpack(col);
            drawDisc(b, m, cx, cy, cz, radius, POOL_SEGMENTS, c[0], c[1], c[2], centerAlpha);
        }
    }

    /**
     * 喷溅血滴：多颗沿抛物线飞出的柔光圆点，颜色由鲜红转暗，落地/飞散后淡出；
     * 另按 {@link #PULSE_PERIOD} 周期性加量，模拟心跳式的大喷发。
     */
    private static void drawDroplets(BufferBuilder b, Matrix4f m,
                                     float cx, float cyFoot, float cz, float width, float height,
                                     float time, int seedId,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ) {
        float launchY = cyFoot + height * LAUNCH_HEIGHT_FACTOR;

        // 心跳脉冲窗口：命中窗口内额外多喷一批较大的血滴
        float pulsePhase = frac(time / PULSE_PERIOD + seedId * 0.13f);
        boolean pulseActive = pulsePhase < PULSE_WINDOW;

        int count = BASE_DROPLETS + (pulseActive ? PULSE_EXTRA_DROPLETS : 0);
        for (int i = 0; i < count; i++) {
            boolean isPulseDroplet = i >= BASE_DROPLETS;
            long s = seedFor(seedId, isPulseDroplet ? (i + 500) : i);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float speedRand = 0.6f + 0.8f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);

            float rate = isPulseDroplet ? DROPLET_RATE * 1.4f : DROPLET_RATE;
            float t = frac(time * rate + phase);

            float speed = LAUNCH_SPEED_BASE * speedRand;
            float horizontal = speed * t;
            float vertical = LAUNCH_VY * t - GRAVITY * t * t;

            float px = cx + (float) Math.cos(ang) * horizontal;
            float pz = cz + (float) Math.sin(ang) * horizontal;
            float py = launchY + vertical;
            if (py < cyFoot) {
                py = cyFoot;
            }

            float alpha = (1f - t) * (isPulseDroplet ? 1.0f : 0.85f);
            if (t < 0.08f) {
                alpha *= t / 0.08f;
            }
            if (alpha <= 0.01f) {
                continue;
            }

            int col = lerpRgb(BLOOD_BRIGHT, BLOOD_DARK, t);
            float[] c = unpack(col);
            float size = DROPLET_SIZE * sizeRand * (isPulseDroplet ? PULSE_SIZE_MULT : 1f);

            emitSoftDrop(b, m, px, py + Y_OFFSET, pz, size, c[0], c[1], c[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /**
     * 心跳脉冲窗口内的额外「迸溅射线」：胸口处向外爆开的放射状血线（内端 {@link #BLOOD_HOT} 高亮、
     * 外端 {@link #BLOOD_DARK} 迅速转暗，模拟血液甩出后氧化变暗），配合脚下同步的冲击闪光，
     * 让心跳喷发的瞬间比单纯的抛物线血滴更有打击感。与 {@link #drawDroplets} 复用同一个脉冲相位，
     * 故两者视觉上是同一次喷发的不同表现层，不会各自为政。
     */
    private static void drawBurstRays(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz, float width, float height,
                                      float time, int seedId) {
        float pulsePhase = frac(time / PULSE_PERIOD + seedId * 0.13f);
        if (pulsePhase >= PULSE_WINDOW) {
            return;
        }
        float p = pulsePhase / PULSE_WINDOW;
        float chestY = cyFoot + height * LAUNCH_HEIGHT_FACTOR;
        float len = width * 2.6f * easeOutCubic(Math.min(1f, p * 2.2f));
        float alpha = (1f - p) * 0.85f;
        float hw = Math.max(0.05f, width * 0.035f);
        float[] hot = unpack(BLOOD_HOT);
        float[] dark = unpack(BLOOD_DARK);

        for (int i = 0; i < PULSE_RAY_COUNT; i++) {
            long s = seedFor(seedId, i + 900);
            float ang = i * (TAU / PULSE_RAY_COUNT) + rngFloat(s) * 0.35f;
            float ox = cx + (float) Math.cos(ang) * len;
            float oz = cz + (float) Math.sin(ang) * len;
            // 射线略带下坠：外端略低于起点，制造甩溅弧线的错觉
            rayLine(b, m, cx, cz, chestY, ox, oz, chestY - len * 0.2f, hw, hot, dark, alpha);
        }

        // 脚下冲击闪光：与射线同步爆发，短促高亮后迅速回落
        if (p < 0.3f) {
            float flash = (0.3f - p) / 0.3f;
            float[] bright = unpack(BLOOD_FLASH);
            drawDisc(b, m, cx, cyFoot + Y_OFFSET, cz, width * 1.6f * (0.4f + flash), 16,
                    bright[0], bright[1], bright[2], 0.45f * flash);
        }
    }

    /**
     * 一条起点 (x1,y0,z1) 到终点 (x2,y1,z2) 的三维血线，内端亮、外端暗且更透明。
     * 与 {@link #verticalLine} 不同之处在于两端可分别指定颜色，用于表现「甩出后迅速氧化变暗」。
     */
    private static void rayLine(BufferBuilder b, Matrix4f m,
                                float x1, float z1, float y0, float x2, float z2, float y1,
                                float hw, float[] colInner, float[] colOuter, float alpha) {
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
        float outerAlpha = alpha * 0.4f;

        b.vertex(m, ax1, y0, az1).color(colInner[0], colInner[1], colInner[2], alpha).endVertex();
        b.vertex(m, bx1, y1, bz1).color(colOuter[0], colOuter[1], colOuter[2], outerAlpha).endVertex();
        b.vertex(m, bx2, y1, bz2).color(colOuter[0], colOuter[1], colOuter[2], outerAlpha).endVertex();

        b.vertex(m, ax1, y0, az1).color(colInner[0], colInner[1], colInner[2], alpha).endVertex();
        b.vertex(m, bx2, y1, bz2).color(colOuter[0], colOuter[1], colOuter[2], outerAlpha).endVertex();
        b.vertex(m, ax2, y0, az2).color(colInner[0], colInner[1], colInner[2], alpha).endVertex();
    }

    /**
     * 血雾：伤口（胸口）附近喷出的红色雾团，原理与冻伤 {@code FrostbiteMistRenderer#drawFrostFog}
     * 相同（大号柔光块叠加漂移），但更集中在伤口位置、颜色更红更暗，配合血滴 / 迸溅射线
     * 共同构成「真正在喷血」的观感，而不是零散的血点各自往外飞。
     */
    private static void drawBloodMist(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz, float width, float height,
                                      float time, int seedId,
                                      float rightX, float rightY, float rightZ,
                                      float upX, float upY, float upZ) {
        float chestY = cyFoot + height * LAUNCH_HEIGHT_FACTOR;
        for (int i = 0; i < MIST_COUNT; i++) {
            long s = seedFor(seedId, i + 1300);
            float baseAngle = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = rngFloat(s);
            s = rngNext(s);
            float heightRand = rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.75f + 0.5f * rngFloat(s);
            s = rngNext(s);
            float driftPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float pulsePhase = rngFloat(s) * TAU;

            float driftAngle = baseAngle + Mth.sin(time * 0.9f + driftPhase) * 0.7f;
            float radius = width * 0.35f * radFactor;
            float px = cx + (float) Math.cos(driftAngle) * radius;
            float pz = cz + (float) Math.sin(driftAngle) * radius;
            float py = chestY + (heightRand - 0.5f) * height * 0.4f;

            float pulse = 0.7f + 0.3f * Mth.sin(time * 1.8f + pulsePhase);
            float alpha = MIST_BASE_ALPHA * pulse;
            float size = width * MIST_SIZE_FACTOR * sizeRand;

            int col = lerpRgb(BLOOD_MID, BLOOD_BRIGHT, 0.4f + 0.4f * pulse);
            float[] c = unpack(col);

            emitSoftDrop(b, m, px, py, pz, size, c[0], c[1], c[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /** 从躯干垂落的粗血线，循环下滑并在近地面淡出。 */
    private static void drawDrips(BufferBuilder b, Matrix4f m,
                                  float cx, float cyFoot, float cz, float width, float height,
                                  float time, int seedId) {
        for (int i = 0; i < DRIP_COUNT; i++) {
            long s = seedFor(seedId, i + 800);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float startHeightFrac = 0.4f + 0.4f * rngFloat(s);

            float t = frac(time * DRIP_RATE + phase);
            float startY = cyFoot + height * startHeightFrac;
            float len = height * 0.2f;
            float headY = startY - t * (startY - cyFoot);
            float tailY = Math.max(cyFoot, headY - len);

            float alpha = 0.7f * (1f - smoothstep(0.75f, 1f, t));
            if (alpha <= 0.01f) {
                continue;
            }

            float px = cx + (float) Math.cos(ang) * width * 0.32f;
            float pz = cz + (float) Math.sin(ang) * width * 0.32f;

            verticalLine(b, m, px, pz, headY, tailY, DRIP_HALF_WIDTH, BLOOD_MID, alpha);
        }
    }

    private static void verticalLine(BufferBuilder b, Matrix4f m,
                                     float x, float z, float yTop, float yBottom,
                                     float hw, int col, float alpha) {
        if (yTop <= yBottom) {
            return;
        }
        float[] c = unpack(col);
        b.vertex(m, x - hw, yTop, z).color(c[0], c[1], c[2], alpha).endVertex();
        b.vertex(m, x + hw, yTop, z).color(c[0], c[1], c[2], alpha).endVertex();
        b.vertex(m, x + hw, yBottom, z).color(c[0], c[1], c[2], 0f).endVertex();

        b.vertex(m, x - hw, yTop, z).color(c[0], c[1], c[2], alpha).endVertex();
        b.vertex(m, x + hw, yBottom, z).color(c[0], c[1], c[2], 0f).endVertex();
        b.vertex(m, x - hw, yBottom, z).color(c[0], c[1], c[2], 0f).endVertex();
    }

    private static void drawDisc(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz, float radius, int segments,
                                 float r, float g, float bl, float centerAlpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = (TAU * i) / segments;
            double a1 = (TAU * (i + 1)) / segments;
            float x0 = cx + radius * (float) Math.cos(a0);
            float z0 = cz + radius * (float) Math.sin(a0);
            float x1 = cx + radius * (float) Math.cos(a1);
            float z1 = cz + radius * (float) Math.sin(a1);
            b.vertex(m, cx, cy, cz).color(r, g, bl, centerAlpha).endVertex();
            b.vertex(m, x0, cy, z0).color(r, g, bl, 0f).endVertex();
            b.vertex(m, x1, cy, z1).color(r, g, bl, 0f).endVertex();
        }
    }

    private static void emitSoftDrop(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float size,
                                     float r, float g, float bl, float alpha,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ) {
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= DROP_SEGMENTS; i++) {
            float ang = TAU * i / DROP_SEGMENTS;
            float ca = (float) Math.cos(ang) * size;
            float sa = (float) Math.sin(ang) * size;
            float ex = cx + rightX * ca + upX * sa;
            float ey = cy + rightY * ca + upY * sa;
            float ez = cz + rightZ * ca + upZ * sa;
            if (i > 0) {
                b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
                b.vertex(m, pex, pey, pez).color(r, g, bl, 0f).endVertex();
                b.vertex(m, ex, ey, ez).color(r, g, bl, 0f).endVertex();
            }
            pex = ex;
            pey = ey;
            pez = ez;
        }
    }

    // ==================== 无分配伪随机（xorshift64） ====================

    private static long seedFor(int entityId, int index) {
        long s = (entityId * 0x9E3779B97F4A7C15L) ^ ((index + 1L) * 0x85EBCA6BL);
        s ^= (s >>> 29);
        return s == 0L ? 0x9E3779B97F4A7C15L : s;
    }

    private static long rngNext(long s) {
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        return s;
    }

    private static float rngFloat(long s) {
        return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    // ==================== 数学 / 颜色辅助 ====================

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = (x - e0) / (e1 - e0);
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        return t * t * (3f - 2f * t);
    }

    private static int lerpRgb(int from, int to, float t) {
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int bl = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static float[] unpack(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f
        };
    }
}
