package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.HowlShabririSyncHelper;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.List;

/**
 * 夏玻利利的嘶吼「发狂层数」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@code EnchantmentHowlShabriri}：攻击后给目标叠加发狂
 * （护甲 -15%×等级、恢复 -10%×等级），<b>满层时攻击者额外造成 15%×附魔等级 的伤害</b>。
 * </p>
 *
 * <h3>这个视觉解决的是一个信息问题，不是氛围问题</h3>
 * <p>
 * 嘶吼的核心机制是「对满层目标额外增伤」——也就是说玩家必须能<b>数出目标身上叠了几层</b>，
 * 才知道下一击是不是那一下重的。而在此之前，层数存在
 * {@code DynamicAttributeManager} 里、<b>客户端那份 Map 恒为空</b>，
 * 玩家除了硬记攻击次数之外没有任何办法知道。
 * </p>
 * <p>
 * 因此本渲染器的第一优先级不是「好看」，而是<b>层数必须一眼数得出来</b>。
 * 这直接决定了下面的元素取舍：
 * </p>
 * <ul>
 *     <li><b>层数刻痕</b>（{@link #drawTallyMarks}）是<b>最重要</b>的元素——
 *         眼睛下方一排 N 个小竖条，N 就是层数。数竖条比判断「眼睛红到什么程度」可靠得多，
 *         也不受色觉差异影响。它顶点量极少，<b>无论细节多低都完整绘制</b>；</li>
 *     <li>眼睛的大小 / 瞳色 / 抖线密度只是<b>冗余编码</b>——远处看不清刻痕时，
 *         「那只眼睛很大很红」也能传达「快满层了」。</li>
 * </ul>
 *
 * <h3>为什么是眼睛</h3>
 * <p>
 * 夏玻利利本身就是「持有癫火之眼的人」，充血之眼是这个角色最直接的符号。
 * 而在本模组已有的十余种演出里，<b>没有任何一个用过眼睛形状</b>——
 * 法阵是多边形 / 星形、光环是同心圆、刀光是弧带、螺旋归睡眠、
 * 尖刺归噩兆、根须归黄金树、月轮归暗月、电柱归龙雷。
 * 因此同屏叠加时不存在辨识歧义。
 * </p>
 * <p>
 * <b>配色刻意与癫火同源</b>（黄 {@link #FRENZY_YELLOW} / 橙 {@link #FRENZY_ORANGE}）——
 * 攻击者自己会被 {@code EPILEPSY_FIRE_BURNING} 点上黄色火焰，
 * 二者同色正是在表达「这是同一种疯狂」；形状完全不同，不会混淆。
 * 只有瞳孔用血赤 {@link #BLOOD_IRIS}，作为唯一的冷…（暖色系里最深的一档）重音。
 * </p>
 *
 * <h3>判定：读 6 个序列号中的哪一个亮着</h3>
 * <p>
 * 嘶吼不是 {@code MobEffect}，{@code hasEffect} 完全用不上；
 * 层数由 {@link HowlShabririSyncHelper} 以「一层一个序列号」的方式同步过来
 * （13~18 对应 amplifier 0~5，详见该类注释）。
 * 本渲染器逐个探测这 6 个序列号，命中哪个就是几层。
 * </p>
 * <p>
 * 每个探测都是一次 {@code Set.contains} 的 O(1) 查询，
 * 6 次乘以同屏候选实体数（几十）完全不构成开销；
 * 而且<b>绝大多数实体一个都不命中，第一次查完就 break</b>。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 眼白填充（20 段 × 6）                    120
 * 眼眶轮廓（上下各 20 段 × 6）             240
 * 虹膜 + 瞳孔（2 × 14 段 × 3）              84
 * 血丝（层数 × 2 条 × 6）                  ≤72
 * 层数刻痕（层数 × 6）                     ≤36
 * 狂乱抖线（层数 × 2 条 × 3 段 × 6）      ≤216
 * ─────────────────────────────────────────
 * 满层合计                          ~770 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 嘶吼是<b>攻击时施加给目标</b>的，群战中同屏挂十几个很常见，
 * 故完整接入 {@link VisualLod}（含 {@link VisualLod#countInstance()}——
 * 少登记一个渲染器就会让全局 {@code crowdFactor} 被系统性高估，
 * 已接入的重量级渲染器就削减不足）。
 * </p>
 * <p>
 * <b>削减策略：</b>眼眶 / 眼白的分段数按细节缩放；血丝与抖线整层可跳过；
 * <b>层数刻痕与瞳孔永不削减</b>——前者是层数的唯一可靠读数，后者是「这是只眼睛」的最后依据。
 * </p>
 * <p>
 * 五个配色全是编译期常量、演出中只有 alpha 与尺寸随层数变化、色相从不插值，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需 {@code SCRATCH} 缓冲。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class HowlShabririRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== 配色（0xRRGGBB）=====
    /** 癫火黄：眼白高光、刻痕、抖线内段。与攻击者身上的黄色癫火同源 */
    private static final int FRENZY_YELLOW = 0xFFE020;
    /** 癫火橙：眼眶、抖线外段 */
    private static final int FRENZY_ORANGE = 0xFF6A1A;
    /** 病态眼白：偏黄的浑浊白，而非纯白——纯白读起来太「干净」，不像发狂 */
    private static final int SCLERA_PALE = 0xF2E4B0;
    /** 血赤虹膜：全套视觉唯一的深色重音 */
    private static final int BLOOD_IRIS = 0xC81028;
    /** 瞳孔墨黑 */
    private static final int PUPIL_DARK = 0x1A0608;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    private static final float[] C_YELLOW = VisualColor.constant(FRENZY_YELLOW);
    private static final float[] C_ORANGE = VisualColor.constant(FRENZY_ORANGE);
    private static final float[] C_SCLERA = VisualColor.constant(SCLERA_PALE);
    private static final float[] C_IRIS = VisualColor.constant(BLOOD_IRIS);
    private static final float[] C_PUPIL = VisualColor.constant(PUPIL_DARK);

    // ===== 头顶充血之眼 =====
    /** 眼睛悬浮高度系数（× 实体高度）：略高于头顶 */
    private static final float EYE_HEIGHT_FACTOR = 1.32f;
    /** 眼睛半宽基准系数（× 实体宽度），再随层数放大 */
    private static final float EYE_HALF_WIDTH_FACTOR = 0.5f;
    /** 每层给眼睛半宽带来的额外放大比例 */
    private static final float EYE_WIDTH_PER_LAYER = 0.07f;
    /** 眼睛半高相对半宽的比例（杏仁形，扁于圆） */
    private static final float EYE_ASPECT = 0.52f;
    /** 眼眶轮廓的细分段数（单侧弧） */
    private static final int EYE_SEGMENTS = 20;
    /** 眼眶轮廓的最少细分段数：8 段仍是平滑的杏仁弧，再低会显出折角 */
    private static final int EYE_SEGMENTS_MIN = 8;
    /** 眼眶线半宽（格） */
    private static final float EYE_RIM_HALF = 0.022f;
    private static final float EYE_BASE_ALPHA = 0.88f;

    /** 虹膜半径相对眼半高的比例 */
    private static final float IRIS_RATIO = 0.78f;
    /** 瞳孔半径相对虹膜的<b>基准</b>比例（层数越高瞳孔收得越小，越显疯狂） */
    private static final float PUPIL_RATIO_BASE = 0.55f;
    /** 每层让瞳孔收缩的比例 */
    private static final float PUPIL_SHRINK_PER_LAYER = 0.06f;
    /** 虹膜 / 瞳孔圆的分段数 */
    private static final int IRIS_SEGMENTS = 14;
    /** 虹膜 / 瞳孔圆的最少分段数 */
    private static final int IRIS_SEGMENTS_MIN = 6;

    /** 每层对应的血丝条数 */
    private static final int VEINS_PER_LAYER = 2;
    /** 血丝层的保留阈值：极细的短线，远处不可见 */
    private static final float VEIN_KEEP_THRESHOLD = 0.5f;

    // ===== 层数刻痕（最重要的元素，永不削减）=====
    /** 刻痕相对眼半高的下沉距离比例 */
    private static final float TALLY_DROP_RATIO = 1.75f;
    /** 单条刻痕的半高（格） */
    private static final float TALLY_HALF_HEIGHT = 0.055f;
    /** 单条刻痕的半宽（格） */
    private static final float TALLY_HALF_WIDTH = 0.018f;
    /** 相邻刻痕的中心间距（格） */
    private static final float TALLY_SPACING = 0.062f;
    private static final float TALLY_ALPHA = 0.95f;

    // ===== 狂乱抖线 =====
    /** 每层对应的抖线条数 */
    private static final int JITTER_PER_LAYER = 2;
    /** 单条抖线的折线段数 */
    private static final int JITTER_SEGMENTS = 3;
    /** 抖线环绕半径系数（× 实体宽度） */
    private static final float JITTER_RADIUS_FACTOR = 0.72f;
    /** 抖线长度系数（× 实体高度） */
    private static final float JITTER_LENGTH_FACTOR = 0.3f;
    /** 抖线半宽（格） */
    private static final float JITTER_HALF_WIDTH = 0.022f;
    /** 抖动频率（越高越「癫」） */
    private static final float JITTER_SPEED = 14f;
    private static final float JITTER_ALPHA = 0.6f;
    /** 抖线层的保留阈值 */
    private static final float JITTER_KEEP_THRESHOLD = 0.45f;

    private HowlShabririRenderer() {
    }

    /**
     * 世界渲染回调：绘制相机附近所有带发狂层数实体的充血之眼。
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
        if (mc.level == null || mc.player == null) {
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

        Matrix4f matrix = VisualBatch.matrix();
        float rgX = VisualBatch.rightX();
        float rgY = VisualBatch.rightY();
        float rgZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            int layer = layerOf(entity.getId());
            if (layer <= 0) {
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

            float detail = VisualLod.detail(distSqr);
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            // 满层：眼睛更大、瞳孔更小、抖动更凶
            boolean atMax = layer >= HowlShabririSyncHelper.HOWL_TIER_COUNT;

            float eyeY = ryFoot + height * EYE_HEIGHT_FACTOR;
            // 心跳式明灭：满层时更急促
            float pulse = 0.82f + 0.18f * Mth.sin(time * (atMax ? 7.5f : 3.2f) + entity.getId());

            drawEye(builder, matrix, rx, eyeY, rz, width, layer, atMax, pulse, time,
                    entity.getId(), rgX, rgY, rgZ, upX, upY, upZ, detail);
            // ⭐ 层数刻痕：层数的唯一可靠读数，永不削减
            drawTallyMarks(builder, matrix, rx, eyeY, rz, width, layer, pulse,
                    rgX, rgY, rgZ, upX, upY, upZ);

            if (VisualLod.keepLayer(detail, JITTER_KEEP_THRESHOLD)) {
                drawFrenzyJitters(builder, matrix, rx, ryFoot, rz, width, height, layer, atMax,
                        time, entity.getId(), detail);
            }
        }
    }

    /**
     * 探测某实体当前的发狂层数。
     * <p>
     * 逐个查 {@link HowlShabririSyncHelper} 的 6 个序列号，命中哪个就是几层。
     * 每次探测是一次 {@code Set.contains} 的 O(1) 查询，
     * 且绝大多数实体一个都不命中——那种情况下这里就是 6 次哈希查表，可以忽略。
     * </p>
     *
     * @param entityId 实体网络 id
     * @return 层数（1 ~ {@link HowlShabririSyncHelper#HOWL_TIER_COUNT}）；未命中返回 0
     */
    private static int layerOf(int entityId) {
        for (int amp = 0; amp < HowlShabririSyncHelper.HOWL_TIER_COUNT; amp++) {
            if (ClientSyncEffectManager.shouldRenderEffect(
                    HowlShabririSyncHelper.serialFor(amp), entityId)) {
                return amp + 1;
            }
        }
        return 0;
    }

    // ==================== 头顶充血之眼 ====================

    /**
     * 绘制头顶的充血之眼：杏仁形眼白 + 眼眶轮廓 + 血赤虹膜 + 收缩的瞳孔 + 眼角血丝。
     * <p>
     * 眼睛整体<b>面向相机</b>（billboard），因此从任何角度看都是一只正对着你的眼睛——
     * 这比「贴在实体某个面上」更有压迫感，也保证信息在任何视角都可读。
     * </p>
     * <p>
     * 层数的三重冗余编码：
     * </p>
     * <ul>
     *     <li>眼睛整体尺寸随层数放大（{@link #EYE_WIDTH_PER_LAYER}）；</li>
     *     <li>瞳孔随层数收缩（{@link #PUPIL_SHRINK_PER_LAYER}）——瞳孔越小越显失控；</li>
     *     <li>血丝条数 = 层数 × {@link #VEINS_PER_LAYER}。</li>
     * </ul>
     * <p>
     * 满层时额外加一圈外爆的黄色光晕，配合更急促的心跳明灭，作为「可以打那一下重的了」的提示。
     * </p>
     * <p>
     * <b>削减：</b>眼眶 / 眼白分段数缩放；血丝整层可跳过。
     * <b>虹膜与瞳孔无论细节多低都完整绘制</b>——没有它们，剩下的只是一个椭圆片，
     * 读不出「眼睛」这个符号。
     * </p>
     */
    private static void drawEye(BufferBuilder b, Matrix4f m,
                                float cx, float cy, float cz, float width,
                                int layer, boolean atMax, float pulse, float time, int seedId,
                                float rgX, float rgY, float rgZ,
                                float upX, float upY, float upZ, float detail) {
        float halfW = width * EYE_HALF_WIDTH_FACTOR * (1f + EYE_WIDTH_PER_LAYER * layer) * pulse;
        float halfH = halfW * EYE_ASPECT;
        if (halfW <= 0.02f) {
            return;
        }
        float alpha = EYE_BASE_ALPHA * pulse;
        int segments = VisualLod.scaleSegments(EYE_SEGMENTS, EYE_SEGMENTS_MIN, detail);

        // ===== 眼白：上下两条杏仁弧之间的填充 =====
        float prevU = -halfW;
        float prevV = 0f;
        for (int i = 1; i <= segments; i++) {
            float t = -1f + 2f * i / segments;
            float u = t * halfW;
            float v = halfH * almond(t);
            // 上半 + 下半各一个梯形，拼成整只眼白
            planeQuad(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                    prevU, 0f, u, 0f, u, v, prevU, prevV,
                    C_SCLERA, alpha * 0.55f);
            planeQuad(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                    prevU, 0f, u, 0f, u, -v, prevU, -prevV,
                    C_SCLERA, alpha * 0.55f);
            prevU = u;
            prevV = v;
        }

        // ===== 眼眶：上下两条轮廓线（橙色，比眼白重，勾出形状）=====
        prevU = -halfW;
        prevV = 0f;
        for (int i = 1; i <= segments; i++) {
            float t = -1f + 2f * i / segments;
            float u = t * halfW;
            float v = halfH * almond(t);
            planeLine(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                    prevU, prevV, u, v, EYE_RIM_HALF, C_ORANGE, alpha, alpha);
            planeLine(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                    prevU, -prevV, u, -v, EYE_RIM_HALF, C_ORANGE, alpha, alpha);
            prevU = u;
            prevV = v;
        }

        // ===== 虹膜与瞳孔（不参与削减：它们是「这是眼睛」的最后依据）=====
        int irisSeg = VisualLod.scaleSegments(IRIS_SEGMENTS, IRIS_SEGMENTS_MIN, detail);
        float irisR = halfH * IRIS_RATIO;
        // 瞳孔随层数收缩——越疯狂瞳孔越针尖
        float pupilRatio = Math.max(0.18f, PUPIL_RATIO_BASE - PUPIL_SHRINK_PER_LAYER * layer);
        // 瞳孔轻微游移，避免像贴图一样死盯着一个方向
        float gazeU = Mth.sin(time * 0.8f + seedId * 0.7f) * halfW * 0.16f;
        planeDisc(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                gazeU, 0f, irisR, irisSeg, C_IRIS, alpha, alpha * 0.35f);
        planeDisc(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                gazeU, 0f, irisR * pupilRatio, irisSeg, C_PUPIL, alpha, alpha);

        // ===== 眼角血丝：条数 = 层数 × 2，是层数的第三重编码 =====
        if (VisualLod.keepLayer(detail, VEIN_KEEP_THRESHOLD)) {
            int veins = layer * VEINS_PER_LAYER;
            for (int i = 0; i < veins; i++) {
                // 血丝自虹膜边缘向外眼角发散，左右各半
                boolean right = (i % 2 == 0);
                float spread = ((i / 2) + 1) / (float) (veins / 2 + 1);
                float startU = (right ? 1f : -1f) * irisR * 0.9f + gazeU;
                float endU = (right ? 1f : -1f) * halfW * (0.55f + 0.4f * spread);
                float endV = halfH * (0.28f - 0.56f * ((i * 0.37f) % 1f));
                planeLine(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                        startU, 0f, endU, endV, EYE_RIM_HALF * 0.55f,
                        C_IRIS, alpha * 0.7f, 0f);
            }
        }

        // ===== 满层外爆光晕：告诉玩家「现在打那一下有加成」 =====
        if (atMax) {
            float burst = 0.5f + 0.5f * Mth.sin(time * 6f + seedId);
            planeRing(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                    halfW * 1.06f, halfW * (1.3f + 0.12f * burst), segments,
                    C_YELLOW, 0.42f * burst, 0f);
        }
    }

    /**
     * 杏仁形轮廓函数：给定横向归一化位置 t∈[-1,1]，返回该处的纵向半高比例。
     * <p>
     * 用 {@code (1 - t²)^0.7} 而非圆形的 {@code sqrt(1 - t²)}：
     * 指数小于 0.5 会让两端收得更尖、中段更饱满，这正是眼睛的轮廓；
     * 用正圆的话画出来是个椭圆片，读不出眼角。
     * </p>
     *
     * @param t 横向归一化位置（-1 = 左眼角，1 = 右眼角）
     * @return 纵向半高比例（0~1）
     */
    private static float almond(float t) {
        float s = 1f - t * t;
        if (s <= 0f) {
            return 0f;
        }
        return (float) Math.pow(s, 0.7);
    }

    // ==================== 层数刻痕（核心信息层）====================

    /**
     * 眼睛下方的一排竖条刻痕，条数 = 层数。
     * <p>
     * <b>这是整套视觉里唯一「可以数」的元素</b>，也是层数信息的主载体。
     * 判断「三条还是四条」比判断「眼睛红到什么程度」可靠得多，
     * 而且不依赖颜色，色觉障碍玩家同样能读。
     * </p>
     * <p>
     * <b>刻意完全不参与 LOD 削减</b>（连 detail 参数都不接收）：满层也才 36 个顶点，
     * 却承担了本渲染器存在的全部理由。削它是纯亏——
     * 远处把刻痕削掉，等于把「还差几层」这个信息也削掉了。
     * </p>
     * <p>
     * 最后一条（刚叠上的那层）比其余更亮，让「刚才那一下叠上了」有即时反馈。
     * </p>
     */
    private static void drawTallyMarks(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz, float width,
                                       int layer, float pulse,
                                       float rgX, float rgY, float rgZ,
                                       float upX, float upY, float upZ) {
        float halfW = width * EYE_HALF_WIDTH_FACTOR * (1f + EYE_WIDTH_PER_LAYER * layer) * pulse;
        float halfH = halfW * EYE_ASPECT;
        float baseV = -halfH * TALLY_DROP_RATIO;
        // 整排居中
        float totalWidth = TALLY_SPACING * (layer - 1);
        float startU = -totalWidth * 0.5f;

        for (int i = 0; i < layer; i++) {
            float u = startU + TALLY_SPACING * i;
            // 最新叠上的那条更亮
            boolean newest = (i == layer - 1);
            float[] col = newest ? C_YELLOW : C_ORANGE;
            float a = TALLY_ALPHA * (newest ? 1f : 0.75f) * pulse;
            planeQuad(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                    u - TALLY_HALF_WIDTH, baseV - TALLY_HALF_HEIGHT,
                    u + TALLY_HALF_WIDTH, baseV - TALLY_HALF_HEIGHT,
                    u + TALLY_HALF_WIDTH, baseV + TALLY_HALF_HEIGHT,
                    u - TALLY_HALF_WIDTH, baseV + TALLY_HALF_HEIGHT,
                    col, a);
        }
    }

    // ==================== 狂乱抖线 ====================

    /**
     * 身周的短促抖动折线：条数 = 层数 × 2，随时间高频抖动。
     * <p>
     * 这是纯氛围层——它的作用是让「这个目标正在发狂」在余光里也能被注意到，
     * 具体层数交给刻痕去表达。<b>抖动频率是全模组最高的</b>
     * （{@link #JITTER_SPEED}），与睡眠那种近乎静止的演出形成极端对比，
     * 二者同屏时不可能混淆。
     * </p>
     * <p>
     * 满层时抖动幅度额外放大，配合眼睛的外爆光晕一起提示「可以打了」。
     * </p>
     * <p>
     * <b>削减：</b>条数按细节缩放；整层由调用方按 {@link #JITTER_KEEP_THRESHOLD} 决定是否绘制。
     * 抖线角度是 {@code TAU × i / count} 均布的，但它本身就是随机抖动的、没有稳定方位，
     * 减少条数只表现为「抖得稀疏一点」，无需按步长抽取。
     * </p>
     */
    private static void drawFrenzyJitters(BufferBuilder b, Matrix4f m,
                                          float cx, float cyFoot, float cz,
                                          float width, float height,
                                          int layer, boolean atMax,
                                          float time, int seedId, float detail) {
        int baseCount = layer * JITTER_PER_LAYER;
        int count = VisualLod.scale(baseCount, detail);
        float radius = width * JITTER_RADIUS_FACTOR;
        float len = height * JITTER_LENGTH_FACTOR;
        float amp = (atMax ? 0.13f : 0.08f) * width;

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 400);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float heightFrac = 0.25f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float phase = rngFloat(s) * TAU;

            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);
            float baseX = cx + ca * radius;
            float baseZ = cz + sa * radius;
            float baseY = cyFoot + height * heightFrac;

            // 折线逐段抖动：每段的横向偏移由高频正弦驱动，相位逐段错开
            float px = baseX;
            float py = baseY;
            float pz = baseZ;
            for (int seg = 1; seg <= JITTER_SEGMENTS; seg++) {
                float u = (float) seg / JITTER_SEGMENTS;
                float wobble = Mth.sin(time * JITTER_SPEED + phase + seg * 1.7f) * amp;
                // 沿切向抖动（垂直于半径方向），沿径向轻微外扩
                float qx = baseX + (-sa) * wobble + ca * len * u * 0.35f;
                float qz = baseZ + ca * wobble + sa * len * u * 0.35f;
                float qy = baseY + len * u;
                float a = JITTER_ALPHA * (1f - u * 0.8f);
                float[] col = (u < 0.5f) ? C_YELLOW : C_ORANGE;
                worldLine(b, m, px, py, pz, qx, qy, qz, JITTER_HALF_WIDTH, col, a, a * 0.4f);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
    }

    // ==================== billboard 平面几何基元 ====================
    // 眼睛的全部图案都活在「面向相机的平面」里，用平面二维坐标 (u, v) 描述
    // 比逐点算三维方便得多，也不易出错。
    // 映射关系：P = center + right·u + up·v

    /**
     * 在 billboard 平面内绘制一个四边形（拆成两三角形），四个顶点共用同一颜色与 alpha。
     *
     * @param u1 顶点 1 的平面横坐标
     * @param v1 顶点 1 的平面纵坐标
     */
    private static void planeQuad(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float u1, float v1, float u2, float v2,
                                  float u3, float v3, float u4, float v4,
                                  float[] col, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];

        float x1 = cx + rgX * u1 + upX * v1, y1 = cy + rgY * u1 + upY * v1, z1 = cz + rgZ * u1 + upZ * v1;
        float x2 = cx + rgX * u2 + upX * v2, y2 = cy + rgY * u2 + upY * v2, z2 = cz + rgZ * u2 + upZ * v2;
        float x3 = cx + rgX * u3 + upX * v3, y3 = cy + rgY * u3 + upY * v3, z3 = cz + rgZ * u3 + upZ * v3;
        float x4 = cx + rgX * u4 + upX * v4, y4 = cy + rgY * u4 + upY * v4, z4 = cz + rgZ * u4 + upZ * v4;

        b.vertex(m, x1, y1, z1).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x2, y2, z2).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x3, y3, z3).color(r, g, bl, alpha).endVertex();

        b.vertex(m, x1, y1, z1).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x3, y3, z3).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x4, y4, z4).color(r, g, bl, alpha).endVertex();
    }

    /**
     * 在 billboard 平面内绘制一条带宽度的线段，两端 alpha 可分别指定。
     *
     * @param hw 线半宽（格）
     */
    private static void planeLine(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float u1, float v1, float u2, float v2,
                                  float hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        float du = u2 - u1;
        float dv = v2 - v1;
        float len = Mth.sqrt(du * du + dv * dv);
        if (len < 1.0e-6f) {
            return;
        }
        float nu = -dv / len * hw;
        float nv = du / len * hw;

        float r = col[0], g = col[1], bl = col[2];

        float au1 = u1 + nu, av1 = v1 + nv;
        float au2 = u1 - nu, av2 = v1 - nv;
        float bu1 = u2 + nu, bv1 = v2 + nv;
        float bu2 = u2 - nu, bv2 = v2 - nv;

        float ax1 = cx + rgX * au1 + upX * av1, ay1 = cy + rgY * au1 + upY * av1, az1 = cz + rgZ * au1 + upZ * av1;
        float ax2 = cx + rgX * au2 + upX * av2, ay2 = cy + rgY * au2 + upY * av2, az2 = cz + rgZ * au2 + upZ * av2;
        float bx1 = cx + rgX * bu1 + upX * bv1, by1 = cy + rgY * bu1 + upY * bv1, bz1 = cz + rgZ * bu1 + upZ * bv1;
        float bx2 = cx + rgX * bu2 + upX * bv2, by2 = cy + rgY * bu2 + upY * bv2, bz2 = cz + rgZ * bu2 + upZ * bv2;

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, by1, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, ay2, az2).color(r, g, bl, a1).endVertex();
    }

    /**
     * 在 billboard 平面内绘制一个径向渐变圆盘（中心 alpha、边缘 alpha 可不同）。
     *
     * @param cu       圆心的平面横坐标（相对 billboard 中心）
     * @param cv       圆心的平面纵坐标
     * @param radius   半径
     * @param segments 分段数
     */
    private static void planeDisc(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float cu, float cv, float radius, int segments,
                                  float[] col, float centerAlpha, float edgeAlpha) {
        if (radius <= 1.0e-4f || (centerAlpha <= 0.004f && edgeAlpha <= 0.004f)) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float ox = cx + rgX * cu + upX * cv;
        float oy = cy + rgY * cu + upY * cv;
        float oz = cz + rgZ * cu + upZ * cv;

        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= segments; i++) {
            float ang = TAU * i / segments;
            float eu = Mth.cos(ang) * radius;
            float ev = Mth.sin(ang) * radius;
            float ex = ox + rgX * eu + upX * ev;
            float ey = oy + rgY * eu + upY * ev;
            float ez = oz + rgZ * eu + upZ * ev;
            if (i > 0) {
                b.vertex(m, ox, oy, oz).color(r, g, bl, centerAlpha).endVertex();
                b.vertex(m, pex, pey, pez).color(r, g, bl, edgeAlpha).endVertex();
                b.vertex(m, ex, ey, ez).color(r, g, bl, edgeAlpha).endVertex();
            }
            pex = ex;
            pey = ey;
            pez = ez;
        }
    }

    /**
     * 在 billboard 平面内绘制一个圆环带（annulus），内外边缘 alpha 可分别指定。
     *
     * @param rInner   内半径
     * @param rOuter   外半径
     * @param segments 分段数
     */
    private static void planeRing(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float rInner, float rOuter, int segments,
                                  float[] col, float alphaInner, float alphaOuter) {
        if (rOuter <= rInner || segments < 3) {
            return;
        }
        if (alphaInner <= 0.004f && alphaOuter <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float prevCos = 1f;
        float prevSin = 0f;
        for (int i = 1; i <= segments; i++) {
            float a = TAU * i / segments;
            float ca = Mth.cos(a);
            float sa = Mth.sin(a);

            float ox0 = cx + (rgX * prevCos + upX * prevSin) * rOuter;
            float oy0 = cy + (rgY * prevCos + upY * prevSin) * rOuter;
            float oz0 = cz + (rgZ * prevCos + upZ * prevSin) * rOuter;
            float ox1 = cx + (rgX * ca + upX * sa) * rOuter;
            float oy1 = cy + (rgY * ca + upY * sa) * rOuter;
            float oz1 = cz + (rgZ * ca + upZ * sa) * rOuter;
            float ix0 = cx + (rgX * prevCos + upX * prevSin) * rInner;
            float iy0 = cy + (rgY * prevCos + upY * prevSin) * rInner;
            float iz0 = cz + (rgZ * prevCos + upZ * prevSin) * rInner;
            float ix1 = cx + (rgX * ca + upX * sa) * rInner;
            float iy1 = cy + (rgY * ca + upY * sa) * rInner;
            float iz1 = cz + (rgZ * ca + upZ * sa) * rInner;

            b.vertex(m, ox0, oy0, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ox1, oy1, oz1).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, iy1, iz1).color(r, g, bl, alphaInner).endVertex();

            b.vertex(m, ox0, oy0, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, iy1, iz1).color(r, g, bl, alphaInner).endVertex();
            b.vertex(m, ix0, iy0, iz0).color(r, g, bl, alphaInner).endVertex();

            // 推进到下一段：本段末端即下一段起点（漏掉这两行会让整环塌成一个扇形）
            prevCos = ca;
            prevSin = sa;
        }
    }

    /**
     * 世界空间的「十字双面」线段：沿世界 X、Z 轴各画一个四边形，
     * 使线段从任意水平视角皆可见、无需 billboard 计算。
     * <p>抖线用它而非 billboard——抖线是绕着身体一圈的，
     * 若做成 billboard 会全部正对相机、失去环绕感。</p>
     *
     * @param hw 线半宽（格）
     */
    private static void worldLine(BufferBuilder b, Matrix4f m,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        // 面1：沿世界 X 轴加宽
        worldQuad(b, m, x1 - hw, y1, z1, x1 + hw, y1, z1, x2 + hw, y2, z2, x2 - hw, y2, z2, r, g, bl, a1, a2);
        // 面2：沿世界 Z 轴加宽
        worldQuad(b, m, x1, y1, z1 - hw, x1, y1, z1 + hw, x2, y2, z2 + hw, x2, y2, z2 - hw, r, g, bl, a1, a2);
    }

    /**
     * 画一个世界空间四边形（拆成两三角形）：a→b 用 alpha {@code aAB}，c→d 用 alpha {@code aCD}。
     */
    private static void worldQuad(BufferBuilder b, Matrix4f m,
                                  float ax, float ay, float az, float bx, float by, float bz,
                                  float cx, float cy, float cz, float dx, float dy, float dz,
                                  float r, float g, float bl, float aAB, float aCD) {
        b.vertex(m, ax, ay, az).color(r, g, bl, aAB).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bl, aAB).endVertex();
        b.vertex(m, cx, cy, cz).color(r, g, bl, aCD).endVertex();

        b.vertex(m, ax, ay, az).color(r, g, bl, aAB).endVertex();
        b.vertex(m, cx, cy, cz).color(r, g, bl, aCD).endVertex();
        b.vertex(m, dx, dy, dz).color(r, g, bl, aCD).endVertex();
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
}
