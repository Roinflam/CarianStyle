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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
 * @author FlameForge
 * @version 6
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
     * <p>用于把墙钟毫秒差换算为 <b>tick 单位</b>的动画时间，从而让下方所有
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
     * 而本渲染器的环半径可达 16 格（圣域）。24 段在 16 格半径下偏离约 14cm，
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
        /**
         * 上次「本帧仍在扫描结果中」的帧号（v6 新增）。
         * <p>
         * 取代了原先每帧新建的 {@code Set<Long> activeKeys}：
         * 刷新时写入 {@link VisualBatch#frameId()}，随后判断「本帧没出现」
         * 就退化成一次 {@code int} 比较，零分配、也不再有 {@code Long} 装箱
         * （详见类注释「v6」小节）。
         * </p>
         * <p>
         * 初值 -1 是刻意的：{@link VisualBatch#frameId()} 从 0 起自增，
         * 用 -1 保证「刚 new 出来但还没被刷新过」的状态不会与任何真实帧号相等。
         * 实际上创建后会立刻被赋值，这只是防御。
         * </p>
         */
        int lastSeenFrame = -1;
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
        /**
         * 最近一次的世界坐标（实体消失后用于淡出定位）。
         * <p>
         * <b>由每帧的位置循环从存活实体写回，而非来自 {@link AuraScanner.ActiveAura}</b>——
         * 后者只携带 {@code entityId}，坐标一律由渲染端反查实体实时获取。
         * </p>
         */
        double lastX;
        double lastY;
        double lastZ;
    }

    /**
     * 准备好渲染的一个光环（坐标已转为相对相机；尺寸已含动画与边缘余量；alpha 为整体淡入淡出系数）。
     * <p>
     * <b>v6：由 {@code record} 改为可变类 + 对象池。</b>原先每帧每光环都要 new 一个 record，
     * 外加装它们的那个 {@code ArrayList}；现在从 {@link #PREPARED_POOL} 取槽位复写字段，
     * 稳态下一个对象都不分配（详见类注释「第二处：Prepared 由 record 改为对象池」）。
     * </p>
     * <p>
     * <b>⚠ 槽位会被下一帧复写</b>，因此其内容<b>只能在同一次 {@link #onRenderLevel}
     * 调用内使用</b>——填充完立刻遍历绘制，绝不跨帧留存引用。这个约束由本类自己保证、不外泄。
     * </p>
     */
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

    /**
     * {@link PreparedSlot} 的复用池（v6 新增，<b>只增不减</b>）。
     * <p>
     * 每帧把 {@link #preparedCount} 归零，需要时从池里取下一个槽位复写字段，
     * 池不够长才 new 一个补进去。于是稳态下（同屏光环数不再创新高）零分配。
     * </p>
     * <p>
     * <b>只增不减是刻意的：</b>同屏光环数的峰值就那么大（几十个封顶），
     * 留着比反复伸缩划算；而且「收缩池」这种逻辑本身就容易写出 bug。
     * </p>
     */
    private static final List<PreparedSlot> PREPARED_POOL = new ArrayList<>();

    /** 本帧已使用的 {@link #PREPARED_POOL} 槽位数（每帧开头归零） */
    private static int preparedCount = 0;

    /**
     * 取下一个可用的 {@link PreparedSlot}（池不够长则扩容一格）。
     *
     * @return 可复写的槽位
     */
    private static PreparedSlot obtainPrepared() {
        if (preparedCount < PREPARED_POOL.size()) {
            return PREPARED_POOL.get(preparedCount++);
        }
        PreparedSlot slot = new PreparedSlot();
        PREPARED_POOL.add(slot);
        preparedCount++;
        return slot;
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
     * 世界渲染回调：在半透明方块之后绘制所有光环法阵。
     * <p>
     * v2：GL 状态与顶点缓冲改由 {@link VisualBatch} 统一管理。
     * v3：动画时间源改为墙钟差值（tick 单位），修复 13.9 小时后光环永久消失的问题。
     * v4：全部元素按 {@link VisualLod} 细节系数缩放，并补上此前缺失的实例登记；
     * 细节系数按<b>到圆环边界的距离</b>取（详见类注释）。
     * v6：「本帧是否出现」改用帧号比对、{@link PreparedSlot} 改用对象池，每帧零结构性分配。
     * </p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // ⚠ 必须在取共享缓冲之前处理「离开世界」：离开世界那一帧共享批次本就不会开启，
        // 若把清空逻辑排在下面的判空之后，淡出状态会残留到下次进入世界，
        // 与新的实体网络 id 撞号后会闪出一两帧错误光环。
        if (mc.level == null) {
            STATE.clear();
            return;
        }
        // 共享批次未开启：直接跳过
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        List<AuraScanner.ActiveAura> auras = AuraScanner.getActiveAuras();
        // v3：墙钟驱动的 tick 时间轴（不再受服务端 TPS 影响，也不会回绕）
        float now = currentTime();
        // ⭐ v6：本帧帧号。VisualBatch 在同一阶段的 HIGHEST 优先级里自增，
        // 而本渲染器是默认的 NORMAL 优先级，故这里读到的必然是本帧的值
        int frameId = VisualBatch.frameId();

        // ===== 1) 刷新状态：本帧仍在扫描结果中的光环，写入当前帧号 =====
        // v6：不再用 HashSet 记录「本帧出现过谁」，改为往状态里写帧号
        for (AuraScanner.ActiveAura aura : auras) {
            long k = key(aura.entityId(), aura.serialId());
            AuraState st = STATE.get(k);
            if (st == null) {
                st = new AuraState();
                st.appearTime = now;
                STATE.put(k, st);
            }
            st.fadeStart = -1f; // 仍激活：清除淡出标记（若此前在淡出会被"救回"）
            st.lastSeenFrame = frameId;
            st.entityId = aura.entityId();
            st.serialId = aura.serialId();
            st.color = aura.color();
            st.nominalRadius = aura.radius();
            st.shape = aura.shape();
            // 注意：ActiveAura 不带坐标分量（扫描器只给 entityId，坐标由渲染端反查实体实时获取），
            // 故 lastX/lastY/lastZ 不在这里写，而是在下方位置循环里从存活实体写回
        }

        // ===== 2) 标记本帧未出现的光环开始淡出 =====
        for (Map.Entry<Long, AuraState> e : STATE.entrySet()) {
            AuraState st = e.getValue();
            // ⭐ v6：「本帧没出现」等价于「lastSeenFrame 不是当前帧号」，
            // 一次 int 比较取代了原先的 HashSet 查找 + Long 装箱
            if (st.fadeStart < 0f && st.lastSeenFrame != frameId) {
                st.fadeStart = now;
            }
        }

        // ===== 3) 计算本帧要画的光环（含正在淡出的），淡出结束则移除状态 =====
        // v6：不再新建 ArrayList + record，改为从对象池取槽位复写
        preparedCount = 0;
        Iterator<Map.Entry<Long, AuraState>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, AuraState> entry = it.next();
            AuraState st = entry.getValue();

            float radiusFactor;
            float animAlpha;
            if (st.fadeStart < 0f) {
                // 出现/稳定：从中心展开（缓出）+ 淡入
                float p = clamp01((now - st.appearTime) / APPEAR_TICKS);
                float eased = easeOutCubic(p);
                radiusFactor = eased;
                animAlpha = eased;
            } else {
                // 消失：收缩 + 淡出
                float p = clamp01((now - st.fadeStart) / FADE_TICKS);
                if (p >= 1f) {
                    it.remove(); // 淡出结束，移除状态
                    continue;
                }
                float v = 1f - p; // 1 -> 0
                radiusFactor = 0.75f + 0.25f * v; // 轻微收缩，不至于塌成一点
                animAlpha = v;
            }

            if (animAlpha <= 0.01f || radiusFactor <= 0.02f) {
                continue;
            }

            // 位置：实体仍在世界中用实时坐标，否则用最近已知坐标原地播完淡出
            double wx, wy, wz;
            Entity ent = mc.level.getEntity(st.entityId);
            if (ent != null && ent.isAlive()) {
                wx = ent.getX();
                wy = ent.getY();
                wz = ent.getZ();
                // 顺手记下最后已知坐标：实体一旦卸载 / 死亡，下面的 else 分支就靠它
                // 把剩余的淡出动画留在原地播完
                st.lastX = wx;
                st.lastY = wy;
                st.lastZ = wz;
            } else {
                wx = st.lastX;
                wy = st.lastY;
                wz = st.lastZ;
            }

            double dx = wx - cam.x;
            double dy = wy - cam.y;
            double dz = wz - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > RENDER_CULL * RENDER_CULL) {
                continue; // 太远：本帧不渲染，但保留状态，淡出计时继续
            }

            // 方形吸附到方块坐标；圆形保持精确小数坐标（与判定一致）
            double px, pz;
            if (st.shape == AuraDisplayRegistry.AuraShape.SQUARE) {
                px = Math.floor(wx) + 0.5;
                pz = Math.floor(wz) + 0.5;
            } else {
                px = wx;
                pz = wz;
            }

            double radius = st.nominalRadius * radiusFactor;

            // ⭐ v4：细节系数必须按「到圆环边界的距离」取，不能按到中心的距离。
            // 圣域半径 16 格 > VisualLod.FULL_DETAIL_RANGE(12)，若按中心距离算，
            // 玩家站在圈内侧边缘时（到圈心 ≈ 16）会被判定为"远"并大幅削减，
            // 但那圈发光边界其实就在脚边、削减清晰可见。
            // 改用到边界的近似距离后，人在圈内或贴着圈边时该值为 0、detail 恒为 1.0。
            // 开方成本可忽略（同屏光环数量通常是个位数）。
            double edgeDist = Math.max(0.0, Math.sqrt(distSqr) - radius);
            float detail = VisualLod.detail(edgeDist * edgeDist);
            // ⭐ v4：补上此前缺失的实例登记。不登记会让全局 crowdFactor 被系统性低估，
            // 导致已接入 LOD 的实体类渲染器在团战时削减不足
            VisualLod.countInstance();

            PreparedSlot slot = obtainPrepared();
            slot.serialId = st.serialId;
            slot.rx = px - cam.x;
            slot.ry = wy - cam.y + Y_OFFSET;
            slot.rz = pz - cam.z;
            slot.color = st.color;
            slot.radius = radius;
            slot.shape = st.shape;
            slot.alpha = animAlpha;
            slot.detail = detail;
        }

        if (preparedCount == 0) {
            return;
        }

        // ===== 4) 绘制 =====
        Matrix4f matrix = VisualBatch.matrix();
        // 整体亮度呼吸（半径严格不缩放，边界始终对应效果范围）
        float pulse = 0.90f + 0.10f * Mth.sin(now * PULSE_SPEED);

        for (int i = 0; i < preparedCount; i++) {
            PreparedSlot p = PREPARED_POOL.get(i);
            if (p.shape == AuraDisplayRegistry.AuraShape.SQUARE) {
                drawSquare(builder, matrix, p, now, pulse);
            } else {
                drawCircle(builder, matrix, p, now, pulse);
            }
        }
    }

    // ==================== 方形（对应 AABB.inflate 判定） ====================

    /**
     * 绘制方形法阵：底色 → 四条发光边（精确边界）→ 四角追逐火花
     * → 从中心向外扩散的方形涟漪 → 沿边框巡游的彗星光点。
     * <p>
     * <b>v4：涟漪数量、火花与彗星层按细节系数削减。</b>
     * 方形的边是四条直线段（顶点量与半边长无关），本身很便宜，故主体不削；
     * 真正的杠杆在循环动效上。
     * </p>
     * <p>v5：颜色解包改用 {@link #SCRATCH} 复用缓冲，{@link #addSpark} 已内联为标量。</p>
     */
    private static void drawSquare(BufferBuilder b, Matrix4f m, PreparedSlot p, float now, float pulse) {
        // v5：写入复用缓冲后立即提取标量，之后不再碰缓冲（与旧 unpack 逐位一致）
        VisualColor.unpackInto(SCRATCH, p.color);
        float cr = SCRATCH[0];
        float cg = SCRATCH[1];
        float cb = SCRATCH[2];

        VisualColor.unpackInto(SCRATCH, brighten(p.color, CORE_BRIGHTEN));
        float lr = SCRATCH[0];
        float lg = SCRATCH[1];
        float lb = SCRATCH[2];

        float a = (float) p.alpha;
        // 半边长 = 名义半径 + 每边外扩余量（近似碰撞箱相交带来的额外生效宽度）
        double half = p.radius + EDGE_MARGIN;

        // 1) 区域底色
        addSquareFill(b, m, p.rx, p.ry, p.rz, half, cr, cg, cb, SQUARE_FILL_ALPHA * a * pulse);

        // 2) 四条发光边（外辉光 → 内辉光 → 核心亮带）
        addSquareEdges(b, m, p.rx, p.ry, p.rz, half, half + GLOW_SPREAD,
                cr, cg, cb, GLOW_ALPHA * a * pulse, 0f);
        addSquareEdges(b, m, p.rx, p.ry, p.rz, half - GLOW_SPREAD * 0.6, half,
                cr, cg, cb, 0f, GLOW_ALPHA * 0.75f * a * pulse);
        addSquareEdges(b, m, p.rx, p.ry, p.rz, half - CORE_HALF, half + CORE_HALF,
                lr, lg, lb, CORE_ALPHA * a * pulse, CORE_ALPHA * a * pulse);

        // 3) 四角追逐火花（每角相位错开，依次明灭）
        if (VisualLod.keepLayer(p.detail, SPARKLE_KEEP_THRESHOLD)) {
            for (int i = 0; i < 4; i++) {
                float phase = now * CORNER_CHASE_SPEED - i * HALF_PI;
                float k = 0.55f + 0.45f * Mth.sin(phase);
                double sx = ((i == 0 || i == 3) ? -half : half);
                double sz = ((i < 2) ? -half : half);
                addSpark(b, m, p.rx + sx, p.ry, p.rz + sz, CORNER_SIZE * (0.75f + 0.5f * k),
                        lr, lg, lb, CORNER_ALPHA * a * k);
            }
        }

        // 4) 方形涟漪：从中心向外扩散的方框，抵达边界时淡出
        int rippleCount = VisualLod.scale(RIPPLE_COUNT, p.detail);
        for (int i = 0; i < rippleCount; i++) {
            float t = frac((now / RIPPLE_PERIOD_TICKS) + (float) i / rippleCount);
            double r = half * t;
            if (r <= 0.05) {
                continue;
            }
            // 起步渐入、末段渐出，避免在中心/边界处突兀出现或消失
            float fade = Mth.sin((float) (t * Math.PI));
            addSquareEdges(b, m, p.rx, p.ry, p.rz, r - RIPPLE_HALF_WIDTH, r + RIPPLE_HALF_WIDTH,
                    cr, cg, cb, RIPPLE_ALPHA * a * fade, RIPPLE_ALPHA * a * fade);
        }

        // 5) 边框彗星：沿方形周长巡游的光点 + 拖尾
        if (VisualLod.keepLayer(p.detail, COMET_KEEP_THRESHOLD)) {
            double perimeter = 8.0 * half;
            for (int i = 0; i < COMET_COUNT; i++) {
                float base = frac((now / COMET_PERIOD_TICKS) + (float) i / COMET_COUNT);
                for (int t = 0; t < COMET_TRAIL; t++) {
                    float tp = frac(base - t * COMET_TRAIL_STEP);
                    // v5：写入 PERIMETER_XZ 后立即读走（与旧 double[2] 返回值逐位一致）
                    perimeterPoint(half, tp * perimeter);
                    float fade = 1f - (float) t / COMET_TRAIL;
                    addSpark(b, m, p.rx + PERIMETER_XZ[0], p.ry, p.rz + PERIMETER_XZ[1],
                            COMET_SIZE * fade, lr, lg, lb, COMET_ALPHA * a * fade * fade);
                }
            }
        }
    }

    /**
     * 把「沿方形周长的行进距离」换算为相对中心的 (dx, dz)，写入 {@link #PERIMETER_XZ}。
     * <p>
     * 从左上角起顺时针：上边 → 右边 → 下边 → 左边。
     * </p>
     * <p>
     * <b>v5：改为写入复用缓冲，不再返回新数组。</b>
     * 调用点只有边框彗星一处（每帧最多 {@code COMET_COUNT × COMET_TRAIL} = 6 次），
     * 且是「写入 → 立刻读走」，故一个缓冲足够。分支逻辑与旧实现一字未改。
     * </p>
     *
     * @param half 半边长
     * @param dist 沿周长的行进距离（0 ~ 8×half）
     */
    private static void perimeterPoint(double half, double dist) {
        double side = 2.0 * half;
        if (dist < side) {
            PERIMETER_XZ[0] = -half + dist;
            PERIMETER_XZ[1] = -half;
        } else if (dist < side * 2) {
            PERIMETER_XZ[0] = half;
            PERIMETER_XZ[1] = -half + (dist - side);
        } else if (dist < side * 3) {
            PERIMETER_XZ[0] = half - (dist - side * 2);
            PERIMETER_XZ[1] = half;
        } else {
            PERIMETER_XZ[0] = -half;
            PERIMETER_XZ[1] = half - (dist - side * 3);
        }
    }

    /**
     * 绘制方形边框带（内外两个同心方形之间的四条带）。
     *
     * @param rInner     内侧半边长
     * @param rOuter     外侧半边长
     * @param alphaOuter 外侧顶点 alpha
     * @param alphaInner 内侧顶点 alpha
     */
    private static void addSquareEdges(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                       double rInner, double rOuter,
                                       float r, float g, float bl,
                                       float alphaOuter, float alphaInner) {
        if (rOuter <= rInner) {
            return;
        }
        // 上（-Z）
        addEdgeStrip(b, m, cx - rOuter, cy, cz - rOuter, cx + rOuter, cy, cz - rOuter,
                cx + rInner, cy, cz - rInner, cx - rInner, cy, cz - rInner, r, g, bl, alphaOuter, alphaInner);
        // 右（+X）
        addEdgeStrip(b, m, cx + rOuter, cy, cz - rOuter, cx + rOuter, cy, cz + rOuter,
                cx + rInner, cy, cz + rInner, cx + rInner, cy, cz - rInner, r, g, bl, alphaOuter, alphaInner);
        // 下（+Z）
        addEdgeStrip(b, m, cx + rOuter, cy, cz + rOuter, cx - rOuter, cy, cz + rOuter,
                cx - rInner, cy, cz + rInner, cx + rInner, cy, cz + rInner, r, g, bl, alphaOuter, alphaInner);
        // 左（-X）
        addEdgeStrip(b, m, cx - rOuter, cy, cz + rOuter, cx - rOuter, cy, cz - rOuter,
                cx - rInner, cy, cz - rInner, cx - rInner, cy, cz + rInner, r, g, bl, alphaOuter, alphaInner);
    }

    /**
     * 绘制一条四边形边带（两个三角形），外侧两点与内侧两点可用不同 alpha。
     */
    private static void addEdgeStrip(BufferBuilder b, Matrix4f m,
                                     double o1x, double o1y, double o1z,
                                     double o2x, double o2y, double o2z,
                                     double i2x, double i2y, double i2z,
                                     double i1x, double i1y, double i1z,
                                     float r, float g, float bl,
                                     float alphaOuter, float alphaInner) {
        b.vertex(m, (float) o1x, (float) o1y, (float) o1z).color(r, g, bl, alphaOuter).endVertex();
        b.vertex(m, (float) o2x, (float) o2y, (float) o2z).color(r, g, bl, alphaOuter).endVertex();
        b.vertex(m, (float) i2x, (float) i2y, (float) i2z).color(r, g, bl, alphaInner).endVertex();

        b.vertex(m, (float) o1x, (float) o1y, (float) o1z).color(r, g, bl, alphaOuter).endVertex();
        b.vertex(m, (float) i2x, (float) i2y, (float) i2z).color(r, g, bl, alphaInner).endVertex();
        b.vertex(m, (float) i1x, (float) i1y, (float) i1z).color(r, g, bl, alphaInner).endVertex();
    }

    /**
     * 绘制一个水平菱形光点（中心亮、四角渐隐），用于角部火花、彗星与母题装饰。
     * <p>
     * <b>v5：四个角点由 {@code float[][]} 内联为标量。</b>
     * 旧实现每次调用要分配 1 个外层数组 + 4 个 {@code float[2]}，
     * 而本方法是全渲染器调用最频繁的基元（单光环峰值 20~26 次 / 帧，
     * 其中 {@code motifCosmic} 的星屑一项就占 14 次）。
     * 圣域是群体增益光环，团战十人举盾时这里就是每秒数万次分配。
     * </p>
     * <p>
     * 做法与 {@code AoeEffectRenderer} v7 处理同名方法完全一致：
     * 角点数值与四个三角形的顶点写入顺序<b>逐字照搬</b>，输出与优化前逐位相同。
     * </p>
     *
     * @param size 半尺寸（格）
     */
    private static void addSpark(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                 float size, float r, float g, float bl, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        // 四个角点（上、右、下、左），内联为标量避免每次调用分配 5 个数组
        double p0x = cx, p0z = cz - size;
        double p1x = cx + size, p1z = cz;
        double p2x = cx, p2z = cz + size;
        double p3x = cx - size, p3z = cz;

        sparkTri(b, m, cx, cy, cz, p0x, p0z, p1x, p1z, r, g, bl, alpha);
        sparkTri(b, m, cx, cy, cz, p1x, p1z, p2x, p2z, r, g, bl, alpha);
        sparkTri(b, m, cx, cy, cz, p2x, p2z, p3x, p3z, r, g, bl, alpha);
        sparkTri(b, m, cx, cy, cz, p3x, p3z, p0x, p0z, r, g, bl, alpha);
    }

    /**
     * 菱形光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     * <p>v5：参数由角点数组改为拆开的标量，与 {@link #addSpark} 的内联化配套。</p>
     */
    private static void sparkTri(BufferBuilder b, Matrix4f m,
                                 double cx, double cy, double cz,
                                 double ax, double az, double bx, double bz,
                                 float r, float g, float bl, float alpha) {
        b.vertex(m, (float) cx, (float) cy, (float) cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, (float) ax, (float) cy, (float) az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, (float) bx, (float) cy, (float) bz).color(r, g, bl, 0f).endVertex();
    }

    /**
     * 绘制方形区域底色（两个三角形铺满）。
     */
    private static void addSquareFill(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                      double half, float r, float g, float bl, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        double x0 = cx - half, x1 = cx + half;
        double z0 = cz - half, z1 = cz + half;

        b.vertex(m, (float) x0, (float) cy, (float) z0).color(r, g, bl, alpha).endVertex();
        b.vertex(m, (float) x1, (float) cy, (float) z0).color(r, g, bl, alpha).endVertex();
        b.vertex(m, (float) x1, (float) cy, (float) z1).color(r, g, bl, alpha).endVertex();

        b.vertex(m, (float) x0, (float) cy, (float) z0).color(r, g, bl, alpha).endVertex();
        b.vertex(m, (float) x1, (float) cy, (float) z1).color(r, g, bl, alpha).endVertex();
        b.vertex(m, (float) x0, (float) cy, (float) z1).color(r, g, bl, alpha).endVertex();
    }

    // ==================== 圆形（对应 distanceTo 球形判定） ====================

    /**
     * 绘制圆形法阵：径向渐变填充 → 主环（外辉光/内辉光/核心）→ 外缘符文刻度
     * → 专属母题 → 圆形涟漪 → 边框彗星。
     * <p>
     * <b>v4：全部元素按细节系数缩放。</b>三层主环 + 涟漪合计占七成顶点，是首要杠杆；
     * 符文刻度的角度是 {@code i × (TAU / 40)} 均布的，必须按步长抽取而非截断，
     * 否则整圈只剩一段圆弧上有刻度、法阵会明显"缺一块"。
     * </p>
     * <p>v5：颜色解包改用 {@link #SCRATCH} 复用缓冲。</p>
     */
    private static void drawCircle(BufferBuilder b, Matrix4f m, PreparedSlot p, float now, float pulse) {
        // v5：写入复用缓冲后立即提取标量，之后不再碰缓冲（与旧 unpack 逐位一致）
        VisualColor.unpackInto(SCRATCH, p.color);
        float cr = SCRATCH[0];
        float cg = SCRATCH[1];
        float cb = SCRATCH[2];

        VisualColor.unpackInto(SCRATCH, brighten(p.color, CORE_BRIGHTEN));
        float lr = SCRATCH[0];
        float lg = SCRATCH[1];
        float lb = SCRATCH[2];

        float a = (float) p.alpha;
        double radius = p.radius;

        int fillSeg = fillSegments(radius, p.detail);
        int ringSeg = ringSegments(radius, p.detail);

        // 1) 径向渐变填充（中心淡、边缘浓，突出边界）
        addGradientDisc(b, m, p.rx, p.ry, p.rz, radius, fillSeg, cr, cg, cb,
                CIRCLE_FILL_ALPHA_CENTER * a * pulse, CIRCLE_FILL_ALPHA_RIM * a * pulse);

        // 2) 主环：外辉光 → 内辉光 → 核心亮带（核心即精确半径）
        addBand(b, m, p.rx, p.ry, p.rz, radius, radius + GLOW_SPREAD, ringSeg,
                cr, cg, cb, GLOW_ALPHA * a * pulse, 0f);
        addBand(b, m, p.rx, p.ry, p.rz, radius - GLOW_SPREAD * 0.6, radius, ringSeg,
                cr, cg, cb, 0f, GLOW_ALPHA * 0.75f * a * pulse);
        addBand(b, m, p.rx, p.ry, p.rz, radius - CORE_HALF, radius + CORE_HALF, ringSeg,
                lr, lg, lb, CORE_ALPHA * a * pulse, CORE_ALPHA * a * pulse);

        // 3) 外缘符文刻度（旋转）
        addRunes(b, m, p.rx, p.ry, p.rz, radius, now, cr, cg, cb, RUNE_ALPHA * a * pulse, p.detail);

        // 4) 专属母题（按 serialId 决定风格）
        drawRuneMotif(b, m, p, now, pulse, cr, cg, cb, lr, lg, lb, a);

        // 5) 圆形涟漪：从中心向外扩散
        int rippleCount = VisualLod.scale(RIPPLE_COUNT, p.detail);
        for (int i = 0; i < rippleCount; i++) {
            float t = frac((now / RIPPLE_PERIOD_TICKS) + (float) i / rippleCount);
            double r = radius * t;
            if (r <= 0.05) {
                continue;
            }
            float fade = Mth.sin((float) (t * Math.PI));
            addBand(b, m, p.rx, p.ry, p.rz, r - RIPPLE_HALF_WIDTH, r + RIPPLE_HALF_WIDTH, ringSeg,
                    cr, cg, cb, RIPPLE_ALPHA * a * fade, RIPPLE_ALPHA * a * fade);
        }

        // 6) 边框彗星：沿圆周巡游的光点 + 拖尾
        if (VisualLod.keepLayer(p.detail, COMET_KEEP_THRESHOLD)) {
            for (int i = 0; i < COMET_COUNT; i++) {
                float base = frac((now / COMET_PERIOD_TICKS) + (float) i / COMET_COUNT);
                for (int t = 0; t < COMET_TRAIL; t++) {
                    float tp = frac(base - t * COMET_TRAIL_STEP);
                    double ang = tp * Math.PI * 2.0;
                    double sx = Math.cos(ang) * radius;
                    double sz = Math.sin(ang) * radius;
                    float fade = 1f - (float) t / COMET_TRAIL;
                    addSpark(b, m, p.rx + sx, p.ry, p.rz + sz, COMET_SIZE * fade,
                            lr, lg, lb, COMET_ALPHA * a * fade * fade);
                }
            }
        }
    }

    /**
     * 绘制径向渐变圆盘（中心 alpha → 边缘 alpha）。
     */
    private static void addGradientDisc(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                        double radius, int segments,
                                        float r, float g, float bl,
                                        float alphaCenter, float alphaRim) {
        if (radius <= 0 || (alphaCenter <= 0.004f && alphaRim <= 0.004f)) {
            return;
        }
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0 * i) / segments;
            double a1 = (Math.PI * 2.0 * (i + 1)) / segments;
            double x0 = cx + Math.cos(a0) * radius;
            double z0 = cz + Math.sin(a0) * radius;
            double x1 = cx + Math.cos(a1) * radius;
            double z1 = cz + Math.sin(a1) * radius;

            b.vertex(m, (float) cx, (float) cy, (float) cz).color(r, g, bl, alphaCenter).endVertex();
            b.vertex(m, (float) x0, (float) cy, (float) z0).color(r, g, bl, alphaRim).endVertex();
            b.vertex(m, (float) x1, (float) cy, (float) z1).color(r, g, bl, alphaRim).endVertex();
        }
    }

    /**
     * 绘制圆环带（内外两个同心圆之间的环形带）。
     */
    private static void addBand(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                double rInner, double rOuter, int segments,
                                float r, float g, float bl,
                                float alphaOuter, float alphaInner) {
        if (rOuter <= rInner || rOuter <= 0) {
            return;
        }
        double ri = Math.max(0.0, rInner);
        for (int i = 0; i < segments; i++) {
            double a0 = (Math.PI * 2.0 * i) / segments;
            double a1 = (Math.PI * 2.0 * (i + 1)) / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0);
            double c1 = Math.cos(a1), s1 = Math.sin(a1);

            double ox0 = cx + c0 * rOuter, oz0 = cz + s0 * rOuter;
            double ox1 = cx + c1 * rOuter, oz1 = cz + s1 * rOuter;
            double ix0 = cx + c0 * ri, iz0 = cz + s0 * ri;
            double ix1 = cx + c1 * ri, iz1 = cz + s1 * ri;

            b.vertex(m, (float) ox0, (float) cy, (float) oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, (float) ox1, (float) cy, (float) oz1).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, (float) ix1, (float) cy, (float) iz1).color(r, g, bl, alphaInner).endVertex();

            b.vertex(m, (float) ox0, (float) cy, (float) oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, (float) ix1, (float) cy, (float) iz1).color(r, g, bl, alphaInner).endVertex();
            b.vertex(m, (float) ix0, (float) cy, (float) iz0).color(r, g, bl, alphaInner).endVertex();
        }
    }

    /**
     * 绘制外缘符文刻度（旋转的短径向线段）。
     * <p>
     * <b>v4：角度均布，必须按步长抽取而非截断。</b>
     * 刻度角度是 {@code rot + i × (TAU / 40)}，若只画前 N 个，
     * 整圈会只剩一段圆弧上有刻度、其余大半圈空着，法阵明显"缺一块"。
     * </p>
     */
    private static void addRunes(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                 double radius, float now,
                                 float r, float g, float bl, float alpha, float detail) {
        if (alpha <= 0.004f) {
            return;
        }
        final int total = 40;
        double rot = now * RUNE_SPEED;
        double rIn = radius + 0.10;
        double rOut = radius + 0.34;
        double halfW = 0.030;

        int drawn = VisualLod.scale(total, detail);
        int step = Math.max(1, total / drawn);

        for (int i = 0; i < total; i += step) {
            double ang = rot + (Math.PI * 2.0 * i) / total;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            // 垂直于半径方向的偏移
            double px = -sa * halfW, pz = ca * halfW;

            double ax = cx + ca * rIn, az = cz + sa * rIn;
            double bx = cx + ca * rOut, bz = cz + sa * rOut;

            b.vertex(m, (float) (ax + px), (float) cy, (float) (az + pz)).color(r, g, bl, alpha).endVertex();
            b.vertex(m, (float) (bx + px), (float) cy, (float) (bz + pz)).color(r, g, bl, 0f).endVertex();
            b.vertex(m, (float) (bx - px), (float) cy, (float) (bz - pz)).color(r, g, bl, 0f).endVertex();

            b.vertex(m, (float) (ax + px), (float) cy, (float) (az + pz)).color(r, g, bl, alpha).endVertex();
            b.vertex(m, (float) (bx - px), (float) cy, (float) (bz - pz)).color(r, g, bl, 0f).endVertex();
            b.vertex(m, (float) (ax - px), (float) cy, (float) (az - pz)).color(r, g, bl, alpha).endVertex();
        }
    }

    // ==================== 数学 / 颜色辅助 ====================
    // v5 说明：原先的 unpack(int) 已删除——它每次调用都 new float[3]，
    // 现由 VisualColor.unpackInto(dst, rgb) 写入 SCRATCH 复用缓冲取代。
    // 注意本渲染器的颜色来自运行时字段（AuraInfo.color()），无法像其它渲染器那样
    // 预解包成 C_ 常量，只能走复用缓冲——新增元素时请沿用该写法，
    // 不要重新引入返回新数组的形式。

    /** 缓出（cubic）。 */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** 取小数部分（结果恒在 0 到 1 之间，不含 1）。 */
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
     * 把 0xRRGGBB 向白提亮（保留色相，不洗白）。
     *
     * @param rgb 原色
     * @param f   提亮比例 0~1
     * @return 提亮后的 0xRRGGBB
     */
    private static int brighten(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r + (255 - r) * f);
        g = Math.round(g + (255 - g) * f);
        b = Math.round(b + (255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 圆盘填充分段数（按半径自适应，全细节）。
     * <p>保留原签名供不参与 LOD 的调用点使用。</p>
     */
    private static int fillSegments(double radius) {
        return (int) Mth.clamp(Math.round(radius * 3.0) + 12, 16, 48);
    }

    /**
     * 圆盘填充分段数（按半径自适应，再按细节系数缩放）。
     *
     * @param detail 细节系数
     */
    private static int fillSegments(double radius, float detail) {
        return VisualLod.scaleSegments(fillSegments(radius), FILL_SEGMENTS_MIN, detail);
    }

    /**
     * 圆环分段数（按半径自适应，全细节）。
     * <p>保留原签名供不参与 LOD 的调用点使用。</p>
     */
    private static int ringSegments(double radius) {
        return (int) Mth.clamp(Math.round(radius * 4.0) + 16, 24, 72);
    }

    /**
     * 圆环分段数（按半径自适应，再按细节系数缩放）。
     * <p>下限 {@link #RING_SEGMENTS_MIN}(24) 比实体渲染器高，因为环半径可达 16 格
     * （多边形与真圆的偏离量正比于半径，详见该常量注释）。</p>
     *
     * @param detail 细节系数
     */
    private static int ringSegments(double radius, float detail) {
        return VisualLod.scaleSegments(ringSegments(radius), RING_SEGMENTS_MIN, detail);
    }

    // ==================== 专属符文母题（按 serialId 区分光环身份） ====================

    /**
     * 符文母题风格。
     * <p>
     * 每个光环有各自的图案语言，让玩家<b>不靠颜色也能一眼分辨脚下是哪个光环</b>——
     * 色觉障碍玩家、以及多个光环重叠时尤其重要。
     * </p>
     */
    private enum RuneStyle {
        /** 卡利亚：内六边形 + 六芒星 + 顶点水晶碎光（辉石魔法的几何语言） */
        CARIAN,
        /** 守护：双层反向断环 + 径向栅条（"屏障"的语言） */
        WARD,
        /** 星辰：放射光线 + 星屑（"星空 / 宇宙"的语言） */
        COSMIC,
        /** 神圣：长短交替光芒 + 十字圣徽 + 芒尖金光（黄金树秩序的语言） */
        HOLY,
        /** 无母题（仅主环与刻度） */
        PLAIN
    }

    /**
     * 按序列号决定母题风格。
     * <p>
     * serialId 直接引用 {@link CarianStyleAuraDisplays} 的公开常量而非魔数——
     * 这样新增 / 调整光环时，编译器会帮忙检查引用是否还成立。
     * </p>
     *
     * @param serialId 光环序列号
     * @return 对应的母题风格；未登记的序列号返回 {@link RuneStyle#PLAIN}
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
     * 按风格分发绘制专属母题。
     * <p>
     * <b>v4 削减原则（各母题内部遵循）：</b>
     * </p>
     * <ul>
     *     <li><b>星形 / 多边形完全不削</b>——六芒星、十字圣徽各只有 30~40 顶点，
     *         却是「这是哪个光环」的唯一辨识依据，减顶点数就不成其形了；</li>
     *     <li><b>均布角度元素按步长抽取</b>——射线、栅条、断环、芒尖的角度都是
     *         {@code i × (TAU / 总数)}，截断会让整圈缺一块；</li>
     *     <li><b>装饰光点层整层跳过</b>——水晶碎光、星屑、芒尖金光按保留阈值决定画不画。</li>
     * </ul>
     *
     * @param p     本帧准备好的光环
     * @param now   当前动画时间（tick）
     * @param pulse 整体亮度呼吸系数
     * @param cr    主题色 R
     * @param cg    主题色 G
     * @param cb    主题色 B
     * @param lr    提亮色 R
     * @param lg    提亮色 G
     * @param lb    提亮色 B
     * @param a     整体淡入淡出系数
     */
    private static void drawRuneMotif(BufferBuilder b, Matrix4f m, PreparedSlot p, float now, float pulse,
                                      float cr, float cg, float cb,
                                      float lr, float lg, float lb, float a) {
        switch (styleFor(p.serialId)) {
            case CARIAN:
                motifCarian(b, m, p, now, pulse, cr, cg, cb, lr, lg, lb, a);
                break;
            case WARD:
                motifWard(b, m, p, now, pulse, cr, cg, cb, lr, lg, lb, a);
                break;
            case COSMIC:
                motifCosmic(b, m, p, now, pulse, cr, cg, cb, lr, lg, lb, a);
                break;
            case HOLY:
                motifHoly(b, m, p, now, pulse, cr, cg, cb, lr, lg, lb, a);
                break;
            case PLAIN:
            default:
                break;
        }
    }

    /**
     * 卡利亚母题：内六边形 + 反向旋转的六芒星 + 顶点水晶碎光。
     * <p>与 {@code GlintbladesEffectRenderer} 的辉石符文阵同源，都是卡利亚辉石魔法的几何语言。</p>
     * <p><b>六边形与六芒星均不削减</b>——顶点数极少却是核心辨识符号。</p>
     */
    private static void motifCarian(BufferBuilder b, Matrix4f m, PreparedSlot p, float now, float pulse,
                                    float cr, float cg, float cb,
                                    float lr, float lg, float lb, float a) {
        double rot = now * RUNE_SPEED * 0.8;
        double hexR = p.radius * 0.55;
        double starR = p.radius * 0.72;
        double halfW = Math.max(0.030, p.radius * 0.010);
        float alpha = 0.55f * a * pulse;

        // 内六边形（正向旋转）
        addPolygonRing(b, m, p.rx, p.ry, p.rz, hexR, 6, rot, halfW, cr, cg, cb, alpha);
        // 六芒星（反向旋转，两叠三角构成）
        addStarPolygon(b, m, p.rx, p.ry, p.rz, starR, 6, 2, -rot * 1.15, halfW,
                lr, lg, lb, alpha * 1.25f);

        // 六芒星顶点的水晶碎光（装饰层，可整层跳过）
        if (VisualLod.keepLayer(p.detail, SPARKLE_KEEP_THRESHOLD)) {
            for (int i = 0; i < 6; i++) {
                double ang = -rot * 1.15 + (Math.PI * 2.0 * i) / 6.0;
                double sx = Math.cos(ang) * starR;
                double sz = Math.sin(ang) * starR;
                float tw = 0.5f + 0.5f * Mth.sin(now * 0.18f + i * 1.7f);
                addSpark(b, m, p.rx + sx, p.ry, p.rz + sz,
                        (float) (p.radius * 0.045) * (0.7f + 0.6f * tw),
                        lr, lg, lb, 0.75f * a * tw);
            }
        }
    }

    /**
     * 守护母题：双层反向旋转的断环 + 径向栅条。
     * <p>断续的环与栅条读作「屏障 / 护盾」，与卡利亚的连续几何形成对比。</p>
     * <p><b>断环与栅条的角度均布，按步长抽取。</b></p>
     */
    private static void motifWard(BufferBuilder b, Matrix4f m, PreparedSlot p, float now, float pulse,
                                  float cr, float cg, float cb,
                                  float lr, float lg, float lb, float a) {
        double rot = now * RUNE_SPEED * 0.9;
        double rOuter = p.radius * 0.78;
        double rInner = p.radius * 0.52;
        double halfW = Math.max(0.035, p.radius * 0.012);
        float alpha = 0.5f * a * pulse;
        int ringSeg = ringSegments(p.radius, p.detail);

        // 外层断环（正转）与内层断环（反转），弧段数不同以免转速看起来一致
        addDashedRing(b, m, p.rx, p.ry, p.rz, rOuter, 8, 0.62, rot, halfW,
                cr, cg, cb, alpha, ringSeg, p.detail);
        addDashedRing(b, m, p.rx, p.ry, p.rz, rInner, 6, 0.55, -rot * 1.3, halfW,
                lr, lg, lb, alpha * 1.15f, ringSeg, p.detail);

        // 径向栅条：连接内外两环，强化「格栅屏障」的读法
        final int bars = 12;
        int drawnBars = VisualLod.scale(bars, p.detail);
        int barStep = Math.max(1, bars / drawnBars);
        for (int i = 0; i < bars; i += barStep) {
            double ang = rot * 0.5 + (Math.PI * 2.0 * i) / bars;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            float k = 0.45f + 0.55f * Mth.sin(now * 0.13f + i * 0.9f);
            addLine(b, m,
                    p.rx + ca * rInner, p.ry, p.rz + sa * rInner,
                    p.rx + ca * rOuter, p.ry, p.rz + sa * rOuter,
                    halfW * 0.8, cr, cg, cb, alpha * k, alpha * k * 0.35f);
        }
    }

    /**
     * 星辰母题：自中心放射的长短光线 + 环绕星屑。
     * <p>放射线读作「星芒」，配合星屑表达「宇宙 / 回归」的语义。</p>
     * <p><b>射线角度均布，步长必须取奇数</b>——否则长短交替的规律会被抽没（详见 {@link #addRays}）。</p>
     */
    private static void motifCosmic(BufferBuilder b, Matrix4f m, PreparedSlot p, float now, float pulse,
                                    float cr, float cg, float cb,
                                    float lr, float lg, float lb, float a) {
        double rot = now * RUNE_SPEED * 0.6;
        double halfW = Math.max(0.028, p.radius * 0.009);
        float alpha = 0.45f * a * pulse;

        // 12 道放射光线，长短交替
        addRays(b, m, p.rx, p.ry, p.rz, p.radius * 0.20, p.radius * 0.80, p.radius * 0.55,
                12, rot, halfW, cr, cg, cb, alpha, p.detail);

        // 环绕星屑（装饰层，可整层跳过）
        if (VisualLod.keepLayer(p.detail, SPARKLE_KEEP_THRESHOLD)) {
            addStarField(b, m, p.rx, p.ry, p.rz, p.radius, now, lr, lg, lb, 0.7f * a, p.detail);
        }
    }

    /**
     * 神圣母题：长短交替的圣光芒 + 中心十字圣徽 + 芒尖金光。
     * <p>与 {@code AoeEffectRenderer} 的神圣净化 / 祈祷一击同一套语言（黄金树秩序）。</p>
     * <p><b>十字圣徽不削减</b>——只有 4 条线，却是圣域最直接的辨识符号。</p>
     */
    private static void motifHoly(BufferBuilder b, Matrix4f m, PreparedSlot p, float now, float pulse,
                                  float cr, float cg, float cb,
                                  float lr, float lg, float lb, float a) {
        double rot = now * RUNE_SPEED * 0.5;
        double halfW = Math.max(0.032, p.radius * 0.010);
        float alpha = 0.5f * a * pulse;

        // 16 道圣光芒，长短交替（像哥特式光轮）
        addRays(b, m, p.rx, p.ry, p.rz, p.radius * 0.26, p.radius * 0.86, p.radius * 0.58,
                16, rot, halfW, cr, cg, cb, alpha, p.detail);

        // 中心十字圣徽：竖长横短，读作「圣十字」而非「加号」
        double armLong = p.radius * 0.42;
        double armShort = p.radius * 0.26;
        float crossAlpha = alpha * 1.4f;
        addLine(b, m, p.rx, p.ry, p.rz - armLong, p.rx, p.ry, p.rz + armLong * 0.62,
                halfW * 1.2, lr, lg, lb, crossAlpha, crossAlpha);
        addLine(b, m, p.rx - armShort, p.ry, p.rz - armLong * 0.30,
                p.rx + armShort, p.ry, p.rz - armLong * 0.30,
                halfW * 1.2, lr, lg, lb, crossAlpha, crossAlpha);

        // 长芒尖端的金光点（装饰层，可整层跳过）
        if (VisualLod.keepLayer(p.detail, SPARKLE_KEEP_THRESHOLD)) {
            final int tips = 8;
            int drawnTips = VisualLod.scale(tips, p.detail);
            int tipStep = Math.max(1, tips / drawnTips);
            for (int i = 0; i < tips; i += tipStep) {
                // 长芒位于偶数序号（i×2），与 addRays 的长短交替对齐
                double ang = rot + (Math.PI * 2.0 * (i * 2)) / 16.0;
                double sx = Math.cos(ang) * p.radius * 0.86;
                double sz = Math.sin(ang) * p.radius * 0.86;
                float tw = 0.5f + 0.5f * Mth.sin(now * 0.15f + i * 1.3f);
                addSpark(b, m, p.rx + sx, p.ry, p.rz + sz,
                        (float) (p.radius * 0.040) * (0.7f + 0.6f * tw),
                        lr, lg, lb, 0.7f * a * tw);
            }
        }
    }

    // ==================== 母题通用几何基元 ====================

    /**
     * 绘制一条水平线段（带宽度，两端可用不同 alpha）。
     *
     * @param halfW 线半宽（格）
     */
    private static void addLine(BufferBuilder b, Matrix4f m,
                                double x1, double y, double z1,
                                double x2, double y2, double z2,
                                double halfW, float r, float g, float bl,
                                float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        double dx = x2 - x1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        // 水平面内的法线 × 半宽
        double nx = -dz / len * halfW;
        double nz = dx / len * halfW;

        b.vertex(m, (float) (x1 + nx), (float) y, (float) (z1 + nz)).color(r, g, bl, a1).endVertex();
        b.vertex(m, (float) (x2 + nx), (float) y2, (float) (z2 + nz)).color(r, g, bl, a2).endVertex();
        b.vertex(m, (float) (x2 - nx), (float) y2, (float) (z2 - nz)).color(r, g, bl, a2).endVertex();

        b.vertex(m, (float) (x1 + nx), (float) y, (float) (z1 + nz)).color(r, g, bl, a1).endVertex();
        b.vertex(m, (float) (x2 - nx), (float) y2, (float) (z2 - nz)).color(r, g, bl, a2).endVertex();
        b.vertex(m, (float) (x1 - nx), (float) y, (float) (z1 - nz)).color(r, g, bl, a1).endVertex();
    }

    /**
     * 绘制正多边形环（各顶点依次连线）。
     * <p><b>不参与削减</b>：顶点数由多边形的边数决定（六边形就是 6 条线），减了就不成其形。</p>
     *
     * @param sides 边数
     * @param rot   起始旋转角（弧度）
     */
    private static void addPolygonRing(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                       double radius, int sides, double rot, double halfW,
                                       float r, float g, float bl, float alpha) {
        if (alpha <= 0.004f || sides < 3) {
            return;
        }
        for (int i = 0; i < sides; i++) {
            double a0 = rot + (Math.PI * 2.0 * i) / sides;
            double a1 = rot + (Math.PI * 2.0 * (i + 1)) / sides;
            addLine(b, m,
                    cx + Math.cos(a0) * radius, cy, cz + Math.sin(a0) * radius,
                    cx + Math.cos(a1) * radius, cy, cz + Math.sin(a1) * radius,
                    halfW, r, g, bl, alpha, alpha);
        }
    }

    /**
     * 绘制星形多边形（{@code {n/skip}} 星形，如 {@code {6/2}} 即六芒星）。
     * <p><b>不参与削减</b>：星形的顶点数就是它的定义，减了不成其形。</p>
     *
     * @param points 顶点数
     * @param skip   连接跨度（每次跳过几个顶点）
     */
    private static void addStarPolygon(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                       double radius, int points, int skip, double rot, double halfW,
                                       float r, float g, float bl, float alpha) {
        if (alpha <= 0.004f || points < 3 || skip < 1) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double a0 = rot + (Math.PI * 2.0 * i) / points;
            double a1 = rot + (Math.PI * 2.0 * ((i + skip) % points)) / points;
            addLine(b, m,
                    cx + Math.cos(a0) * radius, cy, cz + Math.sin(a0) * radius,
                    cx + Math.cos(a1) * radius, cy, cz + Math.sin(a1) * radius,
                    halfW, r, g, bl, alpha, alpha);
        }
    }

    /**
     * 绘制自中心向外的放射光线（长短交替）。
     * <p>
     * <b>v4：按步长抽取，且步长必须取奇数。</b>光线的长短由 {@code i % 2} 决定；
     * 若步长为偶数，抽取出来的下标奇偶性完全相同——<b>要么全是长芒、要么全是短芒</b>，
     * 长短交替的节奏会被抽没。取奇数步长可保证奇偶交替得以保留。
     * </p>
     *
     * @param rInner    起点半径
     * @param rLong     长线终点半径
     * @param rShort    短线终点半径
     * @param count     光线总数
     */
    private static void addRays(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                double rInner, double rLong, double rShort,
                                int count, double rot, double halfW,
                                float r, float g, float bl, float alpha, float detail) {
        if (alpha <= 0.004f) {
            return;
        }
        int drawn = VisualLod.scale(count, detail);
        int step = Math.max(1, count / drawn);
        // ⭐ 步长必须为奇数，否则长短交替会被抽成"全长"或"全短"
        if ((step & 1) == 0) {
            step++;
        }
        for (int i = 0; i < count; i += step) {
            double ang = rot + (Math.PI * 2.0 * i) / count;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double rOut = ((i & 1) == 0) ? rLong : rShort;
            addLine(b, m,
                    cx + ca * rInner, cy, cz + sa * rInner,
                    cx + ca * rOut, cy, cz + sa * rOut,
                    halfW, r, g, bl, alpha, 0f);
        }
    }

    /**
     * 绘制断续圆环（若干等分弧段，段间留空）。
     * <p>
     * <b>v4：弧段数按步长抽取。</b>弧段起始角是 {@code rot + i × (TAU / dashes)} 均布的，
     * 截断会让整环只剩一侧有弧段。每段内部的细分数也随之缩放。
     * </p>
     *
     * @param dashes   弧段数量
     * @param fill     每段占其扇区的比例（0~1；1 即连续环）
     * @param ringSeg  整环的参考分段数（用于决定每段的细分）
     */
    private static void addDashedRing(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                      double radius, int dashes, double fill, double rot, double halfW,
                                      float r, float g, float bl, float alpha, int ringSeg, float detail) {
        if (alpha <= 0.004f || dashes < 1 || radius <= 0) {
            return;
        }
        int drawnDashes = VisualLod.scale(dashes, detail);
        int dashStep = Math.max(1, dashes / drawnDashes);
        // 每段内部的细分数：整环分段数 / 段数，至少 2 段以免弧看起来是直线
        int perDash = Math.max(2, ringSeg / dashes);
        double sector = (Math.PI * 2.0) / dashes;
        double arc = sector * fill;

        for (int d = 0; d < dashes; d += dashStep) {
            double start = rot + sector * d;
            for (int s = 0; s < perDash; s++) {
                double a0 = start + arc * s / perDash;
                double a1 = start + arc * (s + 1) / perDash;
                addLine(b, m,
                        cx + Math.cos(a0) * radius, cy, cz + Math.sin(a0) * radius,
                        cx + Math.cos(a1) * radius, cy, cz + Math.sin(a1) * radius,
                        halfW, r, g, bl, alpha, alpha);
            }
        }
    }

    /**
     * 绘制环绕的星屑光点（伪随机分布，各自错相闪烁）。
     * <p>
     * <b>v4：数量按细节系数缩放，可直接截断。</b>星屑的角度与半径由下标做伪随机散列得到、
     * 与均布无关，因此截断尾部不会造成"缺一块"——只是星星变少。
     * </p>
     */
    private static void addStarField(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                     double radius, float now,
                                     float r, float g, float bl, float alpha, float detail) {
        if (alpha <= 0.004f) {
            return;
        }
        final int total = 14;
        int drawn = VisualLod.scale(total, detail);
        for (int i = 0; i < drawn; i++) {
            // 用黄金角散列出角度与半径，避免星星排成规则圈
            double ang = i * 2.39996 + now * 0.004;
            double rr = radius * (0.30 + 0.62 * frac(i * 0.6180339887f));
            double sx = Math.cos(ang) * rr;
            double sz = Math.sin(ang) * rr;
            float tw = 0.5f + 0.5f * Mth.sin(now * 0.20f + i * 2.1f);
            addSpark(b, m, cx + sx, cy, cz + sz,
                    (float) (radius * 0.030) * (0.6f + 0.7f * tw),
                    r, g, bl, alpha * tw);
        }
    }
}
