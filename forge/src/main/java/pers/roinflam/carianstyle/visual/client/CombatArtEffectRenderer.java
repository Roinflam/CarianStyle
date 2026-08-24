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
 * <h3>v2（顶点量 + 堆分配，近距离视觉逐位一致）</h3>
 * <p>
 * <b>本渲染器此前是全模组唯一三样都没接的</b>——既不接 {@link VisualLod}、
 * 也不登记同屏实例数、颜色还在走返回新数组的 {@code unpack}。而它承载的三套演出
 * 顶点量并不低：
 * </p>
 * <pre>
 * 居合斩：
 *   主刀光三层（3 × 36 段 × 6）              648
 *   残影弧（2 道 × 36 段 × 6）               432
 *   起手竖直刀锋（2 层十字双面）              24
 *   地面切割线                                 6
 *   前端火花                                  12
 *   ─────────────────────────────────────────
 *   小计                                  ~1122
 *
 * 回旋斩：
 *   刀光双层（2 × 64 段 × 6）                768
 *   地面扬尘（64 段 × 6）                    384
 *   收尾双环 + 全周扬尘（3 × 64 段 × 6）    1152
 *   前端火花                                  12
 *   ─────────────────────────────────────────
 *   小计（峰值不同时出现，取扫过阶段）      ~1164
 *
 * 祈祷一击：
 *   圣光柱（10 段 × 3 层 × 十字双面 × 6）   1080
 *   落地双环（2 × 约 40 段 × 18）           1440
 *   十字圣徽 + 底色圆盘                      ~96
 *   升腾金光丝（8 根 × 十字双面 × 6）         96
 *   ─────────────────────────────────────────
 *   小计                                  ~2712
 * </pre>
 * <p>
 * <b>更关键的是触发频率。</b>死亡类演出（猩红立体花、癫火）每人几分钟才一次，
 * 而战技是<b>高频主动技</b>：回旋斩每次冲刺攻击都触发、祈祷一击战斗中每 4 秒蓄一次、
 * 居合虽然基础概率只有 1% 但有「未触发则概率 +0.5%」的保底、必定会打出来。
 * 团战里这三套的同屏并发密度远高于死亡演出。
 * </p>
 * <p>
 * 现按 {@link VisualLod#detail} 缩放：{@link VisualLod#FULL_DETAIL_RANGE} 格内系数恒为 1.0，
 * <b>与优化前逐像素一致</b>；40 格外单次演出降至原来的三成左右。
 * </p>
 *
 * <h4>祈祷一击的细节系数要按「光柱视觉体量」取</h4>
 * <p>
 * 居合（4 格）与回旋（3 格）的半径都远小于 {@link VisualLod#FULL_DETAIL_RANGE}(12)，
 * 直接按中心距离取 detail 即可。但祈祷一击的主要视觉体量<b>不是</b>那个 3.5 格的地面金环，
 * 而是 {@link #PRAYER_COLUMN_HEIGHT}(14) 格高的竖直光柱——玩家站在光柱脚下仰头看时，
 * 到「中心点」的距离很近、但光柱本身横跨整个视野。
 * </p>
 * <p>
 * 故祈祷一击的视觉半径取 {@code max(radius, 柱高 × }{@value #PRAYER_VISUAL_RADIUS_FACTOR}{@code )}
 * ≈ 5.6 格，再按「到该视觉边界的距离」取 detail（做法与
 * {@code AoeEffectRenderer} 处理红色闪电电柱、{@code AuraGroundRenderer} 处理圣域大圈同源）。
 * </p>
 *
 * <h4>削减策略</h4>
 * <ul>
 *     <li><b>弧带分段数是首要杠杆</b>——居合三层 + 残影共 5 次
 *         {@link #drawSlashArc}，回旋收尾的 {@link #glowRing} 内部又叠三层 {@link #band}，
 *         分段数一降全线受益。下限见 {@link #IAI_ARC_SEGMENTS_MIN} /
 *         {@link #SPIN_ARC_SEGMENTS_MIN}；</li>
 *     <li><b>残影整层可跳过</b>——它表达的是「刀走过的空气还没合拢」，
 *         是纯质感层，远处与主刀光完全糊在一起；</li>
 *     <li><b>升腾金光丝按步长抽取，不能截断</b>——角度主项是
 *         {@code baseAngle + TAU × i / 8} 均布的，截断前 N 根会让光丝只朝一侧升，
 *         破坏「圣光自四周升腾」的语义；</li>
 *     <li><b>刀光前端火花、地面切割线、十字圣徽完全不削</b>——
 *         火花 12 顶点、切割线 6 顶点、圣徽 12 顶点，却分别是「刀尖此刻在哪」
 *         「刀劈开了地面」「这是祈祷不是别的金色演出」的唯一表达，顶点性价比极高。</li>
 * </ul>
 *
 * <h4>颜色：本渲染器可以做到完全零动态色</h4>
 * <p>
 * 九个主题色<b>全部是编译期常量</b>，且演出过程中只有 alpha 在变、色相从不插值——
 * 这在本模组的渲染器里是独一份的（出血要按飞行进度插值、黄金树要按祝福纯度插值、
 * 睡眠要按螺旋弧长插值）。因此本渲染器<b>不需要任何 {@code SCRATCH} 复用缓冲</b>，
 * 九个 {@code C_} 常量在类加载时预解包一次即可，此后颜色相关分配恒为 0。
 * </p>
 * <p>
 * <b>顺带清掉几何临时数组：</b>{@link #spark} 原先用 {@code float[][] pts} 字面量表达
 * 四个角点，每次调用分配 5 个数组（1 外层 + 4 个 {@code float[2]}）。三套演出都要用它，
 * 现内联为标量，顶点输出与顺序逐字不变（做法与 {@code AoeEffectRenderer} v7 同源）。
 * </p>
 *
 * @author FlameForge
 * @version 2.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatArtEffectRenderer {

    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;
    /** 距离裁剪（格）：相机太远的特效本帧不绘制 */
    private static final double CULL = 64.0;
    private static final float TAU = (float) (Math.PI * 2.0);

    // ===== v2 LOD 下限与保留阈值 =====

    /**
     * 居合弧带的最少分段数。
     * <p>居合弧跨度约 150°（{@link #IAI_SWEEP_SPAN}），12 段即每段 12.5°，
     * 在 4 格半径下与真弧的偏离约 2.4cm，且刀光是「一闪而过」的高速演出，
     * 再低会让弧带在扫过瞬间显出折线感。</p>
     */
    private static final int IAI_ARC_SEGMENTS_MIN = 12;

    /**
     * 回旋弧带的最少分段数。
     * <p>回旋是整圈 360°，同样的段数摊到的角度是居合的 2.4 倍，故下限取得更高。
     * 20 段整圈即每段 18°，在 3 格半径下偏离约 3.7cm。</p>
     */
    private static final int SPIN_ARC_SEGMENTS_MIN = 20;

    /**
     * 祈祷圣光柱的最少竖直分段数。
     * <p>分段只用于沿高度做透明度渐变（上淡下实），3 段仍能表达「越高越融入天空」，
     * 降到 1 段则整根柱子变成均匀不透明、失去光柱的通透感。</p>
     */
    private static final int PRAYER_COLUMN_SEGMENTS_MIN = 3;

    /** 环 / 扬尘带类基元的最少分段数 */
    private static final int RING_SEGMENTS_MIN = 16;

    /**
     * 居合残影层的保留阈值。
     * <p>残影是纯质感层（表达「刀走过的空气还没合拢」），占居合近四成顶点，
     * 但在削减生效的距离上与主刀光完全糊成一片。</p>
     */
    private static final float IAI_AFTERIMAGE_KEEP_THRESHOLD = 0.55f;

    /**
     * 祈祷升腾金光丝的保留阈值。
     * <p>光丝是极细的竖线（半宽 0.05 格），远处几乎不可见。</p>
     */
    private static final float PRAYER_THREAD_KEEP_THRESHOLD = 0.45f;

    /**
     * 祈祷一击的视觉半径系数（× {@link #PRAYER_COLUMN_HEIGHT}）。
     * <p>祈祷的主要视觉体量是竖直光柱而非地面金环，故以柱高的 0.4 倍（约 5.6 格）
     * 作为视觉边界——玩家站在柱脚仰头看时不会被误判为「远」并削减
     * （详见类注释「祈祷一击的细节系数要按光柱视觉体量取」）。</p>
     */
    private static final double PRAYER_VISUAL_RADIUS_FACTOR = 0.4;

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

    // ===== v2：预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    //
    // 本渲染器的九个主题色全部是编译期常量、演出中只有 alpha 在变、色相从不插值，
    // 因此这一组常量之后颜色相关的堆分配恒为 0，无需任何 SCRATCH 复用缓冲
    // （详见类注释「颜色：本渲染器可以做到完全零动态色」）。

    /** 居合刀锋纯白 */
    private static final float[] C_IAI_EDGE = VisualColor.constant(IAI_EDGE);
    /** 居合刀身银灰 */
    private static final float[] C_IAI_STEEL = VisualColor.constant(IAI_STEEL);
    /** 居合刀影冷蓝 */
    private static final float[] C_IAI_SHADOW = VisualColor.constant(IAI_SHADOW);
    /** 回旋刀锋近白 */
    private static final float[] C_SPIN_EDGE = VisualColor.constant(SPIN_EDGE);
    /** 回旋琥珀边 */
    private static final float[] C_SPIN_AMBER = VisualColor.constant(SPIN_AMBER);
    /** 回旋扬尘土黄 */
    private static final float[] C_SPIN_DUST = VisualColor.constant(SPIN_DUST);
    /** 祈祷圣光核心 */
    private static final float[] C_PRAYER_CORE = VisualColor.constant(PRAYER_CORE);
    /** 祈祷暖金主色 */
    private static final float[] C_PRAYER_GOLD = VisualColor.constant(PRAYER_GOLD);
    /** 祈祷圣光深色 */
    private static final float[] C_PRAYER_DEEP = VisualColor.constant(PRAYER_DEEP);

    // ===== 居合斩几何参数 =====
    /** 弧光横扫的角度跨度（弧度）：约 150°，以正前方居中 */
    private static final float IAI_SWEEP_SPAN = 2.62f;
    /** 弧光扫完所占的进度比例（其余时间用于消散） */
    private static final float IAI_SWEEP_RATIO = 0.25f;
    /** 弧带细分段数（v2 起按细节系数缩放，下限 {@link #IAI_ARC_SEGMENTS_MIN}） */
    private static final int IAI_ARC_SEGMENTS = 36;
    /** 残影弧数量 */
    private static final int IAI_AFTERIMAGE_COUNT = 2;

    // ===== 回旋斩几何参数 =====
    /** 完整 360° 扫过所占的进度比例 */
    private static final float SPIN_SWEEP_RATIO = 0.6f;
    /** 环形弧带细分段数（整圈，需比居合更细才不显棱角；v2 起按细节系数缩放） */
    private static final int SPIN_ARC_SEGMENTS = 64;
    /** 刀光尾迹保留的角度长度（弧度）：约 200° */
    private static final float SPIN_TRAIL_SPAN = 3.5f;

    // ===== 祈祷一击几何参数 =====
    /** 光柱总高度（格） */
    private static final float PRAYER_COLUMN_HEIGHT = 14.0f;
    /** 光柱竖直细分段数（用于沿高度做透明度渐变；v2 起按细节系数缩放） */
    private static final int PRAYER_COLUMN_SEGMENTS = 10;
    /** 光柱底部半宽（格） */
    private static final float PRAYER_COLUMN_HALF = 0.55f;
    /** 升腾金光丝数量 */
    private static final int PRAYER_THREAD_COUNT = 8;

    private CombatArtEffectRenderer() {
    }

    /**
     * 渲染回调：遍历全部存活战技特效，按类型分发到对应自绘演出。
     * <p>
     * v2：新增细节系数计算（{@link #detailFor}）与同屏实例登记
     * （{@link VisualLod#countInstance()}）。后者尤其重要——本渲染器此前完全不登记，
     * 导致全局 {@code crowdFactor} 被系统性低估，已接入 LOD 的实体类渲染器
     * （出血、黄金树祝福、腐败女神）在团战时削减不足。
     * </p>
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
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > cullSqr) {
                continue;
            }

            float p = CombatArtEffectManager.progressFor(fx, now);
            if (p <= 0.0005f || p >= 1f) {
                continue;
            }

            // ⭐ v2：按「到演出视觉边界的距离」取细节系数。
            // 居合 / 回旋的半径远小于 FULL_DETAIL_RANGE，等价于按中心距离；
            // 祈祷的视觉体量是 14 格高的竖直光柱，必须单独换算（详见类注释）
            float detail = detailFor(fx.type, distSqr, fx.radius);
            // ⭐ v2：登记同屏实例。本渲染器此前不登记，会让全局 crowdFactor 被低估
            VisualLod.countInstance();

            float rx = (float) dx;
            float ry = (float) dy + Y_OFFSET;
            float rz = (float) dz;

            switch (fx.type) {
                case CombatArtEffectPacket.TYPE_IAI_SLASH ->
                        drawIaiSlash(builder, matrix, rx, ry, rz, fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_SPIN_SLASH ->
                        drawSpinSlash(builder, matrix, rx, ry, rz, fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_PRAYER_STRIKE ->
                        drawPrayerStrike(builder, matrix, rx, ry, rz, fx.radius, fx.baseAngle, p, detail);
                default -> {
                    // 未知类型：静默跳过，不画通用回退——战技特效都是有明确语义的，
                    // 画个不相干的圈反而误导玩家
                }
            }
        }
    }

    /**
     * 计算某战技特效本帧的细节系数。
     * <p>
     * 用<b>到演出视觉边界的近似距离</b>而非到中心的距离——祈祷一击的光柱高达
     * {@link #PRAYER_COLUMN_HEIGHT} 格，按中心距离会导致「站在柱脚仰头看却被判定为远」
     * 并削减（详见类注释）。
     * </p>
     *
     * @param type    特效类型
     * @param distSqr 相机到特效中心的平方距离
     * @param radius  特效半径（格）
     * @return 细节系数，范围 [{@link VisualLod#MIN_DETAIL} × {@link VisualLod#CROWD_MIN}, 1.0]
     */
    private static float detailFor(int type, double distSqr, double radius) {
        double visualRadius = visualRadiusFor(type, radius);
        double edge = Math.max(0.0, Math.sqrt(distSqr) - visualRadius);
        return VisualLod.detail(edge * edge);
    }

    /**
     * 各战技演出的「视觉边界半径」（格）。
     *
     * @param type   特效类型
     * @param radius 特效名义半径
     * @return 视觉边界半径（格）
     */
    private static double visualRadiusFor(int type, double radius) {
        if (type == CombatArtEffectPacket.TYPE_PRAYER_STRIKE) {
            // 祈祷的主要视觉体量是竖直光柱，而非地面金环
            return Math.max(radius, PRAYER_COLUMN_HEIGHT * PRAYER_VISUAL_RADIUS_FACTOR);
        }
        return radius;
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
     * <p>
     * <b>v2：弧带分段数按细节系数缩放；残影整层可跳过。</b>
     * 起手竖直刀锋、地面切割线、前端火花三者合计仅 42 顶点却承担核心辨识度，不参与削减。
     * </p>
     * <p>v2：三个配色改用只读常量，本方法零分配。</p>
     *
     * @param cx        中心（持有者位置）相对相机 X
     * @param cy        地面高度相对相机 Y
     * @param cz        中心相对相机 Z
     * @param radius    刀光半径（格）
     * @param baseAngle 持有者正前方的极坐标角（弧度）
     * @param p         归一化进度
     * @param detail    本帧细节系数
     */
    private static void drawIaiSlash(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz,
                                     float radius, float baseAngle, float p, float detail) {
        // 扫过进度：前 IAI_SWEEP_RATIO 完成，缓出让起手最快
        float sweep = easeOutCubic(clamp01(p / IAI_SWEEP_RATIO));
        // 整体淡出：扫完后开始消散
        float fade = 1f - smoothstep(IAI_SWEEP_RATIO, 1f, p);
        if (fade <= 0f || sweep <= 0f) {
            return;
        }

        final float[] edge = C_IAI_EDGE;
        final float[] steel = C_IAI_STEEL;
        final float[] shadow = C_IAI_SHADOW;

        // 起点在右侧（-半跨度），扫向左侧（+半跨度）——居合为右手拔刀横斩
        float startAngle = baseAngle - IAI_SWEEP_SPAN * 0.5f;

        // 刀光离地高度：约在腰部（不贴地，否则像法阵而不像刀光）
        float slashY = cy + 1.0f;

        int arcSegments = VisualLod.scaleSegments(IAI_ARC_SEGMENTS, IAI_ARC_SEGMENTS_MIN, detail);

        // ===== 主刀光弧带（三层叠绘）=====
        drawSlashArc(b, m, cx, slashY, cz, radius * 1.02f, radius * 1.18f,
                startAngle, IAI_SWEEP_SPAN, sweep, arcSegments, shadow, 0.30f * fade);
        drawSlashArc(b, m, cx, slashY, cz, radius * 0.86f, radius * 1.04f,
                startAngle, IAI_SWEEP_SPAN, sweep, arcSegments, steel, 0.72f * fade);
        drawSlashArc(b, m, cx, slashY, cz, radius * 0.95f, radius * 1.00f,
                startAngle, IAI_SWEEP_SPAN, sweep, arcSegments, edge, 1.00f * fade);

        // ===== 残影弧：更小半径、进度滞后、更暗，制造「刀走过的空气还没合拢」的观感 =====
        // v2：纯质感层，远处与主刀光完全糊在一起，低细节时整层跳过（省约四成顶点）
        if (VisualLod.keepLayer(detail, IAI_AFTERIMAGE_KEEP_THRESHOLD)) {
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
                        startAngle, IAI_SWEEP_SPAN, ghostSweep, arcSegments, steel, ghostAlpha);
            }
        }

        // ===== 起手竖直刀锋：拔刀那一瞬身前的一道白刃 =====
        // 仅 24 顶点，是「这一刀是拔出来的」的唯一表达，不参与削减
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
        // 仅 6 顶点，是「刀劈开了地面」的唯一表达，不参与削减
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
        // 仅 12 顶点，不参与削减
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
     * <p>
     * <b>v2：全部弧带 / 环 / 扬尘带的分段数按细节系数缩放</b>（下限
     * {@link #SPIN_ARC_SEGMENTS_MIN}）。前端火花仅 12 顶点，是「刀尖此刻在哪」的
     * 唯一表达，不参与削减。
     * </p>
     * <p>v2：三个配色改用只读常量，本方法零分配。</p>
     *
     * @param baseAngle 起始角（持有者正前方）
     * @param detail    本帧细节系数
     */
    private static void drawSpinSlash(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz,
                                      float radius, float baseAngle, float p, float detail) {
        final float[] edge = C_SPIN_EDGE;
        final float[] amber = C_SPIN_AMBER;
        final float[] dust = C_SPIN_DUST;

        float slashY = cy + 0.85f;
        int arcSegments = VisualLod.scaleSegments(SPIN_ARC_SEGMENTS, SPIN_ARC_SEGMENTS_MIN, detail);

        // ===== 扫过阶段：带尾迹的旋转弧 =====
        if (p < SPIN_SWEEP_RATIO) {
            // 匀速扫过，与玩家身体旋转同步
            float sweep = p / SPIN_SWEEP_RATIO;
            float frontAngle = baseAngle + TAU * sweep;
            // 尾迹起点：前端往回退 SPIN_TRAIL_SPAN，但不早于起始角
            float trailLen = Math.min(SPIN_TRAIL_SPAN, TAU * sweep);
            float trailStart = frontAngle - trailLen;

            drawSlashArc(b, m, cx, slashY, cz, radius * 1.02f, radius * 1.16f,
                    trailStart, trailLen, 1f, arcSegments, amber, 0.34f);
            drawSlashArc(b, m, cx, slashY, cz, radius * 0.88f, radius * 1.04f,
                    trailStart, trailLen, 1f, arcSegments, edge, 0.80f);

            // 前端火花：刀尖位置（不削减）
            float fx = cx + (float) Math.cos(frontAngle) * radius;
            float fz = cz + (float) Math.sin(frontAngle) * radius;
            spark(b, m, fx, fz, slashY, radius * 0.12f + 0.10f, edge, 0.95f);

            // 扬尘：跟在刀光后面从地面扬起（低透明度宽带，只在已扫过的扇区）
            band(b, m, cx, cz, cy, radius * 0.45, radius * 1.25,
                    trailStart, trailLen, arcSegments,
                    dust[0], dust[1], dust[2], 0.16f, 0f);
        }

        // ===== 收尾阶段：整圈残留光环 + 全周扬尘一起淡出 =====
        float tail = smoothstep(SPIN_SWEEP_RATIO - 0.1f, SPIN_SWEEP_RATIO, p)
                * (1f - smoothstep(SPIN_SWEEP_RATIO, 1f, p));
        if (tail > 0f) {
            glowRing(b, m, cx, cz, slashY, radius, arcSegments, edge,
                    0.55f * tail, 0.22f * tail, 0.06, 0.35);
            glowRing(b, m, cx, cz, slashY, radius * 1.06f, arcSegments, amber,
                    0.30f * tail, 0.14f * tail, 0.05, 0.30);
            // 地面全周扬尘，随收尾略微向外扩散
            double dustOuter = radius * (1.25 + 0.25 * smoothstep(SPIN_SWEEP_RATIO, 1f, p));
            band(b, m, cx, cz, cy, radius * 0.4, dustOuter, 0f, TAU, arcSegments,
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
     * <p>
     * <b>v2 削减：</b>光柱竖直分段数缩放（这是本演出最大的杠杆——每段要画三层十字双面，
     * 单段就是 108 顶点）；落地双环与圣徽底盘的分段数缩放；升腾金光丝<b>按步长抽取</b>
     * （角度主项均布，截断会让光丝只朝一侧升）且整层可跳过。
     * <b>中央十字圣徽完全不削</b>——仅 12 顶点却是「这是祈祷不是别的金色演出」的唯一标志。
     * </p>
     * <p>v2：三个配色改用只读常量，本方法零分配。</p>
     *
     * @param baseAngle 持有者朝向（此演出与朝向无关，仅用于让光丝分布逐次略有差异）
     * @param detail    本帧细节系数
     */
    private static void drawPrayerStrike(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz,
                                         float radius, float baseAngle, float p, float detail) {
        final float[] core = C_PRAYER_CORE;
        final float[] gold = C_PRAYER_GOLD;
        final float[] deep = C_PRAYER_DEEP;

        float fade = 1f - smoothstep(0.45f, 1f, p);

        // ===== 圣光柱：底端自上而下降落，落地后维持并渐隐 =====
        float drop = easeOutCubic(clamp01(p / 0.25f));
        float columnBottom = cy + PRAYER_COLUMN_HEIGHT * (1f - drop);
        float columnTop = cy + PRAYER_COLUMN_HEIGHT;
        if (columnTop > columnBottom && fade > 0f) {
            // v2：竖直分段数缩放。每段要画三层十字双面（外晕 / 主体 / 白热核），
            // 单段 108 顶点，是本演出最大的顶点杠杆
            int columnSegments = VisualLod.scaleSegments(
                    PRAYER_COLUMN_SEGMENTS, PRAYER_COLUMN_SEGMENTS_MIN, detail);
            // 沿高度分段绘制，越往上越淡（融入天空）
            float segLen = (columnTop - columnBottom) / columnSegments;
            for (int i = 0; i < columnSegments; i++) {
                float y0 = columnBottom + segLen * i;
                float y1 = y0 + segLen;
                float u0 = (float) i / columnSegments;
                float u1 = (float) (i + 1) / columnSegments;
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
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), gold,
                    0.80f * ringFade, 0.35f * ringFade, 0.08, 0.50);
            // 第二道追赶环，错相扩散
            float ring2 = clamp01((p - 0.28f) / 0.5f);
            if (ring2 > 0f && ring2 < 1f) {
                double rr2 = radius * 0.7 * easeOutCubic(ring2);
                glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2, detail), deep,
                        0.45f * (1f - ring2), 0.20f * (1f - ring2), 0.06, 0.38);
            }
        }

        // ===== 地面十字圣徽：沿用「圣域」母题的两条垂直相交粗线，随心跳脉动 =====
        float emblem = smoothstep(0.05f, 0.2f, p) * fade;
        if (emblem > 0f) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(p * 18.0);
            float len = radius * 0.72f;
            double hw = Math.max(0.06, radius * 0.035);
            // 十字本体仅 12 顶点，是本演出的核心标志，不参与削减
            line(b, m, cx - len, cz, cx + len, cz, cy, hw, core, 0.75f * emblem * pulse, 0.75f * emblem * pulse);
            line(b, m, cx, cz - len, cx, cz + len, cy, hw, core, 0.75f * emblem * pulse, 0.75f * emblem * pulse);
            // 圣徽底色圆盘，让十字不至于孤零零浮在地上（分段数缩放）
            band(b, m, cx, cz, cy, 0.0, radius * 0.85, 0f, TAU,
                    VisualLod.scaleSegments(28, RING_SEGMENTS_MIN, detail),
                    gold[0], gold[1], gold[2], 0.10f * emblem, 0f);
        }

        // ===== 升腾金光丝：自地面向上飘的细竖线，逐根错相 =====
        // v2：整层可跳过（极细的竖线，远处几乎不可见）
        float threadPhase = clamp01((p - 0.2f) / 0.8f);
        if (threadPhase > 0f && fade > 0f && VisualLod.keepLayer(detail, PRAYER_THREAD_KEEP_THRESHOLD)) {
            // ⭐ v2：角度主项是 baseAngle + TAU × i / 8 均布的，必须按步长抽取而非截断，
            // 否则光丝只朝一侧升、破坏「圣光自四周升腾」的语义。
            // 角度基准仍用原始 PRAYER_THREAD_COUNT，保证保留光丝的方位与全细节时一致
            int drawnThreads = VisualLod.scale(PRAYER_THREAD_COUNT, detail);
            int threadStep = Math.max(1, PRAYER_THREAD_COUNT / drawnThreads);
            for (int i = 0; i < PRAYER_THREAD_COUNT; i += threadStep) {
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
     * <p>
     * <b>v2：{@code segments} 由调用方按细节系数缩放后传入。</b>
     * 注意本方法内部按 {@code sweep} 比例截取实际绘制段数，缩放 segments 只让折线更粗糙，
     * <b>不改变弧带的起止角与跨度</b>——形状完全一致。
     * </p>
     *
     * @param rInner     弧带内半径
     * @param rOuter     弧带外半径
     * @param startAngle 起始角（弧度）
     * @param span       总跨度（弧度）
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
     * <p><b>v2 注意：</b>本方法内部叠三层 {@link #band}，单次调用即 {@code segs × 18} 顶点，
     * 调用方务必传入经 {@link #segmentsFor(double, float)} 或
     * {@link VisualLod#scaleSegments} 缩放后的分段数。</p>
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
     * <p>仅 12 顶点，不参与分段缩放。</p>
     * <p>
     * <b>v2：四个角点内联为标量。</b>原实现用 {@code float[][] pts} 字面量表达角点，
     * 每次调用分配 <b>5 个临时数组</b>（1 个外层 + 4 个 {@code float[2]}）。
     * 三套演出都会调用本方法（居合 / 回旋各一次），改为标量后本方法零分配，
     * 顶点输出与顺序逐字不变（做法与 {@code AoeEffectRenderer} v7 同源）。
     * </p>
     */
    private static void spark(BufferBuilder b, Matrix4f m, float px, float pz, float y,
                              float size, float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];

        // 四个角点（顺序与原 pts[0..3] 一致：北 → 东 → 南 → 西）
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
     * 火花的一瓣三角形：中心不透明，两个外角渐隐为 0。
     *
     * @param cx 中心 X（相对相机）
     * @param y  水平面高度
     * @param cz 中心 Z
     * @param ax 第一个外角 X
     * @param az 第一个外角 Z
     * @param bx 第二个外角 X
     * @param bz 第二个外角 Z
     */
    private static void sparkTri(BufferBuilder b, Matrix4f m,
                                 float cx, float y, float cz,
                                 float ax, float az, float bx, float bz,
                                 float r, float g, float bl, float alpha) {
        b.vertex(m, cx, y, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, y, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, y, bz).color(r, g, bl, 0f).endVertex();
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
    // v2 说明：原先的 unpack(int) 已删除——它是本类此前唯一的颜色堆分配来源
    // （每次调用 new float[3]），现全部由 VisualColor.constant() 的 C_ 常量取代。
    // 本渲染器的九个主题色全部是编译期常量、演出中只有 alpha 在变、色相从不插值，
    // 因此不需要任何 SCRATCH 复用缓冲。
    // 若后续新增演出需要「随时间变化的配色」，请走 VisualColor.*Into(dst, ...) + 复用缓冲，
    // 不要重新引入返回新数组的写法。

    /**
     * 环分段数（随半径，夹取 28~56）。
     * <p>全细节下的基准值；带 LOD 的版本见 {@link #segmentsFor(double, float)}。</p>
     */
    private static int segmentsFor(double radius) {
        int v = (int) (radius * 4);
        if (v < 28) {
            return 28;
        }
        return Math.min(v, 56);
    }

    /**
     * 带细节层级的环分段数（v2 新增）。
     *
     * @param radius 环半径（格）
     * @param detail 本帧细节系数
     * @return 缩放后的分段数，下限 {@link #RING_SEGMENTS_MIN}
     */
    private static int segmentsFor(double radius, float detail) {
        return VisualLod.scaleSegments(segmentsFor(radius), RING_SEGMENTS_MIN, detail);
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
}
