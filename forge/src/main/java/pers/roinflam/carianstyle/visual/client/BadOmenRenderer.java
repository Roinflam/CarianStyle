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
import pers.roinflam.carianstyle.network.BadOmenSyncHandler;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 噩兆「诅咒」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@code MobEffectBadOmen}：目标攻击力 -20%、攻速 -20%、受到伤害 +25%、治疗量 -50%。
 * 由 {@code EnchantmentBadOmen} 攻击时施加，且该附魔的核心机制是
 * <b>「对已带噩兆的目标额外造成 50% 伤害」</b>——因此这套视觉的首要职责是让玩家
 * <b>一眼看出目标身上有没有噩兆</b>，从而知道下一击能否吃到加成。
 * </p>
 * <p>
 * <b>判定采用双重冗余：</b>{@code hasEffect(BAD_OMEN)} <b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(BAD_OMEN_SERIAL, id)}。
 * 与睡眠同理，噩兆几乎总是施加给正在交战的敌人，后者才是主力链路
 * （详见 {@link BadOmenSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>视觉区分（关键设计约束）：</b>本模组已有的红色系演出相当密集——出血是鲜红飞溅、
 * 切腹是暗红上涌、猩红腐败是深红孢子、龙雷是血红电柱。若噩兆再用红色主体，
 * 同屏叠加时完全无法分辨。故本渲染器：
 * <ul>
 *     <li><b>以墨黑为主体</b>，暗锈红只用于描边与尖端——远看是「一团黑」而非「一团红」；</li>
 *     <li><b>运动方向向下沉降</b>（污浊下滴、荆棘扎地），与出血的向外迸溅、
 *         切腹的向上蒸腾、腐败的向上升腾全部相反；</li>
 *     <li><b>标志物是尖锐的黑荆棘</b>——直线尖刺造型在本模组独一无二
 *         （其余演出都是圆环、弧带、星形、雾团）。</li>
 * </ul>
 * </p>
 * <p>
 * <b>四个元素：</b>
 * <ol>
 *     <li><b>身周黑荆棘</b>（{@link #drawThorns}）——核心标志。自地面向上并向外倾斜扎出的
 *         尖锐黑刺，底宽顶尖，随时间缓慢起伏（像在呼吸的活物）；</li>
 *     <li><b>头顶诅咒断环</b>（{@link #drawCurseRing}）——悬浮于头顶、逆向缓慢旋转的
 *         黑色断裂符文环，尖端染病态暗金；</li>
 *     <li><b>污浊滴落</b>（{@link #drawOoze}）——自躯干向下滴落的黑色短竖线，循环下沉、
 *         近地面淡出；</li>
 *     <li><b>不祥浸染盘</b>（{@link #drawBlightPool}）——脚下的墨黑渐变盘，压住整体重量。</li>
 * </ol>
 * </p>
 * <p>
 * 渲染管线沿用本模组统一方案：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，
 * 实体列表取自 {@link SharedEntityQuery} 的每帧共享查询，{@code POSITION_COLOR} 纯顶点绘制。
 * </p>
 *
 * <h3>v2（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个噩兆实体每帧的顶点量粗算：
 * </p>
 * <pre>
 * 诅咒断环（6 段 × 3 细分 × 6）            108
 * 不祥浸染盘（24 段 × 3）                   72
 * 污浊滴落（8 滴 × 6）                      48
 * 身周黑荆棘（7 根 × 2 三角）               42
 * ─────────────────────────────────────────
 * 合计                               ~270 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * <b>本渲染器是全模组最轻的一个</b>——比出血的 948、黄金树祝福的 2000 低了整整一个数量级。
 * 因此接入 {@link VisualLod} 的<b>主要目的是 {@link VisualLod#countInstance()}</b>：
 * 拥挤度是全局共享的，只要还有渲染器不登记，{@code crowdFactor} 就会被系统性高估，
 * 已接入的重量级渲染器就削减不足。它自己能省的那点顶点反而是次要的。
 * </p>
 * <p>
 * 削减本身仍按 {@link VisualLod#detail} 走，{@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，
 * <b>与优化前逐像素一致</b>；40 格外单实体降至约 140 顶点。
 * </p>
 * <p>
 * <b>荆棘完全不参与削减。</b>7 根刺一共才 42 顶点，却是「这是噩兆不是别的负面状态」的
 * 唯一依据（本模组只有这里用尖刺造型），且角度是
 * {@code orbit + TAU × i / 7} 均布的——削掉任意一根都会让环绕变得不对称。
 * <b>顶点性价比这么高的元素，削它是纯亏。</b>
 * 同理断环的 {@link #RING_DASHES} 也保持 6 段不变（断环的「断」本身就靠这 6 段的规律间隔表达，
 * 抽掉一半会读成「碎环」而非「断环」），只缩每段弧内部的细分数。
 * </p>
 *
 * <h3>v3（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v2 之后本渲染器的顶点量已经很低，但<b>颜色分配密度反而是同量级渲染器里偏高的</b>——
 * 旧实现的 {@code mix(a, b, t)} 与 {@code unpack(color)} <b>每次调用都 {@code new float[3]}</b>：
 * </p>
 * <pre>
 * 诅咒断环（6 段 × 3 细分 = 18 次 mix）      18
 * 污浊滴落（8 滴 × 1 次 mix）                 8
 * 各方法开头的 unpack（2+2+2+1）              7
 * ────────────────────────────────────────
 * 合计                     ~33 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 也就是说本渲染器<b>每 8 个顶点就要分配一个临时数组</b>，比例是全模组最差的。
 * 噩兆由 {@code EnchantmentBadOmen} 在每次攻击时施加，群战中同屏挂十几个目标很常见。
 * </p>
 * <p>
 * 现改为三条路径（工具见 {@link VisualColor}）：
 * </p>
 * <ol>
 *     <li><b>三个主题色类加载时预解包一次</b>（{@code C_} 前缀常量）；</li>
 *     <li><b>固定比例的混色也预算一次</b>——{@link #C_OOZE_HEAD} 是
 *         {@code mix(黑, 锈, 0.45)} 的结果，比例是<b>写死的常量</b>，
 *         旧实现却在每滴污浊的循环里重算重分配。现用静态初始化块算一次、永久复用；</li>
 *     <li><b>真正动态的插值色写入复用缓冲</b>——{@link #SCRATCH}（断环逐段的
 *         「边缘染金」渐变、浸染盘随时间的色相脉动）。</li>
 * </ol>
 * <p>
 * <b>为什么 {@link #C_OOZE_HEAD} 值得单列一条：</b>它是本次优化里唯一「靠观察发现的常量」。
 * {@link #drawOoze} 每滴都调 {@code mix(black, rust, 0.45f)}，
 * 三个参数全是编译期常量、结果恒定不变，却被放在了循环体里——
 * 这类「看起来动态、其实恒定」的表达式是最容易漏掉的分配点。
 * </p>
 * <p>
 * <b>{@link #drawOoze} 为什么不需要双缓冲：</b>{@link #verticalLine} 确实要
 * <b>同时</b>持有顶端色与底端色，但这两个现在<b>都是只读常量</b>
 * （{@link #C_OMEN_BLACK} 与 {@link #C_OOZE_HEAD}），彼此不会互相覆盖。
 * 只有当两端都是<b>动态</b>色时才必须开两个缓冲——那是螺旋 / 刀痕 / 花瓣的情形。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#mixInto} 与旧 {@code mix} 都是归一化域线性插值 +
 * clamp01，{@link VisualColor#lerpInto} 保留了旧 {@code lerpRgb} 在 0~255 整数域取整的行为，
 * {@link VisualColor#constant} 与旧 {@code unpack} 是同一个 {@code /255f} 公式——
 * 输出的每个颜色分量与 v2 数值相等。
 * </p>
 *
 * @author FlameForge
 * @version 3
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class BadOmenRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== v2 LOD 下限 =====
    /** 不祥浸染盘的最少分段数 */
    private static final int POOL_SEGMENTS_MIN = 8;
    /**
     * 断环每段弧的最少细分数。
     * <p>每段弧只占 {@code (TAU / 6) × 0.5 = 30°}，2 段细分近似 30° 圆弧已经足够平滑；
     * 降到 1 会让弧退化成直线弦、断环看起来像折线六边形的碎片。</p>
     */
    private static final int RING_SUB_MIN = 2;

    // ===== 配色（0xRRGGBB）=====
    /** 诅咒墨黑：荆棘主体、污浊、浸染盘的主色。远看整体是「黑」而非「红」 */
    private static final int OMEN_BLACK = 0x140A12;
    /** 血锈色：荆棘尖端与断环描边的暗褐红，只做点缀不做主体 */
    private static final int OMEN_RUST = 0x6E1F1A;
    /** 病态暗金：符文环的强调色，压低饱和度以维持「不祥」而非「神圣」的语义 */
    private static final int OMEN_SICK_GOLD = 0x8A7A2E;

    /** 污浊头端的固定混色比例（黑 → 锈），见 {@link #C_OOZE_HEAD} */
    private static final float OOZE_HEAD_MIX = 0.45f;

    // ===== v3：预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    /** 诅咒墨黑（荆棘底部 / 污浊尾端 / 断环中段 / 浸染盘基色） */
    private static final float[] C_OMEN_BLACK = VisualColor.constant(OMEN_BLACK);
    /** 血锈色（荆棘尖端） */
    private static final float[] C_OMEN_RUST = VisualColor.constant(OMEN_RUST);
    /** 病态暗金（断环两端染色） */
    private static final float[] C_OMEN_SICK_GOLD = VisualColor.constant(OMEN_SICK_GOLD);

    /**
     * v3：污浊头端色（{@code mix(墨黑, 血锈, 0.45)} 的<b>预算结果</b>，⚠ 只读）。
     * <p>
     * 旧实现在 {@link #drawOoze} 的循环里对每滴污浊都算一次
     * {@code mix(black, rust, 0.45f)}——三个参数全是编译期常量、结果恒定不变，
     * 却因为写在循环体内而每帧分配 8 次。这类「看起来动态、其实恒定」的表达式
     * 是最容易漏掉的分配点，故单独提为常量。
     * </p>
     * <p>
     * 用静态初始化块而非硬编码一个混合后的十六进制值，是为了保证与旧实现
     * <b>逐位一致</b>——硬编码要经过一次四舍五入，会在某个通道上差 1/255。
     * </p>
     */
    private static final float[] C_OOZE_HEAD = new float[VisualColor.RGB];

    static {
        VisualColor.mixInto(C_OOZE_HEAD, C_OMEN_BLACK, C_OMEN_RUST, OOZE_HEAD_MIX);
    }

    /**
     * v3：动态插值色的复用缓冲（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * 仅用于两处<b>真正随时间 / 逐元素变化</b>的颜色：
     * {@link #drawCurseRing} 的逐段「边缘染金」渐变、{@link #drawBlightPool} 的色相脉动。
     * 二者不会同时活跃（顺序调用、互不嵌套），且都是「写入 → 紧接着消费」，故一个缓冲即可。
     * </p>
     * <p>
     * <b>{@link #drawOoze} 刻意不使用本缓冲</b>——它的两端色现在都是只读常量
     * （{@link #C_OMEN_BLACK} / {@link #C_OOZE_HEAD}），无需动态计算，
     * 也就绕开了「两个动态色同时存活」这个坑（详见类注释）。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] SCRATCH = new float[VisualColor.RGB];

    // ===== 身周黑荆棘（核心标志）=====
    /** 荆棘数量 */
    private static final int THORN_COUNT = 7;
    /** 荆棘环绕半径系数（× 实体宽度） */
    private static final float THORN_RADIUS_FACTOR = 0.72f;
    /** 荆棘高度系数（× 实体高度） */
    private static final float THORN_HEIGHT_FACTOR = 0.62f;
    /** 荆棘底部半宽（格） */
    private static final float THORN_BASE_HALF = 0.075f;
    /** 荆棘向外倾斜的比例（尖端相对底部向外偏移的量，× 荆棘高度） */
    private static final float THORN_LEAN = 0.32f;
    /** 荆棘起伏速度——缓慢，像活物在呼吸 */
    private static final float THORN_PULSE_SPEED = 0.9f;
    /** 荆棘整体缓慢绕行速度 */
    private static final float THORN_ORBIT_SPEED = 0.12f;
    private static final float THORN_BASE_ALPHA = 0.82f;

    // ===== 头顶诅咒断环 =====
    /** 断环悬浮高度系数（× 实体高度） */
    private static final float RING_HEIGHT_FACTOR = 1.25f;
    /** 断环半径系数（× 实体宽度） */
    private static final float RING_RADIUS_FACTOR = 0.6f;
    /** 断环的断裂段数 */
    private static final int RING_DASHES = 6;
    /** 每段实心占比（0~1），其余留空形成「断裂」 */
    private static final float RING_FILL_RATIO = 0.5f;
    /** 断环旋转速度（负值＝逆向，与多数演出的顺向旋转相反，强化「反常」感） */
    private static final float RING_ROT_SPEED = -0.45f;
    private static final float RING_BASE_ALPHA = 0.7f;
    /** 断环每段弧的细分数（控性能；v2 起按细节系数缩放，下限 {@link #RING_SUB_MIN}） */
    private static final int RING_SUB = 3;

    // ===== 污浊滴落 =====
    /** 同时存在的污浊数量 */
    private static final int OOZE_COUNT = 8;
    /** 单滴污浊的下落循环速度 */
    private static final float OOZE_FALL_SPEED = 0.42f;
    /** 污浊起始高度系数（× 实体高度） */
    private static final float OOZE_START_HEIGHT_FACTOR = 0.75f;
    /** 污浊线条半宽（格） */
    private static final float OOZE_HALF_WIDTH = 0.032f;
    private static final float OOZE_BASE_ALPHA = 0.75f;

    // ===== 不祥浸染盘 =====
    private static final int POOL_SEGMENTS = 24;
    private static final float POOL_RADIUS_FACTOR = 1.25f;
    private static final float POOL_BASE_ALPHA = 0.42f;

    private BadOmenRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有携带噩兆生物的诅咒视觉。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
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

        MobEffect badOmen = CarianStylePotion.BAD_OMEN.get();

        Matrix4f matrix = VisualBatch.matrix();
        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            if (!hasBadOmen(entity, badOmen)) {
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

            // ⭐ v2：本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0，视觉与优化前一致
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度。本渲染器接入 VisualLod 的首要意义就在这一行——
            // 只要还有渲染器不登记，全局 crowdFactor 就会被系统性高估
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawBlightPool(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), detail);
            drawOoze(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), detail);
            drawThorns(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId());
            drawCurseRing(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), detail);
        }
    }

    /**
     * 判断实体是否携带噩兆（双重冗余判定）。
     *
     * @param entity  待判定实体
     * @param badOmen 噩兆效果对象（可能为 null）
     * @return 携带噩兆返回 true
     */
    private static boolean hasBadOmen(LivingEntity entity, @Nullable MobEffect badOmen) {
        if (badOmen != null && entity.hasEffect(badOmen)) {
            return true;
        }
        return ClientSyncEffectManager.shouldRenderEffect(
                BadOmenSyncHandler.BAD_OMEN_SERIAL, entity.getId());
    }

    // ==================== 身周黑荆棘（核心标志）====================

    /**
     * 环绕实体的黑色荆棘尖刺：自地面向上并向外倾斜扎出，底宽顶尖，逐根错相地缓慢起伏。
     * <p>
     * 每根刺用「十字双面三角形」绘制（沿世界 X、Z 轴各一个三角形），
     * 从任意水平视角皆可见、无需 billboard 计算——手法与
     * {@code AoeEffectRenderer} 的闪电电柱、{@code GravitasDistortionRenderer} 的牢笼柱一致，
     * 但这里底宽顶尖收成三角形而非等宽四边形，才有「刺」的锐利感。
     * </p>
     * <p>
     * 颜色由底部墨黑过渡到尖端血锈红——尖端那一点红是整套视觉里<b>唯一的暖色</b>，
     * 用来点出「诅咒」的恶意，同时保证远看主体仍是黑的、不与出血 / 切腹撞色。
     * </p>
     * <p>
     * <b>v2：完全不参与 LOD 削减，故不接收 detail 参数。</b>
     * 7 根刺一共才 42 顶点，却承担「这是噩兆」的全部辨识度；且角度是
     * {@code orbit + TAU × i / THORN_COUNT} 均布的，削掉任意一根都会破坏环绕的对称性。
     * 顶点性价比这么高的元素，削它是纯亏。
     * </p>
     * <p>v3：两个配色改用只读常量，本方法零分配。</p>
     */
    private static void drawThorns(BufferBuilder b, Matrix4f m,
                                   float cx, float cyFoot, float cz,
                                   float width, float height,
                                   float time, int seedId) {
        float radius = width * THORN_RADIUS_FACTOR;
        float baseHeight = height * THORN_HEIGHT_FACTOR;
        // 极缓慢的整体绕行，避免刺的位置像贴图一样死板
        float orbit = time * THORN_ORBIT_SPEED + seedId * 0.5f;

        final float[] black = C_OMEN_BLACK;
        final float[] rust = C_OMEN_RUST;

        for (int i = 0; i < THORN_COUNT; i++) {
            long s = seedFor(seedId, i + 300);
            float lenRand = 0.65f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float pulsePhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float angJitter = (rngFloat(s) - 0.5f) * 0.35f;

            float ang = orbit + TAU * i / THORN_COUNT + angJitter;
            float cosA = (float) Math.cos(ang);
            float sinA = (float) Math.sin(ang);

            // 缓慢起伏：像活物在呼吸
            float pulse = 0.8f + 0.2f * Mth.sin(time * THORN_PULSE_SPEED + pulsePhase);
            float thornLen = baseHeight * lenRand * pulse;

            // 底部位置
            float bx = cx + cosA * radius;
            float bz = cz + sinA * radius;
            // 尖端：向上 + 向外倾斜
            float lean = thornLen * THORN_LEAN;
            float tx = cx + cosA * (radius + lean);
            float tz = cz + sinA * (radius + lean);
            float ty = cyFoot + thornLen;

            float alpha = THORN_BASE_ALPHA * pulse;
            thornSpike(b, m, bx, cyFoot + Y_OFFSET, bz, tx, ty, tz,
                    THORN_BASE_HALF, black, rust, alpha);
        }
    }

    /**
     * 绘制一根「十字双面」尖刺：沿世界 X、Z 轴各画一个三角形（底边宽 {@code 2×hw}、顶点收成一点），
     * 使尖刺从任意水平视角皆可见。底部用 {@code colBase}、尖端用 {@code colTip}。
     *
     * @param hw 底部半宽（格）
     */
    private static void thornSpike(BufferBuilder b, Matrix4f m,
                                   float bx, float by, float bz,
                                   float tx, float ty, float tz,
                                   float hw, float[] colBase, float[] colTip, float alpha) {
        if (alpha <= 0.002f) {
            return;
        }
        float tipAlpha = alpha * 0.85f;
        // 面1：底边沿世界 X 轴展开
        triangle(b, m,
                bx - hw, by, bz, bx + hw, by, bz, tx, ty, tz,
                colBase, alpha, colTip, tipAlpha);
        // 面2：底边沿世界 Z 轴展开
        triangle(b, m,
                bx, by, bz - hw, bx, by, bz + hw, tx, ty, tz,
                colBase, alpha, colTip, tipAlpha);
    }

    /**
     * 画一个三角形：前两个顶点（底边）用 {@code colA/alphaA}，第三个顶点（尖端）用 {@code colB/alphaB}。
     * <p><b>注意：</b>本方法会同时读取 {@code colA} 与 {@code colB}，调用方须保证二者不是同一个
     * 可写缓冲。当前唯一调用点 {@link #thornSpike} 传的是两个只读常量，安全。</p>
     */
    private static void triangle(BufferBuilder b, Matrix4f m,
                                 float ax, float ay, float az,
                                 float bx, float by, float bz,
                                 float cx, float cy, float cz,
                                 float[] colA, float alphaA, float[] colB, float alphaB) {
        b.vertex(m, ax, ay, az).color(colA[0], colA[1], colA[2], alphaA).endVertex();
        b.vertex(m, bx, by, bz).color(colA[0], colA[1], colA[2], alphaA).endVertex();
        b.vertex(m, cx, cy, cz).color(colB[0], colB[1], colB[2], alphaB).endVertex();
    }

    // ==================== 头顶诅咒断环 ====================

    /**
     * 头顶悬浮的黑色断裂符文环：{@link #RING_DASHES} 段弧，每段只占该格 {@link #RING_FILL_RATIO}，
     * 其余留空形成「断裂」；整环<b>逆向</b>缓慢旋转。
     * <p>
     * 逆向旋转是刻意的——本模组其余带旋转的演出（法阵、光环、符文刻度）几乎都是顺向，
     * 反着转会带来微妙的「不对劲」感，正契合「噩兆」的语义。
     * </p>
     * <p>
     * <b>v2：只缩每段弧内部的细分数，{@link #RING_DASHES} 保持 6 段不变。</b>
     * 断环的「断」正是靠这 6 段的规律间隔表达的——抽掉一半会读成「碎环」而非「断环」，
     * 语义就变了。而每段弧只占 30°，细分从 3 降到 2 对弧线平滑度几乎无影响。
     * </p>
     * <p>
     * <b>v3：本方法是本渲染器分配最密集的一处</b>（每段弧一次 mix，共 18 次 / 实体 / 帧）。
     * 现改为写入 {@link #SCRATCH} 后立即被同段的 6 个顶点消费。
     * 注意 6 个顶点全部使用同一个颜色（只是位置不同），故单缓冲足够。
     * </p>
     */
    private static void drawCurseRing(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz,
                                      float width, float height,
                                      float time, int seedId, float detail) {
        float ringY = cyFoot + height * RING_HEIGHT_FACTOR;
        float radius = width * RING_RADIUS_FACTOR;
        float rot = time * RING_ROT_SPEED + seedId * 0.3f;
        // 缓慢明灭，暗示诅咒在「跳动」
        float pulse = 0.72f + 0.28f * Mth.sin(time * 1.1f + seedId * 0.8f);
        float alpha = RING_BASE_ALPHA * pulse;

        final float[] black = C_OMEN_BLACK;
        final float[] gold = C_OMEN_SICK_GOLD;

        float rInner = radius * 0.86f;
        float rOuter = radius;
        // v2：每段弧细分按细节系数缩放（下限 RING_SUB_MIN）
        final int sub = VisualLod.scaleSegments(RING_SUB, RING_SUB_MIN, detail);

        for (int i = 0; i < RING_DASHES; i++) {
            float a0 = rot + TAU * i / RING_DASHES;
            float a1 = a0 + (TAU / RING_DASHES) * RING_FILL_RATIO;

            float prevOx = 0f, prevOz = 0f, prevIx = 0f, prevIz = 0f;
            for (int sIdx = 0; sIdx <= sub; sIdx++) {
                float a = a0 + (a1 - a0) * sIdx / sub;
                float cosA = (float) Math.cos(a);
                float sinA = (float) Math.sin(a);
                float ox = cx + rOuter * cosA, oz = cz + rOuter * sinA;
                float ix = cx + rInner * cosA, iz = cz + rInner * sinA;

                if (sIdx > 0) {
                    // 每段两端染病态暗金、中间墨黑，形成「符文」的断续质感
                    float u = (float) sIdx / sub;
                    float edgeness = Math.abs(u - 0.5f) * 2f;
                    // v3：无分配插值，写入复用缓冲后立即被下方 6 个顶点消费
                    VisualColor.mixInto(SCRATCH, black, gold, edgeness * 0.7f);

                    b.vertex(m, prevOx, ringY, prevOz)
                            .color(SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha).endVertex();
                    b.vertex(m, ox, ringY, oz)
                            .color(SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha).endVertex();
                    b.vertex(m, ix, ringY, iz)
                            .color(SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha).endVertex();

                    b.vertex(m, prevOx, ringY, prevOz)
                            .color(SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha).endVertex();
                    b.vertex(m, ix, ringY, iz)
                            .color(SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha).endVertex();
                    b.vertex(m, prevIx, ringY, prevIz)
                            .color(SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha).endVertex();
                }
                prevOx = ox;
                prevOz = oz;
                prevIx = ix;
                prevIz = iz;
            }
        }
    }

    // ==================== 污浊滴落 ====================

    /**
     * 自躯干向下滴落的黑色污浊：短竖线循环下沉，头端实、尾端渐隐，接近地面时整体淡出。
     * <p>方向<b>向下</b>是关键——出血是向外迸溅、切腹是向上蒸腾，噩兆必须往下沉，
     * 才能在三者同时挂载时靠运动方向区分开。</p>
     * <p>
     * <b>v2：数量按细节系数缩放。</b>污浊位置由 {@code seedFor(entityId, i + 600)} 决定
     * （角度是纯随机、与下标无关），截断尾部时保留污浊的下落轨迹完全不变。
     * </p>
     * <p>
     * <b>v3：本方法现在完全零分配。</b>头端色 {@link #C_OOZE_HEAD} 的混色比例是写死的
     * {@value #OOZE_HEAD_MIX}，结果恒定，已在类加载时算好；尾端色直接用
     * {@link #C_OMEN_BLACK}。两端都是只读常量，因此
     * {@link #verticalLine} 同时持有二者也不存在互相覆盖的问题。
     * </p>
     */
    private static void drawOoze(BufferBuilder b, Matrix4f m,
                                 float cx, float cyFoot, float cz,
                                 float width, float height,
                                 float time, int seedId, float detail) {
        float startHeight = height * OOZE_START_HEIGHT_FACTOR;

        int count = VisualLod.scale(OOZE_COUNT, detail);

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 600);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.35f + 0.65f * rngFloat(s);
            s = rngNext(s);
            float lenRand = 0.7f + 0.6f * rngFloat(s);

            float t = frac(time * OOZE_FALL_SPEED + phase);

            float px = cx + (float) Math.cos(ang) * width * 0.38f * radFactor;
            float pz = cz + (float) Math.sin(ang) * width * 0.38f * radFactor;

            float headY = cyFoot + startHeight * (1f - t);
            float tailY = headY + height * 0.16f * lenRand;

            // 接近地面时淡出
            float alpha = OOZE_BASE_ALPHA * (1f - smoothstep(0.72f, 1f, t)) * smoothstep(0f, 0.08f, t);
            if (alpha <= 0.01f) {
                continue;
            }

            // 头端偏锈红（新滴出的），尾端墨黑——两端均为预算好的只读常量，零分配
            verticalLine(b, m, px, pz, tailY, headY, OOZE_HALF_WIDTH,
                    C_OMEN_BLACK, alpha * 0.4f, C_OOZE_HEAD, alpha);
        }
    }

    // ==================== 不祥浸染盘 ====================

    /**
     * 脚下的墨黑渐变盘：中心较实、边缘渐隐，缓慢呼吸。
     * <p>作用是压住整体重量，让荆棘看起来是「从被污染的地面长出来的」而非凭空插着。</p>
     * <p>v2：分段数按细节系数缩放（下限 {@link #POOL_SEGMENTS_MIN}）。</p>
     * <p>v3：颜色改用 {@link VisualColor#lerpInto} 写入复用缓冲，省掉中间 int 与新数组；
     * 取整行为与旧的 {@code lerpRgb → unpack} 链路逐位一致。</p>
     */
    private static void drawBlightPool(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz, float width,
                                       float time, int seedId, float detail) {
        float breath = 0.88f + 0.12f * Mth.sin(time * 0.75f + seedId * 0.45f);
        float radius = width * POOL_RADIUS_FACTOR * breath;
        // v3：无分配插值（0~255 域取整，与旧 lerpRgb 逐位一致）
        VisualColor.lerpInto(SCRATCH, OMEN_BLACK, OMEN_RUST,
                0.18f + 0.12f * Mth.sin(time * 0.6f + seedId));
        int segments = VisualLod.scaleSegments(POOL_SEGMENTS, POOL_SEGMENTS_MIN, detail);
        drawDisc(b, m, cx, cy, cz, radius, segments,
                SCRATCH[0], SCRATCH[1], SCRATCH[2], POOL_BASE_ALPHA * breath);
    }

    // ==================== 几何基元 ====================

    /**
     * 竖直线段（面向世界 X 轴的四边形），两端颜色与 alpha 可分别指定。
     * <p>污浊滴落用的是细短竖线，单面即可——它贴身且数量多，做成双面反而糊成一团。</p>
     * <p><b>注意：</b>本方法会同时读取 {@code colTop} 与 {@code colBottom}，
     * 调用方须保证二者不是同一个可写缓冲。当前唯一调用点 {@link #drawOoze}
     * 传的是两个只读常量，安全。</p>
     */
    private static void verticalLine(BufferBuilder b, Matrix4f m,
                                     float x, float z, float yTop, float yBottom,
                                     float hw, float[] colTop, float alphaTop,
                                     float[] colBottom, float alphaBottom) {
        if (yTop <= yBottom) {
            return;
        }
        b.vertex(m, x - hw, yTop, z).color(colTop[0], colTop[1], colTop[2], alphaTop).endVertex();
        b.vertex(m, x + hw, yTop, z).color(colTop[0], colTop[1], colTop[2], alphaTop).endVertex();
        b.vertex(m, x + hw, yBottom, z).color(colBottom[0], colBottom[1], colBottom[2], alphaBottom).endVertex();

        b.vertex(m, x - hw, yTop, z).color(colTop[0], colTop[1], colTop[2], alphaTop).endVertex();
        b.vertex(m, x + hw, yBottom, z).color(colBottom[0], colBottom[1], colBottom[2], alphaBottom).endVertex();
        b.vertex(m, x - hw, yBottom, z).color(colBottom[0], colBottom[1], colBottom[2], alphaBottom).endVertex();
    }

    /**
     * 水平径向渐变圆盘（中心 alpha、边缘 0）。
     */
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

    // ==================== 数学辅助 ====================
    // v3 说明：原先的 mix(float[], float[], float)、lerpRgb(int, int, float) 与 unpack(int)
    // 已删除——三者是本类此前唯一的堆分配来源（每次调用 new float[3]），
    // 现全部由 VisualColor 的 constant() / mixInto() / lerpInto() 取代。
    // 若后续新增元素需要配色计算，请一律走 VisualColor.*Into(dst, ...) + 复用缓冲，
    // 不要重新引入返回新数组的写法；固定比例的混色请像 C_OOZE_HEAD 那样提到静态初始化块里。

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
