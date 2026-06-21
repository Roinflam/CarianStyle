package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 光环地面渲染器（客户端）——按形状绘制水平发光法阵。
 * <p>
 * <b>几何精度（与 {@code EntityUtil.getNearbyEntities} 对齐）：</b>
 * 范围判定已改为「精确小数坐标 + 水平圆（垂直 ±R 的圆柱）」——
 * {@code getEntitiesOfClass} 之后按实体中心点的水平距离 {@code dx^2 + dz^2 <= R^2} 过滤。
 * 因此 {@link AuraDisplayRegistry.AuraShape#CIRCLE} 圆形光环（平滑居中、精确半径 R）
 * 与效果生效区<b>逐像素一致</b>：实体中心落在圆内即生效，无方块吸附、无碰撞箱模糊。
 * <p>
 * {@link AuraDisplayRegistry.AuraShape#SQUARE} 方形通道仍保留，供仍按 {@code AABB.inflate(R)}
 * 盒形判定的效果使用：其中心吸附到方块坐标（{@link Math#floor(double)}）、每边外扩 {@link #EDGE_MARGIN}
 * 近似碰撞箱相交余量。当前 4 个光环均为圆形，故此通道暂未使用。
 * <p>
 * <b>混合方式：</b>统一普通 alpha 混合（以颜色“染”地面）。叠加混合在明亮背景下会把颜色加到接近白色而丢色。
 * <p>
 * <b>方形层次：</b>区域底色 → 四条发光边（核心亮带+内外辉光，即精确边界）→ 四角追逐火花
 * → 从中心向外扩散的方形涟漪 → 沿边框巡游的彗星光点。全部方形主题，无内嵌圆环。
 * <p>
 * <b>圆形 {@link AuraDisplayRegistry.AuraShape#CIRCLE}（备用，对应 {@code distanceTo} 球形判定）：</b>
 * 平滑居中、精确半径；径向渐变 + 主环 + 旋转符文。
 * <p>
 * 动画：整体亮度呼吸；四角依次明灭；方形涟漪与边框彗星循环运动；出现时从中心展开（缓出）。
 * <b>半边长/半径严格不缩放（只脉冲亮度），边界始终对应效果范围。</b>
 * <p>
 * <b>性能（保守优化，视觉零变化）：</b>圆形分段数 {@link #ringSegments(double)} /
 * {@link #fillSegments(double)} 的系数与上限已下调——半径 ≤8 的光环仍取原下限、像素无差，
 * 仅最大的圣域（16 格）分段适度减少（与真圆偏离亚厘米级、肉眼不可见）；
 * 同时 {@link #RENDER_CULL} 与扫描范围对齐，去除多余裁剪段。
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class AuraGroundRenderer {

    // ===== 外观/性能常量（可按需调整）=====
    /**
     * 距离裁剪（格）。<b>已与 AuraScanner 的 SCAN_RANGE 对齐为同值</b>：
     * 扫描器只产出该范围内实体的光环，渲染裁剪取相同值可消除原先「裁剪 72 而只扫描 48」
     * 的不一致（48~72 段渲染能力本就拿不到数据、属浪费）。近场视觉完全不变；
     * 若需更远可见距离，请同时上调本值与 AuraScanner.SCAN_RANGE。
     */
    private static final double RENDER_CULL = 48.0;
    /** 离地高度偏移，避免与地面 z-fighting */
    private static final float Y_OFFSET = 0.02f;

    /**
     * 方形每边外扩余量（格）——近似 {@code getEntitiesOfClass} 碰撞箱相交带来的额外生效宽度。
     * 真实值随目标碰撞箱半宽变化（玩家约 0.3、多数生物约 0.4~0.5），取折中值。
     */
    private static final double EDGE_MARGIN = 0.5;

    /** 方形区域底色 alpha（平铺） */
    private static final float SQUARE_FILL_ALPHA = 0.13f;
    /** 圆形填充：中心 alpha */
    private static final float CIRCLE_FILL_ALPHA_CENTER = 0.02f;
    /** 圆形填充：边缘 alpha */
    private static final float CIRCLE_FILL_ALPHA_RIM = 0.16f;

    /** 主边/主环核心亮度 */
    private static final float CORE_ALPHA = 0.85f;
    /** 辉光亮度 */
    private static final float GLOW_ALPHA = 0.32f;
    /** 辉光向外/向内扩散宽度（格） */
    private static final float GLOW_SPREAD = 0.50f;
    /** 主边/主环实芯半宽（格） */
    private static final float CORE_HALF = 0.07f;
    /** 核心线向白提亮比例（保留色相、略增亮，不洗白） */
    private static final float CORE_BRIGHTEN = 0.22f;

    /** 圆形符文刻度亮度 */
    private static final float RUNE_ALPHA = 0.5f;

    /** 四角火花亮度 */
    private static final float CORNER_ALPHA = 0.85f;
    /** 四角火花半尺寸（格） */
    private static final float CORNER_SIZE = 0.20f;
    /** 四角追逐相位速度（弧度/tick） */
    private static final float CORNER_CHASE_SPEED = 0.12f;

    /** 方形涟漪：一个扩散周期时长（tick） */
    private static final float RIPPLE_PERIOD_TICKS = 55.0f;
    /** 方形涟漪：同时存在的条数（相位均布） */
    private static final int RIPPLE_COUNT = 2;
    /** 方形涟漪：峰值亮度 */
    private static final float RIPPLE_ALPHA = 0.42f;
    /** 方形涟漪：线半宽（格） */
    private static final float RIPPLE_HALF_WIDTH = 0.045f;

    /** 边框彗星：绕行一圈周期（tick） */
    private static final float COMET_PERIOD_TICKS = 70.0f;
    /** 边框彗星：数量（相位均布） */
    private static final int COMET_COUNT = 2;
    /** 边框彗星：亮度 */
    private static final float COMET_ALPHA = 0.9f;
    /** 边框彗星：光点半尺寸（格） */
    private static final float COMET_SIZE = 0.22f;
    /** 彗星拖尾：尾点数量 */
    private static final int COMET_TRAIL = 3;
    /** 彗星拖尾：相邻尾点相位间隔 */
    private static final float COMET_TRAIL_STEP = 0.012f;

    /** 圆形符文旋转速度 */
    private static final float RUNE_SPEED = 0.030f;
    /** 亮度呼吸速度 */
    private static final float PULSE_SPEED = 0.075f;
    /** 出现展开时长（tick） */
    private static final float APPEAR_TICKS = 6.0f;
    /** 消失淡出时长（tick） */
    private static final float FADE_TICKS = 7.0f;

    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private AuraGroundRenderer() {
    }

    /**
     * 逐光环动画状态记录（gametick 时间轴），支持出现展开与消失淡出。
     * 键 = entityId 与 serialId 打包。仅渲染线程访问。
     */
    private static final Map<Long, AuraState> STATE = new HashMap<>();

    /**
     * 单个光环的动画状态。
     * <p>
     * 记录出现时刻、消失时刻，以及最近一次的渲染参数与世界坐标——
     * 这样即便光环已离开扫描结果（甚至实体已卸载），也能在原地把淡出动画播完。
     */
    private static final class AuraState {
        /** 出现时刻（tick） */
        float appearTime;
        /** 开始消失的时刻（tick）；<0 表示仍激活（未开始淡出） */
        float fadeStart = -1f;
        /** 实体网络 id（淡出时优先按实体当前位置渲染） */
        int entityId;
        /** 序列号（决定专属符文母题） */
        int serialId;
        /** 主题色（0xRRGGBB） */
        int color;
        /** 名义半径（未含动画/边缘余量） */
        double nominalRadius;
        /** 形状 */
        AuraDisplayRegistry.AuraShape shape;
        /** 最近一次的世界坐标（实体消失后用于淡出定位） */
        double lastX;
        double lastY;
        double lastZ;
    }

    /**
     * 准备好渲染的一个光环（坐标已转为相对相机；尺寸已含动画与边缘余量；alpha 为整体淡入淡出系数）。
     */
    private record Prepared(int serialId, double rx, double ry, double rz, int color, double radius,
                            AuraDisplayRegistry.AuraShape shape, double alpha) {
    }

    /**
     * 打包 (entityId, serialId) 为唯一键。
     */
    private static long key(int entityId, int serialId) {
        return ((long) entityId << 16) | (serialId & 0xFFFFL);
    }

    /**
     * 渲染回调。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        List<AuraScanner.ActiveAura> auras = AuraScanner.getActiveAuras();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATE.clear();
            return;
        }
        // 注意：即使本帧没有任何激活光环（auras 为空），仍需继续——
        // 因为可能有刚消失、正在播放淡出动画的光环要渲染。

        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        float now = (float) (mc.level.getGameTime() % 1_000_000L) + partial;

        // ===== 1) 刷新本帧激活光环的状态（取消淡出、记录最新参数与世界坐标）=====
        Set<Long> activeKeys = new HashSet<>();
        for (AuraScanner.ActiveAura aura : auras) {
            Entity entity = mc.level.getEntity(aura.entityId());
            if (entity == null) {
                continue;
            }
            long k = key(aura.entityId(), aura.serialId());
            activeKeys.add(k);
            AuraState st = STATE.get(k);
            if (st == null) {
                st = new AuraState();
                st.appearTime = now;
                STATE.put(k, st);
            }
            st.fadeStart = -1f; // 仍激活：清除淡出标记（若此前在淡出会被“救回”）
            st.entityId = aura.entityId();
            st.serialId = aura.serialId();
            st.color = aura.color();
            st.nominalRadius = aura.radius();
            st.shape = aura.shape();
            st.lastX = entity.getX();
            st.lastY = entity.getY();
            st.lastZ = entity.getZ();
        }

        // ===== 2) 不在激活集里的状态：开始淡出（若尚未开始）=====
        for (Map.Entry<Long, AuraState> e : STATE.entrySet()) {
            AuraState st = e.getValue();
            if (st.fadeStart < 0f && !activeKeys.contains(e.getKey())) {
                st.fadeStart = now;
            }
        }

        // ===== 3) 由全部状态构建渲染数据（出现=展开+淡入；消失=收缩+淡出；淡出完成移除）=====
        List<Prepared> prepared = new ArrayList<>();
        double cullSqr = RENDER_CULL * RENDER_CULL;
        Iterator<Map.Entry<Long, AuraState>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            AuraState st = it.next().getValue();

            float radiusFactor;
            float alpha;
            if (st.fadeStart < 0f) {
                // 出现/稳定：展开 + 淡入（用同一缓动曲线）
                float eased = easeOutCubic(Mth.clamp((now - st.appearTime) / APPEAR_TICKS, 0f, 1f));
                radiusFactor = eased;
                alpha = eased;
            } else {
                // 消失：收缩 + 淡出
                float p2 = Mth.clamp((now - st.fadeStart) / FADE_TICKS, 0f, 1f);
                if (p2 >= 1f) {
                    it.remove(); // 淡出结束，移除状态
                    continue;
                }
                float v = 1f - p2;                 // 1→0
                radiusFactor = 0.55f + 0.45f * v;  // 收缩到 ~55%
                alpha = v;
            }

            // 位置：实体仍在场用插值；否则用最近世界坐标（实体已卸载也能在原地淡完）
            Entity entity = mc.level.getEntity(st.entityId);
            double lerpX;
            double lerpY;
            double lerpZ;
            if (entity != null) {
                lerpX = Mth.lerp((double) partial, entity.xo, entity.getX());
                lerpY = Mth.lerp((double) partial, entity.yo, entity.getY()) + Y_OFFSET;
                lerpZ = Mth.lerp((double) partial, entity.zo, entity.getZ());
            } else {
                lerpX = st.lastX;
                lerpY = st.lastY + Y_OFFSET;
                lerpZ = st.lastZ;
            }

            double dx = lerpX - cam.x;
            double dy = lerpY - cam.y;
            double dz = lerpZ - cam.z;
            if (dx * dx + dy * dy + dz * dz > cullSqr) {
                continue; // 太远：本帧不渲染，但保留状态，淡出计时继续
            }

            // 方形：中心吸附到方块坐标（floor），半边长含碰撞箱余量；
            // 圆形：平滑居中、精确半径（对应 distanceTo 球形判定）。
            double centerWorldX;
            double centerWorldZ;
            double drawReach;
            if (st.shape == AuraDisplayRegistry.AuraShape.SQUARE) {
                centerWorldX = Math.floor(lerpX);
                centerWorldZ = Math.floor(lerpZ);
                drawReach = st.nominalRadius + EDGE_MARGIN;
            } else {
                centerWorldX = lerpX;
                centerWorldZ = lerpZ;
                drawReach = st.nominalRadius;
            }
            double animatedReach = drawReach * radiusFactor;
            if (animatedReach < 0.05) {
                continue;
            }

            prepared.add(new Prepared(
                    st.serialId,
                    centerWorldX - cam.x, lerpY - cam.y, centerWorldZ - cam.z,
                    st.color, animatedReach, st.shape, alpha));
        }

        if (prepared.isEmpty()) {
            return;
        }

        float pulse = 0.5f + 0.5f * Mth.sin(now * PULSE_SPEED);
        float fillMul = 0.85f + 0.15f * pulse;
        float lineMul = 0.80f + 0.20f * pulse;
        float runeRot = now * RUNE_SPEED;

        Matrix4f matrix = event.getPoseStack().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        // GL 状态：普通 alpha 混合、关闭深度写入、保留深度测试
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 单批绘制：每个光环按 填充→辉光→核心→强调 的顺序追加（顺序即混合顺序）。
        // 把整体 alpha 折入 fillMul/lineMul——因绘制函数内所有顶点 alpha 均为
        // “基值 × fillMul/lineMul”，故乘一次即可让整张光环统一淡入/淡出（颜色不受影响）。
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (Prepared p : prepared) {
            float af = fillMul * (float) p.alpha();
            float al = lineMul * (float) p.alpha();
            if (p.shape() == AuraDisplayRegistry.AuraShape.SQUARE) {
                drawSquare(builder, matrix, p, af, al, now);
            } else {
                drawCircle(builder, matrix, p, af, al, runeRot, now);
            }
        }
        BufferUploader.drawWithShader(builder.end());

        // 恢复 GL 状态
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ==================== 方形绘制 ====================

    /**
     * 绘制一个方形光环（填充 + 发光边 + 四角追逐 + 方形涟漪 + 边框彗星）。
     *
     * @param fillMul 填充呼吸亮度系数
     * @param lineMul 线条呼吸亮度系数
     * @param now     当前时间（tick，含 partial），驱动追逐/涟漪/彗星相位
     */
    private static void drawSquare(BufferBuilder builder, Matrix4f m, Prepared p,
                                   float fillMul, float lineMul, float now) {
        double cx = p.rx(), cy = p.ry(), cz = p.rz();
        double half = p.radius();
        float[] col = unpack(p.color());
        float r = col[0], g = col[1], b = col[2];
        float br = brighten(r), bg = brighten(g), bb = brighten(b);

        // 区域底色
        addSquareFill(builder, m, cx, cy, cz, half, r, g, b, SQUARE_FILL_ALPHA * fillMul);

        // 从中心向外扩散的方形涟漪
        for (int i = 0; i < RIPPLE_COUNT; i++) {
            float phase = frac(now / RIPPLE_PERIOD_TICKS + (float) i / RIPPLE_COUNT);
            double rippleHalf = half * phase;
            if (rippleHalf < 0.3) {
                continue;
            }
            float a = (1f - phase) * RIPPLE_ALPHA * lineMul;
            addSquareEdges(builder, m, cx, cy, cz, rippleHalf,
                    -RIPPLE_HALF_WIDTH, RIPPLE_HALF_WIDTH, br, bg, bb, a, a);
        }

        // 外辉光（边界外侧渐隐）
        addSquareEdges(builder, m, cx, cy, cz, half,
                CORE_HALF, CORE_HALF + GLOW_SPREAD, r, g, b, GLOW_ALPHA * lineMul, 0f);
        // 内辉光（边界内侧渐隐）
        addSquareEdges(builder, m, cx, cy, cz, half,
                -CORE_HALF - GLOW_SPREAD, -CORE_HALF, r, g, b, 0f, GLOW_ALPHA * lineMul);
        // 核心亮带（提亮色，精确边界）
        addSquareEdges(builder, m, cx, cy, cz, half,
                -CORE_HALF, CORE_HALF, br, bg, bb, CORE_ALPHA * lineMul, CORE_ALPHA * lineMul);

        // 四角追逐火花：四个角依次明灭
        double xMin = cx - half, xMax = cx + half;
        double zMin = cz - half, zMax = cz + half;
        double[][] corners = {{xMin, zMin}, {xMax, zMin}, {xMax, zMax}, {xMin, zMax}};
        for (int i = 0; i < 4; i++) {
            float phase = 0.45f + 0.55f * (0.5f + 0.5f * Mth.sin(now * CORNER_CHASE_SPEED + i * HALF_PI));
            float a = CORNER_ALPHA * lineMul * phase;
            addSpark(builder, m, corners[i][0], cy, corners[i][1], CORNER_SIZE, br, bg, bb, a);
        }

        // 沿边框巡游的彗星光点
        for (int i = 0; i < COMET_COUNT; i++) {
            float t = frac(now / COMET_PERIOD_TICKS + (float) i / COMET_COUNT);
            double[] off = perimeterPoint(half, t);
            addSpark(builder, m, cx + off[0], cy, cz + off[1], COMET_SIZE,
                    br, bg, bb, COMET_ALPHA * lineMul);
        }
    }

    /**
     * 计算方形周边某处的偏移坐标（相对中心），t∈[0,1) 沿顺时针绕行一周。
     *
     * @param half 半边长
     * @param t    周长参数（0~1）
     * @return 长度为 2 的数组 {dx, dz}（相对方形中心的偏移）
     */
    private static double[] perimeterPoint(double half, double t) {
        double per = t * 4.0;          // 0~4，整数部分为边序号
        int side = (int) per;          // 0=北 1=东 2=南 3=西
        double f = per - side;         // 该边内的进度 0~1
        double span = 2.0 * half;
        switch (side) {
            case 0:
                // 北边 z=-half，x 从 -half → +half
                return new double[]{-half + f * span, -half};
            case 1:
                // 东边 x=+half，z 从 -half → +half
                return new double[]{half, -half + f * span};
            case 2:
                // 南边 z=+half，x 从 +half → -half
                return new double[]{half - f * span, half};
            default:
                // 西边 x=-half，z 从 +half → -half
                return new double[]{-half, half - f * span};
        }
    }

    /**
     * 绘制方形四条边的一层（指定内外偏移与内外 alpha）。
     * 各边端到端绘制，四角处相邻边自然衔接。
     *
     * @param half       半边长
     * @param offInner   带内沿相对边界线的偏移（向内为负、向外为正）
     * @param offOuter   带外沿偏移
     * @param alphaInner 内沿 alpha
     * @param alphaOuter 外沿 alpha
     */
    private static void addSquareEdges(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                       double half, double offInner, double offOuter,
                                       float r, float g, float b, float alphaInner, float alphaOuter) {
        double xMin = cx - half, xMax = cx + half;
        double zMin = cz - half, zMax = cz + half;
        // 北边 z=zMin，外法线 (0,-1)
        addEdgeStrip(builder, m, xMin, zMin, xMax, zMin, cy, 0, -1, offInner, offOuter, r, g, b, alphaInner, alphaOuter);
        // 南边 z=zMax，外法线 (0,+1)
        addEdgeStrip(builder, m, xMax, zMax, xMin, zMax, cy, 0, 1, offInner, offOuter, r, g, b, alphaInner, alphaOuter);
        // 西边 x=xMin，外法线 (-1,0)
        addEdgeStrip(builder, m, xMin, zMax, xMin, zMin, cy, -1, 0, offInner, offOuter, r, g, b, alphaInner, alphaOuter);
        // 东边 x=xMax，外法线 (+1,0)
        addEdgeStrip(builder, m, xMax, zMin, xMax, zMax, cy, 1, 0, offInner, offOuter, r, g, b, alphaInner, alphaOuter);
    }

    /**
     * 沿一条边（a→b）按外法线 (nx,nz) 生成一个带状四边形（两三角形），内外沿可分别指定 alpha。
     */
    private static void addEdgeStrip(BufferBuilder builder, Matrix4f m,
                                     double ax, double az, double bx, double bz, double cy,
                                     double nx, double nz, double offInner, double offOuter,
                                     float r, float g, float b, float alphaInner, float alphaOuter) {
        float y = (float) cy;
        float aiX = (float) (ax + nx * offInner), aiZ = (float) (az + nz * offInner);
        float biX = (float) (bx + nx * offInner), biZ = (float) (bz + nz * offInner);
        float aoX = (float) (ax + nx * offOuter), aoZ = (float) (az + nz * offOuter);
        float boX = (float) (bx + nx * offOuter), boZ = (float) (bz + nz * offOuter);

        builder.vertex(m, aiX, y, aiZ).color(r, g, b, alphaInner).endVertex();
        builder.vertex(m, biX, y, biZ).color(r, g, b, alphaInner).endVertex();
        builder.vertex(m, boX, y, boZ).color(r, g, b, alphaOuter).endVertex();

        builder.vertex(m, aiX, y, aiZ).color(r, g, b, alphaInner).endVertex();
        builder.vertex(m, boX, y, boZ).color(r, g, b, alphaOuter).endVertex();
        builder.vertex(m, aoX, y, aoZ).color(r, g, b, alphaOuter).endVertex();
    }

    /**
     * 在某点绘制一个小菱形光点（四角火花 / 边框彗星共用），中心最亮、四角渐隐。
     *
     * @param size 半尺寸（格）
     */
    private static void addSpark(BufferBuilder builder, Matrix4f m, double px, double py, double pz,
                                 float size, float r, float g, float b, float alpha) {
        float y = (float) py;
        float cxF = (float) px, czF = (float) pz;
        float[][] pts = {{cxF, czF - size}, {cxF + size, czF}, {cxF, czF + size}, {cxF - size, czF}};
        for (int i = 0; i < 4; i++) {
            float[] a = pts[i];
            float[] c = pts[(i + 1) % 4];
            builder.vertex(m, cxF, y, czF).color(r, g, b, alpha).endVertex();
            builder.vertex(m, a[0], y, a[1]).color(r, g, b, 0f).endVertex();
            builder.vertex(m, c[0], y, c[1]).color(r, g, b, 0f).endVertex();
        }
    }

    /**
     * 绘制方形区域底色填充（平铺单色）。
     */
    private static void addSquareFill(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                      double half, float r, float g, float b, float alpha) {
        float y = (float) cy;
        float xMin = (float) (cx - half), xMax = (float) (cx + half);
        float zMin = (float) (cz - half), zMax = (float) (cz + half);
        builder.vertex(m, xMin, y, zMin).color(r, g, b, alpha).endVertex();
        builder.vertex(m, xMax, y, zMin).color(r, g, b, alpha).endVertex();
        builder.vertex(m, xMax, y, zMax).color(r, g, b, alpha).endVertex();

        builder.vertex(m, xMin, y, zMin).color(r, g, b, alpha).endVertex();
        builder.vertex(m, xMax, y, zMax).color(r, g, b, alpha).endVertex();
        builder.vertex(m, xMin, y, zMax).color(r, g, b, alpha).endVertex();
    }

    // ==================== 圆形绘制（备用） ====================

    /**
     * 绘制一个圆形光环（径向渐变填充 + 发光主环 + 旋转符文）。
     */
    private static void drawCircle(BufferBuilder builder, Matrix4f m, Prepared p,
                                   float fillMul, float lineMul, float runeRot, float now) {
        double cx = p.rx(), cy = p.ry(), cz = p.rz();
        double radius = p.radius();
        float[] col = unpack(p.color());
        float r = col[0], g = col[1], b = col[2];
        float br = brighten(r), bg = brighten(g), bb = brighten(b);
        int ringSeg = ringSegments(radius);

        // 径向渐变填充
        addGradientDisc(builder, m, cx, cy, cz, radius, fillSegments(radius),
                r, g, b, CIRCLE_FILL_ALPHA_CENTER * fillMul, CIRCLE_FILL_ALPHA_RIM * fillMul);

        // 从中心向外扩散的圆形涟漪
        for (int i = 0; i < RIPPLE_COUNT; i++) {
            float phase = frac(now / RIPPLE_PERIOD_TICKS + (float) i / RIPPLE_COUNT);
            double rr = radius * phase;
            if (rr < 0.3) {
                continue;
            }
            float a = (1f - phase) * RIPPLE_ALPHA * lineMul;
            addBand(builder, m, cx, cy, cz, rr - RIPPLE_HALF_WIDTH, rr + RIPPLE_HALF_WIDTH,
                    ringSegments(rr), br, bg, bb, a, a);
        }

        // 外辉光
        addBand(builder, m, cx, cy, cz, radius + CORE_HALF, radius + CORE_HALF + GLOW_SPREAD, ringSeg,
                r, g, b, GLOW_ALPHA * lineMul, 0f);
        // 内辉光
        addBand(builder, m, cx, cy, cz, radius - CORE_HALF - GLOW_SPREAD, radius - CORE_HALF, ringSeg,
                r, g, b, 0f, GLOW_ALPHA * lineMul);
        // 主环核心
        addBand(builder, m, cx, cy, cz, radius - CORE_HALF, radius + CORE_HALF, ringSeg,
                br, bg, bb, CORE_ALPHA * lineMul, CORE_ALPHA * lineMul);

        // 外缘旋转符文刻度（所有母题共用的细密刻度环，强化“法阵”质感）
        int runeCount = Mth.clamp((int) (radius * 2), 12, 40);
        if ((runeCount & 1) == 1) {
            runeCount++;
        }
        addRunes(builder, m, cx, cy, cz, radius + CORE_HALF + 0.05, 0.26, runeCount, runeRot,
                br, bg, bb, RUNE_ALPHA * lineMul);

        // 各光环专属符文母题（取自艾尔登法环原作意象；元素数量固定，不随半径膨胀以控性能）
        drawRuneMotif(builder, m, cx, cy, cz, radius, styleFor(p.serialId()),
                br, bg, bb, lineMul, now);

        // 沿主环绕行的彗星光点（带渐隐拖尾）
        for (int i = 0; i < COMET_COUNT; i++) {
            float base = frac(now / COMET_PERIOD_TICKS + (float) i / COMET_COUNT);
            for (int t = 0; t < COMET_TRAIL; t++) {
                float ph = base - t * COMET_TRAIL_STEP;
                double ang = Math.PI * 2.0 * ph;
                double px = cx + radius * Math.cos(ang);
                double pz = cz + radius * Math.sin(ang);
                float a = COMET_ALPHA * lineMul * (1f - (float) t / COMET_TRAIL);
                float sz = COMET_SIZE * (1f - 0.18f * t);
                addSpark(builder, m, px, cy, pz, sz, br, bg, bb, a);
            }
        }
    }

    /**
     * 追加一个径向渐变圆盘（中心到边缘 alpha 渐变）。
     */
    private static void addGradientDisc(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                        double radius, int segments,
                                        float r, float g, float b, float alphaCenter, float alphaRim) {
        float y = (float) cy;
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2 * i) / segments;
            double a1 = (Math.PI * 2 * (i + 1)) / segments;
            float x0 = (float) (cx + radius * Math.cos(a0));
            float z0 = (float) (cz + radius * Math.sin(a0));
            float x1 = (float) (cx + radius * Math.cos(a1));
            float z1 = (float) (cz + radius * Math.sin(a1));
            builder.vertex(m, (float) cx, y, (float) cz).color(r, g, b, alphaCenter).endVertex();
            builder.vertex(m, x0, y, z0).color(r, g, b, alphaRim).endVertex();
            builder.vertex(m, x1, y, z1).color(r, g, b, alphaRim).endVertex();
        }
    }

    /**
     * 追加一个圆环带（annulus），内/外边缘可分别指定 alpha。
     */
    private static void addBand(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
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
     * 追加一圈旋转符文刻度（沿圆周均布的短径向小段）。
     */
    private static void addRunes(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                 double rStart, double length, int count, float rotation,
                                 float r, float g, float b, float alpha) {
        float y = (float) cy;
        double rEnd = rStart + length;
        float halfW = 0.03f;
        for (int k = 0; k < count; k++) {
            double base = (Math.PI * 2 * k) / count + rotation;
            double aL = base - halfW;
            double aR = base + halfW;
            float cosL = (float) Math.cos(aL), sinL = (float) Math.sin(aL);
            float cosR = (float) Math.cos(aR), sinR = (float) Math.sin(aR);

            float ix0 = (float) (cx + rStart * cosL), iz0 = (float) (cz + rStart * sinL);
            float ix1 = (float) (cx + rStart * cosR), iz1 = (float) (cz + rStart * sinR);
            float ox0 = (float) (cx + rEnd * cosL), oz0 = (float) (cz + rEnd * sinL);
            float ox1 = (float) (cx + rEnd * cosR), oz1 = (float) (cz + rEnd * sinR);

            builder.vertex(m, ix0, y, iz0).color(r, g, b, alpha * 0.5f).endVertex();
            builder.vertex(m, ix1, y, iz1).color(r, g, b, alpha * 0.5f).endVertex();
            builder.vertex(m, ox1, y, oz1).color(r, g, b, alpha).endVertex();

            builder.vertex(m, ix0, y, iz0).color(r, g, b, alpha * 0.5f).endVertex();
            builder.vertex(m, ox1, y, oz1).color(r, g, b, alpha).endVertex();
            builder.vertex(m, ox0, y, oz0).color(r, g, b, alpha).endVertex();
        }
    }

    // ==================== 数学/几何辅助 ====================

    /**
     * 缓出（cubic）。
     */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /**
     * 取小数部分（结果恒在 [0,1)）。
     */
    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /**
     * 颜色提亮：向白色靠拢一点点（保留色相，只略增亮）。
     */
    private static float brighten(float c) {
        return c + (1f - c) * CORE_BRIGHTEN;
    }

    /**
     * 0xRRGGBB 拆为 [r,g,b]（0~1）。
     */
    private static float[] unpack(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f
        };
    }

    /**
     * 圆形填充分段数。
     * <p><b>保守优化（视觉无损）：</b>系数 3→2、上限 96→48。半径 ≤10 的光环仍取下限 32
     * （与优化前完全一致）；仅 16 格的圣域由 48 段降为 32 段。填充盘是中心→边缘的低 alpha
     * 渐变、无描边线，分段下降几乎不可察。
     */
    private static int fillSegments(double radius) {
        return Mth.clamp((int) (radius * 2), 32, 48);
    }

    /**
     * 圆形环线分段数。
     * <p><b>保守优化（视觉无损）：</b>系数 5→3、上限 110→64。半径 ≤8 的光环仍取下限 48
     * （像素级一致）；仅半径最大的圣域（16 格）由 80 段降为 48 段——其多边形与真圆的最大偏离
     * 约 3.4cm（{@code R(1-cos(180°/48))}），在 32 格直径的发光法阵上肉眼不可见，
     * 却省下约四成环带/辉光顶点（多人举盾圣域时收益最大）。
     */
    private static int ringSegments(double radius) {
        return Mth.clamp((int) (radius * 3), 48, 64);
    }

    // ==================== 专属符文母题（取自艾尔登法环原作意象） ====================

    /**
     * 符文母题样式。每个光环对应一套独特纹理：
     * <ul>
     *     <li>{@link #CARIAN}：魔法之境——卡利亚辉石魔法，六芒星徽 + 六边形 + 水晶碎光；</li>
     *     <li>{@link #WARD}：托普斯的立场——魔法免疫，双层反向断环 + 径向栅条构成结界网；</li>
     *     <li>{@link #COSMIC}：回归性原理——塞乐恩宇宙法则，放射光线 + 闪烁星空；</li>
     *     <li>{@link #HOLY}：圣域——黄金树信仰，长短交替金色光芒 + 中央十字圣徽；</li>
     *     <li>{@link #PLAIN}：通用回退（仅外缘刻度环，无额外母题）。</li>
     * </ul>
     */
    private enum RuneStyle {
        CARIAN,
        WARD,
        COSMIC,
        HOLY,
        PLAIN
    }

    /**
     * 由光环序列号映射到符文样式。新光环未登记时回退为 {@link RuneStyle#PLAIN}。
     *
     * @param serialId 光环序列号
     * @return 符文样式
     */
    private static RuneStyle styleFor(int serialId) {
        if (serialId == CarianStyleAuraDisplays.TERRA_MAGICA) {
            return RuneStyle.CARIAN;
        }
        if (serialId == CarianStyleAuraDisplays.TOPPS_STAND) {
            return RuneStyle.WARD;
        }
        if (serialId == CarianStyleAuraDisplays.REGRESSIVE_PRINCIPLE) {
            return RuneStyle.COSMIC;
        }
        if (serialId == CarianStyleAuraDisplays.HOLY_GROUND) {
            return RuneStyle.HOLY;
        }
        return RuneStyle.PLAIN;
    }

    /**
     * 按样式绘制专属符文母题。
     *
     * @param radius  圆半径（格）
     * @param style   样式
     * @param lineMul 呼吸亮度系数
     * @param now     时间（tick，含 partial）
     */
    private static void drawRuneMotif(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                      double radius, RuneStyle style,
                                      float r, float g, float b, float lineMul, float now) {
        switch (style) {
            case CARIAN -> motifCarian(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now);
            case WARD -> motifWard(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now);
            case COSMIC -> motifCosmic(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now);
            case HOLY -> motifHoly(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now);
            default -> {
                // PLAIN：外缘刻度环已表现，无额外母题
            }
        }
    }

    /**
     * 卡利亚辉石母题：内六边形 + 六芒星（两叠三角）+ 顶点水晶碎光，缓慢顺时针旋转。
     */
    private static void motifCarian(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                    double radius, float r, float g, float b, float lineMul, float now) {
        double rot = now * 0.012;
        float a = 0.70f * lineMul;
        double hw = Math.max(0.05, radius * 0.012);
        // 内六边形
        addPolygonRing(builder, m, cx, cy, cz, radius * 0.40, 6, rot, hw, r, g, b, a * 0.8f);
        // 六芒星（6 顶点，连接步距 2 → 两叠三角）
        addStarPolygon(builder, m, cx, cy, cz, radius * 0.62, 6, 2, rot, hw, r, g, b, a);
        // 顶点水晶碎光
        for (int i = 0; i < 6; i++) {
            double ang = rot + Math.PI * 2 * i / 6.0;
            double px = cx + radius * 0.62 * Math.cos(ang);
            double pz = cz + radius * 0.62 * Math.sin(ang);
            addSpark(builder, m, px, cy, pz, (float) (radius * 0.035 + 0.06), r, g, b, a);
        }
    }

    /**
     * 托普斯结界母题：双层反向旋转断环 + 径向栅条，构成屏障网格。
     */
    private static void motifWard(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                  double radius, float r, float g, float b, float lineMul, float now) {
        double rot = now * 0.020;
        float a = 0.60f * lineMul;
        // 双层反向断环
        addDashedRing(builder, m, cx, cy, cz, radius * 0.42, radius * 0.50, 10, 0.55, rot, r, g, b, a);
        addDashedRing(builder, m, cx, cy, cz, radius * 0.62, radius * 0.70, 14, 0.45, -rot * 1.2, r, g, b, a * 0.85f);
        // 径向栅条
        double hw = Math.max(0.05, radius * 0.014);
        int bars = 12;
        for (int i = 0; i < bars; i++) {
            double ang = rot * 0.5 + Math.PI * 2 * i / bars;
            double ix = cx + radius * 0.50 * Math.cos(ang), iz = cz + radius * 0.50 * Math.sin(ang);
            double ox = cx + radius * 0.62 * Math.cos(ang), oz = cz + radius * 0.62 * Math.sin(ang);
            addLine(builder, m, ix, iz, ox, oz, cy, hw, r, g, b, a, a);
        }
    }

    /**
     * 塞乐恩宇宙母题：放射光线（长短交替、尖端渐隐，缓慢逆旋）+ 闪烁星空。
     */
    private static void motifCosmic(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                    double radius, float r, float g, float b, float lineMul, float now) {
        double rot = -now * 0.010;
        float a = 0.60f * lineMul;
        double hw = Math.max(0.04, radius * 0.010);
        addRays(builder, m, cx, cy, cz, radius * 0.12, radius * 0.70, radius * 0.45, 12, rot, hw, r, g, b, a);
        addStarField(builder, m, cx, cy, cz, radius * 0.80, 14, now, (float) (radius * 0.025 + 0.05),
                r, g, b, 0.85f * lineMul);
    }

    /**
     * 黄金树圣域母题：长短交替金色光芒（缓慢顺旋）+ 中央十字圣徽 + 芒尖金光，整体随圣光脉动。
     */
    private static void motifHoly(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                  double radius, float r, float g, float b, float lineMul, float now) {
        double rot = now * 0.008;
        float pulse = 0.55f + 0.45f * (0.5f + 0.5f * Mth.sin(now * 0.06f));
        float a = 0.75f * lineMul * pulse;
        double hw = Math.max(0.05, radius * 0.013);
        // 长短交替金色光芒
        addRays(builder, m, cx, cy, cz, radius * 0.18, radius * 0.72, radius * 0.50, 16, rot, hw, r, g, b, a);
        // 中央十字圣徽
        double cl = radius * 0.30;
        addLine(builder, m, cx - cl, cz, cx + cl, cz, cy, hw * 1.4, r, g, b, a, a);
        addLine(builder, m, cx, cz - cl, cx, cz + cl, cy, hw * 1.4, r, g, b, a, a);
        // 芒尖金光（取偶数光芒尖端）
        for (int i = 0; i < 16; i += 2) {
            double ang = rot + Math.PI * 2 * i / 16.0;
            double px = cx + radius * 0.72 * Math.cos(ang), pz = cz + radius * 0.72 * Math.sin(ang);
            addSpark(builder, m, px, cy, pz, (float) (radius * 0.025 + 0.05), r, g, b, a);
        }
    }

    // ==================== 母题用通用几何辅助 ====================

    /**
     * 绘制一条带宽度的线段（两点之间的细长四边形，两端 alpha 可不同）。
     *
     * @param hw 线半宽（格）
     */
    private static void addLine(BufferBuilder builder, Matrix4f m,
                                double x1, double z1, double x2, double z2, double y,
                                double hw, float r, float g, float b, float a1, float a2) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        // 垂直于线方向的法线 × 半宽
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
     * 绘制一个正多边形外框（N 条边首尾相连）。
     *
     * @param sides    边数
     * @param rotation 旋转角（弧度）
     * @param hw       线半宽
     */
    private static void addPolygonRing(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                       double radius, int sides, double rotation, double hw,
                                       float r, float g, float b, float alpha) {
        double prevX = 0, prevZ = 0;
        for (int i = 0; i <= sides; i++) {
            double ang = rotation + (Math.PI * 2 * i) / sides;
            double x = cx + radius * Math.cos(ang);
            double z = cz + radius * Math.sin(ang);
            if (i > 0) {
                addLine(builder, m, prevX, prevZ, x, z, cy, hw, r, g, b, alpha, alpha);
            }
            prevX = x;
            prevZ = z;
        }
    }

    /**
     * 绘制星形多边形：把 points 个顶点按步距 step 互连（如 points=6,step=2 即六芒星两叠三角）。
     *
     * @param hw 线半宽
     */
    private static void addStarPolygon(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                       double radius, int points, int step, double rotation, double hw,
                                       float r, float g, float b, float alpha) {
        for (int i = 0; i < points; i++) {
            double a1 = rotation + (Math.PI * 2 * i) / points;
            double a2 = rotation + (Math.PI * 2 * ((i + step) % points)) / points;
            double x1 = cx + radius * Math.cos(a1), z1 = cz + radius * Math.sin(a1);
            double x2 = cx + radius * Math.cos(a2), z2 = cz + radius * Math.sin(a2);
            addLine(builder, m, x1, z1, x2, z2, cy, hw, r, g, b, alpha, alpha);
        }
    }

    /**
     * 绘制放射光线：count 条由内向外的径向线，偶/奇数交替使用 rOuter / rOuterAlt，尖端 alpha 渐隐。
     *
     * @param hw 线半宽
     */
    private static void addRays(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                double rInner, double rOuter, double rOuterAlt, int count, double rotation,
                                double hw, float r, float g, float b, float alpha) {
        for (int i = 0; i < count; i++) {
            double ang = rotation + (Math.PI * 2 * i) / count;
            double ro = (i % 2 == 0) ? rOuter : rOuterAlt;
            double ix = cx + rInner * Math.cos(ang), iz = cz + rInner * Math.sin(ang);
            double ox = cx + ro * Math.cos(ang), oz = cz + ro * Math.sin(ang);
            addLine(builder, m, ix, iz, ox, oz, cy, hw, r, g, b, alpha, alpha * 0.15f);
        }
    }

    /**
     * 绘制断环（虚线圆环）：dashes 段弧，每段占该格 fillRatio 比例，其余留空。
     *
     * @param fillRatio 每段实心占比（0~1）
     * @param rotation  旋转角（弧度）
     */
    private static void addDashedRing(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                      double rInner, double rOuter, int dashes, double fillRatio, double rotation,
                                      float r, float g, float b, float alpha) {
        final int sub = 2; // 每段弧细分（控性能）
        float yf = (float) cy;
        for (int i = 0; i < dashes; i++) {
            double a0 = rotation + (Math.PI * 2 * i) / dashes;
            double a1 = a0 + (Math.PI * 2 / dashes) * fillRatio;
            double prevOx = 0, prevOz = 0, prevIx = 0, prevIz = 0;
            for (int s = 0; s <= sub; s++) {
                double a = a0 + (a1 - a0) * s / sub;
                double ox = cx + rOuter * Math.cos(a), oz = cz + rOuter * Math.sin(a);
                double ix = cx + rInner * Math.cos(a), iz = cz + rInner * Math.sin(a);
                if (s > 0) {
                    builder.vertex(m, (float) prevOx, yf, (float) prevOz).color(r, g, b, alpha).endVertex();
                    builder.vertex(m, (float) ox, yf, (float) oz).color(r, g, b, alpha).endVertex();
                    builder.vertex(m, (float) ix, yf, (float) iz).color(r, g, b, alpha).endVertex();

                    builder.vertex(m, (float) prevOx, yf, (float) prevOz).color(r, g, b, alpha).endVertex();
                    builder.vertex(m, (float) ix, yf, (float) iz).color(r, g, b, alpha).endVertex();
                    builder.vertex(m, (float) prevIx, yf, (float) prevIz).color(r, g, b, alpha).endVertex();
                }
                prevOx = ox;
                prevOz = oz;
                prevIx = ix;
                prevIz = iz;
            }
        }
    }

    /**
     * 绘制闪烁星空：count 个确定性分布的小星点（黄金角排布，半径错落），各自正弦闪烁。
     *
     * @param size      星点半尺寸（格）
     * @param baseAlpha 基础亮度（再乘以闪烁系数）
     */
    private static void addStarField(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                     double radius, int count, float now, float size,
                                     float r, float g, float b, float baseAlpha) {
        for (int i = 0; i < count; i++) {
            // 确定性伪随机：黄金角铺角度，黄金比小数铺半径，分布均匀且稳定
            double ang = i * 2.399963;
            double frac = (i * 0.6180339) - Math.floor(i * 0.6180339);
            double rr = radius * (0.18 + 0.78 * frac);
            double px = cx + rr * Math.cos(ang);
            double pz = cz + rr * Math.sin(ang);
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * Mth.sin(now * 0.18f + i * 1.7f));
            addSpark(builder, m, px, cy, pz, size, r, g, b, baseAlpha * tw);
        }
    }
}
