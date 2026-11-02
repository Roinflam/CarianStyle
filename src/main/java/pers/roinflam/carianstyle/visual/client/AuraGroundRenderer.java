package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V1)
 *
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
 * 而该光环<b>仍在激活集合之中</b>（附魔还穿在身上），
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
 * <b>复用缓冲的约束：</b>{@link #SCRATCH} 与 {@link #PERIMETER_XZ} 都必须
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
 * <h3>v6（堆分配，行为逐帧一致）：干掉每帧的 HashSet 与 Prepared 列表</h3>
 * <p>
 * v5 之后逐元素的分配已经清干净，但<b>每帧开头还剩两个结构性分配</b>：
 * </p>
 * <pre>
 * Set&lt;Long&gt; activeKeys = new HashSet&lt;&gt;();      // 每帧一个 HashSet
 * activeKeys.add(key(entityId, serialId));          // 每次 add 装箱一个 Long
 *
 * List&lt;Prepared&gt; prepared = new ArrayList&lt;&gt;();  // 每帧一个 ArrayList
 * prepared.add(new Prepared(...));                  // 每个光环一个 record 实例
 * </pre>
 * <p>
 * 装箱这一项尤其亏：键是 {@code (entityId << 16) | serialId}，数值远超
 * {@code Long.valueOf} 的小值缓存范围（-128~127），<b>每一个都是真分配</b>。
 * 附近 20 个实体各带 2~4 个光环时，单帧就是几十个 {@code Long} 加一个 HashSet 的桶数组。
 * </p>
 *
 * <h4>第一处：用帧号比对取代 HashSet</h4>
 * <p>
 * {@code activeKeys} 的用途只有一个——记住「本帧哪些光环还在扫描结果里」，
 * 好在下一段循环里判断谁该开始淡出。与其在外面维护这份名单，
 * 不如让每个 {@link AuraState} <b>自己记住上次被看到是哪一帧</b>
 * （{@link AuraState#lastSeenFrame}）：
 * </p>
 * <pre>
 * // 旧：查集合（HashSet 分配 + Long 装箱 + 哈希查找）
 * if (st.fadeStart &lt; 0f &amp;&amp; !activeKeys.contains(e.getKey())) { ... }
 *
 * // 新：比帧号（零分配，一次 int 比较）
 * if (st.fadeStart &lt; 0f &amp;&amp; st.lastSeenFrame != frameId) { ... }
 * </pre>
 * <p>
 * 帧号取自 {@link VisualBatch#frameId()}——它在同一阶段的 HIGHEST 优先级里自增，
 * 而本渲染器是默认的 NORMAL 优先级，因此读到的<b>必然是本帧的值</b>。
 * 「本帧出现过」与「lastSeenFrame == 当前帧号」是等价命题，行为完全一致。
 * </p>
 *
 * <h4>第二处：Prepared 由 record 改为对象池</h4>
 * <p>
 * {@link PreparedSlot} 的前身是 {@code record}——不可变、每帧每光环新建一个，
 * 加上装它的 {@code ArrayList} 本身。改为可变的 {@link PreparedSlot} 加一个
 * <b>只增不减的复用池</b>（{@link #PREPARED_POOL}）：每帧把 {@link #preparedCount}
 * 归零，需要时从池里取下一个槽位复写字段，池不够长才 new 一个补进去。
 * </p>
 * <p>
 * 于是稳态下（同屏光环数不再创新高）<b>一个对象都不分配</b>。
 * 池只增不减是刻意的：同屏光环数的峰值就那么大（几十个封顶），
 * 留着比反复伸缩划算；而且「收缩池」这种逻辑本身就容易写出 bug。
 * </p>
 * <p>
 * <b>代价是失去了 record 的不可变性</b>——槽位会被下一帧复写。
 * 但 {@link #PREPARED_POOL} 的内容<b>只在同一次 {@link #onRenderLevel} 调用内使用</b>
 * （填充完立刻遍历绘制，绝不跨帧留存引用），这个约束由本类自己保证，不外泄。
 * </p>
 *
 * @author RoinFlam
 * @version 6
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class AuraGroundRenderer {
    private static final double RENDER_CULL = 48.0;
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final float MILLIS_PER_TICK = 50.0f;
    private static final double EDGE_MARGIN = 0.5;
    private static final int FILL_SEGMENTS_MIN = 16;
    private static final int RING_SEGMENTS_MIN = 24;
    private static final float SPARKLE_KEEP_THRESHOLD = 0.45f;
    private static final float COMET_KEEP_THRESHOLD = 0.5f;
    private static final float SQUARE_FILL_ALPHA = 0.13f;
    private static final float CIRCLE_FILL_ALPHA_CENTER = 0.02f;
    private static final float CIRCLE_FILL_ALPHA_RIM = 0.16f;
    private static final float CORE_ALPHA = 0.85f;
    private static final float GLOW_ALPHA = 0.32f;
    private static final float GLOW_SPREAD = 0.50f;
    private static final float CORE_HALF = 0.07f;
    private static final float CORE_BRIGHTEN = 0.22f;
    private static final float RUNE_ALPHA = 0.5f;
    private static final float CORNER_ALPHA = 0.85f;
    private static final float CORNER_SIZE = 0.20f;
    private static final float CORNER_CHASE_SPEED = 0.12f;
    private static final float RIPPLE_PERIOD_TICKS = 55.0f;
    private static final int RIPPLE_COUNT = 2;
    private static final float RIPPLE_ALPHA = 0.42f;
    private static final float RIPPLE_HALF_WIDTH = 0.045f;
    private static final float COMET_PERIOD_TICKS = 70.0f;
    private static final int COMET_COUNT = 2;
    private static final float COMET_ALPHA = 0.9f;
    private static final float COMET_SIZE = 0.22f;
    private static final int COMET_TRAIL = 3;
    private static final float COMET_TRAIL_STEP = 0.012f;
    private static final float RUNE_SPEED = 0.030f;
    private static final float PULSE_SPEED = 0.075f;
    private static final float APPEAR_TICKS = 6.0f;
    private static final float FADE_TICKS = 7.0f;
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final float[] SCRATCH = new float[VisualColor.RGB];
    private static final double[] PERIMETER_XZ = new double[2];
    private AuraGroundRenderer() {
    }
    private static final Map<Long, AuraState> STATE = new HashMap<>();

    private static final class AuraState {
        float appearTime;
        float fadeStart;
        int lastSeenFrame;
        int entityId;
        int serialId;
        int color;
        double nominalRadius;
        AuraDisplayRegistry.AuraShape shape;
        double lastX;
        double lastY;
        double lastZ;
    }

    private static final class PreparedSlot {
        int serialId;
        double rx;
        double ry;
        double rz;
        int color;
        double radius;
        AuraDisplayRegistry.AuraShape shape;
        double alpha;
        float detail;
    }
    private static final List<PreparedSlot> PREPARED_POOL = new ArrayList<>();

    private enum RuneStyle {
;

        public static final RuneStyle CARIAN = null; // PORT-STUB
        public static final RuneStyle WARD = null; // PORT-STUB
        public static final RuneStyle COSMIC = null; // PORT-STUB
        public static final RuneStyle HOLY = null; // PORT-STUB
        public static final RuneStyle PLAIN = null; // PORT-STUB
    }
}

