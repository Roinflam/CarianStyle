package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.List;

/**
 * 魔法辉剑「辉石魔法」特效渲染器（纯客户端自绘）。
 * <p>
 * 为 {@link EntityGlintblades} 补上卡利亚辉石魔法的视觉：悬浮期在剑柄后浮现符文法阵、
 * 刀身裹上魔力光晕；发射后转为高速拖尾 + 剑尖白热核。
 * 卡利亚圆阵一次生成 8 把剑，8 个法阵自然就围成一圈——<b>圆阵的「阵」是由剑各自的法阵拼出来的，
 * 不需要额外画一个大法阵</b>，也就不需要新增任何网络包。
 * </p>
 *
 * <h3>为什么不走 AoeEffectPacket</h3>
 * <p>
 * 本模组已有的定点特效链路（{@code CarianStyleEffects} → {@code AoeEffectPacket} →
 * {@code AoeEffectRenderer}）是为<b>一次性、无载体</b>的演出设计的：发包时刻确定坐标，
 * 客户端按 age 播完即销毁。而辉剑特效的载体是一个<b>本来就在同步的实体</b>，
 * 它的位置、朝向、大小、蓄力进度客户端全都拿得到——再发一遍包纯属重复传输，
 * 而且延迟发射的悬浮期长达 55~125 tick，靠一次性特效根本对不齐。
 * </p>
 * <p>
 * 因此本渲染器<b>完全由实体驱动</b>：不新增包、不改
 * {@code AoeEffectPacket}/{@code AoeEffectRenderer}，三个附魔类
 * （卡利亚圆阵 / 巨剑阵 / 卡利亚式奉还）也<b>一行都不用改</b>。
 * </p>
 *
 * <h3>渲染管线</h3>
 * <p>
 * 沿用本模组统一方案：{@link RenderLevelStageEvent} 的 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，
 * GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理（与其余渲染器合并为一次状态切换 + 一次 draw call），
 * {@code POSITION_COLOR} 纯顶点绘制，无贴图、无原版粒子，颜色走 {@link VisualColor} 零分配路径。
 * </p>
 *
 * <h3>v2 修正：改用 {@link SharedEntityQuery} 的辉剑缓存</h3>
 * <p>
 * <b>此前本渲染器是唯一自行做范围查询的世界渲染器</b>——因为
 * {@link SharedEntityQuery#livingEntitiesNearCamera} 那份共享列表的元素类型是
 * {@code LivingEntity}，而辉剑继承自 {@code ThrowableProjectile}，不在其中。
 * </p>
 * <p>
 * 于是它自己开了一次 {@code getEntitiesOfClass}，且半径 64 与共享列表的 48 不一致——
 * 两个数字一个写在渲染器里、一个写在 {@link SharedEntityQuery} 里，
 * 对不上也没人会发现。
 * </p>
 * <p>
 * 现在 {@link SharedEntityQuery} 单开了一份辉剑专用的帧级缓存
 * （{@link SharedEntityQuery#glintbladesNearCamera}，半径
 * {@link SharedEntityQuery#PROJECTILE_QUERY_RANGE}）。<b>收益不在于省掉一次查询</b>
 * ——本渲染器每帧本来也只查一次；真正的收益是范围常量集中管理、
 * 且将来若给辉剑加第二个渲染器（例如命中特效）不会再冒出第三次查询。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 悬浮期（每把剑）：
 *   辉石符文阵（双环 336 + 六芒星 36 + 外缘刻度 72）   ~444
 *   环绕碎片（6 颗 × 12）                                72
 *   剑尖光核                                             12
 *   ───────────────────────────────────────────────────
 *   小计                                              ~528
 *
 * 飞行期（每把剑）：拖尾 108 + 光核 12 ≈ 120
 * </pre>
 * <p>
 * 卡利亚圆阵一次 8 把、巨剑阵 3 把，悬浮期同屏峰值约 4200 顶点——比出血单个患者的 948
 * 高不了太多，但<b>会连着 3~6 秒持续存在</b>，故全部元素接入 {@link VisualLod}：
 * 12 格内系数恒为 1.0（与不做 LOD 逐像素一致），远处逐步削减，40 格外单把剑降至约 140 顶点。
 * </p>
 * <p>
 * <b>细节系数按「到剑体边界的距离」取</b>，而非到中心：巨剑阵的剑 {@code size} 为 7.5，
 * 视觉半径约 5 格，玩家贴着巨剑站时到中心的距离已经不小，按中心算会被误判为「远」并削减，
 * 但那把剑就横在眼前。这与 {@code AuraGroundRenderer} 处理圣域大圈是同一个问题。
 * </p>
 *
 * <h3>削减策略</h3>
 * <ul>
 *     <li><b>符文阵的环是首要杠杆</b>（占悬浮期顶点的 78%），分段数按细节缩放，
 *         下限 {@link #RUNE_SEGMENTS_MIN}；</li>
 *     <li><b>六芒星完全不削</b>——仅 36 顶点却是「这是卡利亚辉石魔法」的唯一辨识依据，
 *         且六个顶点是均布的，减到 4 个就不是六芒星了；</li>
 *     <li><b>外缘刻度与环绕碎片按步长抽取</b>——角度是 {@code i × (TAU / 总数)} 均布的，
 *         截断前 N 个会让法阵明显「缺一块」。</li>
 * </ul>
 *
 * @author FlameForge
 * @version 2.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class GlintbladesEffectRenderer {

    /**
     * 距离裁剪（格）。
     * <p>必须 ≤ {@link SharedEntityQuery#PROJECTILE_QUERY_RANGE}，否则会漏掉辉剑。</p>
     */
    private static final double CULL = 64.0;
    private static final double CULL_SQR = CULL * CULL;

    private static final float TAU = (float) (Math.PI * 2.0);

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    /** 出现动画时长（tick），须与 {@code GlintbladesRender.APPEAR_TICKS} 一致 */
    private static final float APPEAR_TICKS = 5.0f;

    // ===== 配色（0xRRGGBB，卡利亚辉石：冷蓝白）=====
    /** 辉石白热：剑尖光核、符文阵高光 */
    private static final int GLINT_CORE = 0xEAF4FF;
    /** 辉石蓝：主色，刀身光晕与法阵主环 */
    private static final int GLINT_BLUE = 0x8FD2FF;
    /** 辉石深蓝：法阵内环与拖尾末端的暗部 */
    private static final int GLINT_DEEP = 0x3A6FC0;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，会永久污染该配色。
    private static final float[] C_CORE = VisualColor.constant(GLINT_CORE);
    private static final float[] C_BLUE = VisualColor.constant(GLINT_BLUE);
    private static final float[] C_DEEP = VisualColor.constant(GLINT_DEEP);

    /**
     * 动态插值色的复用缓冲（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>本渲染器不存在「两个动态色同时存活」的场景——每处都是
     * 「算一个色 → 立刻画完 → 不再用」，故一个缓冲即可。
     * 若将来新增两端异色的渐变线段，必须再加一个独立缓冲。</p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] SCRATCH = new float[VisualColor.RGB];

    // v4.0 说明：原「刀身魔力光晕」（沿刀身的十字光带）已整体移除——
    // 剑体本身改为满亮度自发光，不再需要外挂线条来表达魔力，
    // 加了反而像「给铁剑贴了特效」而不是「这把剑本身是魔法造物」。
    // 保留的元素：辉石符文阵、剑尖光核、环绕碎片、飞行拖尾。
    // v4.1 说明：「剑尖锁定引导虚线」也已移除——原作没有这根线，
    //     剑尖朝向本身就足以表达锁定，多一根线反而像塔防游戏的激光指示器。
    // v2 说明：随引导线一起遗留下来的 frac(float) 工具方法也已删除（自 v4.1 起就无人调用）。

    // ===== 几何比例（均为 × size 的系数）=====
    /** 剑尖相对中心的距离系数。物品模型对角线半长约 0.707，取略小值贴合可见刀尖 */
    private static final float TIP_FACTOR = 0.62f;
    /** 剑柄相对中心的距离系数（负号表示反方向） */
    private static final float HILT_FACTOR = -0.56f;

    // ===== 辉石符文阵（悬浮期核心标志）=====
    private static final int RUNE_SEGMENTS = 28;
    private static final int RUNE_SEGMENTS_MIN = 10;
    /** 法阵外环半径系数 */
    private static final float RUNE_RADIUS_FACTOR = 0.52f;
    /** 法阵基础旋转速度（弧度/秒），蓄力时会加速 */
    private static final float RUNE_ROT_SPEED = 1.05f;
    /** 外缘刻度数量 */
    private static final int RUNE_TICK_COUNT = 12;
    private static final float RUNE_BASE_ALPHA = 0.85f;

    // ===== 环绕碎片（悬浮期，辉石碎屑绕刀身公转）=====
    private static final int MOTE_COUNT = 6;
    private static final float MOTE_ORBIT_SPEED = 1.7f;

    // ===== 飞行拖尾 =====
    private static final int TRAIL_SEGMENTS = 9;
    private static final int TRAIL_SEGMENTS_MIN = 3;
    /** 拖尾总长度系数（× size），再乘一个由速度决定的因子 */
    private static final float TRAIL_LENGTH_FACTOR = 2.2f;

    // ===== LOD 保留阈值 =====
    /** 环绕碎片的保留阈值：极小的装饰光点 */
    private static final float MOTE_KEEP_THRESHOLD = 0.4f;
    /** 法阵外缘刻度的保留阈值 */
    private static final float RUNE_TICK_KEEP_THRESHOLD = 0.5f;

    private GlintbladesEffectRenderer() {
    }

    /**
     * 世界渲染回调：绘制相机附近全部辉剑的魔法特效。
     * <p>
     * v2：实体列表改从 {@link SharedEntityQuery#glintbladesNearCamera} 取，
     * 不再自行做 {@code getEntitiesOfClass}（详见类注释「v2 修正」小节）。
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
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        List<EntityGlintblades> blades = SharedEntityQuery.glintbladesNearCamera(mc, cam);
        if (blades.isEmpty()) {
            return;
        }

        Matrix4f matrix = VisualBatch.matrix();
        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (EntityGlintblades blade : blades) {
            Vec3 dir = blade.getAimDirection(partial);
            if (dir == null) {
                // 朝向无法确定（目标已卸载且尚未发射）：本体渲染器会退回竖直姿态，
                // 但特效依赖刀身轴构建坐标系，没有可靠方向时宁可不画，也不要画歪
                continue;
            }

            Vec3 center = blade.getRenderCenter(partial);
            double ddx = center.x - cam.x;
            double ddy = center.y - cam.y;
            double ddz = center.z - cam.z;
            double distSqr = ddx * ddx + ddy * ddy + ddz * ddz;
            if (distSqr > CULL_SQR) {
                continue;
            }

            float size = blade.getSize();
            // 出现动画：与本体渲染器同一条曲线，保证特效与剑身同步浮现
            float appear = easeOutCubic(Mth.clamp((blade.tickCount + partial) / APPEAR_TICKS, 0f, 1f));
            if (appear <= 0.02f || size <= 1.0e-4f) {
                continue;
            }

            // ⭐ 细节系数按「到剑体边界的距离」取，不能按到中心的距离（详见类注释）
            double visualRadius = size * TIP_FACTOR;
            double edge = Math.max(0.0, Math.sqrt(distSqr) - visualRadius);
            float detail = VisualLod.detail(edge * edge);
            VisualLod.countInstance();

            // ===== 构建以刀身轴为主轴的正交基 =====
            float dx = (float) dir.x;
            float dy = (float) dir.y;
            float dz = (float) dir.z;
            // 参考轴：与刀身轴尽量不平行，避免叉积退化
            float hx, hy, hz;
            if (Math.abs(dy) < 0.95f) {
                hx = 0f;
                hy = 1f;
                hz = 0f;
            } else {
                hx = 1f;
                hy = 0f;
                hz = 0f;
            }
            // u = normalize(d × h)
            float ux = dy * hz - dz * hy;
            float uy = dz * hx - dx * hz;
            float uz = dx * hy - dy * hx;
            float ulen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (ulen < 1.0e-6f) {
                continue;
            }
            ux /= ulen;
            uy /= ulen;
            uz /= ulen;
            // w = d × u（d 与 u 均为单位向量且正交，故 w 自然是单位向量，无需再归一化）
            float wx = dy * uz - dz * uy;
            float wy = dz * ux - dx * uz;
            float wz = dx * uy - dy * ux;

            // 中心（相对相机）
            float cx = (float) ddx;
            float cy = (float) ddy;
            float cz = (float) ddz;
            // 剑尖 / 剑柄（相对相机）
            float tipX = cx + dx * size * TIP_FACTOR;
            float tipY = cy + dy * size * TIP_FACTOR;
            float tipZ = cz + dz * size * TIP_FACTOR;
            float hiltX = cx + dx * size * HILT_FACTOR;
            float hiltY = cy + dy * size * HILT_FACTOR;
            float hiltZ = cz + dz * size * HILT_FACTOR;

            boolean shooted = blade.isShooted();
            float charge = blade.getChargeProgress();
            int seedId = blade.getId();

            if (!shooted) {
                // ===== 悬浮期：符文阵 + 环绕碎片 =====
                drawRuneCircle(builder, matrix, hiltX, hiltY, hiltZ,
                        ux, uy, uz, wx, wy, wz, size, appear, charge, time, seedId, detail);

                if (VisualLod.keepLayer(detail, MOTE_KEEP_THRESHOLD)) {
                    drawOrbitMotes(builder, matrix, cx, cy, cz, dx, dy, dz,
                            ux, uy, uz, wx, wy, wz, size, appear, charge, time, seedId, detail);
                }
            } else {
                // ===== 飞行期：高速拖尾 =====
                drawTrail(builder, matrix, blade, hiltX, hiltY, hiltZ, dx, dy, dz,
                        ux, uy, uz, wx, wy, wz, size, appear, detail);
            }

            // ===== 剑尖光核（两阶段共有，飞行时更亮）=====
            float coreAlpha = appear * (shooted ? 0.95f : (0.45f + 0.45f * charge));
            float corePulse = 0.85f + 0.15f * Mth.sin(time * 6f + seedId);
            billboardDiamond(builder, matrix, tipX, tipY, tipZ,
                    size * 0.17f * corePulse, C_CORE, coreAlpha);
        }
    }

    // ==================== 悬浮期：辉石符文阵 ====================

    /**
     * 剑柄后的辉石符文阵：双环 + 六芒星 + 外缘刻度，绕刀身轴旋转，随蓄力加速并增亮。
     * <p>
     * 法阵平面<b>垂直于刀身轴</b>，因此不管剑指向哪，玩家都能从法阵的椭圆投影
     * 一眼读出「这把剑正对着谁」。这是整套视觉里信息量最大的元素。
     * </p>
     * <p>
     * <b>六芒星不参与削减</b>：仅 36 顶点却是卡利亚辉石魔法的唯一辨识符号，
     * 且六个顶点均布，减少数量就不成六芒星了（详见类注释「削减策略」）。
     * </p>
     */
    private static void drawRuneCircle(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz,
                                       float ux, float uy, float uz,
                                       float wx, float wy, float wz,
                                       float size, float appear, float charge,
                                       float time, int seedId, float detail) {
        // 蓄力越满转得越快、越亮——给被瞄准的玩家一个可读的「快飞出来了」的预警
        float rot = time * RUNE_ROT_SPEED * (1f + charge * 1.8f) + seedId * 0.7f;
        float breath = 0.92f + 0.08f * Mth.sin(time * 2.2f + seedId * 0.5f);
        float radius = size * RUNE_RADIUS_FACTOR * breath * appear;
        if (radius <= 1.0e-3f) {
            return;
        }
        float alpha = RUNE_BASE_ALPHA * appear * (0.5f + 0.5f * charge);
        int segments = VisualLod.scaleSegments(RUNE_SEGMENTS, RUNE_SEGMENTS_MIN, detail);
        float bandHalf = Math.max(0.008f, size * 0.022f);

        // 外环（主环，辉石蓝）
        axialRing(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                radius - bandHalf, radius + bandHalf, segments, rot, C_BLUE, alpha, alpha);
        // 外环辉光（向外渐隐）
        axialRing(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                radius + bandHalf, radius + bandHalf + size * 0.09f, segments, rot,
                C_BLUE, alpha * 0.35f, 0f);
        // 内环（反向旋转，深蓝，叠出层次）
        float innerRadius = radius * 0.60f;
        axialRing(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                innerRadius - bandHalf * 0.8f, innerRadius + bandHalf * 0.8f, segments, -rot * 1.25f,
                C_DEEP, alpha * 0.8f, alpha * 0.8f);

        // 六芒星（两叠三角）：卡利亚辉石魔法的核心符号，不参与削减
        float starRadius = radius * 0.82f;
        float starHalf = Math.max(0.007f, size * 0.018f);
        for (int i = 0; i < 6; i++) {
            float a1 = rot + TAU * i / 6f;
            float a2 = rot + TAU * ((i + 2) % 6) / 6f;
            planeLine(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                    starRadius * Mth.cos(a1), starRadius * Mth.sin(a1),
                    starRadius * Mth.cos(a2), starRadius * Mth.sin(a2),
                    starHalf, C_CORE, alpha * 0.9f, alpha * 0.9f);
        }

        // 外缘刻度：均布角度，必须按步长抽取（截断会让法阵缺一块）
        if (VisualLod.keepLayer(detail, RUNE_TICK_KEEP_THRESHOLD)) {
            int drawn = VisualLod.scale(RUNE_TICK_COUNT, detail);
            int step = Math.max(1, RUNE_TICK_COUNT / drawn);
            float rStart = radius + bandHalf + size * 0.03f;
            float rEnd = rStart + size * 0.10f;
            float tickHalf = Math.max(0.006f, size * 0.014f);
            for (int i = 0; i < RUNE_TICK_COUNT; i += step) {
                float a = -rot * 0.6f + TAU * i / RUNE_TICK_COUNT;
                float ca = Mth.cos(a);
                float sa = Mth.sin(a);
                planeLine(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                        rStart * ca, rStart * sa, rEnd * ca, rEnd * sa,
                        tickHalf, C_BLUE, alpha * 0.5f, 0f);
            }
        }
    }

    // ==================== 悬浮期：环绕碎片 ====================

    /**
     * 绕刀身轴公转的辉石碎片：小菱形光点，沿刀身分布在不同高度、各自错相闪烁。
     * <p>均布公转角度，故按步长抽取而非截断。</p>
     */
    private static void drawOrbitMotes(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz,
                                       float dx, float dy, float dz,
                                       float ux, float uy, float uz,
                                       float wx, float wy, float wz,
                                       float size, float appear, float charge,
                                       float time, int seedId, float detail) {
        int drawn = VisualLod.scale(MOTE_COUNT, detail);
        int step = Math.max(1, MOTE_COUNT / drawn);
        float orbitRadius = size * 0.42f;
        float rot = time * MOTE_ORBIT_SPEED * (1f + charge) + seedId * 0.4f;

        for (int i = 0; i < MOTE_COUNT; i += step) {
            float a = rot + TAU * i / MOTE_COUNT;
            // 沿刀身来回游走，避免碎片全挤在同一个横截面上
            float along = Mth.sin(time * 0.9f + i * 1.3f + seedId * 0.2f) * size * 0.35f;
            float r = orbitRadius * (0.75f + 0.25f * Mth.sin(time * 1.4f + i * 2.1f));
            float ca = Mth.cos(a);
            float sa = Mth.sin(a);
            float px = cx + dx * along + (ux * ca + wx * sa) * r;
            float py = cy + dy * along + (uy * ca + wy * sa) * r;
            float pz = cz + dz * along + (uz * ca + wz * sa) * r;

            float twinkle = 0.5f + 0.5f * Mth.sin(time * 4.5f + i * 1.7f + seedId);
            float alpha = appear * (0.35f + 0.4f * charge) * twinkle;
            if (alpha <= 0.01f) {
                continue;
            }
            VisualColor.mixInto(SCRATCH, C_BLUE, C_CORE, twinkle);
            billboardDiamond(b, m, px, py, pz, size * 0.055f * (0.7f + 0.5f * twinkle),
                    SCRATCH, alpha);
        }
    }

    // ==================== 飞行期：拖尾 ====================

    /**
     * 高速飞行拖尾：自剑柄向后沿反方向延伸的渐隐光带。
     * <p>
     * <b>不存历史位置</b>——辉剑的追踪插值很平滑、轨迹接近直线，
     * 直接沿 {@code -d} 反向外推得到的形状与「记录每 tick 位置再连线」肉眼无法分辨，
     * 却省掉了逐实体的环形缓冲与其生命周期管理。
     * </p>
     * <p>长度随实际速度伸缩：慢速接近目标时拖尾自然收短，不会拖着一条不动的长尾巴。</p>
     */
    private static void drawTrail(BufferBuilder b, Matrix4f m, EntityGlintblades blade,
                                  float hiltX, float hiltY, float hiltZ,
                                  float dx, float dy, float dz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
                                  float size, float appear, float detail) {
        double speed = blade.getDeltaMovement().length();
        // 速度因子：1.5 格/tick 时拖尾满长，低于此按比例收短
        float speedFactor = (float) Math.min(speed / 1.5, 1.0);
        if (speedFactor <= 0.05f) {
            return;
        }
        float total = size * TRAIL_LENGTH_FACTOR * speedFactor;
        int segments = VisualLod.scaleSegments(TRAIL_SEGMENTS, TRAIL_SEGMENTS_MIN, detail);
        float halfWidth = size * 0.13f;

        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments;
            float t1 = (float) (i + 1) / segments;
            float d0 = t0 * total;
            float d1 = t1 * total;

            // 越往后越细、越暗、越透明（平方衰减，尾端收得干脆）
            float a0 = appear * 0.75f * (1f - t0) * (1f - t0);
            float a1 = appear * 0.75f * (1f - t1) * (1f - t1);
            float hw = halfWidth * (1f - 0.8f * t0);
            VisualColor.mixInto(SCRATCH, C_BLUE, C_DEEP, t0);

            crossQuadAlong(b, m,
                    hiltX - dx * d0, hiltY - dy * d0, hiltZ - dz * d0,
                    hiltX - dx * d1, hiltY - dy * d1, hiltZ - dz * d1,
                    hw, ux, uy, uz, wx, wy, wz, SCRATCH, a0, a1);
        }
    }

    // ==================== 几何基元 ====================

    /**
     * 绘制一个<b>垂直于刀身轴</b>的圆环带（annulus），内外边缘可分别指定 alpha。
     * <p>点位由平面基向量 {@code u}、{@code w} 张成：{@code P(θ) = c + u·r·cosθ + w·r·sinθ}。</p>
     *
     * @param rInner   内半径
     * @param rOuter   外半径
     * @param segments 分段数
     * @param rot      整环旋转角（弧度）
     */
    private static void axialRing(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
                                  float rInner, float rOuter, int segments, float rot,
                                  float[] col, float alphaInner, float alphaOuter) {
        if (rOuter <= rInner || segments < 3) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float prevCos = Mth.cos(rot);
        float prevSin = Mth.sin(rot);
        for (int i = 1; i <= segments; i++) {
            float a = rot + TAU * i / segments;
            float ca = Mth.cos(a);
            float sa = Mth.sin(a);

            float ox0 = cx + (ux * prevCos + wx * prevSin) * rOuter;
            float oy0 = cy + (uy * prevCos + wy * prevSin) * rOuter;
            float oz0 = cz + (uz * prevCos + wz * prevSin) * rOuter;
            float ox1 = cx + (ux * ca + wx * sa) * rOuter;
            float oy1 = cy + (uy * ca + wy * sa) * rOuter;
            float oz1 = cz + (uz * ca + wz * sa) * rOuter;
            float ix0 = cx + (ux * prevCos + wx * prevSin) * rInner;
            float iy0 = cy + (uy * prevCos + wy * prevSin) * rInner;
            float iz0 = cz + (uz * prevCos + wz * prevSin) * rInner;
            float ix1 = cx + (ux * ca + wx * sa) * rInner;
            float iy1 = cy + (uy * ca + wy * sa) * rInner;
            float iz1 = cz + (uz * ca + wz * sa) * rInner;

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
     * 在<b>垂直于刀身轴的平面内</b>绘制一条带宽度的线段（用平面二维坐标表达端点）。
     * <p>供六芒星与外缘刻度使用：法阵的全部图案都活在这个平面里，
     * 用二维坐标描述比逐点算三维方便得多，也不易出错。</p>
     *
     * @param px1 起点在平面内的 u 分量
     * @param py1 起点在平面内的 w 分量
     * @param px2 终点在平面内的 u 分量
     * @param py2 终点在平面内的 w 分量
     * @param hw  线半宽
     */
    private static void planeLine(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
                                  float px1, float py1, float px2, float py2,
                                  float hw, float[] col, float a1, float a2) {
        float ddx = px2 - px1;
        float ddy = py2 - py1;
        float len = (float) Math.sqrt(ddx * ddx + ddy * ddy);
        if (len < 1.0e-6f) {
            return;
        }
        // 平面内的法线 × 半宽
        float nx = -ddy / len * hw;
        float ny = ddx / len * hw;

        float r = col[0], g = col[1], bl = col[2];
        // 四个角点（平面二维 → 世界三维）
        float a1u = px1 + nx, a1w = py1 + ny;
        float a2u = px1 - nx, a2w = py1 - ny;
        float b1u = px2 + nx, b1w = py2 + ny;
        float b2u = px2 - nx, b2w = py2 - ny;

        float ax1 = cx + ux * a1u + wx * a1w, ay1 = cy + uy * a1u + wy * a1w, az1 = cz + uz * a1u + wz * a1w;
        float ax2 = cx + ux * a2u + wx * a2w, ay2 = cy + uy * a2u + wy * a2w, az2 = cz + uz * a2u + wz * a2w;
        float bx1 = cx + ux * b1u + wx * b1w, by1 = cy + uy * b1u + wy * b1w, bz1 = cz + uz * b1u + wz * b1w;
        float bx2 = cx + ux * b2u + wx * b2w, by2 = cy + uy * b2u + wy * b2w, bz2 = cz + uz * b2u + wz * b2w;

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, by1, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, ay2, az2).color(r, g, bl, a1).endVertex();
    }

    /**
     * 沿任意方向的「十字双面」带状线段：以基向量 {@code u}、{@code w} 各展开一个四边形，
     * 使线段从任意水平 / 竖直视角皆可见，无需 billboard 计算。
     * <p>手法与 {@code AoeEffectRenderer#lightningSegment} 一致，
     * 区别是这里的展开方向取自刀身正交基而非世界轴——刀身可以指向任意方向，
     * 用世界 X/Z 展开在剑竖直时会退化。</p>
     *
     * @param hw 线半宽（格）
     */
    private static void crossQuadAlong(BufferBuilder b, Matrix4f m,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float hw,
                                       float ux, float uy, float uz,
                                       float wx, float wy, float wz,
                                       float[] col, float a1, float a2) {
        if (a1 <= 0.002f && a2 <= 0.002f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        // 面 1：沿 u 展开
        quad(b, m,
                x1 - ux * hw, y1 - uy * hw, z1 - uz * hw,
                x1 + ux * hw, y1 + uy * hw, z1 + uz * hw,
                x2 + ux * hw, y2 + uy * hw, z2 + uz * hw,
                x2 - ux * hw, y2 - uy * hw, z2 - uz * hw,
                r, g, bl, a1, a2);
        // 面 2：沿 w 展开
        quad(b, m,
                x1 - wx * hw, y1 - wy * hw, z1 - wz * hw,
                x1 + wx * hw, y1 + wy * hw, z1 + wz * hw,
                x2 + wx * hw, y2 + wy * hw, z2 + wz * hw,
                x2 - wx * hw, y2 - wy * hw, z2 - wz * hw,
                r, g, bl, a1, a2);
    }

    /**
     * 画一个四边形（拆成两三角形）：顶点 a→b 用 alpha {@code aAB}，c→d 用 alpha {@code aCD}。
     */
    private static void quad(BufferBuilder b, Matrix4f m,
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

    /**
     * 面向相机的小菱形光点：中心最亮、四角渐隐。
     * <p>仅 12 顶点，不参与分段缩放；是否绘制由调用方按保留阈值决定。</p>
     */
    private static void billboardDiamond(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz, float size,
                                         float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rx = VisualBatch.rightX() * size;
        float ry = VisualBatch.rightY() * size;
        float rz = VisualBatch.rightZ() * size;
        float upx = VisualBatch.upX() * size;
        float upy = VisualBatch.upY() * size;
        float upz = VisualBatch.upZ() * size;

        // 四个角点：上、右、下、左（相机平面内）
        float p0x = cx + upx, p0y = cy + upy, p0z = cz + upz;
        float p1x = cx + rx, p1y = cy + ry, p1z = cz + rz;
        float p2x = cx - upx, p2y = cy - upy, p2z = cz - upz;
        float p3x = cx - rx, p3y = cy - ry, p3z = cz - rz;

        diamondTri(b, m, cx, cy, cz, p0x, p0y, p0z, p1x, p1y, p1z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p1x, p1y, p1z, p2x, p2y, p2z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p2x, p2y, p2z, p3x, p3y, p3z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p3x, p3y, p3z, p0x, p0y, p0z, r, g, bl, alpha);
    }

    /**
     * 菱形光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     */
    private static void diamondTri(BufferBuilder b, Matrix4f m,
                                   float cx, float cy, float cz,
                                   float ax, float ay, float az,
                                   float bx, float by, float bz,
                                   float r, float g, float bl, float alpha) {
        b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, ay, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bl, 0f).endVertex();
    }

    // ==================== 数学辅助 ====================
    // v2 说明：原先的 frac(float) 已删除——它是 v4.1 移除「剑尖锁定引导虚线」时
    // 遗留下来的死代码，此后再无任何调用点。

    /** 缓出（cubic）。 */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }
}
