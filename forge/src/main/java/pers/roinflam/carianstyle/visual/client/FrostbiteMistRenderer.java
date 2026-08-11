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
import pers.roinflam.carianstyle.network.FrostbiteSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 冻伤「冰霜」客户端渲染器（纯客户端自绘）。
 * <p>
 * <b>判定采用双重冗余：</b>{@code entity.hasEffect(CarianStylePotion.FROSTBITE.get())}
 * （覆盖玩家自己、以及在观察者开始追踪前就已带冻伤的实体）<b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(FrostbiteSyncHandler.FROSTBITE_SERIAL, id)}
 * （覆盖战斗中途才被施加冻伤的怪物，见 {@link FrostbiteSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>三个元素分工：</b>
 * <ol>
 *     <li><b>冰晶星</b>（{@link #drawIceCrystalStar}）——唯一的标志性主视觉：八角星 + 内
 *         六边形，任何角度都能一眼认出「冰」；带周期性脉冲（短暂增亮增大 + 中心闪光）；</li>
 *     <li><b>霜地</b>（{@link #drawFrostedGround}）——单层渐变圆盘 + 从冰晶星八个尖角方向
 *         延伸出去、与星形同步旋转的裂纹；</li>
 *     <li><b>冰雾 + 冷蒸汽</b>（{@link #drawFrostFog} / {@link #drawColdVapor}）——
 *         前者是半径接近实体宽度一半的大号柔光雾块，缓慢漂移、彼此叠加，是「冒寒气」的主要来源；
 *         后者是贴身持续冒出的细蒸汽丝，作为补充细节层。</li>
 * </ol>
 * </p>
 * <p>
 * 渲染管线与 {@code ScarletRotMistRenderer} 同款：{@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，
 * 实体列表取自 {@link SharedEntityQuery} 的每帧共享查询，{@code POSITION_COLOR} 纯顶点绘制。
 * </p>
 *
 * <h3>v7（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个冻伤实体每帧的顶点量粗算：
 * </p>
 * <pre>
 * 霜地圆盘（24 段 × 3）                    72
 * 地面裂纹（8 条 × 6）                      48
 * 冰晶星（八角星 48 + 六边形 36 + 闪光 54） 138
 * 冰雾（9 团 × 10 段 × 3）                 270
 * 冷蒸汽（18 丝 × 10 段 × 3）              540
 * ─────────────────────────────────────────
 * 合计                              ~1068 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 冻伤是本模组<b>最容易群体挂载</b>的效果之一——星律附魔在夜间「造成或受到物理伤害」就叠冻伤、
 * 亚杜拉的月光剑每次攻击对目标周围一圈敌人施加冻伤，一次挥砍就能让十几个实体同时带上。
 * </p>
 * <p>
 * 现按 {@link VisualLod#detail} 缩放：{@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，
 * <b>与优化前逐像素一致</b>；40 格外单实体降至约 220 顶点。
 * </p>
 * <p>
 * <b>削减策略：</b>
 * </p>
 * <ul>
 *     <li><b>冰晶星完全不削</b>——八角星 + 内六边形共 84 顶点，是「这是冰不是别的」的唯一依据，
 *         而且是<b>顶点性价比最高</b>的元素（84 顶点换来全部辨识度）。只把脉冲峰值那一下的
 *         中心闪光圆盘按细节缩分段（那是锦上添花的柔光，不影响星形轮廓）；</li>
 *     <li><b>冰雾与蒸汽按种子截断尾部</b>——位置由 {@code seedFor(entityId, i)} 决定，
 *         保留元素的漂移轨迹完全不变；</li>
 *     <li><b>蒸汽丝整层可跳过</b>——它本就是冰雾的「补充细节层」（见类内 v5 说明），
 *         远处两者会糊成一片，低于 {@link #VAPOR_KEEP_THRESHOLD} 时只留大雾块
 *         （省 540 顶点，占总量一半）；</li>
 *     <li><b>裂纹按步长抽取</b>——裂纹角度是 {@code rot + i × (TAU / 8)} 均布的，
 *         且刻意与冰晶星的八个尖角对齐，截断会让一半尖角失去延伸出去的裂纹、
 *         破坏「星形向外裂开」的一体感，故按步长抽取保持对称。</li>
 * </ul>
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class FrostbiteMistRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;
    /** 蒸汽 / 雾团光点的 billboard 分段数 */
    private static final int MOTE_SEGMENTS = 10;
    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。动画时间必须用「当前毫秒 - 此起始值」的差值再转 float，
     * 直接用 currentTimeMillis()/1000f 数值过大会导致 float 精度不足、动画卡死。
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== v7 LOD 下限与保留阈值 =====
    /** 雾团 / 蒸汽光点的最少分段数：4 段仍是个饱满的菱形柔光块 */
    private static final int MOTE_SEGMENTS_MIN = 4;
    /** 霜地圆盘的最少分段数 */
    private static final int GROUND_SEGMENTS_MIN = 8;
    /** 冰晶星脉冲闪光圆盘的最少分段数 */
    private static final int FLASH_SEGMENTS_MIN = 6;
    /** 冷蒸汽层的保留阈值：它是冰雾的补充细节层，远处两者糊成一片 */
    private static final float VAPOR_KEEP_THRESHOLD = 0.5f;

    // ===== 配色（0xRRGGBB）=====
    private static final int ICE_WHITE = 0xEAF6FF;
    private static final int ICE_BLUE = 0x8FD4FF;
    private static final int ICE_DEEP = 0x2E5C82;

    // ===== 冰晶星（主视觉，含周期性脉冲）=====
    private static final float ICE_STAR_ROT_SPEED = 0.15f;
    private static final float ICE_STAR_RADIUS_FACTOR = 0.6f;
    private static final float ICE_STAR_ALPHA = 0.75f;
    /** 脉冲周期（秒）：给冰晶星一个「呼吸之外」的短促增亮增大时刻 */
    private static final float PULSE_PERIOD = 3.0f;
    /** 脉冲动画占周期的比例（其余时间只有常规呼吸） */
    private static final float PULSE_ACTIVE_RATIO = 0.25f;
    /** 冰晶星脉冲峰值中心闪光的分段数 */
    private static final int FLASH_SEGMENTS = 18;

    // ===== 霜地（单层圆盘 + 与冰晶星同步旋转的裂纹）=====
    private static final int GROUND_SEGMENTS = 24;
    private static final float GROUND_RADIUS_FACTOR = 1.7f;
    private static final float GROUND_ALPHA = 0.42f;
    private static final int CRACK_COUNT = 8;
    private static final float CRACK_HALF_WIDTH = 0.03f;
    private static final float CRACK_ALPHA = 0.55f;

    // ===== 冷蒸汽（贴身持续冒出的细丝，"冒寒气"的细节层）=====
    /** 同时存在的蒸汽光点数量 */
    private static final int VAPOR_COUNT = 18;
    /** 单颗蒸汽从生成到消散的循环速度（每秒推进的归一化进度） */
    private static final float VAPOR_RISE_SPEED = 0.55f;
    /** 蒸汽贴身半径系数（× 实体宽度），刻意贴近身体而非四散飘 */
    private static final float VAPOR_RADIUS_FACTOR = 0.4f;
    /** 单颗蒸汽上飘的距离系数（× 实体高度），较短——强调"贴身冒出"而非"漫天飞舞" */
    private static final float VAPOR_RISE_DIST_FACTOR = 0.35f;
    private static final float VAPOR_BASE_ALPHA = 0.8f;

    // ===== 冰雾（真正有体积感的雾块，"冒寒气"的主要来源）=====
    /** 同时存在的雾块数量（叠加出体积感，而不是稀疏的几个点） */
    private static final int FOG_COUNT = 9;
    /** 雾块半径系数（× 实体宽度），明显比蒸汽丝大得多，才能读出"雾"而不是"光点" */
    private static final float FOG_SIZE_FACTOR = 0.42f;
    /** 雾块漂浮覆盖的高度系数（× 实体高度） */
    private static final float FOG_HEIGHT_FACTOR = 0.55f;
    private static final float FOG_BASE_ALPHA = 0.4f;
    /** 雾块缓慢漂移的速度 */
    private static final float FOG_DRIFT_SPEED = 0.3f;

    private FrostbiteMistRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有冻伤生物的冰霜视觉。
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

        MobEffect frostbite = CarianStylePotion.FROSTBITE.get();

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
            // 原先作为查询谓词的判定，现下沉为循环内筛选（共享列表已保证 isAlive）
            if (!hasFrostbite(entity, frostbite)) {
                continue;
            }

            double ex = Mth.lerp((double) partial, entity.xo, entity.getX());
            double ey = Mth.lerp((double) partial, entity.yo, entity.getY());
            double ez = Mth.lerp((double) partial, entity.zo, entity.getZ());

            double dx = ex - cam.x;
            double dy = ey - cam.y;
            double dz = ez - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue;
            }

            // ⭐ v7：本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0，视觉与优化前一致
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度（不影响本帧绘制）
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryGround = (float) dy + Y_OFFSET;
            float rz = (float) dz;

            drawFrostedGround(builder, matrix, rx, ryGround, rz, width, time, entity.getId(), detail);
            drawIceCrystalStar(builder, matrix, rx, ryGround, rz, width, time, entity.getId(), detail);
            drawFrostFog(builder, matrix, rx, (float) dy, rz, width, height, time, entity.getId(),
                    rightX, rightY, rightZ, upX, upY, upZ, detail);
            // 冷蒸汽是冰雾的补充细节层，远处两者糊成一片，低细节时整层跳过（省约 540 顶点）
            if (VisualLod.keepLayer(detail, VAPOR_KEEP_THRESHOLD)) {
                drawColdVapor(builder, matrix, rx, (float) dy, rz, width, height, time, entity.getId(),
                        rightX, rightY, rightZ, upX, upY, upZ, detail);
            }
        }
    }

    /**
     * 判断实体是否应显示冻伤视觉（双重冗余判定）。
     *
     * @param entity    待判定实体
     * @param frostbite 冻伤效果对象（可能为 null）
     * @return 应显示返回 true
     */
    private static boolean hasFrostbite(LivingEntity entity, @Nullable MobEffect frostbite) {
        if (frostbite != null && entity.hasEffect(frostbite)) {
            return true;
        }
        return ClientSyncEffectManager.shouldRenderEffect(
                FrostbiteSyncHandler.FROSTBITE_SERIAL, entity.getId());
    }

    /**
     * 冰晶星（主视觉）：脚下缓慢旋转的八角星 + 反向旋转的内六边形轮廓。除常规呼吸外，
     * 每 {@link #PULSE_PERIOD} 秒有一次短促的增亮增大 + 中心闪光。
     * <p>
     * <b>v7：星形本体完全不参与 LOD 削减。</b>八角星 + 内六边形共 84 顶点却承担全部辨识度，
     * 是本渲染器顶点性价比最高的元素；只把脉冲峰值那一下的中心闪光圆盘按细节缩分段
     * （那是锦上添花的柔光，不影响星形轮廓）。
     * </p>
     */
    private static void drawIceCrystalStar(BufferBuilder b, Matrix4f m,
                                           float cx, float cy, float cz, float width,
                                           float time, int seedId, float detail) {
        float rot = time * ICE_STAR_ROT_SPEED + seedId * 0.3f;
        float breath = 0.75f + 0.25f * Mth.sin(time * 1.4f + seedId);

        // 周期性脉冲：前 25% 内有效，之后归零，仅在这段时间内让星形更亮更大
        float pulseCycle = frac(time / PULSE_PERIOD + seedId * 0.17f);
        float pulseBoost = (pulseCycle < PULSE_ACTIVE_RATIO)
                ? (1f - pulseCycle / PULSE_ACTIVE_RATIO) : 0f;

        float radius = width * ICE_STAR_RADIUS_FACTOR * (1f + 0.18f * pulseBoost);
        float hw = Math.max(0.03f, width * 0.018f) * (1f + 0.3f * pulseBoost);
        float alphaMul = breath * (1f + 0.5f * pulseBoost);
        float[] white = unpack(ICE_WHITE);
        float[] blue = unpack(ICE_BLUE);

        // 八角星（点数8、步距3，两叠三角效果，即"雪花"轮廓）
        starPolygon(b, m, cx, cz, cy, radius, 8, 3, rot, hw, white, ICE_STAR_ALPHA * alphaMul);
        // 内六边形（反向旋转），叠出更丰富的冰晶层次
        polygonRing(b, m, cx, cz, cy, radius * 0.55f, 6, -rot * 1.3f, hw * 0.85f, blue,
                ICE_STAR_ALPHA * 0.8f * alphaMul);

        // 脉冲峰值时的中心闪光，复用星形自身的透明度节奏，不新增独立系统
        if (pulseBoost > 0.55f) {
            float flash = (pulseBoost - 0.55f) / 0.45f;
            int flashSegments = VisualLod.scaleSegments(FLASH_SEGMENTS, FLASH_SEGMENTS_MIN, detail);
            drawDisc(b, m, cx, cy, cz, width * 0.55f, flashSegments,
                    white[0], white[1], white[2], 0.35f * flash);
        }
    }

    /**
     * 霜地：单层渐变圆盘 + 从冰晶星八个尖角方向延伸出去、与星形同步旋转的裂纹——
     * 裂纹与冰晶星共用同一个旋转相位，视觉上是「星形向外裂开」的一体图案。
     * <p>
     * <b>v7：圆盘缩分段；裂纹按步长抽取。</b>裂纹角度是 {@code rot + i × (TAU / 8)} 均布的，
     * 且刻意与冰晶星的八个尖角对齐——截断前 N 条会让一半尖角失去延伸出去的裂纹、
     * 破坏「星形向外裂开」的一体感，故必须按步长抽取以保持对称。
     * </p>
     */
    private static void drawFrostedGround(BufferBuilder b, Matrix4f m,
                                          float cx, float cy, float cz, float width,
                                          float time, int seedId, float detail) {
        float breath = 0.88f + 0.12f * Mth.sin(time * 1.0f + seedId * 0.5f);
        float radius = width * GROUND_RADIUS_FACTOR * breath;
        int col = lerpRgb(ICE_DEEP, ICE_BLUE, 0.5f + 0.5f * Mth.sin(time * 0.9f + seedId));
        float[] c = unpack(col);
        int segments = VisualLod.scaleSegments(GROUND_SEGMENTS, GROUND_SEGMENTS_MIN, detail);
        drawDisc(b, m, cx, cy, cz, radius, segments, c[0], c[1], c[2], GROUND_ALPHA * breath);

        // 裂纹从星尖方向延伸出去，与冰晶星共用同一旋转相位
        float rot = time * ICE_STAR_ROT_SPEED + seedId * 0.3f;
        float innerR = width * ICE_STAR_RADIUS_FACTOR * 0.95f;

        // ⭐ v7：均布裂纹按步长抽取，保持与八角星尖角的对称关系
        int drawnCracks = VisualLod.scale(CRACK_COUNT, detail);
        int step = Math.max(1, CRACK_COUNT / drawnCracks);

        for (int i = 0; i < CRACK_COUNT; i += step) {
            // 种子仍用原始下标 i，保证保留裂纹的长度与全细节时完全一致
            long s = seedFor(seedId, i + 50);
            float ang = rot + i * (TAU / CRACK_COUNT);
            float len = radius * (0.7f + 0.3f * rngFloat(s));
            float flick = 0.6f + 0.4f * Mth.sin(time * 1.6f + i * 1.1f + seedId);
            float ix = cx + (float) Math.cos(ang) * innerR;
            float iz = cz + (float) Math.sin(ang) * innerR;
            float ox = cx + (float) Math.cos(ang) * len;
            float oz = cz + (float) Math.sin(ang) * len;
            line(b, m, ix, iz, ox, oz, cy, CRACK_HALF_WIDTH, ICE_WHITE, CRACK_ALPHA * flick, 0f);
        }
    }

    /**
     * 冰雾：多团比蒸汽丝大得多的柔光雾块，缓慢漂移、彼此叠加，营造真正的「体积感」——
     * 这是「冒寒气」的主要来源，{@link #drawColdVapor} 的细丝作为补充细节层。
     * <p>
     * <b>v7：数量与分段数按细节系数缩放。</b>雾块位置由 {@code seedFor(entityId, i + 900)} 决定，
     * 截断尾部时保留元素的漂移轨迹完全不变。
     * </p>
     */
    private static void drawFrostFog(BufferBuilder b, Matrix4f m,
                                     float cx, float cyFoot, float cz, float width, float height,
                                     float time, int seedId,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ, float detail) {
        int count = VisualLod.scale(FOG_COUNT, detail);
        int segments = VisualLod.scaleSegments(MOTE_SEGMENTS, MOTE_SEGMENTS_MIN, detail);

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 900);
            float baseAngle = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = rngFloat(s);
            s = rngNext(s);
            float heightFrac = rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.8f + 0.5f * rngFloat(s);
            s = rngNext(s);
            float driftPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float pulsePhase = rngFloat(s) * TAU;

            // 缓慢漂移的角度（围绕基准角小幅摆动，而不是固定不动或大范围环绕）
            float driftAngle = baseAngle + Mth.sin(time * FOG_DRIFT_SPEED + driftPhase) * 0.6f;
            float radius = width * 0.3f * radFactor;
            float px = cx + (float) Math.cos(driftAngle) * radius;
            float pz = cz + (float) Math.sin(driftAngle) * radius;
            float py = cyFoot + height * (0.08f + heightFrac * FOG_HEIGHT_FACTOR);

            float pulse = 0.75f + 0.25f * Mth.sin(time * 0.7f + pulsePhase);
            float alpha = FOG_BASE_ALPHA * pulse;
            float size = width * FOG_SIZE_FACTOR * sizeRand;

            int col = lerpRgb(ICE_BLUE, ICE_WHITE, 0.5f + 0.5f * Mth.sin(time * 0.5f + i));
            float[] c = unpack(col);

            // 复用蒸汽丝的 billboard 几何，sizeH=sizeV 即退化为正圆雾块
            emitVaporWisp(b, m, px, py, pz, size, size, c[0], c[1], c[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ, segments);
        }
    }

    /**
     * 冷蒸汽：贴身持续冒出的蒸汽状光点（拉长的水滴形，而非正圆），是「冒寒气」的细节层——
     * 生成点覆盖躯干从脚踝到头顶的不同高度，各自独立循环生成、缓慢短距离上飘后消散。
     * <p>
     * <b>v7：数量与分段数按细节系数缩放；整层由调用方按 {@link #VAPOR_KEEP_THRESHOLD} 决定是否绘制。</b>
     * 蒸汽位置由 {@code seedFor(entityId, i + 400)} 决定，截断尾部安全。
     * </p>
     */
    private static void drawColdVapor(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz, float width, float height,
                                      float time, int seedId,
                                      float rightX, float rightY, float rightZ,
                                      float upX, float upY, float upZ, float detail) {
        float radius = width * VAPOR_RADIUS_FACTOR;
        float riseDist = height * VAPOR_RISE_DIST_FACTOR;

        int count = VisualLod.scale(VAPOR_COUNT, detail);
        int segments = VisualLod.scaleSegments(MOTE_SEGMENTS, MOTE_SEGMENTS_MIN, detail);

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 400);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.5f + 0.5f * rngFloat(s);
            s = rngNext(s);
            float startHeightFrac = 0.05f + 0.85f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.5f * rngFloat(s);
            s = rngNext(s);
            float swayPhase = rngFloat(s) * TAU;

            float t = frac(time * VAPOR_RISE_SPEED + phase); // 0=刚冒出 1=消散完毕

            float env;
            if (t < 0.12f) {
                env = t / 0.12f;
            } else if (t > 0.55f) {
                env = 1f - (t - 0.55f) / 0.45f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }

            float sway = (float) Math.sin(time * 1.6f + swayPhase) * 0.1f;
            float curRad = radius * radFactor;
            float px = cx + (float) Math.cos(ang) * curRad + sway;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float startY = cyFoot + height * startHeightFrac;
            float py = startY + t * riseDist + Y_OFFSET;

            float twinkle = 0.75f + 0.25f * Mth.sin(time * 3.2f + swayPhase);
            float alpha = VAPOR_BASE_ALPHA * env * twinkle;
            if (alpha <= 0.01f) {
                continue;
            }

            int col = lerpRgb(ICE_BLUE, ICE_WHITE, 0.3f + 0.4f * t);
            float[] c = unpack(col);
            float sizeH = 0.06f * sizeRand;
            // 越往上拉得越长，模拟蒸汽消散拉丝的观感
            float sizeV = 0.13f * sizeRand * (1f + 0.4f * t);

            emitVaporWisp(b, m, px, py, pz, sizeH, sizeV, c[0], c[1], c[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ, segments);
        }
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

    /** 星形多边形：把 points 个顶点按步距 step 互连（如 points=8,step=3 即冰花八角星）。 */
    private static void starPolygon(BufferBuilder b, Matrix4f m, float cx, float cz, float cy,
                                    float radius, int points, int step, float rotation, float hw,
                                    float[] col, float alpha) {
        for (int i = 0; i < points; i++) {
            double a1 = rotation + (Math.PI * 2 * i) / points;
            double a2 = rotation + (Math.PI * 2 * ((i + step) % points)) / points;
            float x1 = cx + radius * (float) Math.cos(a1);
            float z1 = cz + radius * (float) Math.sin(a1);
            float x2 = cx + radius * (float) Math.cos(a2);
            float z2 = cz + radius * (float) Math.sin(a2);
            lineF(b, m, x1, z1, x2, z2, cy, hw, col, alpha, alpha);
        }
    }

    /** 正多边形外框（N 条边首尾相连），用作冰花内层六边形轮廓。 */
    private static void polygonRing(BufferBuilder b, Matrix4f m, float cx, float cz, float cy,
                                    float radius, int sides, float rotation, float hw,
                                    float[] col, float alpha) {
        float prevX = 0f, prevZ = 0f;
        for (int i = 0; i <= sides; i++) {
            double ang = rotation + (Math.PI * 2 * i) / sides;
            float x = cx + radius * (float) Math.cos(ang);
            float z = cz + radius * (float) Math.sin(ang);
            if (i > 0) {
                lineF(b, m, prevX, prevZ, x, z, cy, hw, col, alpha, alpha);
            }
            prevX = x;
            prevZ = z;
        }
    }

    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float z1, float x2, float z2, float y,
                             float hw, int col, float a1, float a2) {
        lineF(b, m, x1, z1, x2, z2, y, hw, unpack(col), a1, a2);
    }

    /** 两端 alpha 可分别指定的线段（浮点颜色版，供裂纹、冰花等直接传入解析好的 float[] 颜色）。 */
    private static void lineF(BufferBuilder b, Matrix4f m,
                              float x1, float z1, float x2, float z2, float y,
                              float hw, float[] c, float a1, float a2) {
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

        b.vertex(m, ax1, y, az1).color(c[0], c[1], c[2], a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(c[0], c[1], c[2], a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(c[0], c[1], c[2], a2).endVertex();

        b.vertex(m, ax1, y, az1).color(c[0], c[1], c[2], a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(c[0], c[1], c[2], a2).endVertex();
        b.vertex(m, ax2, y, az2).color(c[0], c[1], c[2], a1).endVertex();
    }

    /**
     * 绘制一片面向相机的柔和「蒸汽」光点：billboard 平面内水平方向 {@code sizeH}、
     * 竖直方向 {@code sizeV} 独立指定，{@code sizeV > sizeH} 时呈拉长的椭圆，
     * 比正圆更贴合「蒸汽上飘拉丝」的观感。中心不透明、边缘渐隐为 0。
     *
     * @param segments 分段数。v7 起由调用方按细节系数传入，下限 {@link #MOTE_SEGMENTS_MIN}；
     *                 全细节时即 {@link #MOTE_SEGMENTS}。雾团与蒸汽合计 27 个光点，
     *                 是本渲染器的主要顶点杠杆。
     */
    private static void emitVaporWisp(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz, float sizeH, float sizeV,
                                      float r, float g, float bl, float alpha,
                                      float rightX, float rightY, float rightZ,
                                      float upX, float upY, float upZ, int segments) {
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= segments; i++) {
            float ang = TAU * i / segments;
            float ca = (float) Math.cos(ang) * sizeH;
            float sa = (float) Math.sin(ang) * sizeV;
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
