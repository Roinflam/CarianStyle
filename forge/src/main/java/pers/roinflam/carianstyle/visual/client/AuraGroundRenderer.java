package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
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
 * <p>
 * <b>v2（性能，视觉零变化）：</b>接入 {@link VisualBatch}——不再自行设置 / 恢复 GL 状态、
 * 不再自行 {@code begin/end} 顶点缓冲，改为向共享缓冲写顶点，由 {@link VisualBatch} 在本帧末
 * 统一提交（本模组多个世界渲染器合并为一次 GL 状态切换与一次 draw call）。
 * 本渲染器不做范围实体查询（数据来自 {@link AuraScanner} 的 tick 级扫描），故不涉及
 * {@link SharedEntityQuery}。
 * <p>
 * <b>注意（迁移要点）：</b>{@code mc.level == null} 时清空 {@link #STATE} 的分支<b>必须放在
 * 取共享缓冲之前</b>——离开世界那一帧共享批次本就不会开启，若把清空逻辑排在取缓冲的判空之后，
 * 淡出状态会残留到下次进入世界，与新的实体网络 id 撞号后会闪出一两帧错误光环。
 *
 * <h3>v3 修复：动画时间源回绕导致光环永久不可见</h3>
 * <p>
 * <b>问题：</b>动画时间此前取自世界游戏刻并对 100 万取模：
 * </p>
 * <pre>
 * float now = (float) (mc.level.getGameTime() % 1_000_000L) + partial;
 * </pre>
 * <p>
 * 该值每 1,000,000 tick（约 <b>13.9 小时</b>游戏时间）从接近 100 万<b>瞬间跌回 0</b>。
 * 跌回的那一帧，所有正在显示的光环其 {@link AuraState#appearTime} 仍停留在回绕前的大数值，
 * 于是：
 * </p>
 * <pre>
 * now - st.appearTime  →  0 - 999999  =  一个大负数
 * eased = easeOutCubic(clamp(负数 / APPEAR_TICKS, 0, 1)) = 0
 * radiusFactor = alpha = 0        // 完全不可见
 * </pre>
 * <p>
 * 而该光环<b>仍在 {@code activeKeys} 之中</b>（附魔还穿在身上），
 * 每帧都会被刷新回 {@code fadeStart = -1}（视为"仍激活"），
 * 因此永远不会进入淡出分支、也就永远不会被移除重建——
 * <b>光环会永久消失，直到玩家把装备脱下再穿上</b>（或重进世界）。
 * 长时间不关的服务器上，多个玩家会在同一时刻集体"丢光环"，且没有任何报错。
 * </p>
 * <p>
 * <b>顺带的精度问题：</b>float 只有 24 位有效尾数，在 1e6 量级下最小分辨间隔（ULP）已达
 * <b>0.0625 tick</b>，而 {@code partial} 的取值范围本就是 0~1——也就是说接近回绕点时，
 * 帧间插值 {@code partial} 提供的平滑度<b>大部分被精度截断吞掉</b>，动画会出现肉眼可辨的阶梯感。
 * </p>
 * <p>
 * <b>修复：</b>改用墙钟差值（{@link #START_MILLIS}），与本模组其余全部渲染器
 * （{@code SleepRenderer} / {@code IncisionRenderer} / {@code HemorrhageBloodRenderer} 等）
 * 的做法完全一致。为保证<b>视觉零变化</b>，这里除以 {@link #MILLIS_PER_TICK}(50) 换算成
 * <b>tick 单位</b>，因此下方全部时间常量（{@link #APPEAR_TICKS}、{@link #PULSE_SPEED}、
 * {@link #RIPPLE_PERIOD_TICKS} 等）<b>一个都不用改</b>，动画速度与优化前完全相同。
 * </p>
 * <p>
 * <b>行为差异（是改进）：</b>时间源由「世界游戏刻」变为「墙钟」，动画不再受服务端 TPS 影响——
 * 服务器卡顿时光环旋转 / 呼吸不会跟着变慢，与本模组其余特效表现统一。
 * 由于是纯客户端的视觉动画、不参与任何机制判定，该差异无副作用。
 * </p>
 * <p>
 * <b>关于 partial：</b>墙钟本身即连续量，无需再叠加帧间插值，故 {@code now} 不再使用
 * {@code partial}；但实体位置插值（{@code Mth.lerp(partial, entity.xo, entity.getX())}）
 * <b>仍然保留</b>——那是位置平滑，与动画时间无关。
 * </p>
 * <p>
 * <b>新的精度边界：</b>float 尾数 24 位，tick 单位下约 1677 万 tick（连续运行 <b>233 小时</b>）
 * 之内 ULP ≤ 1；由于取的是<b>客户端进程启动以来</b>的时长（而非世界游戏刻），
 * 实际很难触及，且即便触及也只是动画略显阶梯，<b>不会再出现光环永久消失</b>。
 * </p>
 *
 * <h3>v4（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个圆形光环每帧的顶点量粗算（以最大的圣域 16 格为例）：
 * </p>
 * <pre>
 * 外辉光 + 内辉光 + 主环核心（3 × 48 段 × 6）      864
 * 圆形涟漪（2 环 × 48 段 × 6）                     576
 * 外缘符文刻度（40 条 × 6）                        240
 * 母题 motifHoly（16 射线 + 十字 + 8 芒尖）        204
 * 径向渐变填充（32 段 × 3）                         96
 * 边框彗星（2 × 3 尾点 × 12）                       72
 * ─────────────────────────────────────────────
 * 合计                                       ~2050 顶点 / 光环 / 帧
 * </pre>
 * <p>
 * 圣域是<b>群体增益</b>光环（举盾即为 16 格内全部友方提供减伤与护盾），
 * 团战中十人同时举盾并不罕见——2 万顶点，且这些圈会大面积重叠。
 * 由于全部是关深度写入的半透明叠加，重叠区域的 overdraw 是实打实的填充率开销。
 * </p>
 * <p>
 * 现全部元素按 {@link VisualLod#detail} 缩放；同时补上此前缺失的
 * {@link VisualLod#countInstance()}——本渲染器不登记实例会让全局 {@code crowdFactor}
 * 被系统性低估，已接入 LOD 的实体类渲染器在团战时削减不足。
 * </p>
 *
 * <h4>细节系数必须按「到圆环边界的距离」取</h4>
 * <p>
 * 与 {@code GravitasDistortionRenderer} 的力场圈完全同源的问题：
 * 圣域半径 16 格，<b>大于 {@link VisualLod#FULL_DETAIL_RANGE}(12)</b>。
 * 若照搬「按光环中心的平方距离取 detail」，会出现：
 * </p>
 * <pre>
 * 玩家站在自己圣域圈的内侧边缘
 *   → 到圈心距离 ≈ 16 格
 *   → detail 被判定为"远"、大幅削减
 *   → 但那圈发光边界就在脚边，削减清晰可见
 * </pre>
 * <p>
 * 故改用<b>到圆环边界的近似距离</b>（{@code max(0, 到圈心距离 - 当前半径)}）：
 * 人站在圈内或贴着圈边时该值为 0、detail 恒为 1.0，只有整个圈都离得很远时才削减。
 * 这里需要一次 {@link Math#sqrt}，但同屏光环数量通常是个位数，开方成本可忽略。
 * </p>
 *
 * <h4>削减策略</h4>
 * <ul>
 *     <li><b>环的分段数是首要杠杆</b>——三层主环 + 涟漪合计占七成顶点。下限
 *         {@link #RING_SEGMENTS_MIN}(24) 比实体渲染器高不少：多边形与真圆的偏离量正比于半径，
 *         而光环半径可达 16 格（24 段时偏离约 14cm，在远处不可察）；</li>
 *     <li><b>角度均布的元素按步长抽取，绝不能截断</b>——符文刻度、母题射线、断环、
 *         栅条的角度都是 {@code i × (TAU / 总数)}，截断前 N 个会让整圈只剩一段圆弧上有内容，
 *         法阵会明显"缺一块"；</li>
 *     <li><b>星形 / 多边形母题完全不削</b>——卡利亚六芒星、圣域十字圣徽每个只有 30~40 顶点，
 *         却是「这是哪个光环」的唯一辨识依据；</li>
 *     <li><b>彗星与火花整层可跳过</b>——都是极小的装饰光点，远处完全看不出。</li>
 * </ul>
 *
 * <h3>v5（堆分配，视觉逐位一致）：颜色与几何数组零分配化</h3>
 * <p>
 * v4 把顶点量压下去了，但本渲染器<b>是全模组最后一个还在用返回新数组写法的</b>，
 * 而且它的分配点比其它渲染器更隐蔽——不在颜色上，而在那个到处都在调的小火花：
 * </p>
 * <pre>
 * addSpark（每次 1 个 float[][] 外层 + 4 个 float[2] = 5 个数组）：
 *   方形：四角火花 4 + 边框彗星 2                          = 6 次
 *   圆形：边框彗星 2×3 尾点                                = 6 次
 *   母题 motifCarian：顶点水晶碎光                          = 6 次
 *   母题 motifCosmic：addStarField 星屑                    = 14 次
 *   母题 motifHoly：芒尖金光                                = 8 次
 *   ──────────────────────────────────────────────────
 *   单光环峰值约 20~26 次 × 5 = ~100~130 个数组 / 光环 / 帧
 *
 * unpack（每次 1 个 float[3]）：                            1 次
 * perimeterPoint（每次 1 个 double[2]）：                   2 次
 * </pre>
 * <p>
 * <b>圣域是群体增益光环</b>——举盾即为 16 格内全部友方提供减伤与护盾，
 * 团战中十人同时举盾并不罕见。10 个光环 × 60fps ≈ <b>每秒 7.8 万次</b>小数组分配，
 * 而这些数组的存活期只有紧随其后的几行。
 * </p>
 * <p>
 * 现改为三条路径：
 * </p>
 * <ol>
 *     <li><b>{@link #addSpark} 的四个角点内联为标量</b>——做法与
 *         {@code AoeEffectRenderer} v7 处理同名方法完全一致，本方法自此零分配。
 *         这是本次收益最大的一项（占全部分配的九成以上）；</li>
 *     <li><b>{@link #unpack} 改为 {@link VisualColor#unpackInto} 写入 {@link #SCRATCH}</b>——
 *         注意本渲染器的颜色<b>不是编译期常量</b>（来自 {@link AuraDisplayRegistry.AuraInfo}
 *         的运行时字段），因此无法像其它渲染器那样预解包成 {@code C_} 常量，只能走复用缓冲。
 *         好在两个调用点都是「解包 → 立刻提取 r/g/b 标量 → 之后再不碰缓冲」，
 *         一个缓冲绰绰有余；</li>
 *     <li><b>{@link #perimeterPoint} 改为写入 {@link #PERIMETER_XZ}</b>——
 *         调用点只有边框彗星一处，且同样是「写入 → 立刻读走」。</li>
 * </ol>
 * <p>
 * <b>⚠ 复用缓冲的约束：</b>{@link #SCRATCH} 与 {@link #PERIMETER_XZ} 都必须
 * 「写入 → 立即消费 → 不跨调用留存」。本渲染器不存在「两个动态值同时存活」的场景
 * （{@link #addBand} / {@link #addLine} 等基元接收的都是已拆开的 r/g/b 标量，
 * 而非数组引用），故各一个缓冲即可。若将来新增需要同时持有两组颜色的元素，
 * 必须另开缓冲——参照 {@code SleepRenderer} 螺旋的双缓冲滚动写法。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#unpackInto} 与旧 {@code unpack} 是同一个
 * {@code /255f} 公式；{@link #addSpark} 的角点数值与顶点写入顺序逐字照搬；
 * {@link #perimeterPoint} 的分支逻辑一字未改。输出的每个顶点与 v4 完全相同。
 * </p>
 *
 * @author FlameForge
 * @version 5
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
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>v3：动画时间改为「当前毫秒 - 此起始值」的差值，取代原先会回绕的
     * {@code gameTime % 1_000_000}（详见类注释「v3 修复」小节）。
     * 必须用差值而非 {@code System.currentTimeMillis()} 本身——后者数值约 1.7e12，
     * 远超 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    /**
     * 每游戏刻的毫秒数。
     * <p>用于把墙钟毫秒差换算为 <b>tick 单位</b>的动画时间，从而让下方全部
     * {@code *_TICKS} / {@code *_SPEED} 常量原样沿用、动画速度与修复前逐帧一致。</p>
     */
    private static final float MILLIS_PER_TICK = 50.0f;

    /**
     * 方形每边外扩余量（格）——近似 {@code getEntitiesOfClass} 碰撞箱相交带来的额外生效宽度。
     * 真实值随目标碰撞箱半宽变化（玩家约 0.3、多数生物约 0.4~0.5），取折中值。
     */
    private static final double EDGE_MARGIN = 0.5;

    // ===== v4 LOD 下限与保留阈值 =====

    /**
     * 圆形径向渐变填充的最少分段数。
     * <p>填充盘是中心→边缘的低 alpha 渐变、无描边线，多边形化最不易察觉，故下限可比环低。</p>
     */
    private static final int FILL_SEGMENTS_MIN = 16;

    /**
     * 环 / 涟漪类基元的最少分段数。
     * <p>
     * 比实体渲染器的下限（8~10）高不少：多边形与真圆的偏离量正比于半径，
     * 而光环半径可达 16 格（圣域）。24 段在 16 格半径下偏离约 14cm，
     * 在削减生效的距离上不可察；再低就能看出明显棱角了。
     * </p>
     */
    private static final int RING_SEGMENTS_MIN = 24;

    /**
     * 装饰光点层（四角火花 / 边框彗星 / 母题芒尖）的保留阈值。
     * <p>这些都是尺寸极小的菱形光点，远处完全看不出，却各占几十上百顶点。</p>
     */
    private static final float SPARKLE_KEEP_THRESHOLD = 0.45f;

    /**
     * 边框彗星层的保留阈值。
     * <p>彗星带 3 个渐隐尾点，是纯动效装饰；比火花更早跳过（它还多占 3 倍顶点）。</p>
     */
    private static final float COMET_KEEP_THRESHOLD = 0.5f;

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

    /**
     * v5：颜色解包的复用缓冲（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * <b>为什么本渲染器不能用 {@code C_} 常量：</b>其它渲染器的主题色都是编译期常量、
     * 可以在类加载时预解包一次；而本渲染器的颜色来自
     * {@link AuraDisplayRegistry.AuraInfo#color()} 这个<b>运行时字段</b>
     * （每个光环各不相同，且由注册表决定），无法预解包，只能走复用缓冲。
     * </p>
     * <p>
     * 两个调用点（{@link #drawSquare} / {@link #drawCircle}）都是
     * 「解包 → 立刻提取 r/g/b 标量 → 之后再不碰缓冲」，故一个缓冲足够。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] SCRATCH = new float[VisualColor.RGB];

    /**
     * v5：方形周边坐标的复用缓冲（⚠ 同上，仅供 {@link #perimeterPoint} 使用）。
     * <p>索引 0 为相对中心的 dx，索引 1 为 dz。调用点只有 {@link #drawSquare} 的边框彗星一处。</p>
     */
    private static final double[] PERIMETER_XZ = new double[2];

    private AuraGroundRenderer() {
    }

    /**
     * 逐光环动画状态记录（tick 时间轴），支持出现展开与消失淡出。
     * 键 = entityId 与 serialId 打包。仅渲染线程访问。
     * <p>v3：时间轴由「世界游戏刻取模」改为「客户端墙钟差值（换算为 tick）」，
     * 字段语义与单位不变，详见类注释。</p>
     */
    private static final Map<Long, AuraState> STATE = new HashMap<>();

    /**
     * 单个光环的动画状态。
     * <p>
     * 记录出现时刻、消失时刻，以及最近一次的渲染参数与世界坐标——
     * 这样即便光环已离开扫描结果（甚至实体已卸载），也能在原地把淡出动画播完。
     */
    private static final class AuraState {
        /** 出现时刻（tick，墙钟换算） */
        float appearTime;
        /** 开始消失的时刻（tick，墙钟换算）；<0 表示仍激活（未开始淡出） */
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
     *
     * @param detail 本帧细节系数（v4 新增，按到圆环边界的距离取，详见类注释）
     */
    private record Prepared(int serialId, double rx, double ry, double rz, int color, double radius,
                            AuraDisplayRegistry.AuraShape shape, double alpha, float detail) {
    }

    /**
     * 打包 (entityId, serialId) 为唯一键。
     */
    private static long key(int entityId, int serialId) {
        return ((long) entityId << 16) | (serialId & 0xFFFFL);
    }

    /**
     * 取当前动画时间（tick 单位，墙钟驱动）。
     * <p>
     * v3：取代原先的 {@code (float)(gameTime % 1_000_000L) + partial}。
     * 除以 {@link #MILLIS_PER_TICK} 换算为 tick，使下方所有时间常量无需改动、
     * 动画速度与修复前完全一致（详见类注释「v3 修复」小节）。
     * </p>
     *
     * @return 自渲染器加载以来的时长（tick）
     */
    private static float currentTime() {
        return (System.currentTimeMillis() - START_MILLIS) / MILLIS_PER_TICK;
    }

    /**
     * 渲染回调。
     * <p>
     * v2：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理；本方法只负责状态机推进与写顶点。
     * v3：动画时间源改为墙钟（{@link #currentTime()}），修复 13.9 小时回绕导致光环永久消失。
     * v4：新增细节系数计算（按到圆环边界的距离）与同屏实例登记。
     * </p>
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
        // 迁移要点：清空状态必须先于「取共享缓冲」的判空，否则离开世界时状态会残留（见类注释）
        if (mc.level == null) {
            STATE.clear();
            return;
        }
        // 注意：即使本帧没有任何激活光环（auras 为空），仍需继续——
        // 因为可能有刚消失、正在播放淡出动画的光环要渲染。

        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        float partial = VisualBatch.partialTick();
        // ⭐ v3：墙钟驱动的动画时间（tick 单位）。
        // 不再叠加 partial —— 墙钟本身即连续量；partial 仅保留用于下方的实体位置插值。
        float now = currentTime();

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
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > cullSqr) {
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

            // ⭐ v4：细节系数必须按「到圆环边界的距离」取，不能按到圈心的距离。
            // 圣域半径 16 格 > VisualLod.FULL_DETAIL_RANGE(12)，按圈心算会导致
            // 玩家站在自己圈的边缘时被判定为"远"并削减，而那圈线其实就在脚边（详见类注释）。
            double edgeDist = Math.max(0.0, Math.sqrt(distSqr) - animatedReach);
            float detail = VisualLod.detail(edgeDist * edgeDist);
            // ⭐ v4：登记同屏实例。本渲染器此前不登记，导致全局 crowdFactor 被系统性低估，
            // 已接入 LOD 的实体类渲染器在团战时削减不足
            VisualLod.countInstance();

            prepared.add(new Prepared(
                    st.serialId,
                    centerWorldX - cam.x, lerpY - cam.y, centerWorldZ - cam.z,
                    st.color, animatedReach, st.shape, alpha, detail));
        }

        if (prepared.isEmpty()) {
            return;
        }

        float pulse = 0.5f + 0.5f * Mth.sin(now * PULSE_SPEED);
        float fillMul = 0.85f + 0.15f * pulse;
        float lineMul = 0.80f + 0.20f * pulse;
        float runeRot = now * RUNE_SPEED;

        Matrix4f matrix = VisualBatch.matrix();

        // 每个光环按 填充→辉光→核心→强调 的顺序追加（顺序即混合顺序）。
        // 把整体 alpha 折入 fillMul/lineMul——因绘制函数内所有顶点 alpha 均为
        // “基值 × fillMul/lineMul”，故乘一次即可让整张光环统一淡入/淡出（颜色不受影响）。
        for (Prepared p : prepared) {
            float af = fillMul * (float) p.alpha();
            float al = lineMul * (float) p.alpha();
            if (p.shape() == AuraDisplayRegistry.AuraShape.SQUARE) {
                drawSquare(builder, matrix, p, af, al, now, p.detail());
            } else {
                drawCircle(builder, matrix, p, af, al, runeRot, now, p.detail());
            }
        }
    }

    // ==================== 方形绘制 ====================

    /**
     * 绘制一个方形光环（填充 + 发光边 + 四角追逐 + 方形涟漪 + 边框彗星）。
     * <p>
     * v4 削减：涟漪条数缩减；四角火花与边框彗星按保留阈值整层跳过。
     * <b>底色填充、三层发光边、四角位置本身完全不削</b>——方形只有 4 条边、4 个角，
     * 削掉任何一个都不再是「方形」；且三层边加起来才 72 顶点，是精确边界的唯一表达。
     * </p>
     * <p>
     * v5：颜色改为 {@link VisualColor#unpackInto} 写入 {@link #SCRATCH} 后立即提取标量；
     * 边框彗星的周边坐标改为写入 {@link #PERIMETER_XZ}。二者时序上先后不重叠，互不干扰。
     * </p>
     *
     * @param fillMul 填充呼吸亮度系数
     * @param lineMul 线条呼吸亮度系数
     * @param now     当前时间（tick，墙钟驱动），驱动追逐/涟漪/彗星相位
     * @param detail  本帧细节系数
     */
    private static void drawSquare(BufferBuilder builder, Matrix4f m, Prepared p,
                                   float fillMul, float lineMul, float now, float detail) {
        double cx = p.rx(), cy = p.ry(), cz = p.rz();
        double half = p.radius();
        // v5：无分配解包，写入复用缓冲后立即提取为标量（之后再不碰缓冲）
        VisualColor.unpackInto(SCRATCH, p.color());
        float r = SCRATCH[0], g = SCRATCH[1], b = SCRATCH[2];
        float br = brighten(r), bg = brighten(g), bb = brighten(b);

        // 区域底色
        addSquareFill(builder, m, cx, cy, cz, half, r, g, b, SQUARE_FILL_ALPHA * fillMul);

        // 从中心向外扩散的方形涟漪。相位为 i/count 均布的循环波，没有固定方位，
        // 减条数只表现为「波与波之间隔得更开」，观感自然，无需按步长抽取
        int rippleCount = VisualLod.scale(RIPPLE_COUNT, detail);
        for (int i = 0; i < rippleCount; i++) {
            float phase = frac(now / RIPPLE_PERIOD_TICKS + (float) i / rippleCount);
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

        // 四角追逐火花：四个角依次明灭。纯装饰光点，远处看不出，低细节时整层跳过
        if (VisualLod.keepLayer(detail, SPARKLE_KEEP_THRESHOLD)) {
            double xMin = cx - half, xMax = cx + half;
            double zMin = cz - half, zMax = cz + half;
            // v5：四个角点内联为标量，不再用 double[][] 字面量（原实现每帧分配 5 个数组）
            for (int i = 0; i < 4; i++) {
                double cornerX;
                double cornerZ;
                switch (i) {
                    case 0 -> {
                        cornerX = xMin;
                        cornerZ = zMin;
                    }
                    case 1 -> {
                        cornerX = xMax;
                        cornerZ = zMin;
                    }
                    case 2 -> {
                        cornerX = xMax;
                        cornerZ = zMax;
                    }
                    default -> {
                        cornerX = xMin;
                        cornerZ = zMax;
                    }
                }
                float phase = 0.45f + 0.55f * (0.5f + 0.5f * Mth.sin(now * CORNER_CHASE_SPEED + i * HALF_PI));
                float a = CORNER_ALPHA * lineMul * phase;
                addSpark(builder, m, cornerX, cy, cornerZ, CORNER_SIZE, br, bg, bb, a);
            }
        }

        // 沿边框巡游的彗星光点
        if (VisualLod.keepLayer(detail, COMET_KEEP_THRESHOLD)) {
            for (int i = 0; i < COMET_COUNT; i++) {
                float t = frac(now / COMET_PERIOD_TICKS + (float) i / COMET_COUNT);
                // v5：写入复用缓冲后立即读走，不再返回新数组
                perimeterPoint(half, t);
                addSpark(builder, m, cx + PERIMETER_XZ[0], cy, cz + PERIMETER_XZ[1], COMET_SIZE,
                        br, bg, bb, COMET_ALPHA * lineMul);
            }
        }
    }

    /**
     * 计算方形周边某处的偏移坐标（相对中心），t∈[0,1) 沿顺时针绕行一周。
     * <p>
     * <b>v5：结果写入 {@link #PERIMETER_XZ} 而非返回新数组。</b>
     * 索引 0 为 dx、索引 1 为 dz。调用方必须「调用后立即读走」，不可跨调用留存。
     * </p>
     *
     * @param half 半边长
     * @param t    周长参数（0~1）
     */
    private static void perimeterPoint(double half, double t) {
        double per = t * 4.0;          // 0~4，整数部分为边序号
        int side = (int) per;          // 0=北 1=东 2=南 3=西
        double f = per - side;         // 该边内的进度 0~1
        double span = 2.0 * half;
        switch (side) {
            case 0 -> {
                // 北边 z=-half，x 从 -half → +half
                PERIMETER_XZ[0] = -half + f * span;
                PERIMETER_XZ[1] = -half;
            }
            case 1 -> {
                // 东边 x=+half，z 从 -half → +half
                PERIMETER_XZ[0] = half;
                PERIMETER_XZ[1] = -half + f * span;
            }
            case 2 -> {
                // 南边 z=+half，x 从 +half → -half
                PERIMETER_XZ[0] = half - f * span;
                PERIMETER_XZ[1] = half;
            }
            default -> {
                // 西边 x=-half，z 从 +half → -half
                PERIMETER_XZ[0] = -half;
                PERIMETER_XZ[1] = half - f * span;
            }
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
     * 在某点绘制一个小菱形光点（四角火花 / 边框彗星 / 母题芒尖 / 星屑共用），
     * 中心最亮、四角渐隐。
     * <p>仅 12 顶点，不参与分段缩放；是否绘制由调用方按保留阈值决定。</p>
     * <p>
     * <b>v5：四个角点内联为标量，本方法自此零分配。</b>
     * 原实现用 {@code float[][] pts} 字面量表达角点，每次调用分配 <b>5 个临时数组</b>
     * （1 个外层 + 4 个 {@code float[2]}）。而本方法是全渲染器调用最密集的一个——
     * 四角火花、边框彗星、卡利亚水晶碎光、宇宙星屑、圣域芒尖<b>全都走它</b>，
     * 单光环峰值 20~26 次 / 帧，合计约 100~130 个数组（详见类注释的「v5」小节）。
     * </p>
     * <p>顶点输出与顺序逐字不变（做法与 {@code AoeEffectRenderer} v7 同源）。</p>
     *
     * @param size 半尺寸（格）
     */
    private static void addSpark(BufferBuilder builder, Matrix4f m, double px, double py, double pz,
                                 float size, float r, float g, float b, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float y = (float) py;
        float cxF = (float) px, czF = (float) pz;

        // 四个角点（顺序与原 pts[0..3] 一致：北 → 东 → 南 → 西）
        float p0x = cxF, p0z = czF - size;
        float p1x = cxF + size, p1z = czF;
        float p2x = cxF, p2z = czF + size;
        float p3x = cxF - size, p3z = czF;

        sparkTri(builder, m, cxF, y, czF, p0x, p0z, p1x, p1z, r, g, b, alpha);
        sparkTri(builder, m, cxF, y, czF, p1x, p1z, p2x, p2z, r, g, b, alpha);
        sparkTri(builder, m, cxF, y, czF, p2x, p2z, p3x, p3z, r, g, b, alpha);
        sparkTri(builder, m, cxF, y, czF, p3x, p3z, p0x, p0z, r, g, b, alpha);
    }

    /**
     * 菱形光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     *
     * @param cx 中心 X（相对相机）
     * @param y  水平面高度
     * @param cz 中心 Z
     * @param ax 第一个外角 X
     * @param az 第一个外角 Z
     * @param bx 第二个外角 X
     * @param bz 第二个外角 Z
     */
    private static void sparkTri(BufferBuilder builder, Matrix4f m,
                                 float cx, float y, float cz,
                                 float ax, float az, float bx, float bz,
                                 float r, float g, float b, float alpha) {
        builder.vertex(m, cx, y, cz).color(r, g, b, alpha).endVertex();
        builder.vertex(m, ax, y, az).color(r, g, b, 0f).endVertex();
        builder.vertex(m, bx, y, bz).color(r, g, b, 0f).endVertex();
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

    // ==================== 圆形绘制 ====================

    /**
     * 绘制一个圆形光环（径向渐变填充 + 发光主环 + 旋转符文）。
     * <p>
     * v4 削减：填充盘与三层主环、涟漪的分段数缩放（<b>占本方法七成顶点，是首要杠杆</b>）；
     * 涟漪条数缩减；外缘符文刻度按步长抽取（均布角度）；彗星层按保留阈值整层跳过。
     * </p>
     * <p>
     * v5：颜色改为 {@link VisualColor#unpackInto} 写入 {@link #SCRATCH} 后立即提取标量。
     * 注意 {@link #drawRuneMotif} 及其下游母题方法接收的都是已拆开的 r/g/b 标量，
     * 不持有任何数组引用，因此不受缓冲复用影响。
     * </p>
     *
     * @param now    当前时间（tick，墙钟驱动）
     * @param detail 本帧细节系数
     */
    private static void drawCircle(BufferBuilder builder, Matrix4f m, Prepared p,
                                   float fillMul, float lineMul, float runeRot, float now, float detail) {
        double cx = p.rx(), cy = p.ry(), cz = p.rz();
        double radius = p.radius();
        // v5：无分配解包，写入复用缓冲后立即提取为标量（之后再不碰缓冲）
        VisualColor.unpackInto(SCRATCH, p.color());
        float r = SCRATCH[0], g = SCRATCH[1], b = SCRATCH[2];
        float br = brighten(r), bg = brighten(g), bb = brighten(b);
        int ringSeg = ringSegments(radius, detail);

        // 径向渐变填充
        addGradientDisc(builder, m, cx, cy, cz, radius, fillSegments(radius, detail),
                r, g, b, CIRCLE_FILL_ALPHA_CENTER * fillMul, CIRCLE_FILL_ALPHA_RIM * fillMul);

        // 从中心向外扩散的圆形涟漪（相位均布的循环波，减条数只是波间隔变大）
        int rippleCount = VisualLod.scale(RIPPLE_COUNT, detail);
        for (int i = 0; i < rippleCount; i++) {
            float phase = frac(now / RIPPLE_PERIOD_TICKS + (float) i / rippleCount);
            double rr = radius * phase;
            if (rr < 0.3) {
                continue;
            }
            float a = (1f - phase) * RIPPLE_ALPHA * lineMul;
            addBand(builder, m, cx, cy, cz, rr - RIPPLE_HALF_WIDTH, rr + RIPPLE_HALF_WIDTH,
                    ringSegments(rr, detail), br, bg, bb, a, a);
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
                br, bg, bb, RUNE_ALPHA * lineMul, detail);

        // 各光环专属符文母题（取自艾尔登法环原作意象；元素数量固定，不随半径膨胀以控性能）
        drawRuneMotif(builder, m, cx, cy, cz, radius, styleFor(p.serialId()),
                br, bg, bb, lineMul, now, detail);

        // 沿主环绕行的彗星光点（带渐隐拖尾）。纯动效装饰，远处看不出，低细节时整层跳过
        if (VisualLod.keepLayer(detail, COMET_KEEP_THRESHOLD)) {
            // 尾点是相位递减序列，截断保留头部（最亮那个），衰减曲线不变
            int trail = VisualLod.scale(COMET_TRAIL, detail);
            for (int i = 0; i < COMET_COUNT; i++) {
                float base = frac(now / COMET_PERIOD_TICKS + (float) i / COMET_COUNT);
                for (int t = 0; t < trail; t++) {
                    float ph = base - t * COMET_TRAIL_STEP;
                    double ang = Math.PI * 2.0 * ph;
                    double px = cx + radius * Math.cos(ang);
                    double pz = cz + radius * Math.sin(ang);
                    // 分母仍用原始 COMET_TRAIL，保证保留尾点的亮度与全细节时逐点一致
                    float a = COMET_ALPHA * lineMul * (1f - (float) t / COMET_TRAIL);
                    float sz = COMET_SIZE * (1f - 0.18f * t);
                    addSpark(builder, m, px, cy, pz, sz, br, bg, bb, a);
                }
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
     * <p><b>本渲染器的首要顶点杠杆</b>：单次调用即 {@code segments × 6} 顶点，
     * 而每个圆形光环有三层主环 + 若干涟漪。调用方应传入
     * {@link #ringSegments(double, float)} 的结果。</p>
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
     * <p><b>v4：按细节系数步长抽取</b>——角度是 {@code (TAU × k / count) + rotation} 均布的，
     * 若截断前 N 条，整圈刻度会只剩一段圆弧、法阵明显「缺一块」。
     * 步长抽取时角度基准仍用<b>原始 count</b>，保证保留刻度的方位与全细节时完全一致。</p>
     *
     * @param detail 本帧细节系数
     */
    private static void addRunes(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                 double rStart, double length, int count, float rotation,
                                 float r, float g, float b, float alpha, float detail) {
        float y = (float) cy;
        double rEnd = rStart + length;
        float halfW = 0.03f;
        int drawn = VisualLod.scale(count, detail);
        int step = Math.max(1, count / drawn);
        for (int k = 0; k < count; k += step) {
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

    // v5 说明：原先的 unpack(int) 已删除——它每次调用 new float[3]，
    // 现由 VisualColor.unpackInto(SCRATCH, color) 取代。
    // 注意本渲染器的颜色来自运行时字段（AuraInfo.color），无法像其它渲染器那样
    // 预解包成 C_ 常量，只能走复用缓冲；调用后务必立即提取 r/g/b 标量。

    /**
     * 圆形填充分段数（全细节基准值）。
     * <p><b>保守优化（视觉无损）：</b>系数 3→2、上限 96→48。半径 ≤10 的光环仍取下限 32
     * （与优化前完全一致）；仅 16 格的圣域由 48 段降为 32 段。填充盘是中心→边缘的低 alpha
     * 渐变、无描边线，分段下降几乎不可察。
     */
    private static int fillSegments(double radius) {
        return Mth.clamp((int) (radius * 2), 32, 48);
    }

    /**
     * 带细节层级的圆形填充分段数（v4 新增）。
     *
     * @param radius 半径（格）
     * @param detail 本帧细节系数
     * @return 缩放后的分段数，下限 {@link #FILL_SEGMENTS_MIN}
     */
    private static int fillSegments(double radius, float detail) {
        return VisualLod.scaleSegments(fillSegments(radius), FILL_SEGMENTS_MIN, detail);
    }

    /**
     * 圆形环线分段数（全细节基准值）。
     * <p><b>保守优化（视觉无损）：</b>系数 5→3、上限 110→64。半径 ≤8 的光环仍取下限 48
     * （像素级一致）；仅半径最大的圣域（16 格）由 80 段降为 48 段——其多边形与真圆的最大偏离
     * 约 3.4cm（{@code R(1-cos(180°/48))}），在 32 格直径的发光法阵上肉眼不可见，
     * 却省下约四成环带/辉光顶点（多人举盾圣域时收益最大）。
     */
    private static int ringSegments(double radius) {
        return Mth.clamp((int) (radius * 3), 48, 64);
    }

    /**
     * 带细节层级的圆形环线分段数（v4 新增）。
     * <p>
     * 下限取 {@link #RING_SEGMENTS_MIN}(24)，比实体渲染器的 8~10 高不少——
     * 多边形与真圆的偏离量正比于半径，而光环半径可达 16 格。
     * </p>
     *
     * @param radius 半径（格）
     * @param detail 本帧细节系数
     * @return 缩放后的分段数
     */
    private static int ringSegments(double radius, float detail) {
        return VisualLod.scaleSegments(ringSegments(radius), RING_SEGMENTS_MIN, detail);
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
     * <p><b>v5 说明：</b>本方法与全部 {@code motifXxx} 接收的都是已拆开的 r/g/b 标量，
     * 不持有任何数组引用，因此完全不受 {@link #SCRATCH} 复用影响。</p>
     *
     * @param radius  圆半径（格）
     * @param style   样式
     * @param lineMul 呼吸亮度系数
     * @param now     时间（tick，墙钟驱动）
     * @param detail  本帧细节系数
     */
    private static void drawRuneMotif(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                      double radius, RuneStyle style,
                                      float r, float g, float b, float lineMul, float now, float detail) {
        switch (style) {
            case CARIAN -> motifCarian(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now, detail);
            case WARD -> motifWard(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now, detail);
            case COSMIC -> motifCosmic(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now, detail);
            case HOLY -> motifHoly(builder, m, cx, cy, cz, radius, r, g, b, lineMul, now, detail);
            default -> {
                // PLAIN：外缘刻度环已表现，无额外母题
            }
        }
    }

    /**
     * 卡利亚辉石母题：内六边形 + 六芒星（两叠三角）+ 顶点水晶碎光，缓慢顺时针旋转。
     * <p>v4：六边形与六芒星共 72 顶点却是本光环的唯一辨识依据，<b>完全不削</b>；
     * 仅 6 颗水晶碎光按保留阈值整层跳过。</p>
     */
    private static void motifCarian(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                    double radius, float r, float g, float b, float lineMul,
                                    float now, float detail) {
        double rot = now * 0.012;
        float a = 0.70f * lineMul;
        double hw = Math.max(0.05, radius * 0.012);
        // 内六边形
        addPolygonRing(builder, m, cx, cy, cz, radius * 0.40, 6, rot, hw, r, g, b, a * 0.8f);
        // 六芒星（6 顶点，连接步距 2 → 两叠三角）
        addStarPolygon(builder, m, cx, cy, cz, radius * 0.62, 6, 2, rot, hw, r, g, b, a);
        // 顶点水晶碎光
        if (VisualLod.keepLayer(detail, SPARKLE_KEEP_THRESHOLD)) {
            for (int i = 0; i < 6; i++) {
                double ang = rot + Math.PI * 2 * i / 6.0;
                double px = cx + radius * 0.62 * Math.cos(ang);
                double pz = cz + radius * 0.62 * Math.sin(ang);
                addSpark(builder, m, px, cy, pz, (float) (radius * 0.035 + 0.06), r, g, b, a);
            }
        }
    }

    /**
     * 托普斯结界母题：双层反向旋转断环 + 径向栅条，构成屏障网格。
     * <p>v4：两层断环的段数与径向栅条数量均按步长抽取（都是均布角度，截断会让结界网只剩一段圆弧）。</p>
     */
    private static void motifWard(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                  double radius, float r, float g, float b, float lineMul,
                                  float now, float detail) {
        double rot = now * 0.020;
        float a = 0.60f * lineMul;
        // 双层反向断环
        addDashedRing(builder, m, cx, cy, cz, radius * 0.42, radius * 0.50, 10, 0.55, rot, r, g, b, a, detail);
        addDashedRing(builder, m, cx, cy, cz, radius * 0.62, radius * 0.70, 14, 0.45, -rot * 1.2,
                r, g, b, a * 0.85f, detail);
        // 径向栅条：均布角度，按步长抽取
        double hw = Math.max(0.05, radius * 0.014);
        final int bars = 12;
        int drawnBars = VisualLod.scale(bars, detail);
        int barStep = Math.max(1, bars / drawnBars);
        for (int i = 0; i < bars; i += barStep) {
            double ang = rot * 0.5 + Math.PI * 2 * i / bars;
            double ix = cx + radius * 0.50 * Math.cos(ang), iz = cz + radius * 0.50 * Math.sin(ang);
            double ox = cx + radius * 0.62 * Math.cos(ang), oz = cz + radius * 0.62 * Math.sin(ang);
            addLine(builder, m, ix, iz, ox, oz, cy, hw, r, g, b, a, a);
        }
    }

    /**
     * 塞乐恩宇宙母题：放射光线（长短交替、尖端渐隐，缓慢逆旋）+ 闪烁星空。
     * <p>v4：放射光线按步长抽取（均布角度）；星屑数量缩减
     * （黄金角螺旋的前 N 个本身即均匀铺满，截断安全）。</p>
     */
    private static void motifCosmic(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                    double radius, float r, float g, float b, float lineMul,
                                    float now, float detail) {
        double rot = -now * 0.010;
        float a = 0.60f * lineMul;
        double hw = Math.max(0.04, radius * 0.010);
        addRays(builder, m, cx, cy, cz, radius * 0.12, radius * 0.70, radius * 0.45, 12, rot, hw,
                r, g, b, a, detail);
        addStarField(builder, m, cx, cy, cz, radius * 0.80, 14, now, (float) (radius * 0.025 + 0.05),
                r, g, b, 0.85f * lineMul, detail);
    }

    /**
     * 黄金树圣域母题：长短交替金色光芒（缓慢顺旋）+ 中央十字圣徽 + 芒尖金光，整体随圣光脉动。
     * <p>v4：16 条光芒按步长抽取（均布，且长短交替——步长为偶数时会全取到长芒，
     * 故步长强制取奇数以保留长短交替的节奏）；<b>中央十字圣徽只有 12 顶点却是圣域的核心标志，
     * 完全不削</b>；8 个芒尖金光按保留阈值整层跳过。</p>
     */
    private static void motifHoly(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                  double radius, float r, float g, float b, float lineMul,
                                  float now, float detail) {
        double rot = now * 0.008;
        float pulse = 0.55f + 0.45f * (0.5f + 0.5f * Mth.sin(now * 0.06f));
        float a = 0.75f * lineMul * pulse;
        double hw = Math.max(0.05, radius * 0.013);
        // 长短交替金色光芒
        addRays(builder, m, cx, cy, cz, radius * 0.18, radius * 0.72, radius * 0.50, 16, rot, hw,
                r, g, b, a, detail);
        // 中央十字圣徽：仅 12 顶点，是圣域的核心标志，不参与削减
        double cl = radius * 0.30;
        addLine(builder, m, cx - cl, cz, cx + cl, cz, cy, hw * 1.4, r, g, b, a, a);
        addLine(builder, m, cx, cz - cl, cx, cz + cl, cy, hw * 1.4, r, g, b, a, a);
        // 芒尖金光（取偶数光芒尖端）
        if (VisualLod.keepLayer(detail, SPARKLE_KEEP_THRESHOLD)) {
            for (int i = 0; i < 16; i += 2) {
                double ang = rot + Math.PI * 2 * i / 16.0;
                double px = cx + radius * 0.72 * Math.cos(ang), pz = cz + radius * 0.72 * Math.sin(ang);
                addSpark(builder, m, px, cy, pz, (float) (radius * 0.025 + 0.05), r, g, b, a);
            }
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
     * <p>边数很少（6），共 36 顶点，是母题的辨识核心，<b>不参与 LOD 削减</b>。</p>
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
     * <p>共 36 顶点，是母题的辨识核心，<b>不参与 LOD 削减</b>。</p>
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
     * <p>
     * <b>v4：按细节系数步长抽取，且步长强制取奇数。</b>角度是 {@code rotation + TAU × i / count}
     * 均布的，截断会让光芒只朝一侧喷；而步长若取偶数，{@code i % 2} 会恒为同一值，
     * <b>长短交替的节奏会退化成全长或全短</b>——圣域母题正是靠这个交替来表现「圣光芒」的，
     * 故这里额外把步长调整为奇数。
     * </p>
     *
     * @param hw     线半宽
     * @param detail 本帧细节系数
     */
    private static void addRays(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                double rInner, double rOuter, double rOuterAlt, int count, double rotation,
                                double hw, float r, float g, float b, float alpha, float detail) {
        int drawn = VisualLod.scale(count, detail);
        int step = Math.max(1, count / drawn);
        // 步长取偶数会让 i % 2 恒定、长短交替消失，故强制调整为奇数
        if (step > 1 && (step & 1) == 0) {
            step--;
        }
        for (int i = 0; i < count; i += step) {
            double ang = rotation + (Math.PI * 2 * i) / count;
            double ro = (i % 2 == 0) ? rOuter : rOuterAlt;
            double ix = cx + rInner * Math.cos(ang), iz = cz + rInner * Math.sin(ang);
            double ox = cx + ro * Math.cos(ang), oz = cz + ro * Math.sin(ang);
            addLine(builder, m, ix, iz, ox, oz, cy, hw, r, g, b, alpha, alpha * 0.15f);
        }
    }

    /**
     * 绘制断环（虚线圆环）：dashes 段弧，每段占该格 fillRatio 比例，其余留空。
     * <p><b>v4：段数按细节系数步长抽取</b>（均布角度）。每段内部的细分 {@code sub} 本就只有 2，
     * 不再缩减——降到 1 会让弧退化成直线弦、断环变成折线碎片。</p>
     *
     * @param fillRatio 每段实心占比（0~1）
     * @param rotation  旋转角（弧度）
     * @param detail    本帧细节系数
     */
    private static void addDashedRing(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                      double rInner, double rOuter, int dashes, double fillRatio, double rotation,
                                      float r, float g, float b, float alpha, float detail) {
        final int sub = 2; // 每段弧细分（控性能；本就是 2，不再缩减）
        float yf = (float) cy;
        int drawn = VisualLod.scale(dashes, detail);
        int step = Math.max(1, dashes / drawn);
        for (int i = 0; i < dashes; i += step) {
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
     * <p>
     * <b>v4：按细节系数直接减数量（截断尾部）。</b>分布用黄金角螺旋
     * （{@code ang = i × 2.399963}）+ 黄金比小数半径，<b>前 N 个本身即均匀铺满整个圆面</b>，
     * 这是黄金角螺旋的固有性质，故截断安全、不会出现「只剩中心一撮」的塌陷。
     * </p>
     *
     * @param size      星点半尺寸（格）
     * @param baseAlpha 基础亮度（再乘以闪烁系数）
     * @param detail    本帧细节系数
     */
    private static void addStarField(BufferBuilder builder, Matrix4f m, double cx, double cy, double cz,
                                     double radius, int count, float now, float size,
                                     float r, float g, float b, float baseAlpha, float detail) {
        int drawn = VisualLod.scale(count, detail);
        for (int i = 0; i < drawn; i++) {
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
