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
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.List;

/**
 * 定点 AOE 自绘渲染器（纯客户端）。
 * <p>
 * 与光环 {@link AuraGroundRenderer} 采用同款管线：订阅 {@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，用 {@code POSITION_COLOR} 纯顶点绘制——无贴图、无原版粒子。
 * </p>
 * <p>
 * 多数演出为<b>水平地面法阵</b>；而<b>猩红罗妮亚 {@link #drawScarletBloom}</b> 为还原玛莲妮亚的
 * 「猩红艾奥尼亚」开花，额外绘制一朵<b>竖直 3D 立体绽放花</b>（{@link #drawAeoniaFlower}）。其余演出
 * （含癫火 {@link #drawFrenziedFlame}）保持平面。
 * </p>
 * <p>
 * <b>龙雷红色闪电 {@link #drawRedLightning}（v4）：</b>另一类<b>竖直</b>演出——还原艾尔登法环「龙雷」的
 * 红色雷击：一道自天而降的红色之字电柱劈在目标脚下，白热核 + 红辉光 + 浓红外晕三层电流，沿途分出数条
 * 短分叉，落地处掀起红色冲击环与地面强闪，整体瞬间炸亮后缓慢庄重明灭、较慢消散。电柱每段用「十字双面」
 * 绘制（沿世界 X、Z 轴各一个四边形），从任意水平视角皆可见、无需 billboard。外形由管理器创建时确定的
 * 固定种子决定，每道闪电形态各异、同一道闪电在其生命周期内形态稳定。古龙雷击等高频重复降雷由管理器的
 * 「同位置合并」收敛为一道持续的雷，避免叠加成「鬼畜」。
 * </p>
 * <p>
 * <b>v5（性能，视觉零变化）：</b>接入 {@link VisualBatch}——不再自行设置 / 恢复 GL 状态、
 * 不再自行 {@code begin/end} 顶点缓冲，改为向共享缓冲写顶点，由 {@link VisualBatch} 在本帧末
 * 统一提交（本模组七个世界渲染器合并为一次 GL 状态切换与一次 draw call）。
 * <b>空缓冲兜底</b>（零面积全透明退化三角形）原先由本类在自己的 {@code begin()} 后追加，
 * 现已上移至 {@link VisualBatch} 统一处理，本类不再需要。
 * 本渲染器不做范围实体查询（数据来自 {@link AoeEffectManager} 的存活特效列表），故不涉及
 * {@link SharedEntityQuery}；仅在绑定跟随实体时按 id 精确查一次实体，与优化前一致。
 * 距离裁剪、进度映射、分发顺序与全部几何参数均未改动。
 * </p>
 *
 * <h3>v6（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * <b>本渲染器此前是全模组唯一完全没有细节层级裁剪、也不登记同屏实例数的渲染器</b>，
 * 而它承载的恰恰是全模组顶点量最高的几套演出：
 * </p>
 * <pre>
 * 猩红立体花（32 花瓣 × 8 段曲面 + 地面法阵 + 双爆发环 + 星屑）   ~4000
 * 龙雷红色闪电（主干 18 段 × 3 层十字双面 + 6 分叉 + 落地四环）   ~4400
 * 癫火扩散（18 焰舌 + 三重星阵 + 爆发环 + 星屑）                 ~2700
 * 冻结地震（24 条地裂 + 双霜环 + 冰花）                          ~1540
 * 因果律（六芒星法阵 + 因果之线 + 顶点火花）                     ~1490
 * 排斥（双环外推）                                              ~1300
 * </pre>
 * <p>
 * {@code AoeEffectManager.MAX_ACTIVE} 为 40（v6.2 由 64 下调）。古龙雷击单次触发最多命中
 * {@code MAX_TARGETS}(100) 个目标、每目标最多 {@code level × 15} 次落雷；红闪的「同位置合并」
 * 只在 2.5 格内生效，<b>目标分散时会各自成为独立的一道</b>。跑满 40 道红闪即约
 * <b>17.6 万顶点</b>——而这个爆发恰好发生在「大量实体正在死亡」的最卡时刻。
 * </p>
 * <p>
 * 现全部演出按 {@link VisualLod#detail} 缩放元素数量与分段数：
 * {@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，<b>与优化前逐像素一致</b>；
 * 远处与同屏拥挤时逐步削减，最远处单道红闪降至约 900 顶点、单朵立体花降至约 1100 顶点。
 * </p>
 *
 * <h4>细节系数必须按「到特效边界的距离」取，不能按到中心的距离</h4>
 * <p>
 * 这是本次改造与多数实体渲染器最大的不同。实体类特效的尺寸与实体相当（1~2 格），
 * 按实体中心距离取 detail 即可；但本渲染器的演出<b>本身就很大</b>：
 * </p>
 * <ul>
 *     <li>猩红立体花主体半径 7.5 格，其爆发冲击环再 ×1.9 ≈ <b>直径 28 格</b>；</li>
 *     <li>红色闪电柱高 {@link #LIGHTNING_HEIGHT}(28) 格，玩家可能贴着柱子仰头看；</li>
 *     <li>因果律 / 冻结地震的半径直接取附魔实际作用半径，最大可达 10 格以上。</li>
 * </ul>
 * <p>
 * 若照搬「按中心平方距离算 detail」，会出现这样的荒谬情况：玩家站在立体花的花瓣边缘，
 * 到花心距离约 14 格 → 被判定为「远」并削减，但那片花瓣就贴在脸上、削减清晰可见。
 * 故本渲染器改用 {@link #detailFor} 按<b>到演出视觉边界的近似距离</b>取 detail：
 * 人站在演出范围内时该值为 0、detail 恒为 1.0，只有整个演出都离得很远时才削减。
 * 这里需要一次 {@link Math#sqrt}，但同屏特效上限 40 个，开方成本可忽略。
 * </p>
 *
 * <h4>三条削减原则</h4>
 * <ol>
 *     <li><b>环 / 弧的分段数是首要杠杆</b>——{@code glowRing} 内部叠三层 {@code band}，
 *         单个 {@code glowRing} 就是 {@code segs × 18} 个顶点，而本渲染器的每套演出都有 2~4 个环。
 *         分段数下限取 {@link #RING_SEGMENTS_MIN}(20)，比实体渲染器高不少——
 *         多边形与真圆的偏离量正比于半径，AOE 环半径远大于实体类特效；</li>
 *     <li><b>角度均布的元素按步长抽取，绝不能截断</b>——放射线（{@code rays}）、
 *         符文刻度（{@code tickRing}）、癫火焰舌的角度都是 {@code i × (TAU / 总数)}，
 *         截断前 N 个会让整套演出<b>只朝一侧喷</b>，破坏对称性；</li>
 *     <li><b>星形 / 多边形法阵完全不削</b>——六芒星、冰花、癫火的三重星阵每个只有 36~48 顶点，
 *         却是「这是哪套演出」的唯一辨识依据，顶点性价比极高，削它是纯亏。</li>
 * </ol>
 *
 * <h4>闪电主干为什么要「先算全部节点、再跳着连线」</h4>
 * <p>
 * 闪电主干的每个节点由 xorshift 逐段推进生成，<b>段数直接决定 rng 的推进次数</b>。
 * 若简单地把循环上界从 {@link #LIGHTNING_SEGMENTS} 改小，同一道闪电在不同距离下
 * 会得到完全不同的节点序列——玩家走近走远时电柱形状会来回跳变。
 * </p>
 * <p>
 * 故 {@link #drawRedLightning} 始终按原段数算出全部 19 个节点（rng 推进次数不变、
 * 分叉锚点位置不变），仅在<b>绘制</b>时按步长跳着连线：保留的节点仍在原轨迹上，
 * 只是折线更粗糙。形状骨架恒定，远近切换无跳变。分叉同理——分叉数截断的是
 * 循环尾部，保留下来那几条的形状与全细节时完全一致。
 * </p>
 *
 * <h4>花瓣只削曲面细分，不削瓣数</h4>
 * <p>
 * 花瓣顶点占立体花的近 40%（32 瓣 × 8 段 × 6 = 1536）。两种削法：
 * </p>
 * <ul>
 *     <li>减<b>瓣数</b>：{@code azimuth = baseRot + TAU × i / petals}，减 petals 会重新均布，
 *         花仍对称但瓣的方位随距离变化 → 远近切换时花会「重新长一遍」；</li>
 *     <li>减<b>每瓣的脊线细分 seg</b>：只让花瓣曲面从平滑变硬直，<b>轮廓、方位、尺寸全部不变</b>。</li>
 * </ul>
 * <p>
 * 故只削 seg（8 → 下限 {@link #PETAL_SEGMENTS_MIN}），已能省掉一半以上花瓣顶点；
 * 细节极低时再额外跳过最内层的花蕊小瓣（那层被外三层遮住大半，远处完全看不出）。
 * </p>
 *
 * <h3>v7（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v6 把顶点量压下去了，但本渲染器还藏着<b>全模组最后一处、也是最密集的一处</b>小对象分配：
 * 旧实现的 {@code mix(a, b, t)} 与 {@code unpack(color)} <b>每次调用都 {@code new float[3]}</b>，
 * 而 {@link #drawPetal} 的绘制循环里<b>每段要调两次</b>（本段起点色 + 本段末端色）。
 * </p>
 * <p>
 * 单朵猩红立体花的花瓣结构是 {@code 8 + 7 + 6 + 5 + 6 = 32} 瓣、每瓣 8 段，于是：
 * </p>
 * <pre>
 * 花瓣（32 瓣 × 8 段 × 2 次 petalColor→mix）      512
 * 各 draw 方法开头的主题色 unpack（3+3+2+2+3+1+1）  15
 * 花心 / 白热核 / 起手闪光的 mix                      5
 * ─────────────────────────────────────────────────
 * 合计                        ~532 次 new float[3] / 朵 / 帧
 * </pre>
 * <p>
 * 猩红艾奥尼亚是<b>群体死亡演出</b>——{@code EnchantmentAeonia} 会让附近死亡的实体
 * 50% 概率把腐败传播出去，刷怪塔 / 团灭场景下同屏可能同时开好几朵花；
 * 而更糟的是这个爆发<b>恰好发生在大量实体正在死亡的最卡时刻</b>。
 * 5 朵同屏 × 60fps ≈ <b>每秒 16 万次</b>朝生夕死的小数组分配，
 * 全部集中在客户端最需要帧率的那两秒里。
 * </p>
 * <p>
 * 现改为两条路径（工具见 {@link VisualColor}）：
 * </p>
 * <ol>
 *     <li><b>全部主题色类加载时预解包一次</b>（{@code C_} 前缀常量），此后永久复用。
 *         本渲染器的主题色全是编译期常量，无一例外，因此这一项直接归零；</li>
 *     <li><b>动态插值色写入复用缓冲</b>——{@link #PETAL_COL_A} / {@link #PETAL_COL_B}
 *         专供花瓣滚动，{@link #SCRATCH} 供其余一次性混色。</li>
 * </ol>
 * <p>
 * <b>另有一处同源浪费一并清理：</b>{@link #drawPetal} 里的脊线位移数组
 * {@code new double[seg + 1]}，每片花瓣两个、单朵花 <b>64 个 / 帧</b>。
 * 数量比颜色数组少一个数量级，但每个更大（9 个 double = 72 字节），总字节数相当。
 * 该方法不可重入（同一线程内不会嵌套调用自己），故提为静态定长缓冲
 * {@link #PETAL_HOR} / {@link #PETAL_VER} 复用。
 * </p>
 *
 * <h4>花瓣为什么必须用两个缓冲、且要滚动交换</h4>
 * <p>
 * {@link #drawPetal} 是把花瓣沿脊线拆成若干四边形段绘制的，<b>每段的两端颜色不同</b>
 * （根部暗、尖端亮），因此写顶点时必须<b>同时</b>持有 {@code c0} 与 {@code c1}——
 * 这正是 {@link VisualColor} 类注释里点名的「两个动态色同时存活」场景。
 * 若两次都写同一个缓冲，后写的会覆盖先写的，<b>整片花瓣会退化成纯色</b>，
 * 花的立体感与根尖渐变全部消失。
 * </p>
 * <p>
 * 更进一步，由于「第 i 段的末端色 == 第 i+1 段的起点色」（两者都是 {@code petalColor(u)}
 * 在同一个 u 上的取值），这里采用<b>滚动交换</b>：每段只算一次新颜色，
 * 用完把两个缓冲的引用对调。于是插值次数从 {@code 2 × seg} 降为 {@code seg + 1}，
 * <b>在零分配之外又顺带省掉一半的插值计算</b>。
 * </p>
 *
 * <h4>⚠ 传给 drawPetal 的 deep / mid / tip 必须是只读常量</h4>
 * <p>
 * {@link #drawPetalLayer} 会把三个配色数组一路传进 {@link #drawPetal}，
 * 而后者<b>在整个脊线循环期间反复读取它们</b>。因此这三个参数只能传
 * {@code C_} 前缀的只读常量，<b>绝不能传 {@link #SCRATCH} 或花瓣缓冲</b>——
 * 否则循环跑到一半配色就被改掉了。当前 {@link #drawAeoniaFlower} 传的全是常量，
 * 后续若要做「随时间变化的花瓣配色」，必须另开一组专用缓冲，不可复用现有三个。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#mixInto} 与旧的 {@code mix} 都是在归一化域直接
 * 线性插值并 {@code clamp01}，{@link VisualColor#constant} 与旧的 {@code unpack} 是同一个
 * {@code /255f} 公式，因此输出的每个颜色分量与 v6 完全相同——不是「肉眼看不出」而是「数值相等」。
 * </p>
 *
 * <h3>v9 新增：满月月华（{@link #drawMoonBlessing}）与神圣净化（{@link #drawSacredPurge}）</h3>
 * <p>
 * 两套新演出<b>完全复用现有几何基元</b>（{@code band} / {@code glowRing} / {@code line} /
 * {@code spark} / {@code tickRing} / {@code lightningSegment}），没有新增任何基元，
 * 也没有改动任何既有演出的一行代码。
 * </p>
 *
 * <h4>满月月华：为什么月轮是「满」的</h4>
 * <p>
 * {@code DarkMoonRenderer}（暗月的持续视觉）画的是<b>带暗面弧的亏凸月</b>，
 * 而本演出画的是<b>完整无缺的圆盘</b>。这不是偷懒省一个基元，而是语义自洽：
 * 一个叫暗月、一个叫满月，月相当然应该不同——玩家同时装备两者时
 * （机制上正是「月之共鸣」的组合），头顶的亏月与复活时降下的满月形成呼应。
 * </p>
 * <p>
 * <b>回春环刻意向内收拢。</b>本模组其余全部环形演出都是向外扩散（因果律、冻结地震、
 * 排斥、龙雷、癫火、立体花的爆发环无一例外），反向收拢在视觉上直接读作
 * 「能量在往身上汇聚」，与「每秒回 0.5% 最大生命」的语义一致；
 * 同屏叠加时也能一眼与那些爆发类演出区分开。
 * </p>
 * <p>
 * <b>细节系数按柱高换算。</b>月华柱高 {@link #MOON_COLUMN_HEIGHT} 格、月轮悬浮在
 * {@link #MOON_DISC_HEIGHT} 格处，而半径只有 3.5——玩家站在自己脚边仰头看月轮时，
 * 按中心距离算会显得「近」，但演出的主体其实在头顶上方几格。
 * 故 {@link #visualRadiusFor} 取 {@code max(radius, 月轮高度 × }{@value #MOON_VISUAL_RADIUS_FACTOR}{@code )}，
 * 与红闪电柱同源的处理。
 * </p>
 *
 * <h4>神圣净化：三维十字而非平面法阵</h4>
 * <p>
 * 本模组的地面演出已经密集到「再加一个平面法阵就没人分得清」的程度，
 * 因此神圣净化的主视觉做成<b>立在目标躯干高度的三维十字光刃</b>——
 * 一道竖刃（{@code lightningSegment} 十字双面）+ 两道正交横刃（水平 {@code line}），
 * 从任意角度看都是一个悬在半空的发光十字，而不是又一个铺在地上的圆。
 * </p>
 * <p>
 * <b>时长只有 700ms</b>，是全部演出里第二短的（仅次于排斥的 520ms）。
 * 这是命中反馈而非状态演出，拖长了会让连续攻击亡灵时前后两次的爆闪叠成一片。
 * </p>
 *
 * @author RoinFlam
 * @version 11.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class AoeEffectRenderer {

    /** 离地高度偏移，避免与地面 z-fighting */
    private static final float Y_OFFSET = 0.02f;
    /** 距离裁剪（格）：相机太远的特效本帧不绘制。已调大以容纳更大的爆发冲击环与立体花。 */
    private static final double CULL = 96.0;
    private static final float TAU = (float) (Math.PI * 2.0);

    // ===== v6 LOD 下限与保留阈值 =====

    /**
     * 环 / 弧类基元的最少分段数。
     * <p>
     * 比实体渲染器的下限（8~10）高不少：多边形与真圆的偏离量正比于半径，
     * 而本渲染器的环半径可达 14 格以上（立体花爆发环）。20 段在 14 格半径下
     * 偏离约 17cm，在快速播放的爆发演出里不可察；再低就能看出明显棱角了。
     * </p>
     */
    private static final int RING_SEGMENTS_MIN = 20;

    /**
     * 花瓣脊线的最少细分段数。
     * <p>3 段仍能表现出花瓣「由根部向外翻卷」的弯曲曲面；降到 2 段花瓣会变成折角明显的三角片。</p>
     */
    private static final int PETAL_SEGMENTS_MIN = 3;

    /**
     * 闪电主干的最少绘制段数。
     * <p>6 段仍是一道明确的之字形电柱；再低就退化成近似直线、失去「闪电」的语义。</p>
     */
    private static final int LIGHTNING_SEGMENTS_MIN = 6;

    /**
     * 闪电分叉的最少绘制段数。
     */
    private static final int LIGHTNING_BRANCH_SEGMENTS_MIN = 2;

    /**
     * 闪电最外层浓红光晕的保留阈值。
     * <p>该层是最宽最淡的体量光晕（每段 {@code LIGHTNING_HALO_HALF} 半宽），
     * 占主干顶点的三分之一，但在远处会与中层辉光完全糊成一片，低细节时整层跳过。</p>
     */
    private static final float LIGHTNING_HALO_KEEP_THRESHOLD = 0.55f;

    /**
     * 因果律六芒星顶点火花的保留阈值。
     * <p>6 颗火花共 72 顶点，是纯装饰层，远处看不出。</p>
     */
    private static final float SPARK_KEEP_THRESHOLD = 0.45f;

    /**
     * 立体花最内层花蕊小瓣的保留阈值。
     * <p>该层被外三层花瓣遮住大半，远处完全不可见。</p>
     */
    private static final float PETAL_INNER_LAYER_KEEP_THRESHOLD = 0.4f;

    // ===== v9 LOD 下限与保留阈值（满月月华 / 神圣净化）=====

    /**
     * 月华柱的最少竖直分段数。
     * <p>分段只用于沿高度做透明度渐变（上亮下柔），3 段仍能表达「光从月轮流下来」；
     * 降到 1 段整根柱子会变成均匀不透明的方棱柱，失去通透感。</p>
     */
    private static final int MOON_COLUMN_SEGMENTS_MIN = 3;

    /**
     * 月轮盘 / 环的最少分段数。
     * <p>月轮是本演出的核心标志，多边形化最不能容忍，故下限比通用环高。
     * 半径仅约 1.7 格，18 段的偏离量约 2.6cm，肉眼不可辨。</p>
     */
    private static final int MOON_DISC_SEGMENTS_MIN = 18;

    /**
     * 月尘层的保留阈值。
     * <p>极细的上升短光丝（半宽 0.035 格），远处几乎不可见。</p>
     */
    private static final float MOON_DUST_KEEP_THRESHOLD = 0.45f;

    /**
     * 月轮外缘刻度层的保留阈值。
     * <p>细密小段，远处糊成一圈，而月轮本体的盘 + 环已足够表达「这是月亮」。</p>
     */
    private static final float MOON_TICK_KEEP_THRESHOLD = 0.5f;

    /**
     * 神圣净化升天光尘层的保留阈值。
     */
    private static final float SACRED_MOTE_KEEP_THRESHOLD = 0.45f;

    // ===== v6 视觉尺度系数（用于把「特效半径」换算成「视觉边界半径」）=====

    /**
     * 猩红立体花的视觉尺度系数。
     * <p>其爆发冲击环取 {@code radius × 1.9}（见 {@link #drawScarletBloom}），
     * 是整套演出中最占视野的部分，故以此为视觉边界。</p>
     */
    private static final double SCARLET_BLOOM_VISUAL_SCALE = 1.9;

    /**
     * 癫火扩散的视觉尺度系数（其爆发冲击环取 {@code radius × 1.75}）。
     */
    private static final double FRENZIED_FLAME_VISUAL_SCALE = 1.75;

    /**
     * 红色闪电的视觉半径系数（× {@link #LIGHTNING_HEIGHT}）。
     * <p>闪电的主要视觉体量是竖直的 28 格电柱而非落地冲击环，
     * 故用柱高的 0.4 倍（约 11 格）作为视觉边界——玩家在柱子附近仰头看时不会被削减。</p>
     */
    private static final double LIGHTNING_VISUAL_RADIUS_FACTOR = 0.4;

    /**
     * 满月月华的视觉半径系数（× {@link #MOON_DISC_HEIGHT}）。
     * <p>月轮悬浮在 3.4 格高处、月华柱贯穿其下，主体视觉在<b>头顶上方</b>而非脚下平面，
     * 故用月轮高度的 0.9 倍（约 3.1 格）与名义半径取大值作为视觉边界
     * （详见类注释「细节系数按柱高换算」）。</p>
     */
    private static final double MOON_VISUAL_RADIUS_FACTOR = 0.9;

    // ===== 主题配色（0xRRGGBB）=====
    private static final int CAUSALITY_GOLD = 0xFFD24A;
    private static final int CAUSALITY_VIOLET = 0xB47BFF;
    private static final int FROST_ICE = 0x9ED8FF;
    private static final int FROST_WHITE = 0xEAF6FF;
    private static final int REPULSE_WHITE = 0xF0F4FF;
    private static final int SCARLET = 0xE0244A;
    private static final int SCARLET_DEEP = 0x800018;
    /** 猩红血核白热（炸裂/蓄力顶点用） */
    private static final int SCARLET_HOT = 0xFFD0D8;
    private static final int FRENZY_YELLOW = 0xFFE020;
    private static final int FRENZY_ORANGE = 0xFF6A1A;
    /** 癫火白热（顶点冲击强闪用） */
    private static final int FRENZY_WHITE = 0xFFF4C0;
    private static final int GENERIC_BLUE = 0xCFE0FF;

    // ===== 龙雷红色闪电配色（0xRRGGBB）=====
    /** 龙雷电柱核心：近白热（仅微带红），原作龙雷核心极亮发白 */
    private static final int LIGHTNING_CORE = 0xFFF0F0;
    /** 龙雷电柱中层辉光：亮红 */
    private static final int LIGHTNING_GLOW = 0xFF2A36;
    /** 龙雷电柱外层光晕 / 落地冲击 / 分叉末端：浓深红 */
    private static final int LIGHTNING_DEEP = 0xC81022;

    // ===== v9 满月月华配色（0xRRGGBB）=====
    // 与 DarkMoonRenderer 取同一组三色，使暗月的持续视觉与满月的复活演出形成视觉家族。
    /** 月白：月轮盘面、月华柱核心、起手闪光 */
    private static final int MOON_CORE = 0xF0F6FF;
    /** 月华蓝：月轮外环、月华柱主体、回春环、月尘 */
    private static final int MOON_GLOW = 0xA8C4F0;
    /** 夜蓝：月光池外缘、月华柱外晕 */
    private static final int MOON_DEEP = 0x3F5C99;

    // ===== v9 神圣净化配色（0xRRGGBB）=====
    // 与黄金树祝福（0xFFC23A）色相接近但形态完全不同（瞬时三维十字爆闪 vs 持续贴身光晕），
    // 且神圣刀刃的语义本就是「神圣」，用金色是对的、不构成辨识歧义。
    /** 圣光核心：近白暖光 */
    private static final int SACRED_CORE = 0xFFFBE8;
    /** 圣光主色：暖金 */
    private static final int SACRED_GOLD = 0xFFD470;
    /** 圣光深色：净化环外缘与地面圣徽暗部 */
    private static final int SACRED_DEEP = 0xC08A28;

    // ===== v7：预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    //
    // 本渲染器的主题色全部是编译期常量、无一例外，因此这一组常量把
    // 「每次 draw 方法开头 unpack 一遍」的开销直接归零。

    /** 因果律金（法阵主色） */
    private static final float[] C_CAUSALITY_GOLD = VisualColor.constant(CAUSALITY_GOLD);
    /** 因果律紫（因果之线） */
    private static final float[] C_CAUSALITY_VIOLET = VisualColor.constant(CAUSALITY_VIOLET);
    /** 冰蓝（地裂 / 霜环 / 冰花内层） */
    private static final float[] C_FROST_ICE = VisualColor.constant(FROST_ICE);
    /** 冰白（裂纹内芯 / 八角星 / 中心闪光） */
    private static final float[] C_FROST_WHITE = VisualColor.constant(FROST_WHITE);
    /** 排斥白（双环外推） */
    private static final float[] C_REPULSE_WHITE = VisualColor.constant(REPULSE_WHITE);
    /** 猩红（花瓣中段 / 法阵 / 爆发环） */
    private static final float[] C_SCARLET = VisualColor.constant(SCARLET);
    /** 深猩红（花瓣根部 / 余烬 / 地面铺底） */
    private static final float[] C_SCARLET_DEEP = VisualColor.constant(SCARLET_DEEP);
    /** 猩红白热（花瓣尖端 / 炸裂强闪） */
    private static final float[] C_SCARLET_HOT = VisualColor.constant(SCARLET_HOT);
    /** 癫火黄（焰舌内段 / 星阵） */
    private static final float[] C_FRENZY_YELLOW = VisualColor.constant(FRENZY_YELLOW);
    /** 癫火橙（焰舌外段 / 爆发环） */
    private static final float[] C_FRENZY_ORANGE = VisualColor.constant(FRENZY_ORANGE);
    /** 癫火白热（顶点冲击强闪） */
    private static final float[] C_FRENZY_WHITE = VisualColor.constant(FRENZY_WHITE);
    /** 通用回退蓝白 */
    private static final float[] C_GENERIC_BLUE = VisualColor.constant(GENERIC_BLUE);
    /** 龙雷白热核 */
    private static final float[] C_LIGHTNING_CORE = VisualColor.constant(LIGHTNING_CORE);
    /** 龙雷亮红辉光 */
    private static final float[] C_LIGHTNING_GLOW = VisualColor.constant(LIGHTNING_GLOW);
    /** 龙雷浓深红外晕 */
    private static final float[] C_LIGHTNING_DEEP = VisualColor.constant(LIGHTNING_DEEP);
    /** v9 月白（月轮盘面 / 柱核 / 起手闪光） */
    private static final float[] C_MOON_CORE = VisualColor.constant(MOON_CORE);
    /** v9 月华蓝（月轮外环 / 柱主体 / 回春环 / 月尘） */
    private static final float[] C_MOON_GLOW = VisualColor.constant(MOON_GLOW);
    /** v9 夜蓝（月光池 / 柱外晕） */
    private static final float[] C_MOON_DEEP = VisualColor.constant(MOON_DEEP);
    /** v9 圣光核心（十字光刃 / 中心爆闪） */
    private static final float[] C_SACRED_CORE = VisualColor.constant(SACRED_CORE);
    /** v9 圣光暖金（净化环 / 光尘） */
    private static final float[] C_SACRED_GOLD = VisualColor.constant(SACRED_GOLD);
    /** v9 圣光深金（外环 / 地面圣徽） */
    private static final float[] C_SACRED_DEEP = VisualColor.constant(SACRED_DEEP);

    /**
     * v7：花瓣脊线滚动双缓冲之一（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * <b>必须与 {@link #PETAL_COL_B} 配对使用。</b>{@link #drawPetal} 的每一段四边形
     * 需要<b>同时</b>持有根侧与尖侧两个不同的颜色，一个缓冲不够——
     * 共用会让整片花瓣退化成纯色（详见类注释「花瓣为什么必须用两个缓冲」）。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] PETAL_COL_A = new float[VisualColor.RGB];

    /**
     * v7：花瓣脊线滚动双缓冲之二（⚠ 同上，专供 {@link #drawPetal} 与 {@link #PETAL_COL_A} 配对）。
     */
    private static final float[] PETAL_COL_B = new float[VisualColor.RGB];

    /**
     * 花瓣脊线的<b>全细节</b>细分段数，同时也是 {@link #PETAL_HOR} / {@link #PETAL_VER}
     * 两个复用缓冲的定长依据。
     * <p><b>⚠ 若要调大此值，必须同步意识到那两个缓冲会跟着变长</b>；
     * {@link #drawPetal} 内有防御性钳制，超出时会静默降到本值而不是越界崩溃。</p>
     */
    private static final int PETAL_SEGMENTS = 8;

    /**
     * v7：花瓣脊线的水平位移复用缓冲（⚠ 仅供 {@link #drawPetal} 内部使用）。
     * <p>
     * 旧实现在每片花瓣里 {@code new double[seg + 1]} 两次。单朵花 32 瓣即
     * <b>64 个临时数组 / 帧</b>——数量虽比颜色数组少一个数量级，但每个都更大（9 个 double = 72 字节），
     * 总字节数反而相当。{@link #drawPetal} 不可重入（同一线程内不会嵌套调用自己），
     * 故提为静态定长缓冲复用，分配归零。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final double[] PETAL_HOR = new double[PETAL_SEGMENTS + 1];

    /**
     * v7：花瓣脊线的竖直位移复用缓冲（⚠ 同上，与 {@link #PETAL_HOR} 配对）。
     */
    private static final double[] PETAL_VER = new double[PETAL_SEGMENTS + 1];

    /**
     * v7：一次性混色的复用缓冲（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * 用于花心白热球、猩红炸裂核、癫火蓄能核与冲击强闪这类
     * 「算一个色 → 立刻画完 → 不再用」的场景，任一时刻只有一个动态色存活，故一个缓冲即可。
     * </p>
     * <p>
     * <b>与花瓣缓冲严格分开</b>：{@link #drawAeoniaFlower} 在画完花瓣之后才用本缓冲画花心，
     * 二者时序上不重叠；但分开命名可以杜绝「将来有人把 SCRATCH 传进 drawPetalLayer」这类误用
     * （那会在花瓣循环跑到一半时把配色改掉）。
     * </p>
     * <p>
     * <b>v9 说明：</b>新增的两套演出（满月月华 / 神圣净化）<b>不使用本缓冲</b>——
     * 它们的六个配色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值，
     * 因此全部直接引用 {@code C_} 只读常量，零动态色。
     * </p>
     */
    private static final float[] SCRATCH = new float[VisualColor.RGB];

    // ===== 龙雷红色闪电几何参数 =====
    /** 闪电柱总高度（格，自落地点向上延伸） */
    private static final double LIGHTNING_HEIGHT = 28.0;
    /** 主干段数 */
    private static final int LIGHTNING_SEGMENTS = 18;
    /** 每段水平蜿蜒幅度（格，越大电柱越曲折） */
    private static final double LIGHTNING_WANDER = 0.65;
    /** 主干水平最大偏移（格，限制电柱不漂离落地点太远） */
    private static final double LIGHTNING_MAX_OFFSET = 3.0;
    /** 电柱核心半宽（格，白热炽核） */
    private static final double LIGHTNING_CORE_HALF = 0.16;
    /** 电柱中层辉光半宽（格，红色主体，包裹核心） */
    private static final double LIGHTNING_GLOW_HALF = 0.52;
    /** 电柱外层光晕半宽（格，浓红体量光晕，最宽最淡） */
    private static final double LIGHTNING_HALO_HALF = 1.05;
    /** 分叉数量 */
    private static final int LIGHTNING_BRANCHES = 6;
    /** 每条分叉段数 */
    private static final int LIGHTNING_BRANCH_SEGMENTS = 5;
    /** 分叉每段步长（格） */
    private static final double LIGHTNING_BRANCH_STEP = 1.7;

    // ===== v9 满月月华几何参数 =====
    /** 月轮悬浮高度（格，自脚底算起）：约在玩家头顶上方 1.5 格 */
    private static final double MOON_DISC_HEIGHT = 3.4;
    /** 月轮半径系数（× 特效半径） */
    private static final double MOON_DISC_RADIUS_FACTOR = 0.48;
    /** 月轮盘面 / 外环分段数 */
    private static final int MOON_DISC_SEGMENTS = 32;
    /** 月轮外缘刻度数量 */
    private static final int MOON_TICK_COUNT = 16;
    /** 月华柱高度（格）：自月轮下沿延伸到地面 */
    private static final double MOON_COLUMN_HEIGHT = 3.4;
    /** 月华柱竖直分段数（用于沿高度做透明度渐变） */
    private static final int MOON_COLUMN_SEGMENTS = 8;
    /** 月华柱底部半宽（格） */
    private static final double MOON_COLUMN_HALF = 0.62;
    /** 回春环数量（同时存在的收拢波） */
    private static final int MOON_RING_COUNT = 3;
    /** 月尘数量（自地面向上飘的短光丝） */
    private static final int MOON_DUST_COUNT = 14;
    /** 月尘上升高度（格） */
    private static final double MOON_DUST_RISE = 3.0;
    /** 月尘短光丝长度（格） */
    private static final double MOON_DUST_LENGTH = 0.32;
    /** 月尘半宽（格） */
    private static final double MOON_DUST_HALF = 0.035;

    // ===== v10 满月月华：时间轴与月轮外观（新增）=====


    /** 月轮展开时长（秒） */
    private static final float MOON_APPEAR_SECONDS = 0.9f;
    /** 收尾整体淡出时长（秒） */
    private static final float MOON_FADE_SECONDS = 1.8f;
    /** 起手白热强闪时长（秒） */
    private static final float MOON_FLASH_SECONDS = 0.5f;
    /** 月华柱开始降落的时刻（秒） */
    private static final float MOON_COLUMN_DROP_DELAY = 0.15f;
    /** 月华柱降落耗时（秒） */
    private static final float MOON_COLUMN_DROP_SECONDS = 0.7f;
    /** 月华柱开始收细的时刻（秒） */
    private static final float MOON_COLUMN_FADE_START = 1.5f;
    /** 月华柱收细完成的时刻（秒） */
    private static final float MOON_COLUMN_FADE_END = 3.5f;
    /**
     * 月华柱收细后保留的强度。
     * <p>不做成 0 是因为「光柱完全消失」会让人以为效果结束了；
     * 保留三成既维持「月光还笼罩着我」的信息，又不至于 20 秒一直糊住视野。</p>
     */
    private static final float MOON_COLUMN_MIN_STRENGTH = 0.3f;
    /** 月轮自转速度（弧度/秒）——绝对速度，与总时长无关 */
    private static final float MOON_DISC_ROT_SPEED = 0.5f;
    /** 回春环收拢频率（圈/秒）：1 圈/秒，与「每秒回一次血」的节奏对齐 */
    private static final float MOON_RING_PER_SECOND = 1.0f;
    /** 月尘上升频率（次/秒） */
    private static final float MOON_DUST_PER_SECOND = 0.4f;

    /** 月轮球体的径向环数（明暗过渡的平滑度，是球体的主要顶点杠杆） */
    private static final int MOON_SPHERE_RINGS = 6;
    /** 月轮球体的最少径向环数：3 环仍能读出明暗渐变，再低会出现明显色带 */
    private static final int MOON_SPHERE_RINGS_MIN = 3;
    /**
     * 月轮光源方向的 right 分量。
     * <p>本演出画的是<b>满</b>月，故光源接近正照（{@link #MOON_LIGHT_W} 接近 1），
     * 只留一点点侧偏——太正会让球退化成平盘，稍偏反而更能读出立体感。</p>
     */
    private static final float MOON_LIGHT_U = -0.22f;
    /** 月轮光源方向的 up 分量（略偏上，符合「月光自上而来」的直觉） */
    private static final float MOON_LIGHT_V = 0.26f;
    /** 月轮光源方向的「朝向相机」分量：接近 1 即满月（详见 {@link #bbSphere} 注释） */
    private static final float MOON_LIGHT_W = 0.94f;
    /** 月轮环境光下限：0 会让暗侧纯黑、像挖了个洞，留一点更像被地球反照的月面 */
    private static final float MOON_AMBIENT = 0.42f;

    /** 月海不透明度 */
    private static final float MARIA_ALPHA = 0.30f;


    /**
     * 月海斑块（盘面上稍暗的圆斑），每 3 个 float 一组：
     * {@code [平面内 right 分量, 平面内 up 分量, 半径]}，三者均为<b>相对月轮半径的比例</b>。
     * <p>
     * <b>这是「像月亮」最关键的一笔。</b>纯亮圆盘读起来是「法阵的光球」，
     * 叠上几块深色斑块之后才会被认成月亮——真实月面的月海正是这种不规则的暗色区域。
     * 坐标是手调的，刻意做成大小不一、分布不对称（对称会显得像图案而非天体）。
     * </p>
     * <p>
     * <b>⚠ 只读常量数组</b>，绘制时只按下标读取、绝不写入。
     * </p>
     */
    private static final float[] MOON_MARIA = {
            -0.25f, 0.30f, 0.30f,
            0.28f, 0.12f, 0.22f,
            -0.05f, -0.28f, 0.26f,
            0.35f, -0.35f, 0.16f,
            -0.42f, -0.08f, 0.14f
    };

    // ===== v9 神圣净化几何参数 =====
    /** 十字光刃中心高度（格，自脚底算起）：约在人形躯干处 */
    private static final double SACRED_CROSS_HEIGHT = 1.05;
    /** 十字光刃臂长系数（× 特效半径） */
    private static final double SACRED_CROSS_LENGTH_FACTOR = 0.95;
    /** 十字光刃半宽（格） */
    private static final double SACRED_CROSS_HALF = 0.075;
    /** 升天光尘数量 */
    private static final int SACRED_MOTE_COUNT = 10;
    /** 升天光尘上升高度（格） */
    private static final double SACRED_MOTE_RISE = 2.4;
    /** 升天光尘短光丝长度（格） */
    private static final double SACRED_MOTE_LENGTH = 0.28;
    /** 升天光尘半宽（格） */
    private static final double SACRED_MOTE_HALF = 0.03;

    /**
     * v7：闪电主干节点坐标的复用缓冲 X（⚠ 仅供 {@link #drawRedLightning} 内部使用）。
     * <p>
     * 旧实现每道闪电 {@code new double[segs + 1]} 三次。而红闪恰恰是<b>最容易跑满
     * {@code AoeEffectManager.MAX_ACTIVE}(40) 的类型</b>——古龙雷击对分散目标降雷时，
     * 同位置合并只覆盖 2.5 格，目标散开就会各成一道。40 道 × 3 个数组 ×
     * {@value #LIGHTNING_SEGMENTS} + 1 个 double，<b>而这正好发生在大量实体死亡、
     * 客户端最卡的那一瞬</b>。
     * </p>
     * <p>
     * 节点数恒为 {@value #LIGHTNING_SEGMENTS} + 1（v6 的段数削减只影响<b>绘制</b>时的连线步长，
     * 节点始终按原段数完整生成以保持形状骨架恒定），故定长缓冲尺寸精确、无需钳制。
     * {@link #drawRedLightning} 不可重入，复用安全。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final double[] LIGHTNING_NX = new double[LIGHTNING_SEGMENTS + 1];

    /** v7：闪电主干节点坐标的复用缓冲 Y（⚠ 同上）。 */
    private static final double[] LIGHTNING_NY = new double[LIGHTNING_SEGMENTS + 1];

    /** v7：闪电主干节点坐标的复用缓冲 Z（⚠ 同上）。 */
    private static final double[] LIGHTNING_NZ = new double[LIGHTNING_SEGMENTS + 1];

    private AoeEffectRenderer() {
    }

    /**
     * 渲染回调：遍历全部存活特效，按类型分发到对应自绘演出。
     * <p>
     * v5：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理；本方法只负责裁剪与写顶点。
     * v6：新增细节系数计算（{@link #detailFor}）与同屏实例登记（{@link VisualLod#countInstance()}）。
     * </p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        List<AoeEffectManager.AoeEffect> list = AoeEffectManager.getActive();
        if (list.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
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

        long now = System.currentTimeMillis();
        double cullSqr = CULL * CULL;

        boolean anyVisible = false;
        for (AoeEffectManager.AoeEffect fx : list) {
            double dx = fx.x - cam.x;
            double dy = fx.y - cam.y;
            double dz = fx.z - cam.z;
            if (dx * dx + dy * dy + dz * dz > cullSqr) {
                continue;
            }
            float progress = (now - fx.birthMs) / (float) fx.durationMs;
            if (progress > 0.0005f && progress < 1f) {
                anyVisible = true;
                break;
            }
        }

        if (!anyVisible) {
            return;
        }

        Matrix4f matrix = VisualBatch.matrix();
        float partial = VisualBatch.partialTick();

        for (AoeEffectManager.AoeEffect fx : list) {
            double fxX = fx.x;
            double fxY = fx.y;
            double fxZ = fx.z;
            if (fx.entityId >= 0) {
                Entity bound = mc.level.getEntity(fx.entityId);
                if (bound != null && bound.isAlive()) {
                    Vec3 pos = bound.getPosition(partial);
                    fxX = pos.x;
                    fxY = pos.y;
                    fxZ = pos.z;
                    fx.x = fxX;
                    fx.y = fxY;
                    fx.z = fxZ;
                }
            }
            double dx = fxX - cam.x;
            double dy = fxY - cam.y;
            double dz = fxZ - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > cullSqr) {
                continue;
            }
            float progress = AoeEffectManager.progressFor(fx, now);
            double rx = fxX - cam.x;
            double ry = fxY - cam.y + Y_OFFSET;
            double rz = fxZ - cam.z;

            // ⭐ v6：按「到演出视觉边界的距离」取细节系数（不能按到中心的距离，详见类注释）。
            // 演出范围内 detail 恒为 1.0，与优化前逐像素一致
            float detail = detailFor(fx.type, distSqr, fx.radius);
            // ⭐ v6：登记同屏实例。本渲染器此前完全不登记，导致全局 crowdFactor 被系统性低估，
            // 已接入 LOD 的黄金树祝福 / 腐败女神在团战时削减不足
            VisualLod.countInstance();

            // fx.seed 为该特效创建时确定的固定外形种子（仅红色闪电用到），整段生命周期恒定，
            // 即便红闪被「同位置合并」反复续命也不跳变外形。
            // v11：把本次演出的实际总时长（秒）一并传下去。满月月华用它把 progress
            // 换算回绝对秒数，使 10 秒版与 20 秒版的动画速度完全一致
            float totalSeconds = fx.durationMs / 1000f;
            dispatch(builder, matrix, fx.type, rx, ry, rz, fx.radius, progress, fx.seed, totalSeconds, detail);
        }
    }

    /**
     * 计算某特效本帧的细节系数。
     * <p>
     * 用<b>到演出视觉边界的近似距离</b>而非到中心的距离——本渲染器的演出尺寸远大于实体
     * （立体花视觉直径约 28 格、闪电柱高 28 格），按中心距离会导致「站在演出范围内却被判定为远」，
     * 详见类注释「细节系数必须按到特效边界的距离取」小节。
     * </p>
     *
     * @param type    特效类型
     * @param distSqr 相机到特效中心的平方距离
     * @param radius  特效半径（已含 {@code AoeEffectManager.scaleFor} 的放大）
     * @return 细节系数，范围 [{@link VisualLod#MIN_DETAIL} × {@link VisualLod#CROWD_MIN}, 1.0]
     */
    private static float detailFor(int type, double distSqr, double radius) {
        double visualRadius = visualRadiusFor(type, radius);
        double edge = Math.max(0.0, Math.sqrt(distSqr) - visualRadius);
        return VisualLod.detail(edge * edge);
    }

    /**
     * 各类型演出的「视觉边界半径」（格）。
     * <p>取该演出中<b>最占视野</b>的那一部分的半径，而非其名义半径：
     * 爆发类演出的冲击环会显著超出主体半径，闪电的主要体量则是竖直电柱。</p>
     *
     * @param type   特效类型
     * @param radius 特效名义半径
     * @return 视觉边界半径（格）
     */
    private static double visualRadiusFor(int type, double radius) {
        switch (type) {
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
                return radius * SCARLET_BLOOM_VISUAL_SCALE;
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                return radius * FRENZIED_FLAME_VISUAL_SCALE;
            case AoeEffectPacket.TYPE_RED_LIGHTNING:
                // 闪电的主要视觉体量是 28 格高的竖直电柱，而非落地冲击环
                return Math.max(radius, LIGHTNING_HEIGHT * LIGHTNING_VISUAL_RADIUS_FACTOR);
            case AoeEffectPacket.TYPE_MOON_BLESSING:
                // ⭐ v9：月轮悬浮在 3.4 格高处、月华柱贯穿其下，主体视觉在头顶上方而非脚下平面
                return Math.max(radius, MOON_DISC_HEIGHT * MOON_VISUAL_RADIUS_FACTOR);
            default:
                return radius;
        }
    }

    /**
     * 按类型分发到具体演出。
     *
     * @param seed   该特效的固定外形种子（创建时确定、生命周期不变，当前仅红色闪电使用）
     * @param detail 本帧细节系数（v6 新增）
     */
    private static void dispatch(BufferBuilder b, Matrix4f m, int type,
                                 double cx, double cy, double cz, double radius, float p,
                                 long seed, float totalSeconds, float detail) {
        switch (type) {
            case AoeEffectPacket.TYPE_CAUSALITY -> drawCausality(b, m, cx, cy, cz, radius, p, detail);
            case AoeEffectPacket.TYPE_FROST_QUAKE -> drawFrostQuake(b, m, cx, cy, cz, radius, p, detail);
            case AoeEffectPacket.TYPE_REPULSION -> drawRepulsion(b, m, cx, cy, cz, radius, p, detail);
            case AoeEffectPacket.TYPE_SCARLET_BLOOM -> drawScarletBloom(b, m, cx, cy, cz, radius, p, detail);
            case AoeEffectPacket.TYPE_FRENZIED_FLAME -> drawFrenziedFlame(b, m, cx, cy, cz, radius, p, detail);
            case AoeEffectPacket.TYPE_RED_LIGHTNING -> drawRedLightning(b, m, cx, cy, cz, radius, p, seed, detail);
            case AoeEffectPacket.TYPE_MOON_BLESSING -> drawMoonBlessing(b, m, cx, cy, cz, radius, p, totalSeconds, detail);
            case AoeEffectPacket.TYPE_SACRED_PURGE -> drawSacredPurge(b, m, cx, cy, cz, radius, p, detail);
            default -> drawGeneric(b, m, cx, cy, cz, radius, p, detail);
        }
    }

    // ============================== 各附魔专属演出 ==============================

    /**
     * 因果律：地面金色六芒星法阵（旋转）+ 内六边形 + 外发光环（脉冲扩张）+ 紫色「因果之线」放射抽射
     * + 六芒星顶点火花。整体淡入淡出。
     * <p>
     * v6 削减：{@code glowRing} 分段数缩放（主要杠杆）；「因果之线」8 条按步长抽取
     * （均布角度，截断会让线只朝一侧喷）；6 颗顶点火花为纯装饰层，低细节时整层跳过。
     * <b>六芒星与内六边形完全不削</b>——共 72 顶点却承担全部辨识度。
     * </p>
     * <p>v7：主题色改用只读常量，本方法零分配。</p>
     */
    private static void drawCausality(BufferBuilder b, Matrix4f m,
                                      double cx, double cy, double cz, double radius, float p, float detail) {
        float fade = fadeInOut(p, 0.15f, 0.80f);
        if (fade <= 0f) {
            return;
        }
        float rot = p * 1.2f * TAU;
        double expand = easeOutCubic(clamp01(p / 0.30f));
        double rOuter = radius * (0.85 + 0.15 * expand);
        final float[] gold = C_CAUSALITY_GOLD;
        final float[] violet = C_CAUSALITY_VIOLET;
        double hw = lineHalf(radius);
        int segs = segmentsFor(rOuter, detail);

        glowRing(b, m, cx, cz, cy, rOuter, segs, gold, 0.85f * fade, 0.32f * fade, 0.07, 0.50);
        starPolygon(b, m, cx, cz, cy, radius * 0.62, 6, 2, rot, hw, gold, 0.70f * fade);
        polygonRing(b, m, cx, cz, cy, radius * 0.40, 6, rot, hw, gold, 0.55f * fade);
        float lash = clamp01((p - 0.30f) / 0.20f);
        if (lash > 0f) {
            double rl = radius * 0.20 + (radius - radius * 0.20) * easeOutCubic(lash);
            rays(b, m, cx, cz, cy, radius * 0.20, rl, 8, rot * 0.5f, hw,
                    violet, 0.75f * fade * lash, 0.05f * fade * lash, detail);
        }
        // 顶点火花：纯装饰层，远处看不出，低细节时整层跳过
        if (VisualLod.keepLayer(detail, SPARK_KEEP_THRESHOLD)) {
            for (int i = 0; i < 6; i++) {
                double ang = rot + TAU * i / 6.0;
                double px = cx + radius * 0.62 * Math.cos(ang);
                double pz = cz + radius * 0.62 * Math.sin(ang);
                float tw = 0.5f + 0.5f * (float) Math.sin(p * 18.0 + i);
                spark(b, m, px, pz, cy, (float) (radius * 0.035 + 0.07), gold, 0.8f * fade * tw);
            }
        }
    }

    /**
     * 冻结地震：12 条放射地裂纹由内向外生长 + 2 道霜环扩张外滚 + 中心冰花（八角星 + 六边形反向旋转）
     * + 起手中心闪光。
     * <p>
     * v6 削减：两组地裂纹（12 条 × 2）按步长抽取，且<b>两组共用同一 detail</b>，
     * 保证内层白芯与外层冰色始终重合在同一批角度上（否则会看到「有芯没壳」的裂纹）；
     * 双霜环分段数缩放。冰花（八角星 + 六边形）与中心闪光不削。
     * </p>
     * <p>v7：主题色改用只读常量，本方法零分配。</p>
     */
    private static void drawFrostQuake(BufferBuilder b, Matrix4f m,
                                       double cx, double cy, double cz, double radius, float p, float detail) {
        float fade = fadeInOut(p, 0.12f, 0.70f);
        if (fade <= 0f) {
            return;
        }
        final float[] ice = C_FROST_ICE;
        final float[] white = C_FROST_WHITE;
        double hw = lineHalf(radius);

        double grow = easeOutCubic(clamp01(p / 0.45f));
        double crackLen = radius * grow;
        // 两组地裂共用同一 detail → 抽取到相同的角度子集，内芯与外壳始终重合
        rays(b, m, cx, cz, cy, radius * 0.10, crackLen, 12, 0f, hw, ice,
                0.80f * fade, 0.08f * fade, detail);
        rays(b, m, cx, cz, cy, radius * 0.10, crackLen * 0.70, 12, 0f, hw * 0.7,
                white, 0.90f * fade, 0.10f * fade, detail);

        for (int i = 0; i < 2; i++) {
            float rp = clamp01((p - i * 0.12f) / 0.60f);
            if (rp <= 0f || rp >= 1f) {
                continue;
            }
            double rr = radius * easeOutCubic(rp);
            float a = (1f - rp) * fade;
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), ice, 0.50f * a, 0.20f * a, 0.05, 0.35);
        }

        float rot = p * 0.6f * TAU;
        starPolygon(b, m, cx, cz, cy, radius * 0.18, 8, 3, rot, hw, white, 0.70f * fade);
        polygonRing(b, m, cx, cz, cy, radius * 0.12, 6, -rot, hw, ice, 0.60f * fade);

        if (p < 0.20f) {
            spark(b, m, cx, cz, cy, (float) (radius * 0.10 + 0.20), white, (0.20f - p) / 0.20f * fade);
        }
    }

    /**
     * 排斥：双发光环从中心猛烈外推扩张并快速淡出（短促的「砰」一下冲击波）。
     * <p>v6 削减：两个环的分段数缩放。本演出只有两个环，是最简单的一套。</p>
     * <p>v7：主题色改用只读常量，本方法零分配。</p>
     */
    private static void drawRepulsion(BufferBuilder b, Matrix4f m,
                                      double cx, double cy, double cz, double radius, float p, float detail) {
        float fade = 1f - smoothstep(0.40f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        final float[] w = C_REPULSE_WHITE;
        double expand = easeOutCubic(clamp01(p / 0.70f));
        double r1 = radius * expand;
        double r2 = radius * 0.6 * expand;
        glowRing(b, m, cx, cz, cy, r1, segmentsFor(r1, detail), w, 0.85f * fade, 0.35f * fade, 0.06, 0.45);
        glowRing(b, m, cx, cz, cy, r2, segmentsFor(r2, detail), w, 0.50f * fade, 0.20f * fade, 0.05, 0.30);
    }

    // ============================== v11 满月月华 ==============================

    /**
     * 满月月华：头顶浮现<b>球形月轮</b> → 一道月华柱自上而下笼罩全身 →
     * 脚下每秒一圈<b>向内收拢</b>的回春环 → 月尘持续上升。
     * <p>
     * 用于 {@code EnchantmentFullMoon} 的濒死复活：阻止死亡、残血保命、随后持续回血。
     * <b>演出时长由服务端按实际回血时间指定</b>（不带暗月 10 秒 / 带暗月 20 秒），
     * 与机制严格对齐。
     * </p>
     *
     * <h4>v11 修复一：月轮从「圆」变成「球」</h4>
     * <p>
     * v10 已经把月轮改成 billboard 面向相机（解决了「平视看是一条线」），
     * 但它仍然是个<b>平面圆盘</b>——中心到边缘只有单调的 alpha 渐变，
     * 读起来是「一枚发光的硬币」而不是一颗悬着的天体。
     * </p>
     * <p>
     * 现在改用 {@link #bbSphere} 画：把圆盘拆成同心环带，<b>逐顶点计算球面法线并做兰伯特着色</b>。
     * 平面内归一化坐标 {@code (u, v)} 对应的球面法线是
     * </p>
     * <pre>
     * n = (u, v, sqrt(1 - u² - v²))
     * </pre>
     * <p>
     * 其中第三个分量朝向相机。与光源方向点乘即得该点亮度，于是在<b>没有任何光照管线</b>的
     * {@code POSITION_COLOR} 纯顶点绘制下，也能得到真正的球体明暗过渡与边缘暗化。
     * </p>
     *
     * <h4>v11 修复二：月相不再是画上去的，而是光照的自然结果</h4>
     * <p>
     * 有了球面着色之后，<b>月相就是光源方向</b>——这正是现实中月相的成因，
     * 因此暗月那边（{@code DarkMoonRenderer}）单独绘制明暗界线月牙带的做法也一并废弃了，
     * 两处统一由光源方向表达月相。
     * </p>
     * <ul>
     *     <li>光源朝向相机 {@code (0, 0, 1)} → <b>满月</b>（整个盘面亮）；</li>
     *     <li>光源偏侧 → 明暗界线自然出现在球面上，越偏越接近新月。</li>
     * </ul>
     * <p>
     * 本演出画的是<b>满</b>月，故光源取 {@link #MOON_LIGHT_W} 接近 1 的近正照方向，
     * 只留一点点偏移让球体不至于像个纯平的亮盘——太正会失去立体感，
     * 稍偏一点点反而更能读出「这是个球」。
     * </p>
     *
     * <h4>v11 修复三：时长跟随机制，不再写死</h4>
     * <p>
     * v10 写死 20 秒（覆盖带暗月的最长情况），于是不带暗月时特效比回血多播 10 秒。
     * 现在服务端把实际时长随包发下来，本方法用
     * </p>
     * <pre>
     * float age   = p * totalSeconds;      // 已播放秒数
     * </pre>
     * <p>
     * 换算出绝对时间来驱动全部动画。由于 {@code totalSeconds} 取自
     * {@code AoeEffect.durationMs}（服务端指定），<b>渲染器不再需要维护任何时长常量</b>，
     * v10 那个「两处常量必须手工同步」的隐患也随之消失。
     * </p>
     * <p>
     * 全部动画速度以「弧度/秒」「圈/秒」表达，因此 10 秒版和 20 秒版的
     * 月轮转速、回春环频率完全一致，只是持续段长短不同。
     * </p>
     *
     * <h4>时间轴（秒）</h4>
     * <ul>
     *     <li>0 ~ 0.9：月轮浮现（半径 0 → 满、亮度淡入）；</li>
     *     <li>0 ~ 0.5：中心白热强闪，对应「阻止死亡」那一瞬的顿挫感；</li>
     *     <li>0.15 ~ 0.85：月华柱底端自月轮高度降落到地面；</li>
     *     <li>1.5 ~ 3.5：光柱收细变淡（保留但不再挡视野）；</li>
     *     <li>0.3 ~ 结束：回春环每秒一圈向内收拢，与「每秒回一次血」的节奏对齐；</li>
     *     <li>0.5 ~ 结束：月尘持续上升；</li>
     *     <li>最后 1.8 秒：整体淡出。</li>
     * </ul>
     * <p>
     * <b>回春环为什么向内收拢：</b>本模组其余全部环形演出都是向外扩散，
     * 反向收拢直接读作「能量在往身上汇聚」，与回血语义一致、且同屏叠加时一眼可辨。
     * </p>
     * <p>
     * <b>削减：</b>球体的环数与分段数、回春环分段数、月华柱竖直分段数均按细节系数缩放；
     * 回春环圈数缩减；月尘与月海<b>按步长抽取</b>（角度均布，截断会让元素只朝一侧）
     * 且可整层跳过。<b>球体本身无论细节多低都完整绘制</b>——它是本演出的唯一标志。
     * </p>
     * <p>v11：三个配色全是编译期常量，直接引用只读常量，本方法零分配。</p>
     *
     * @param p            归一化播放进度
     * @param totalSeconds 本次演出的总时长（秒，服务端指定）
     * @param detail       本帧细节系数
     */
    private static void drawMoonBlessing(BufferBuilder b, Matrix4f m,
                                         double cx, double cy, double cz, double radius,
                                         float p, float totalSeconds, float detail) {
        // ⭐ v11：绝对播放秒数。totalSeconds 来自服务端指定的实际回血时长，
        // 因此 10 秒版与 20 秒版的动画速度完全一致（详见方法注释）
        float age = p * totalSeconds;

        // 收尾淡出：最后 MOON_FADE_SECONDS 秒
        float fade = 1f - smoothstep(totalSeconds - MOON_FADE_SECONDS, totalSeconds, age);
        if (fade <= 0f) {
            return;
        }

        final float[] core = C_MOON_CORE;
        final float[] glow = C_MOON_GLOW;
        final float[] deep = C_MOON_DEEP;

        // 月轮浮现（前 0.9 秒展开，半径与亮度同步淡入）
        float appear = (float) easeOutCubic(clamp01(age / MOON_APPEAR_SECONDS));
        // 缓慢呼吸，避免长演出里月轮像贴图一样死板
        float breath = 0.94f + 0.06f * Mth.sin(age * 1.8f);

        double discY = cy + MOON_DISC_HEIGHT;
        double discRadius = radius * MOON_DISC_RADIUS_FACTOR * appear * breath;
        // 自转：弧度/秒，与总时长无关（只作用于外缘刻度）
        float rot = age * MOON_DISC_ROT_SPEED;

        // billboard 平面基（面向相机）
        float rgX = VisualBatch.rightX();
        float rgY = VisualBatch.rightY();
        float rgZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        int discSegs = VisualLod.scaleSegments(MOON_DISC_SEGMENTS, MOON_DISC_SEGMENTS_MIN, detail);
        int discRings = VisualLod.scaleSegments(MOON_SPHERE_RINGS, MOON_SPHERE_RINGS_MIN, detail);

        // ===== 地面月光池：铺底，让整片区域读作「被月光照着」 =====
        band(b, m, cx, cz, cy, 0.0, radius * 1.05, segmentsFor(radius, detail),
                deep[0], deep[1], deep[2], 0.05f * fade * appear, 0.13f * fade * appear);

        // ===== 月华柱：底端自上而下降落到地面；中段收细，避免长时间糊住视野 =====
        float drop = (float) easeOutCubic(clamp01((age - MOON_COLUMN_DROP_DELAY) / MOON_COLUMN_DROP_SECONDS));
        float columnStrength = 1f - (1f - MOON_COLUMN_MIN_STRENGTH)
                * smoothstep(MOON_COLUMN_FADE_START, MOON_COLUMN_FADE_END, age);
        double columnBottom = discY - MOON_COLUMN_HEIGHT * drop;
        if (discY > columnBottom + 1.0e-4 && columnStrength > 0.02f) {
            int columnSegs = VisualLod.scaleSegments(
                    MOON_COLUMN_SEGMENTS, MOON_COLUMN_SEGMENTS_MIN, detail);
            double segLen = (discY - columnBottom) / columnSegs;
            // 柱身半宽也随强度收细——只调 alpha 的话远看仍是一根粗柱子
            double halfMul = 0.35 + 0.65 * columnStrength;
            for (int i = 0; i < columnSegs; i++) {
                double y0 = columnBottom + segLen * i;
                double y1 = y0 + segLen;
                float u0 = (float) i / columnSegs;
                float u1 = (float) (i + 1) / columnSegs;
                // 上端（贴近月轮）亮、下端（贴近地面）柔——光是从月轮流下来的
                float a0 = (0.35f + 0.65f * u0) * fade * columnStrength;
                float a1 = (0.35f + 0.65f * u1) * fade * columnStrength;
                lightningSegment(b, m, cx, y0, cz, cx, y1, cz,
                        MOON_COLUMN_HALF * 1.9 * halfMul, deep, 0.16f * a0, 0.16f * a1);
                lightningSegment(b, m, cx, y0, cz, cx, y1, cz,
                        MOON_COLUMN_HALF * halfMul, glow, 0.42f * a0, 0.42f * a1);
                lightningSegment(b, m, cx, y0, cz, cx, y1, cz,
                        MOON_COLUMN_HALF * 0.3 * halfMul, core, 0.85f * a0, 0.85f * a1);
            }
        }

        // ===== 月轮：球体（逐顶点兰伯特着色）+ 月海 + 月晕 + 外缘刻度 =====
        if (discRadius > 0.05) {
            float discAlpha = fade * appear;

            // ⭐ v11 核心：球面着色。月相由光源方向决定，此处取近正照 = 满月
            bbSphere(b, m, cx, discY, cz, discRadius, discRings, discSegs,
                    rgX, rgY, rgZ, upX, upY, upZ, core,
                    MOON_LIGHT_U, MOON_LIGHT_V, MOON_LIGHT_W,
                    MOON_AMBIENT, 0.92f * discAlpha);

            // ⭐ 月海：球面上几块稍暗的斑块。「像月亮」最关键的一笔——
            // 纯亮球读起来是「光球」，有了不规则暗斑才认得出是天体。
            // 月海用同一套光照着色，因此暗面那侧的月海会自然融进阴影里
            int mariaCount = MOON_MARIA.length / 3;
            int mariaDrawn = VisualLod.scale(mariaCount, detail);
            int mariaStep = Math.max(1, mariaCount / mariaDrawn);
            int mariaSegs = Math.max(6, discSegs / 3);
            for (int i = 0; i < mariaCount; i += mariaStep) {
                float mu = MOON_MARIA[i * 3];
                float mv = MOON_MARIA[i * 3 + 1];
                float mr = MOON_MARIA[i * 3 + 2];
                // 该斑块所在球面点的亮度——与球体本身用同一光源，融合自然
                float shade = sphereShade(mu, mv, MOON_LIGHT_U, MOON_LIGHT_V, MOON_LIGHT_W, MOON_AMBIENT);
                // 球面透视：越靠边缘的斑块在视觉上越窄（沿径向压缩）
                float edge = Mth.sqrt(Math.max(0f, 1f - mu * mu - mv * mv));
                float mx = (float) (cx + rgX * (mu * discRadius) + upX * (mv * discRadius));
                float my = (float) (discY + rgY * (mu * discRadius) + upY * (mv * discRadius));
                float mz = (float) (cz + rgZ * (mu * discRadius) + upZ * (mv * discRadius));
                bbDisc(b, m, mx, my, mz, mr * discRadius * (0.55 + 0.45 * edge), mariaSegs,
                        rgX, rgY, rgZ, upX, upY, upZ,
                        deep, MARIA_ALPHA * shade * discAlpha, 0f);
            }

            // 月晕：球体外圈一层向外渐隐的柔光（不是硬轮廓线——那会破坏球感）
            bbRing(b, m, cx, discY, cz, discRadius, discRadius * 1.22, discSegs, 0f,
                    rgX, rgY, rgZ, upX, upY, upZ, glow, 0.34f * discAlpha, 0f);

            // 外缘刻度：均布角度，按步长抽取（截断会让刻度只剩一段圆弧）
            if (VisualLod.keepLayer(detail, MOON_TICK_KEEP_THRESHOLD)) {
                int drawn = VisualLod.scale(MOON_TICK_COUNT, detail);
                int step = Math.max(1, MOON_TICK_COUNT / drawn);
                double rStart = discRadius * 1.26;
                double rEnd = rStart + discRadius * 0.20;
                double tickHalf = discRadius * 0.03;
                for (int i = 0; i < MOON_TICK_COUNT; i += step) {
                    // 角度基准用原始 MOON_TICK_COUNT，保证保留刻度的方位与全细节时一致
                    float a = rot + TAU * i / MOON_TICK_COUNT;
                    float ca = Mth.cos(a);
                    float sa = Mth.sin(a);
                    bbLine(b, m, cx, discY, cz, rgX, rgY, rgZ, upX, upY, upZ,
                            rStart * ca, rStart * sa, rEnd * ca, rEnd * sa,
                            tickHalf, glow, 0.5f * discAlpha, 0f);
                }
            }
        }

        // ===== 回春环：向内收拢，每秒一圈（与「每秒回一次血」的节奏对齐）=====
        int ringCount = VisualLod.scale(MOON_RING_COUNT, detail);
        for (int i = 0; i < ringCount; i++) {
            float phase = (float) i / ringCount;
            // 绝对时间驱动：MOON_RING_PER_SECOND 圈/秒，与总时长无关
            float t = frac(age * MOON_RING_PER_SECOND + phase);
            // ⭐ 收拢：半径从外向内递减（本模组其余环全是向外扩散，反向即读作「汇聚」）
            double rr = radius * (1.0 - easeOutCubic(t));
            if (rr <= 0.25) {
                continue;
            }
            float a = 0.55f * fade * smoothstep(0f, 0.12f, t) * (1f - smoothstep(0.82f, 1f, t));
            if (a <= 0.01f) {
                continue;
            }
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), glow,
                    a, a * 0.4f, 0.05, 0.30);
        }

        // ===== 月尘：自地面向上飘的短光丝（均布角度，必须按步长抽取）=====
        if (VisualLod.keepLayer(detail, MOON_DUST_KEEP_THRESHOLD)) {
            int drawn = VisualLod.scale(MOON_DUST_COUNT, detail);
            int step = Math.max(1, MOON_DUST_COUNT / drawn);
            for (int i = 0; i < MOON_DUST_COUNT; i += step) {
                // 角度基准用原始 MOON_DUST_COUNT，保证保留光丝的方位与全细节时一致
                double ang = TAU * i / MOON_DUST_COUNT + age * 0.12;
                // 黄金比小数铺半径，使各条光丝的落点错开而不聚成一圈
                double fr = (i * 0.6180339) - Math.floor(i * 0.6180339);
                double rr = radius * (0.25 + 0.6 * fr);
                double px = cx + rr * Math.cos(ang);
                double pz = cz + rr * Math.sin(ang);

                float t = frac(age * MOON_DUST_PER_SECOND + i * 0.137f);
                double y0 = cy + t * MOON_DUST_RISE;
                double y1 = y0 + MOON_DUST_LENGTH;
                float a = 0.75f * fade * smoothstep(0f, 0.12f, t) * (1f - smoothstep(0.65f, 1f, t));
                if (a <= 0.01f) {
                    continue;
                }
                float[] col = (t < 0.5f) ? glow : core;
                lightningSegment(b, m, px, y0, pz, px, y1, pz, MOON_DUST_HALF, col, a, 0f);
            }
        }

        // ===== 起手白热闪光：对应「阻止死亡」那一瞬的顿挫感 =====
        if (age < MOON_FLASH_SECONDS) {
            float flash = 1f - age / MOON_FLASH_SECONDS;
            spark(b, m, cx, cz, cy, (float) (radius * 0.35 + 0.3), core, 0.85f * flash);
            glowRing(b, m, cx, cz, cy, radius * 0.6, segmentsFor(radius, detail), core,
                    0.6f * flash, 0.3f * flash, 0.10, 0.45);
        }
    }

    // ==================== v11 球体基元 ====================

    /**
     * 面向相机的<b>着色球体</b>：用同心环带 + 逐顶点兰伯特光照，
     * 在没有任何光照管线的 {@code POSITION_COLOR} 纯顶点绘制下做出真正的球感。
     *
     * <h4>原理</h4>
     * <p>
     * 球体正对相机时，其轮廓是一个圆；圆内任意一点 {@code (u, v)}（归一化到单位圆）
     * 对应的球面法线为
     * </p>
     * <pre>
     * n = (u, v, sqrt(1 - u² - v²))
     * </pre>
     * <p>
     * 第三个分量朝向相机。把它与光源方向 {@code L} 点乘即得该点的兰伯特亮度。
     * 于是逐顶点写入不同的颜色，就能得到球体的明暗过渡与边缘暗化——
     * 这两样正是「圆盘」与「球」的分野。
     * </p>
     *
     * <h4>为什么这比「中心亮边缘暗的径向渐变」好</h4>
     * <p>
     * 径向渐变是<b>各向同性</b>的：亮斑永远在正中心，看起来像一枚发光的硬币。
     * 兰伯特着色的亮斑<b>偏向光源一侧</b>，明暗界线是一条椭圆弧而非同心圆，
     * 大脑会立刻把它读成三维物体。
     * </p>
     *
     * <h4>顺带解决月相</h4>
     * <p>
     * <b>月相就是光源方向</b>，不需要额外绘制暗面：
     * {@code L = (0,0,1)}（正照）即满月；L 越偏向侧面，明暗界线越往中间推，
     * 依次经过亏凸月、半月、蛾眉月。这与现实中月相的成因完全一致。
     * </p>
     *
     * @param radius   球体半径（格）
     * @param rings    径向环数（决定明暗过渡的平滑度，是主要顶点杠杆）
     * @param segments 每环的角度分段数
     * @param col      基色（只读；实际顶点色为基色 × 该点亮度）
     * @param lu       光源方向的 right 分量
     * @param lv       光源方向的 up 分量
     * @param lw       光源方向的「朝向相机」分量（1 = 正照 = 满月）
     * @param ambient  环境光下限（0 会让暗面纯黑、显得像挖了个洞；留一点更自然）
     * @param alpha    整体不透明度
     */
    private static void bbSphere(BufferBuilder b, Matrix4f m,
                                 double cx, double cy, double cz, double radius,
                                 int rings, int segments,
                                 float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                                 float[] col, float lu, float lv, float lw,
                                 float ambient, float alpha) {
        if (alpha <= 0.004f || radius <= 1.0e-4 || rings < 1 || segments < 3) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float cxf = (float) cx, cyf = (float) cy, czf = (float) cz;
        float rad = (float) radius;

        for (int i = 0; i < rings; i++) {
            float t0 = (float) i / rings;
            float t1 = (float) (i + 1) / rings;
            for (int j = 0; j < segments; j++) {
                float a0 = TAU * j / segments;
                float a1 = TAU * (j + 1) / segments;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

                // 四个角点的平面归一化坐标
                float u00 = t0 * c0, v00 = t0 * s0;
                float u01 = t0 * c1, v01 = t0 * s1;
                float u11 = t1 * c1, v11 = t1 * s1;
                float u10 = t1 * c0, v10 = t1 * s0;

                // 逐顶点兰伯特亮度
                float sh00 = sphereShade(u00, v00, lu, lv, lw, ambient);
                float sh01 = sphereShade(u01, v01, lu, lv, lw, ambient);
                float sh11 = sphereShade(u11, v11, lu, lv, lw, ambient);
                float sh10 = sphereShade(u10, v10, lu, lv, lw, ambient);

                // 平面坐标 → 世界坐标
                float x00 = cxf + rgX * (u00 * rad) + upX * (v00 * rad);
                float y00 = cyf + rgY * (u00 * rad) + upY * (v00 * rad);
                float z00 = czf + rgZ * (u00 * rad) + upZ * (v00 * rad);
                float x01 = cxf + rgX * (u01 * rad) + upX * (v01 * rad);
                float y01 = cyf + rgY * (u01 * rad) + upY * (v01 * rad);
                float z01 = czf + rgZ * (u01 * rad) + upZ * (v01 * rad);
                float x11 = cxf + rgX * (u11 * rad) + upX * (v11 * rad);
                float y11 = cyf + rgY * (u11 * rad) + upY * (v11 * rad);
                float z11 = czf + rgZ * (u11 * rad) + upZ * (v11 * rad);
                float x10 = cxf + rgX * (u10 * rad) + upX * (v10 * rad);
                float y10 = cyf + rgY * (u10 * rad) + upY * (v10 * rad);
                float z10 = czf + rgZ * (u10 * rad) + upZ * (v10 * rad);

                // 最外环稍降 alpha，柔化轮廓、避免硬锯齿边
                float aIn = alpha;
                float aOut = (i == rings - 1) ? alpha * 0.82f : alpha;

                b.vertex(m, x00, y00, z00).color(r * sh00, g * sh00, bl * sh00, aIn).endVertex();
                b.vertex(m, x01, y01, z01).color(r * sh01, g * sh01, bl * sh01, aIn).endVertex();
                b.vertex(m, x11, y11, z11).color(r * sh11, g * sh11, bl * sh11, aOut).endVertex();

                b.vertex(m, x00, y00, z00).color(r * sh00, g * sh00, bl * sh00, aIn).endVertex();
                b.vertex(m, x11, y11, z11).color(r * sh11, g * sh11, bl * sh11, aOut).endVertex();
                b.vertex(m, x10, y10, z10).color(r * sh10, g * sh10, bl * sh10, aOut).endVertex();
            }
        }
    }

    /**
     * 球面某点的兰伯特亮度系数。
     * <p>
     * 给定球体正对相机时平面内的归一化坐标 {@code (u, v)}（单位圆内），
     * 其球面法线为 {@code (u, v, sqrt(1 - u² - v²))}；与光源方向点乘、
     * 夹取到非负后，混入环境光下限即得最终亮度。
     * </p>
     *
     * @param u       平面内 right 分量（归一化，|(u,v)| ≤ 1）
     * @param v       平面内 up 分量
     * @param lu      光源方向 right 分量
     * @param lv      光源方向 up 分量
     * @param lw      光源方向「朝向相机」分量
     * @param ambient 环境光下限
     * @return 亮度系数（{@code ambient} ~ 1.0）
     */
    private static float sphereShade(float u, float v, float lu, float lv, float lw, float ambient) {
        float nzSq = 1f - u * u - v * v;
        float nz = (nzSq <= 0f) ? 0f : Mth.sqrt(nzSq);
        float lam = u * lu + v * lv + nz * lw;
        if (lam < 0f) {
            lam = 0f;
        }
        return ambient + (1f - ambient) * lam;
    }

    // ==================== v10 billboard 基元（面向相机的平面图形）====================
    // 本渲染器原有的 band / line / spark 全部固定在水平面上（y 恒定），
    // 适合地面法阵，但画月轮时会变成「平视只看到一条线」。
    // 以下三个基元用相机的 right / up 向量张成平面，从任意角度看都是正对观察者的圆 / 线。

    /**
     * 面向相机的径向渐变圆盘。
     *
     * @param radius      半径（格）
     * @param segments    分段数
     * @param centerAlpha 中心不透明度
     * @param edgeAlpha   边缘不透明度（取 0 即完全渐隐）
     */
    private static void bbDisc(BufferBuilder b, Matrix4f m,
                               double cx, double cy, double cz, double radius, int segments,
                               float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                               float[] col, float centerAlpha, float edgeAlpha) {
        if (centerAlpha <= 0.004f && edgeAlpha <= 0.004f) {
            return;
        }
        if (radius <= 1.0e-4) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float cxf = (float) cx, cyf = (float) cy, czf = (float) cz;
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= segments; i++) {
            float ang = TAU * i / segments;
            float ca = Mth.cos(ang) * (float) radius;
            float sa = Mth.sin(ang) * (float) radius;
            float ex = cxf + rgX * ca + upX * sa;
            float ey = cyf + rgY * ca + upY * sa;
            float ez = czf + rgZ * ca + upZ * sa;
            if (i > 0) {
                b.vertex(m, cxf, cyf, czf).color(r, g, bl, centerAlpha).endVertex();
                b.vertex(m, pex, pey, pez).color(r, g, bl, edgeAlpha).endVertex();
                b.vertex(m, ex, ey, ez).color(r, g, bl, edgeAlpha).endVertex();
            }
            pex = ex;
            pey = ey;
            pez = ez;
        }
    }

    /**
     * 面向相机的圆环带（annulus），内外边缘可分别指定 alpha。
     *
     * @param rInner   内半径
     * @param rOuter   外半径
     * @param segments 分段数
     * @param rot      整环旋转角（弧度）
     */
    private static void bbRing(BufferBuilder b, Matrix4f m,
                               double cx, double cy, double cz,
                               double rInner, double rOuter, int segments, float rot,
                               float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                               float[] col, float alphaInner, float alphaOuter) {
        if (rOuter <= rInner || segments < 3) {
            return;
        }
        if (alphaInner <= 0.004f && alphaOuter <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float cxf = (float) cx, cyf = (float) cy, czf = (float) cz;
        float ri = (float) rInner, ro = (float) rOuter;
        float prevCos = Mth.cos(rot);
        float prevSin = Mth.sin(rot);
        for (int i = 1; i <= segments; i++) {
            float a = rot + TAU * i / segments;
            float ca = Mth.cos(a);
            float sa = Mth.sin(a);

            float ox0 = cxf + (rgX * prevCos + upX * prevSin) * ro;
            float oy0 = cyf + (rgY * prevCos + upY * prevSin) * ro;
            float oz0 = czf + (rgZ * prevCos + upZ * prevSin) * ro;
            float ox1 = cxf + (rgX * ca + upX * sa) * ro;
            float oy1 = cyf + (rgY * ca + upY * sa) * ro;
            float oz1 = czf + (rgZ * ca + upZ * sa) * ro;
            float ix0 = cxf + (rgX * prevCos + upX * prevSin) * ri;
            float iy0 = cyf + (rgY * prevCos + upY * prevSin) * ri;
            float iz0 = czf + (rgZ * prevCos + upZ * prevSin) * ri;
            float ix1 = cxf + (rgX * ca + upX * sa) * ri;
            float iy1 = cyf + (rgY * ca + upY * sa) * ri;
            float iz1 = czf + (rgZ * ca + upZ * sa) * ri;

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
     * 在面向相机的平面内绘制一条带宽度的线段（用平面二维坐标表达端点）。
     *
     * @param px1 起点在平面内的 right 分量
     * @param py1 起点在平面内的 up 分量
     * @param px2 终点在平面内的 right 分量
     * @param py2 终点在平面内的 up 分量
     * @param hw  线半宽
     */
    private static void bbLine(BufferBuilder b, Matrix4f m,
                               double cx, double cy, double cz,
                               float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                               double px1, double py1, double px2, double py2,
                               double hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        double ddx = px2 - px1;
        double ddy = py2 - py1;
        double len = Math.sqrt(ddx * ddx + ddy * ddy);
        if (len < 1.0e-6) {
            return;
        }
        // 平面内的法线 × 半宽
        double nx = -ddy / len * hw;
        double ny = ddx / len * hw;

        float r = col[0], g = col[1], bl = col[2];
        float cxf = (float) cx, cyf = (float) cy, czf = (float) cz;

        float a1u = (float) (px1 + nx), a1w = (float) (py1 + ny);
        float a2u = (float) (px1 - nx), a2w = (float) (py1 - ny);
        float b1u = (float) (px2 + nx), b1w = (float) (py2 + ny);
        float b2u = (float) (px2 - nx), b2w = (float) (py2 - ny);

        float ax1 = cxf + rgX * a1u + upX * a1w;
        float ay1 = cyf + rgY * a1u + upY * a1w;
        float az1 = czf + rgZ * a1u + upZ * a1w;
        float ax2 = cxf + rgX * a2u + upX * a2w;
        float ay2 = cyf + rgY * a2u + upY * a2w;
        float az2 = czf + rgZ * a2u + upZ * a2w;
        float bx1 = cxf + rgX * b1u + upX * b1w;
        float by1 = cyf + rgY * b1u + upY * b1w;
        float bz1 = czf + rgZ * b1u + upZ * b1w;
        float bx2 = cxf + rgX * b2u + upX * b2w;
        float by2 = cyf + rgY * b2u + upY * b2w;
        float bz2 = czf + rgZ * b2u + upZ * b2w;

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, by1, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, ay2, az2).color(r, g, bl, a1).endVertex();
    }

    // ============================== v9 神圣净化 ==============================

    /**
     * 神圣净化：目标躯干处金色<b>三维十字光刃</b>爆开 → 净化环向外扩散 →
     * 金色光尘升天 → 地面圣徽余辉。约 700ms，定点。
     * <p>
     * 用于 {@code EnchantmentSacredBlade} 击中亡灵：额外伤害 + 吸血 + 永久削弱目标。
     * </p>
     * <p>
     * <b>时间轴（全程仅 0.7 秒，一切都很快）：</b>
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.25]：十字光刃自中心快速张开（缓出，起手最快）；
     *         同时中心白热爆闪（p &lt; 0.2）；</li>
     *     <li>p ∈ [0.05, 0.7]：净化环向外扩散，另有一道错相的追赶环；</li>
     *     <li>p ∈ [0.1, 1]：金色光尘升天；</li>
     *     <li>p ∈ [0.05, 0.6]：地面圣徽随心跳脉动；</li>
     *     <li>p ∈ [0.45, 1]：整体渐隐。</li>
     * </ul>
     * <p>
     * <b>为什么是三维十字而非地面法阵：</b>本模组的地面演出已经密集到「再加一个平面法阵
     * 就没人分得清」的程度。这里用一道竖刃（{@code lightningSegment} 十字双面）
     * 加两道正交横刃（水平 {@code line}）构成悬在半空的发光十字，
     * 从任意角度看都是十字，与所有既有演出的形状语言都不重合。
     * </p>
     * <p>
     * <b>v9 削减：</b>净化环分段数缩放；升天光尘按步长抽取且整层可跳过。
     * <b>三维十字光刃完全不削</b>——仅约 50 顶点却是本演出的全部辨识度，
     * 且它只有 3 条线，削掉任何一条都不再是十字。
     * </p>
     * <p>v9：三个配色全是编译期常量，直接引用只读常量，本方法零分配。</p>
     *
     * @param detail 本帧细节系数
     */
    private static void drawSacredPurge(BufferBuilder b, Matrix4f m,
                                        double cx, double cy, double cz, double radius, float p, float detail) {
        float fade = 1f - smoothstep(0.45f, 1f, p);
        if (fade <= 0f) {
            return;
        }

        final float[] core = C_SACRED_CORE;
        final float[] gold = C_SACRED_GOLD;
        final float[] deep = C_SACRED_DEEP;

        double bodyY = cy + SACRED_CROSS_HEIGHT;

        // ===== 三维十字光刃：一竖两横，快速张开（不参与削减）=====
        float open = (float) easeOutCubic(clamp01(p / 0.25f));
        double armLen = radius * SACRED_CROSS_LENGTH_FACTOR * open;
        if (armLen > 0.05) {
            float crossAlpha = 0.95f * fade;
            // 竖刃（十字双面，任意水平视角可见）：外金 + 内白热
            lightningSegment(b, m, cx, bodyY - armLen, cz, cx, bodyY + armLen, cz,
                    SACRED_CROSS_HALF * 2.4, gold, 0.30f * crossAlpha, 0.05f * crossAlpha);
            lightningSegment(b, m, cx, bodyY - armLen, cz, cx, bodyY + armLen, cz,
                    SACRED_CROSS_HALF, core, crossAlpha, 0.15f * crossAlpha);
            // 两道正交横刃（水平），与竖刃共同构成三维十字
            line(b, m, cx - armLen, cz, cx + armLen, cz, bodyY, SACRED_CROSS_HALF * 2.4,
                    gold[0], gold[1], gold[2], 0.05f * crossAlpha, 0.05f * crossAlpha);
            line(b, m, cx - armLen, cz, cx + armLen, cz, bodyY, SACRED_CROSS_HALF,
                    core[0], core[1], core[2], crossAlpha, 0.15f * crossAlpha);
            line(b, m, cx, cz - armLen, cx, cz + armLen, bodyY, SACRED_CROSS_HALF,
                    core[0], core[1], core[2], crossAlpha, 0.15f * crossAlpha);
        }

        // ===== 中心白热爆闪：命中那一瞬的强反馈 =====
        if (p < 0.2f) {
            float flash = 1f - p / 0.2f;
            spark(b, m, cx, cz, bodyY, (float) (radius * 0.4 + 0.25), core, 0.95f * flash);
        }

        // ===== 净化环：向外扩散 =====
        float ringP = clamp01((p - 0.05f) / 0.6f);
        if (ringP > 0f && ringP < 1f) {
            double rr = radius * easeOutCubic(ringP);
            float ringFade = 1f - ringP;
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), gold,
                    0.85f * ringFade * fade, 0.36f * ringFade * fade, 0.07, 0.42);
            // 错相追赶环
            float ring2 = clamp01((p - 0.18f) / 0.5f);
            if (ring2 > 0f && ring2 < 1f) {
                double rr2 = radius * 0.72 * easeOutCubic(ring2);
                glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2, detail), deep,
                        0.5f * (1f - ring2) * fade, 0.22f * (1f - ring2) * fade, 0.05, 0.32);
            }
        }

        // ===== 地面圣徽：十字 + 底盘，随心跳脉动 =====
        float emblem = smoothstep(0.03f, 0.15f, p) * (1f - smoothstep(0.5f, 0.75f, p));
        if (emblem > 0f) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(p * 26.0);
            double len = radius * 0.75;
            double hw = Math.max(0.05, radius * 0.03);
            float a = 0.7f * emblem * pulse * fade;
            line(b, m, cx - len, cz, cx + len, cz, cy, hw, core[0], core[1], core[2], a, a);
            line(b, m, cx, cz - len, cx, cz + len, cy, hw, core[0], core[1], core[2], a, a);
            band(b, m, cx, cz, cy, 0.0, radius * 0.85, segmentsFor(radius, detail),
                    gold[0], gold[1], gold[2], 0.12f * emblem * fade, 0f);
        }

        // ===== 升天光尘：短竖光丝向上飘（均布角度，必须按步长抽取）=====
        if (VisualLod.keepLayer(detail, SACRED_MOTE_KEEP_THRESHOLD)) {
            float moteP = clamp01((p - 0.1f) / 0.9f);
            if (moteP > 0f) {
                int drawn = VisualLod.scale(SACRED_MOTE_COUNT, detail);
                int step = Math.max(1, SACRED_MOTE_COUNT / drawn);
                for (int i = 0; i < SACRED_MOTE_COUNT; i += step) {
                    // 角度基准用原始 SACRED_MOTE_COUNT，保证保留光尘方位与全细节时一致
                    double ang = TAU * i / SACRED_MOTE_COUNT;
                    double fr = (i * 0.6180339) - Math.floor(i * 0.6180339);
                    double rr = radius * (0.2 + 0.5 * fr);
                    double px = cx + rr * Math.cos(ang);
                    double pz = cz + rr * Math.sin(ang);

                    // 逐条错开起飞时刻，避免整圈光尘齐刷刷同时升起
                    float t = clamp01((moteP - i * 0.04f) / 0.75f);
                    if (t <= 0f) {
                        continue;
                    }
                    double y0 = cy + t * SACRED_MOTE_RISE;
                    double y1 = y0 + SACRED_MOTE_LENGTH;
                    float a = 0.8f * (1f - t) * fade;
                    if (a <= 0.01f) {
                        continue;
                    }
                    lightningSegment(b, m, px, y0, pz, px, y1, pz, SACRED_MOTE_HALF, gold, a, 0f);
                }
            }
        }
    }

    /**
     * 猩红艾奥尼亚（还原玛莲妮亚开花，含竖直 3D 立体绽放花）。
     * <p>
     * v6 削减：地面法阵的环 / 带分段数缩放；两圈符文刻度按步长抽取；星屑数量缩减
     * （黄金角螺旋的前 N 个本身即均匀铺满，直接减数量安全）；爆发射线按步长抽取；
     * 花瓣<b>只削脊线细分、不削瓣数</b>（详见类注释）。
     * </p>
     * <p>
     * v7：三个主题色改用只读常量；唯一的动态混色（炸裂白热核）写入
     * {@link #SCRATCH} 后立即被 {@link #drawOrb} 消费。此时花瓣已绘制完毕，
     * 与花瓣缓冲不冲突。
     * </p>
     */
    private static void drawScarletBloom(BufferBuilder b, Matrix4f m,
                                         double cx, double cy, double cz, double radius, float p, float detail) {
        final float[] red = C_SCARLET;
        final float[] deep = C_SCARLET_DEEP;
        final float[] hot = C_SCARLET_HOT;
        double hw = lineHalf(radius);
        float appear = clamp01(p / 0.015f);
        float rot = p * 0.35f * TAU;

        float open;
        if (p < 0.40f) {
            open = lerp(0.08f, 0.72f, (float) easeOutCubic(p / 0.40f));
        } else if (p < 0.44f) {
            open = lerp(0.72f, 1.0f, (p - 0.40f) / 0.04f);
        } else {
            open = 1.0f + 0.6f * smoothstep(0.44f, 1.0f, p);
        }
        float riseH = clamp01(p / 0.04f);
        if (p > 0.62f) {
            riseH *= (1f - 0.55f * smoothstep(0.62f, 1f, p));
        }
        float flowerAlpha = appear * (1f - smoothstep(0.65f, 1.0f, p));

        float baseA = appear * (1f - smoothstep(0.48f, 0.56f, p));
        if (baseA > 0f) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(p * 12.0);
            band(b, m, cx, cz, cy, 0.0, radius * 1.05, segmentsFor(radius, detail),
                    deep[0], deep[1], deep[2], 0.04f * baseA * pulse, 0.09f * baseA);
            polygonRing(b, m, cx, cz, cy, radius * 0.92, 6, rot * 0.5f, hw, red, 0.35f * baseA);
            tickRing(b, m, cx, cz, cy, radius * 0.74, 0.28, 36, p * 0.4f * TAU, 0.04, red, 0.32f * baseA, detail);
            tickRing(b, m, cx, cz, cy, radius * 0.58, 0.20, 24, -p * 0.5f * TAU, 0.035, deep, 0.28f * baseA, detail);
            for (int i = 0; i < 3; i++) {
                float ph = frac(p * 0.9f + i / 3f);
                double rr = radius * ph;
                if (rr > 0.3) {
                    glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), red,
                            0f, 0.13f * baseA * (1f - ph), 0.05, 0.40);
                }
            }
            starField(b, m, cx, cz, cy, radius * 0.95, 16, p,
                    (float) (radius * 0.022 + 0.035), red, 0.4f * baseA, detail);
        }

        drawAeoniaFlower(b, m, cx, cy, cz, radius, open, riseH, flowerAlpha, rot, detail);

        if (p >= 0.40f && p < 0.52f) {
            float kf = clamp01((p - 0.40f) / 0.04f) * (1f - smoothstep(0.46f, 0.52f, p));
            double coreH = radius * 0.95 * riseH * 0.16;
            // v7：无分配插值，写入复用缓冲后立即消费（花瓣此时已画完，不冲突）
            VisualColor.mixInto(SCRATCH, red, hot, 0.7f);
            drawOrb(b, m, cx, cy + coreH, cz, radius * (0.10 + 0.18 * kf), SCRATCH, 0.9f * appear);
        }

        float burst = clamp01((p - 0.42f) / 0.30f);
        if (burst > 0f) {
            float bfade = 1f - burst;
            double rr = radius * 1.9 * easeOutCubic(burst);
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), red,
                    0.85f * bfade, 0.42f * bfade, 0.10, 0.70);
            glowRing(b, m, cx, cz, cy, rr * 0.74, segmentsFor(rr, detail), deep,
                    0.6f * bfade, 0.28f * bfade, 0.07, 0.50);
            float b2 = clamp01((p - 0.47f) / 0.28f);
            if (b2 > 0f) {
                double rr2 = radius * 1.45 * easeOutCubic(b2);
                glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2, detail), red,
                        0.45f * (1f - b2), 0.2f * (1f - b2), 0.06, 0.45);
            }
            if (burst < 0.55f) {
                float rf = 1f - burst / 0.55f;
                rays(b, m, cx, cz, cy, radius * 0.2, radius * 1.7 * easeOutCubic(burst), 16,
                        p * 0.2f, hw, deep, 0.6f * rf, 0f, detail);
            }
            if (burst < 0.15f) {
                float flash = 1f - burst / 0.15f;
                band(b, m, cx, cz, cy, 0.0, radius * 0.9, segmentsFor(radius, detail),
                        hot[0], hot[1], hot[2], 0.5f * flash, 0.0f);
            }
        }

        float ember = smoothstep(0.65f, 0.75f, p) * (1f - smoothstep(0.95f, 1f, p));
        if (ember > 0f) {
            double rr = radius * 2.2;
            band(b, m, cx, cz, cy, 0.0, rr, segmentsFor(rr, detail),
                    deep[0], deep[1], deep[2], 0.0f, 0.15f * ember);
            for (int i = 0; i < 2; i++) {
                float ph = frac(p * 2.5f + i * 0.5f);
                double rrr = radius * 2.2 * ph;
                if (rrr > 0.3) {
                    glowRing(b, m, cx, cz, cy, rrr, segmentsFor(rrr, detail), deep,
                            0f, 0.10f * ember * (1f - ph), 0.04, 0.28);
                }
            }
            starField(b, m, cx, cz, cy, radius * 1.8, 12, p,
                    (float) (radius * 0.02 + 0.03), deep, 0.32f * ember, detail);
        }
    }

    // ============================== 猩红艾奥尼亚 · 3D 立体花专用几何 ==============================

    /**
     * 绘制整朵猩红艾奥尼亚之花：四层曼陀罗式层叠花瓣 + 花蕊小瓣 + 花心白热球。
     * <p>
     * v6 削减：<b>只削每瓣的脊线细分、不削瓣数</b>——减瓣数会让花瓣方位随距离变化、
     * 远近切换时花「重新长一遍」；减脊线细分只让曲面从平滑变硬直，轮廓与方位完全不变。
     * 细节极低时额外跳过最内层的花蕊小瓣（被外三层遮住大半，远处不可见）。
     * 花心白热球仅 48 顶点，不削。
     * </p>
     * <p>
     * <b>v7 ⚠：</b>传给 {@link #drawPetalLayer} 的三个配色数组必须是只读常量。
     * {@link #drawPetal} 会在整个脊线循环期间反复读取它们，若传入可写缓冲，
     * 循环跑到一半配色就会被改掉（详见类注释）。
     * 花心球的动态混色写入 {@link #SCRATCH}——此时全部花瓣已绘制完毕，时序上不重叠。
     * </p>
     */
    private static void drawAeoniaFlower(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                         double radius, float open, float riseH, float flowerAlpha,
                                         float rot, float detail) {
        if (flowerAlpha <= 0.01f || riseH <= 0.01f) {
            return;
        }
        // ⚠ 只读常量，drawPetal 会跨整个循环反复读取，绝不可传入可写缓冲
        final float[] deep = C_SCARLET_DEEP;
        final float[] red = C_SCARLET;
        final float[] hot = C_SCARLET_HOT;
        double l = radius * 0.95 * riseH;

        // 每瓣脊线细分段数：这是花瓣的主要顶点杠杆（32 瓣 × seg × 6）
        int petalSeg = VisualLod.scaleSegments(PETAL_SEGMENTS, PETAL_SEGMENTS_MIN, detail);

        drawPetalLayer(b, m, cx, cy, cz, 8, l * 1.00, radius * 0.24, deg2rad(82), open, rot,
                deep, red, red, flowerAlpha, petalSeg);
        drawPetalLayer(b, m, cx, cy, cz, 7, l * 0.78, radius * 0.21, deg2rad(64), open, rot + 0.34f,
                deep, red, hot, flowerAlpha * 0.96f, petalSeg);
        drawPetalLayer(b, m, cx, cy, cz, 6, l * 0.56, radius * 0.18, deg2rad(48), open, rot + 0.66f,
                red, red, hot, flowerAlpha * 0.92f, petalSeg);
        drawPetalLayer(b, m, cx, cy, cz, 5, l * 0.38, radius * 0.14, deg2rad(34), open, rot + 0.95f,
                red, hot, hot, flowerAlpha * 0.90f, petalSeg);
        // 最内层花蕊小瓣：被外三层遮住大半，远处完全看不出，低细节时整层跳过
        if (VisualLod.keepLayer(detail, PETAL_INNER_LAYER_KEEP_THRESHOLD)) {
            drawPetalLayer(b, m, cx, cy, cz, 6, l * 0.24, radius * 0.08, deg2rad(20), open * 0.5f, rot + 0.15f,
                    red, hot, hot, flowerAlpha, petalSeg);
        }
        // v7：无分配插值，写入复用缓冲后立即消费（全部花瓣已画完，不冲突）
        VisualColor.mixInto(SCRATCH, red, hot, 0.5f);
        drawOrb(b, m, cx, cy + l * 0.12, cz, radius * 0.06, SCRATCH, 0.7f * flowerAlpha);
    }

    /**
     * 绘制一层花瓣：{@code petals} 片均布，方位以 {@code baseRot} 为起点。
     * <p><b>⚠ {@code deep} / {@code mid} / {@code tip} 必须是只读常量数组</b>，
     * 它们会被一路传进 {@link #drawPetal} 并在其脊线循环中反复读取。</p>
     *
     * @param seg 每瓣的脊线细分段数（v6 起由调用方按细节系数传入）
     */
    private static void drawPetalLayer(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                       int petals, double length, double maxWidth,
                                       float fullAngle, float open, float baseRot,
                                       float[] deep, float[] mid, float[] tip, float alphaMul, int seg) {
        float budAngle = deg2rad(12);
        float baseAngle = budAngle + open * (fullAngle - budAngle);
        float curlAngle = deg2rad(12) + deg2rad(28) * open;
        for (int i = 0; i < petals; i++) {
            float az = baseRot + TAU * i / petals;
            drawPetal(b, m, cx, cy, cz, az, baseAngle, curlAngle, length, maxWidth, deep, mid, tip, alphaMul, seg);
        }
    }

    /**
     * 绘制一片 3D 花瓣：沿一条「向上弯曲」的脊线积分采样，左右按宽度轮廓展开成三角形带曲面。
     * <p>
     * <b>v7：颜色改用滚动双缓冲，每段只插值一次。</b>这里是全模组分配最密集的一处——
     * 旧实现每段两次 {@code new float[3]}，单朵花每帧 512 次。由于
     * 「第 i 段的末端色 == 第 i+1 段的起点色」，算完一段后把
     * {@link #PETAL_COL_A} / {@link #PETAL_COL_B} 的引用对调即可复用，
     * 插值次数由 {@code 2 × seg} 降为 {@code seg + 1}、分配归零。
     * </p>
     * <p>
     * <b>两个缓冲缺一不可</b>——写顶点时必须同时持有段两端的颜色（根侧暗、尖侧亮），
     * 共用一个会让整片花瓣退化成纯色（详见类注释）。
     * </p>
     *
     * @param seg 脊线细分段数（v6 起可变；下限 {@link #PETAL_SEGMENTS_MIN}）
     */
    private static void drawPetal(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                  float azimuth, float baseAngle, float curlAngle,
                                  double length, double maxWidth,
                                  float[] deep, float[] mid, float[] tip, float alphaMul, int seg) {
        double cosA = Math.cos(azimuth), sinA = Math.sin(azimuth);
        double wx = -sinA, wz = cosA;

        // ⭐ v7 防御性钳制：脊线缓冲按 PETAL_SEGMENTS 定长分配，
        // 若将来有人把细分数调大而忘了同步缓冲长度，这里静默降级而非数组越界崩溃
        if (seg > PETAL_SEGMENTS) {
            seg = PETAL_SEGMENTS;
        }

        // ⭐ v7：脊线位移改用静态复用缓冲。旧实现每片花瓣 new 两个 double 数组，
        // 单朵花 32 瓣即 64 个临时数组 / 帧。drawPetal 不可重入，故复用安全。
        final double[] hor = PETAL_HOR;
        final double[] ver = PETAL_VER;
        double h = 0, v = 0;
        double ds = length / seg;
        for (int i = 0; i <= seg; i++) {
            hor[i] = h;
            ver[i] = v;
            double u = (double) i / seg;
            double ang = baseAngle + curlAngle * u;
            h += Math.sin(ang) * ds;
            v += Math.cos(ang) * ds;
        }

        // ⭐ v7 滚动双缓冲：colPrev 持有本段根侧（u0）的颜色，colCur 持有本段尖侧（u1）的颜色。
        // 循环外先算出 u=0 的颜色作为第一段的起点色；每段末尾交换引用，
        // 本段的尖侧色即成为下一段的根侧色，省掉一半插值。
        float[] colPrev = PETAL_COL_A;
        float[] colCur = PETAL_COL_B;
        petalColorInto(colPrev, 0.0, deep, mid, tip);

        for (int i = 0; i < seg; i++) {
            double u0 = (double) i / seg;
            double u1 = (double) (i + 1) / seg;
            double w0 = petalWidth(u0) * maxWidth * 0.5;
            double w1 = petalWidth(u1) * maxWidth * 0.5;

            double mx0 = cx + hor[i] * cosA, mz0 = cz + hor[i] * sinA, my0 = cy + ver[i];
            double mx1 = cx + hor[i + 1] * cosA, mz1 = cz + hor[i + 1] * sinA, my1 = cy + ver[i + 1];

            float l0x = (float) (mx0 - wx * w0), l0y = (float) my0, l0z = (float) (mz0 - wz * w0);
            float r0x = (float) (mx0 + wx * w0), r0y = (float) my0, r0z = (float) (mz0 + wz * w0);
            float l1x = (float) (mx1 - wx * w1), l1y = (float) my1, l1z = (float) (mz1 - wz * w1);
            float r1x = (float) (mx1 + wx * w1), r1y = (float) my1, r1z = (float) (mz1 + wz * w1);

            petalColorInto(colCur, u1, deep, mid, tip);
            float a0 = petalAlpha(u0) * alphaMul;
            float a1 = petalAlpha(u1) * alphaMul;

            b.vertex(m, l0x, l0y, l0z).color(colPrev[0], colPrev[1], colPrev[2], a0).endVertex();
            b.vertex(m, r0x, r0y, r0z).color(colPrev[0], colPrev[1], colPrev[2], a0).endVertex();
            b.vertex(m, r1x, r1y, r1z).color(colCur[0], colCur[1], colCur[2], a1).endVertex();

            b.vertex(m, l0x, l0y, l0z).color(colPrev[0], colPrev[1], colPrev[2], a0).endVertex();
            b.vertex(m, r1x, r1y, r1z).color(colCur[0], colCur[1], colCur[2], a1).endVertex();
            b.vertex(m, l1x, l1y, l1z).color(colCur[0], colCur[1], colCur[2], a1).endVertex();

            // 交换缓冲：本段尖侧色成为下一段的根侧色，省掉一次插值
            float[] tmp = colPrev;
            colPrev = colCur;
            colCur = tmp;
        }
    }

    /**
     * 绘制一个小亮球（上下两个四棱锥拼成的八面体近似），用作花心白热核。
     * <p>仅 48 顶点，不参与 LOD 削减。</p>
     * <p>
     * <b>v7：顶点内联为标量。</b>原实现用 {@code float[]} / {@code float[][]} 字面量表达
     * 上下极点与四个赤道点，每次调用分配 <b>7 个临时数组</b>。
     * 本方法虽只在每朵花每帧调用两次，但清理方式与 {@link #spark} 完全同源，一并处理；
     * 顶点输出与顺序逐字不变。
     * </p>
     */
    private static void drawOrb(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                double size, float[] col, float alpha) {
        float r = col[0], g = col[1], bb = col[2];
        float cxf = (float) cx, cyf = (float) cy, czf = (float) cz, s = (float) size;

        // 四个赤道点（y 恒为 cyf），顺序与原 eq[0..3] 一致
        float e0x = cxf + s, e0z = czf;
        float e1x = cxf, e1z = czf + s;
        float e2x = cxf - s, e2z = czf;
        float e3x = cxf, e3z = czf - s;

        orbSlice(b, m, cxf, cyf, czf, s, e0x, e0z, e1x, e1z, r, g, bb, alpha);
        orbSlice(b, m, cxf, cyf, czf, s, e1x, e1z, e2x, e2z, r, g, bb, alpha);
        orbSlice(b, m, cxf, cyf, czf, s, e2x, e2z, e3x, e3z, r, g, bb, alpha);
        orbSlice(b, m, cxf, cyf, czf, s, e3x, e3z, e0x, e0z, r, g, bb, alpha);
    }

    /**
     * 亮球的一瓣：上极点 + 两个相邻赤道点构成的上三角，与下极点构成的下三角。
     *
     * @param s  半径（上下极点相对中心的偏移）
     * @param ax 第一个赤道点 X
     * @param az 第一个赤道点 Z
     * @param dx 第二个赤道点 X
     * @param dz 第二个赤道点 Z
     */
    private static void orbSlice(BufferBuilder b, Matrix4f m,
                                 float cxf, float cyf, float czf, float s,
                                 float ax, float az, float dx, float dz,
                                 float r, float g, float bb, float alpha) {
        float edge = alpha * 0.55f;
        b.vertex(m, cxf, cyf + s, czf).color(r, g, bb, alpha).endVertex();
        b.vertex(m, ax, cyf, az).color(r, g, bb, edge).endVertex();
        b.vertex(m, dx, cyf, dz).color(r, g, bb, edge).endVertex();
        b.vertex(m, cxf, cyf - s, czf).color(r, g, bb, alpha).endVertex();
        b.vertex(m, dx, cyf, dz).color(r, g, bb, edge).endVertex();
        b.vertex(m, ax, cyf, az).color(r, g, bb, edge).endVertex();
    }

    /** 花瓣宽度轮廓（根窄、中宽、尖收的叶形）。 */
    private static double petalWidth(double u) {
        return Math.sin(Math.PI * Math.pow(u, 0.65));
    }

    /**
     * 花瓣颜色：根部 deep → 中部 mid → 尖端偏向 tip，结果写入调用方提供的缓冲。
     * <p>
     * v7：由「返回新数组」改为「写入缓冲」，消除花瓣循环里每段两次的堆分配。
     * 插值口径与旧的 {@code mix} 完全一致（归一化域线性插值 + clamp01），输出逐位相同。
     * </p>
     *
     * @param dst  目标缓冲（⚠ 不可传入 {@code C_} 常量数组）
     * @param u    沿脊线的归一化位置（0 = 根部，1 = 尖端）
     * @param deep 根部色（只读）
     * @param mid  中部色（只读）
     * @param tip  尖端色（只读）
     */
    private static void petalColorInto(float[] dst, double u, float[] deep, float[] mid, float[] tip) {
        if (u < 0.5) {
            VisualColor.mixInto(dst, deep, mid, (float) (u / 0.5));
            return;
        }
        VisualColor.mixInto(dst, mid, tip, (float) ((u - 0.5) / 0.5) * 0.7f);
    }

    /** 花瓣 alpha：中段最实的驼峰。 */
    private static float petalAlpha(double u) {
        return 0.5f + 0.45f * (float) Math.sin(Math.PI * u);
    }

    /**
     * 癫火蔓延（大型多段平面演出，开场即满状态）。
     * <p>
     * v6 削减：18 条颤动焰舌按步长抽取（均布角度，截断会让焰舌只朝一侧喷）；
     * 星屑数量缩减；爆发环分段数缩放；爆发射线按步长抽取。
     * <b>三重星形法阵（六芒 / 五芒 / 七芒）完全不削</b>——共约 108 顶点，
     * 是「这是癫火不是别的爆发」的唯一辨识依据。
     * </p>
     * <p>
     * v7：三个主题色改用只读常量；三处动态混色（蓄能核、起手核、冲击强闪）
     * 均写入 {@link #SCRATCH} 后立即被 {@link #spark} 消费。本演出不涉及花瓣，
     * 与花瓣缓冲无交集。
     * </p>
     */
    private static void drawFrenziedFlame(BufferBuilder b, Matrix4f m,
                                          double cx, double cy, double cz, double radius, float p, float detail) {
        final float[] yellow = C_FRENZY_YELLOW;
        final float[] orange = C_FRENZY_ORANGE;
        final float[] hot = C_FRENZY_WHITE;
        double hw = lineHalf(radius);
        float appear = clamp01(p / 0.015f);

        float pre = appear * (1f - smoothstep(0.40f, 0.46f, p));
        if (pre > 0f) {
            float spin = p * 5f * TAU;
            float flick = 0.55f + 0.45f * (float) Math.sin(p * 60.0);

            band(b, m, cx, cz, cy, 0.0, radius * 1.0, segmentsFor(radius, detail),
                    orange[0] * 0.6f, orange[1] * 0.45f, orange[2] * 0.35f, 0.03f * pre, 0.08f * pre);
            float wobble = (float) (Math.sin(p * 35.0) * 0.05);
            polygonRing(b, m, cx, cz, cy, radius * (0.30 + wobble), 7, spin * 0.5f, hw, yellow, 0.4f * pre * flick);

            // 焰舌：均布角度，必须按步长抽取（截断会让焰舌只朝一侧喷）
            final int n = 18;
            int drawnTongues = VisualLod.scale(n, detail);
            int tongueStep = Math.max(1, n / drawnTongues);
            for (int i = 0; i < n; i += tongueStep) {
                double base = TAU * i / n;
                double jitter = Math.sin(p * 48.0 + i * 1.7) * 0.16 + Math.sin(p * 29.0 + i * 3.1) * 0.10;
                double ang = base + jitter;
                double lenPulse = 0.55 + 0.45 * Math.abs(Math.sin(p * 8.0 + i * 0.9));
                double len = radius * lenPulse;
                double cos = Math.cos(ang), sin = Math.sin(ang);
                double ix = cx + cos * radius * 0.08, iz = cz + sin * radius * 0.08;
                double mx = cx + cos * len * 0.55, mz = cz + sin * len * 0.55;
                double ox = cx + cos * len, oz = cz + sin * len;
                line(b, m, ix, iz, mx, mz, cy, hw, yellow[0], yellow[1], yellow[2], 0.90f * pre, 0.70f * pre);
                line(b, m, mx, mz, ox, oz, cy, hw * 0.8, orange[0], orange[1], orange[2], 0.70f * pre, 0.06f * pre);
                if (len > radius * 0.45) {
                    for (int s = -1; s <= 1; s += 2) {
                        double fang = ang + s * 0.38;
                        double fox = ox + Math.cos(fang) * len * 0.28;
                        double foz = oz + Math.sin(fang) * len * 0.28;
                        line(b, m, ox, oz, fox, foz, cy, hw * 0.6, orange[0], orange[1], orange[2], 0.5f * pre, 0f);
                    }
                }
            }
            starPolygon(b, m, cx, cz, cy, radius * 0.30, 6, 2, spin, hw, yellow, 0.65f * pre * flick);
            starPolygon(b, m, cx, cz, cy, radius * 0.22, 5, 2, -spin * 1.3f, hw, orange, 0.55f * pre * flick);
            starPolygon(b, m, cx, cz, cy, radius * 0.15, 7, 3, spin * 1.6f, hw, hot, 0.45f * pre * flick);
            starField(b, m, cx, cz, cy, radius * 0.95, 22, p * 1.5f,
                    (float) (radius * 0.025 + 0.035), yellow, 0.5f * pre, detail);
            // v7：无分配插值，写入复用缓冲后立即消费
            VisualColor.mixInto(SCRATCH, yellow, hot, 0.4f + 0.3f * flick);
            spark(b, m, cx, cz, cy, (float) (radius * 0.12 + 0.05), SCRATCH, 0.8f * pre);
        }

        if (p >= 0.38f && p < 0.46f) {
            float kf = clamp01((p - 0.38f) / 0.06f);
            float coreSize = (float) (radius * (0.12 + 0.28 * easeOutCubic(kf)));
            // v7：无分配插值，写入复用缓冲后立即消费
            VisualColor.mixInto(SCRATCH, yellow, hot, kf);
            spark(b, m, cx, cz, cy, coreSize, SCRATCH, 0.95f * appear);
            rays(b, m, cx, cz, cy, radius * (1.0 - 0.6 * kf), radius * 0.2, 14,
                    p * 2.5f, hw, hot, 0.1f, 0.6f * kf * appear, detail);
        }

        float burst = clamp01((p - 0.42f) / 0.28f);
        if (burst > 0f) {
            float cfade = 1f - burst;
            double rr = radius * 1.75 * easeOutCubic(burst);
            glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), orange,
                    0.85f * cfade, 0.42f * cfade, 0.09, 0.60);
            glowRing(b, m, cx, cz, cy, rr * 0.72, segmentsFor(rr, detail), yellow,
                    0.60f * cfade, 0.28f * cfade, 0.06, 0.45);
            float b2 = clamp01((p - 0.47f) / 0.26f);
            if (b2 > 0f) {
                double rr2 = radius * 1.35 * easeOutCubic(b2);
                glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2, detail), orange,
                        0.5f * (1f - b2), 0.22f * (1f - b2), 0.05, 0.40);
            }
            if (burst < 0.55f) {
                float rf = 1f - burst / 0.55f;
                rays(b, m, cx, cz, cy, radius * 0.2, radius * 1.6 * easeOutCubic(burst), 18,
                        p * 0.3f, hw, orange, 0.6f * rf, 0f, detail);
            }
            if (burst < 0.16f) {
                float flash = 1f - burst / 0.16f;
                // v7：无分配插值，写入复用缓冲后立即消费
                VisualColor.mixInto(SCRATCH, yellow, hot, 0.7f);
                spark(b, m, cx, cz, cy, (float) (radius * 0.55 + 0.4), SCRATCH, 0.95f * flash);
                glowRing(b, m, cx, cz, cy, radius * 0.7, segmentsFor(radius, detail), hot,
                        0.75f * flash, 0.35f * flash, 0.12, 0.5);
            }
        }

        float emberOut = smoothstep(0.65f, 0.75f, p) * (1f - smoothstep(0.93f, 1f, p));
        if (emberOut > 0f) {
            final int n = 18;
            int drawnEmber = VisualLod.scale(n, detail);
            int emberStep = Math.max(1, n / drawnEmber);
            for (int i = 0; i < n; i += emberStep) {
                double ang = TAU * i / n + Math.sin(i * 1.7) * 0.1;
                double len = radius * 0.85;
                double cos = Math.cos(ang), sin = Math.sin(ang);
                line(b, m, cx + cos * radius * 0.08, cz + sin * radius * 0.08,
                        cx + cos * len, cz + sin * len, cy, hw * 0.7,
                        orange[0] * 0.5f, orange[1] * 0.4f, orange[2] * 0.35f, 0.3f * emberOut, 0f);
            }
            float flick = 0.4f + 0.6f * (float) Math.abs(Math.sin(p * 28.0));
            starField(b, m, cx, cz, cy, radius * 0.8, 10, p,
                    (float) (radius * 0.02 + 0.03), orange, 0.3f * emberOut * flick, detail);
        }
    }

    /**
     * 通用回退：中性蓝白双环扩张（兼容层未匹配到专属类型时使用）。
     * <p>v6 削减：两个环的分段数缩放。</p>
     * <p>v7：主题色改用只读常量，本方法零分配。</p>
     */
    private static void drawGeneric(BufferBuilder b, Matrix4f m,
                                    double cx, double cy, double cz, double radius, float p, float detail) {
        float fade = fadeInOut(p, 0.15f, 0.60f);
        if (fade <= 0f) {
            return;
        }
        final float[] c = C_GENERIC_BLUE;
        double expand = easeOutCubic(clamp01(p / 0.60f));
        glowRing(b, m, cx, cz, cy, radius * expand, segmentsFor(radius, detail), c,
                0.70f * fade, 0.30f * fade, 0.06, 0.40);
        glowRing(b, m, cx, cz, cy, radius * 0.55 * expand, segmentsFor(radius, detail), c,
                0.40f * fade, 0.18f * fade, 0.05, 0.30);
    }

    // ============================== 龙雷 · 红色闪电专属演出（竖直） ==============================

    /**
     * 古龙雷击 / 维克的龙雷：垂直红色之字闪电柱（含蜿蜒主干 + 分叉 + 落地红色冲击环）。
     * <p>还原艾尔登法环「龙雷」的红色雷击意象：一道自天而降的红色电柱劈在目标脚下，
     * 粉白红核 + 红辉光双层电流，沿途分出数条短分叉，落地处掀起红色冲击环与地面强闪；
     * 整体瞬间炸亮后持续强烈明灭、较慢消散（约 1.4 秒），还原原作龙雷的巨大亮眼与余震重闪。
     * 电柱外形由管理器在创建时确定的固定种子决定，
     * 每道闪电形态各异、同一道闪电（含被「同位置合并」续命的）在整段生命周期内形态稳定，不逐帧 / 逐次抖动。</p>
     * <p>电柱为竖直几何，每段用「十字双面」绘制（沿世界 X、Z 轴各一个四边形），
     * 从任意水平视角都可见、无需 billboard 计算。{@code radius} 仅用于落地冲击环尺寸，
     * 闪电柱粗细 / 高度由本类顶部的 {@code LIGHTNING_*} 常量控制。</p>
     *
     * <h3>v6 削减（这是本渲染器最大的性能杠杆）</h3>
     * <p>
     * 单道红闪开场峰值约 4400 顶点，而 {@code MAX_ACTIVE} 为 40，
     * 古龙雷击对分散目标降雷时会跑满上限（同位置合并只覆盖 2.5 格）。削减点：
     * </p>
     * <ul>
     *     <li><b>主干先算全部节点、再按步长跳着连线</b>——rng 推进次数与分叉锚点位置完全不变，
     *         保留的节点仍在原轨迹上，远近切换时电柱形状骨架恒定、不跳变（详见类注释）；</li>
     *     <li><b>最外层浓红光晕整层可跳过</b>——占主干顶点的三分之一，远处与中层辉光糊成一片；</li>
     *     <li><b>分叉数截断尾部</b>——分叉方向随机，保留下来那几条的形状与全细节时完全一致；</li>
     *     <li>分叉内部段数缩放；落地四个环的分段数缩放。</li>
     * </ul>
     * <p>v7：三个主题色改用只读常量，本方法零分配。</p>
     *
     * @param seed   该闪电的固定外形种子（由管理器创建时生成、生命周期不变）
     * @param detail 本帧细节系数
     */
    private static void drawRedLightning(BufferBuilder b, Matrix4f m,
                                         double cx, double cy, double cz, double radius, float p,
                                         long seed, float detail) {
        float intensity = lightningIntensity(p, seed);
        if (intensity <= 0f) {
            return;
        }
        final float[] core = C_LIGHTNING_CORE;
        final float[] glow = C_LIGHTNING_GLOW;
        final float[] deep = C_LIGHTNING_DEEP;

        // ===== 主电柱节点：自落地点向上蜿蜒（底部两段保持接地不偏移，使落点精确居中）=====
        // ⭐ v6：节点始终按原段数完整生成——rng 推进次数、分叉锚点范围均不受细节系数影响，
        // 保证同一道闪电在任何距离下骨架完全一致（详见类注释）
        final int segs = LIGHTNING_SEGMENTS;
        // ⭐ v7：节点坐标改用静态复用缓冲。旧实现每道闪电 new 三个 double 数组，
        // 而红闪是最容易跑满同屏上限（40 道）的类型，且爆发时机恰是客户端最卡的一瞬。
        // 节点数恒为 segs + 1，缓冲尺寸精确匹配；drawRedLightning 不可重入，复用安全。
        final double[] nx = LIGHTNING_NX;
        final double[] ny = LIGHTNING_NY;
        final double[] nz = LIGHTNING_NZ;
        long s = seed;
        double walkX = 0, walkZ = 0;
        for (int i = 0; i <= segs; i++) {
            double frac = (double) i / segs;
            if (i >= 2) {
                s = rngNext(s);
                walkX += rngUnit(s) * LIGHTNING_WANDER;
                s = rngNext(s);
                walkZ += rngUnit(s) * LIGHTNING_WANDER;
                walkX = clampAbs(walkX, LIGHTNING_MAX_OFFSET);
                walkZ = clampAbs(walkZ, LIGHTNING_MAX_OFFSET);
            }
            nx[i] = cx + walkX;
            ny[i] = cy + LIGHTNING_HEIGHT * frac;
            nz[i] = cz + walkZ;
        }

        // ⭐ v6：绘制时按步长跳着连线（节点本身不变，只是折线更粗糙）
        int drawnSegs = VisualLod.scaleSegments(segs, LIGHTNING_SEGMENTS_MIN, detail);
        int segStep = Math.max(1, segs / drawnSegs);

        // ===== 主干：三层叠绘（外层浓红光晕 → 中层亮红主体 → 白热炽核），顶端略淡没入天空 =====
        // 最外层光晕：占主干顶点三分之一，远处与中层糊成一片，低细节时整层跳过
        if (VisualLod.keepLayer(detail, LIGHTNING_HALO_KEEP_THRESHOLD)) {
            for (int i = 0; i < segs; i += segStep) {
                int next = Math.min(i + segStep, segs);
                float aTop = 1f - 0.40f * (float) next / segs;
                lightningSegment(b, m, nx[i], ny[i], nz[i], nx[next], ny[next], nz[next],
                        LIGHTNING_HALO_HALF, deep, 0.30f * intensity, 0.30f * intensity * aTop);
            }
        }
        for (int i = 0; i < segs; i += segStep) {
            int next = Math.min(i + segStep, segs);
            float aTop = 1f - 0.35f * (float) next / segs;
            lightningSegment(b, m, nx[i], ny[i], nz[i], nx[next], ny[next], nz[next],
                    LIGHTNING_GLOW_HALF, glow, 0.72f * intensity, 0.72f * intensity * aTop);
        }
        for (int i = 0; i < segs; i += segStep) {
            int next = Math.min(i + segStep, segs);
            float aTop = 1f - 0.28f * (float) next / segs;
            lightningSegment(b, m, nx[i], ny[i], nz[i], nx[next], ny[next], nz[next],
                    LIGHTNING_CORE_HALF, core, 1.0f * intensity, 1.0f * intensity * aTop);
        }

        // ===== 分叉：在中上部若干节点分出蜿蜒短支（逐段变细、淡出、收尖）=====
        // ⭐ v6：截断尾部而非按步长抽取——分叉方向是随机的（非均布），
        // 截断保留的那几条形状与全细节时逐点一致
        int drawnBranches = VisualLod.scale(LIGHTNING_BRANCHES, detail);
        int branchSegs = VisualLod.scaleSegments(
                LIGHTNING_BRANCH_SEGMENTS, LIGHTNING_BRANCH_SEGMENTS_MIN, detail);
        for (int k = 0; k < LIGHTNING_BRANCHES; k++) {
            s = rngNext(s);
            int anchor = 4 + (int) (rngFloat01(s) * (segs - 6));
            s = rngNext(s);
            if (k >= drawnBranches) {
                // rng 仍按原节奏推进（保持后续状态一致），但不绘制
                continue;
            }
            if (anchor < 1 || anchor >= segs) {
                continue;
            }
            drawLightningBranch(b, m, nx[anchor], ny[anchor], nz[anchor], s, intensity, core, glow, branchSegs);
        }

        // ===== 落地冲击：红色扩张环 + 适度地面强闪 + 白热落地核（尺寸收敛，更贴近原作）=====
        // 主冲击环（随进度向外扩张，幅度收敛）
        double rr = radius * (0.40 + 0.55 * easeOutCubic(clamp01(p / 0.70f)));
        glowRing(b, m, cx, cz, cy, rr, segmentsFor(rr, detail), glow,
                0.85f * intensity, 0.5f * intensity, 0.12, 0.70);
        glowRing(b, m, cx, cz, cy, rr * 0.70, segmentsFor(rr, detail), deep,
                0.6f * intensity, 0.32f * intensity, 0.09, 0.50);
        // 第二道追赶环（错相扩张，连续冲击感，幅度收敛）
        float w2 = clamp01((p - 0.10f) / 0.55f);
        if (w2 > 0f && w2 < 1f) {
            double rr2 = radius * 1.05 * easeOutCubic(w2);
            glowRing(b, m, cx, cz, cy, rr2, segmentsFor(rr2, detail), glow,
                    0.45f * (1f - w2) * intensity, 0.22f * (1f - w2) * intensity, 0.07, 0.45);
        }
        // 地面血色铺底（持续红晕，范围收敛）
        band(b, m, cx, cz, cy, 0.0, radius * 0.85, segmentsFor(radius, detail),
                deep[0], deep[1], deep[2], 0.0f, 0.16f * intensity);
        // 开场白热强闪（落地瞬间炸亮，范围收敛）
        if (p < 0.22f) {
            float flash = (0.22f - p) / 0.22f;
            band(b, m, cx, cz, cy, 0.0, radius * 0.95, segmentsFor(radius, detail),
                    core[0], core[1], core[2], 0.50f * flash, 0.0f);
            glowRing(b, m, cx, cz, cy, radius * 0.70, segmentsFor(radius, detail), core,
                    0.65f * flash, 0.35f * flash, 0.14, 0.55);
        }
        // 落地白热核（随强度明灭的中心亮点）
        spark(b, m, cx, cz, cy, (float) (radius * 0.16 + 0.22), core, 1.0f * intensity);
    }

    /**
     * 绘制一条闪电分叉：从锚点出发的几段蜿蜒短支（随机水平方向 + 略偏向下），逐段变细、变暗、末端收尖。
     *
     * @param ax        锚点世界坐标 X
     * @param ay        锚点世界坐标 Y
     * @param az        锚点世界坐标 Z
     * @param seed      分叉随机种子
     * @param intensity 整体强度（继承主干）
     * @param core      核心色（只读常量）
     * @param glow      辉光色（只读常量）
     * @param segs      分叉段数（v6 起由调用方按细节系数传入，截断尾部；
     *                  保留段的形状与全细节时逐点一致）
     */
    private static void drawLightningBranch(BufferBuilder b, Matrix4f m,
                                            double ax, double ay, double az,
                                            long seed, float intensity, float[] core, float[] glow, int segs) {
        long s = seed;
        s = rngNext(s);
        double ang = rngFloat01(s) * TAU;        // 随机水平方向
        s = rngNext(s);
        double vy = (rngFloat01(s) - 0.3) * 0.9; // 竖直分量，略偏向下
        double dirX = Math.cos(ang), dirZ = Math.sin(ang);
        double step = LIGHTNING_BRANCH_STEP;

        double px = ax, py = ay, pz = az;
        for (int i = 0; i < segs; i++) {
            s = rngNext(s);
            double jitterX = rngUnit(s) * 0.4;
            s = rngNext(s);
            double jitterZ = rngUnit(s) * 0.4;
            double qx = px + dirX * step + jitterX;
            double qy = py + vy * step;
            double qz = pz + dirZ * step + jitterZ;
            // 淡出比例仍按原段数计算，保证削减后末端亮度与全细节时的对应段一致
            float fade = 1f - (float) i / LIGHTNING_BRANCH_SEGMENTS;
            float a = intensity * fade;
            lightningSegment(b, m, px, py, pz, qx, qy, qz, LIGHTNING_GLOW_HALF * 0.6, glow, 0.45f * a, 0.0f);
            lightningSegment(b, m, px, py, pz, qx, qy, qz, LIGHTNING_CORE_HALF * 0.7, core, 0.85f * a, 0.1f * a);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    /**
     * 竖直 / 任意朝向的「十字双面」线段：沿世界 X、Z 轴各画一个四边形，使线段从任意水平视角皆可见。
     * <p>用于闪电电柱 / 分叉、月华柱、月尘、神圣净化的竖刃与升天光尘。
     * 两端 alpha 可不同（a1 起点、a2 终点）。双面绘制已开启，缠绕方向无所谓。</p>
     *
     * @param hw 线半宽（格）
     */
    private static void lightningSegment(BufferBuilder b, Matrix4f m,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2,
                                         double hw, float[] col, float a1, float a2) {
        float r = col[0], g = col[1], bb = col[2];
        // 面1：沿世界 X 轴加宽
        quad(b, m,
                x1 - hw, y1, z1, x1 + hw, y1, z1,
                x2 + hw, y2, z2, x2 - hw, y2, z2,
                r, g, bb, a1, a2);
        // 面2：沿世界 Z 轴加宽
        quad(b, m,
                x1, y1, z1 - hw, x1, y1, z1 + hw,
                x2, y2, z2 + hw, x2, y2, z2 - hw,
                r, g, bb, a1, a2);
    }

    /**
     * 画一个四边形（拆成两三角形）：顶点 a→b 用 alpha {@code aAB}，c→d 用 alpha {@code aCD}。
     */
    private static void quad(BufferBuilder b, Matrix4f m,
                             double ax, double ay, double az, double bx, double by, double bz,
                             double cxp, double cyp, double czp, double dx, double dy, double dz,
                             float r, float g, float bb, float aAB, float aCD) {
        b.vertex(m, (float) ax, (float) ay, (float) az).color(r, g, bb, aAB).endVertex();
        b.vertex(m, (float) bx, (float) by, (float) bz).color(r, g, bb, aAB).endVertex();
        b.vertex(m, (float) cxp, (float) cyp, (float) czp).color(r, g, bb, aCD).endVertex();

        b.vertex(m, (float) ax, (float) ay, (float) az).color(r, g, bb, aAB).endVertex();
        b.vertex(m, (float) cxp, (float) cyp, (float) czp).color(r, g, bb, aCD).endVertex();
        b.vertex(m, (float) dx, (float) dy, (float) dz).color(r, g, bb, aCD).endVertex();
    }

    /**
     * 闪电强度包络（还原原作龙雷：缓慢庄重明灭 + 慢消散，刻意低频以避免抽搐）。
     * <ul>
     *     <li>主体 body：前 60% 维持满亮，后 40% 平方渐隐——消散慢，能看清整个过程；</li>
     *     <li>缓慢大明灭 pulse：整段约 2 次温和的明暗起伏（0.72~1.0），庄重而不闪烁抽搐。</li>
     * </ul>
     * <b>刻意不叠高频抖动</b>——上一版的高频 jitter 是「抽搐」的元凶，已移除。明灭节奏由 pulse 的
     * 频率 {@code p * 6.3} 决定（一个完整 sin 周期取绝对值 ≈ 2 次明灭，很慢）；要更慢就调小该系数。
     * seed 仅用于让不同闪电的明灭相位错开。
     *
     * @param p    归一化进度
     * @param seed 闪电种子（错开明灭相位）
     * @return 强度系数（0~1）
     */
    private static float lightningIntensity(float p, long seed) {
        if (p >= 1f) {
            return 0f;
        }
        float body;
        if (p < 0.60f) {
            body = 1f;
        } else {
            float t = (p - 0.60f) / 0.40f;
            body = 1f - t * t;
        }
        float pulse = 0.72f + 0.28f * (float) Math.abs(Math.sin(p * 6.3 + (seed & 0x7)));
        return body * pulse;
    }

    // ============================== 几何基元（水平面） ==============================

    /**
     * 发光圆环：外辉 + 内辉 + 核心亮带，三层叠出柔和光环。
     * <p><b>本渲染器的首要顶点杠杆</b>：内部叠三层 {@code band}，单次调用即
     * {@code segs × 18} 个顶点，而本渲染器的每套演出都有 2~4 个环。调用方应传入
     * {@link #segmentsFor(double, float)} 的结果。</p>
     */
    private static void glowRing(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                 double radius, int segs, float[] col,
                                 float coreA, float glowA, double coreHalf, double glowSpread) {
        if (radius < 0.05) {
            return;
        }
        float r = col[0], g = col[1], bb = col[2];
        band(b, m, cx, cz, y, radius + coreHalf, radius + coreHalf + glowSpread, segs, r, g, bb, glowA, 0f);
        band(b, m, cx, cz, y, Math.max(0.0, radius - coreHalf - glowSpread), radius - coreHalf, segs,
                r, g, bb, 0f, glowA);
        band(b, m, cx, cz, y, Math.max(0.0, radius - coreHalf), radius + coreHalf, segs, r, g, bb, coreA, coreA);
    }

    /**
     * 圆环带（annulus），内/外边缘可分别指定 alpha；{@code rInner=0} 时退化为渐变圆盘。
     */
    private static void band(BufferBuilder builder, Matrix4f m, double cx, double cz, double cy,
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
     * 带宽度的线段（两点之间的细长四边形，两端 alpha 可不同）。水平面（y 固定）。
     */
    private static void line(BufferBuilder builder, Matrix4f m,
                             double x1, double z1, double x2, double z2, double y,
                             double hw, float r, float g, float b, float a1, float a2) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
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
     * 小菱形光点（火花），中心最亮、四角渐隐。水平面。
     * <p>仅 12 顶点，不参与分段缩放；是否绘制由调用方按保留阈值决定。</p>
     * <p>
     * <b>v7：四个角点内联为标量。</b>原实现用 {@code float[][]} 字面量表达角点，
     * 每次调用分配 <b>5 个临时数组</b>（1 个外层 + 4 个 {@code float[2]}）。
     * 而 {@link #starField} <b>每颗星屑都要调一次本方法</b>——单次 22 颗星屑的调用
     * 就是 110 个临时数组，且猩红立体花与癫火各要调两次 starField。
     * 改为标量后本方法零分配，顶点输出与顺序逐字不变。
     * </p>
     */
    private static void spark(BufferBuilder builder, Matrix4f m, double px, double pz, double y,
                              float size, float[] col, float alpha) {
        float r = col[0], g = col[1], b = col[2];
        float yf = (float) y;
        float cxF = (float) px, czF = (float) pz;

        // 四个角点（顺序与原 pts[0..3] 一致：北 → 东 → 南 → 西）
        float p0x = cxF, p0z = czF - size;
        float p1x = cxF + size, p1z = czF;
        float p2x = cxF, p2z = czF + size;
        float p3x = cxF - size, p3z = czF;

        sparkTri(builder, m, cxF, yf, czF, p0x, p0z, p1x, p1z, r, g, b, alpha);
        sparkTri(builder, m, cxF, yf, czF, p1x, p1z, p2x, p2z, r, g, b, alpha);
        sparkTri(builder, m, cxF, yf, czF, p2x, p2z, p3x, p3z, r, g, b, alpha);
        sparkTri(builder, m, cxF, yf, czF, p3x, p3z, p0x, p0z, r, g, b, alpha);
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
    private static void sparkTri(BufferBuilder builder, Matrix4f m,
                                 float cx, float y, float cz,
                                 float ax, float az, float bx, float bz,
                                 float r, float g, float b, float alpha) {
        builder.vertex(m, cx, y, cz).color(r, g, b, alpha).endVertex();
        builder.vertex(m, ax, y, az).color(r, g, b, 0f).endVertex();
        builder.vertex(m, bx, y, bz).color(r, g, b, 0f).endVertex();
    }

    /**
     * 放射线：count 条由内向外的径向线（尖端 alpha 可渐隐）。水平面。
     * <p>
     * <b>v6：按细节系数<u>步长抽取</u>，绝不能截断。</b>角度是 {@code rotation + TAU × i / count}
     * 均布的，若只画前 N 条，整套放射会退化成「只朝一侧喷」，破坏对称性。
     * 步长抽取时角度基准仍用<b>原始 count</b>，保证保留下来的线方位与全细节时完全一致。
     * </p>
     *
     * @param detail 本帧细节系数
     */
    private static void rays(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                             double rInner, double rOuter, int count, float rotation, double hw,
                             float[] col, float alphaInner, float alphaOuter, float detail) {
        int drawn = VisualLod.scale(count, detail);
        int step = Math.max(1, count / drawn);
        for (int i = 0; i < count; i += step) {
            double ang = rotation + (Math.PI * 2 * i) / count;
            double cos = Math.cos(ang), sin = Math.sin(ang);
            double ix = cx + rInner * cos, iz = cz + rInner * sin;
            double ox = cx + rOuter * cos, oz = cz + rOuter * sin;
            line(b, m, ix, iz, ox, oz, y, hw, col[0], col[1], col[2], alphaInner, alphaOuter);
        }
    }

    /**
     * 正多边形外框（N 条边首尾相连）。水平面。
     * <p>边数很少（5~7），共 30~42 顶点，却是法阵的辨识核心，<b>不参与 LOD 削减</b>。</p>
     */
    private static void polygonRing(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                    double radius, int sides, float rotation, double hw,
                                    float[] col, float alpha) {
        double prevX = 0, prevZ = 0;
        for (int i = 0; i <= sides; i++) {
            double ang = rotation + (Math.PI * 2 * i) / sides;
            double x = cx + radius * Math.cos(ang);
            double z = cz + radius * Math.sin(ang);
            if (i > 0) {
                line(b, m, prevX, prevZ, x, z, y, hw, col[0], col[1], col[2], alpha, alpha);
            }
            prevX = x;
            prevZ = z;
        }
    }

    /**
     * 星形多边形：把 points 个顶点按步距 step 互连（如 points=6,step=2 即六芒星两叠三角）。水平面。
     * <p>共 30~48 顶点，是「这是哪套演出」的唯一辨识依据，<b>不参与 LOD 削减</b>——
     * 顶点性价比极高，削它是纯亏。</p>
     */
    private static void starPolygon(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                    double radius, int points, int step, float rotation, double hw,
                                    float[] col, float alpha) {
        for (int i = 0; i < points; i++) {
            double a1 = rotation + (Math.PI * 2 * i) / points;
            double a2 = rotation + (Math.PI * 2 * ((i + step) % points)) / points;
            double x1 = cx + radius * Math.cos(a1), z1 = cz + radius * Math.sin(a1);
            double x2 = cx + radius * Math.cos(a2), z2 = cz + radius * Math.sin(a2);
            line(b, m, x1, z1, x2, z2, y, hw, col[0], col[1], col[2], alpha, alpha);
        }
    }

    /**
     * 旋转符文刻度环：沿圆周均布的 count 个短径向小段（内端略暗、外端亮）。水平面。
     * <p><b>v6：按细节系数步长抽取</b>（均布角度，同 {@link #rays}），角度基准用原始 count。</p>
     *
     * @param detail 本帧细节系数
     */
    private static void tickRing(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                 double rStart, double length, int count, float rotation, double hw,
                                 float[] col, float alpha, float detail) {
        double rEnd = rStart + length;
        int drawn = VisualLod.scale(count, detail);
        int step = Math.max(1, count / drawn);
        for (int k = 0; k < count; k += step) {
            double ang = rotation + (Math.PI * 2 * k) / count;
            double cos = Math.cos(ang), sin = Math.sin(ang);
            double ix = cx + rStart * cos, iz = cz + rStart * sin;
            double ox = cx + rEnd * cos, oz = cz + rEnd * sin;
            line(b, m, ix, iz, ox, oz, y, hw, col[0], col[1], col[2], alpha * 0.5f, alpha);
        }
    }

    /**
     * 闪烁星屑场：count 个确定性分布的小光点，各自正弦闪烁。水平面。
     * <p>
     * <b>v6：按细节系数直接减数量（截断尾部）。</b>分布用黄金角螺旋
     * （{@code ang = i × 2.399963}）+ 黄金比小数半径，<b>前 N 个本身即均匀铺满整个圆面</b>，
     * 这是黄金角螺旋的固有性质，故截断安全、不会出现「只剩中心一撮」的塌陷
     * （对比 {@code ScarletRotMistRenderer} 的 {@code sqrt(i/总数)} 分布，那种必须按步长抽取）。
     * </p>
     *
     * @param detail 本帧细节系数
     */
    private static void starField(BufferBuilder b, Matrix4f m, double cx, double cz, double y,
                                  double radius, int count, float time, float size,
                                  float[] col, float baseAlpha, float detail) {
        int drawn = VisualLod.scale(count, detail);
        for (int i = 0; i < drawn; i++) {
            double ang = i * 2.399963;
            double fr = (i * 0.6180339) - Math.floor(i * 0.6180339);
            double rr = radius * (0.15 + 0.82 * fr);
            double px = cx + rr * Math.cos(ang);
            double pz = cz + rr * Math.sin(ang);
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 6.0 + i * 1.7));
            spark(b, m, px, pz, y, size, col, baseAlpha * tw);
        }
    }

    // ============================== 数学 / 颜色辅助 ==============================
    // v7 说明：原先的 mix(float[], float[], float) 与 unpack(int) 已删除——
    // 二者是本类此前唯一的堆分配来源（每次调用 new float[3]），
    // 现全部由 VisualColor 的 constant() / mixInto() 取代。
    // 若后续新增演出需要混色，请一律走 VisualColor.mixInto(dst, a, b, t) + 复用缓冲，
    // 不要重新引入返回新数组的写法。

    /** 角度转弧度。 */
    private static float deg2rad(double deg) {
        return (float) (deg * Math.PI / 180.0);
    }

    /** 线性插值（t 自动夹取到 0~1）。 */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    /** 线半宽随半径缩放（最小 0.04 格）。 */
    private static double lineHalf(double radius) {
        return Math.max(0.04, radius * 0.012);
    }

    /**
     * 环分段数（随半径，夹取 36~72）。
     * <p>全细节下的基准值；带 LOD 的版本见 {@link #segmentsFor(double, float)}。</p>
     */
    private static int segmentsFor(double radius) {
        int v = (int) (radius * 3);
        if (v < 36) {
            return 36;
        }
        return Math.min(v, 72);
    }

    /**
     * 带细节层级的环分段数（v6 新增）。
     * <p>
     * 下限取 {@link #RING_SEGMENTS_MIN}(20)，比实体渲染器的 8~10 高不少——
     * 多边形与真圆的偏离量正比于半径，而本渲染器的环半径可达 14 格以上。
     * </p>
     *
     * @param radius 环半径（格）
     * @param detail 本帧细节系数
     * @return 缩放后的分段数
     */
    private static int segmentsFor(double radius, float detail) {
        return VisualLod.scaleSegments(segmentsFor(radius), RING_SEGMENTS_MIN, detail);
    }

    /** 淡入淡出包络：progress 在 [0,inEnd] 线性淡入、在 [outStart,1] 平滑淡出。 */
    private static float fadeInOut(float p, float inEnd, float outStart) {
        float fi = clamp01(p / inEnd);
        float fo = 1f - smoothstep(outStart, 1f, p);
        return fi * fo;
    }

    /** 平滑阶跃（Hermite）。 */
    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    /** 缓出（cubic）。 */
    private static double easeOutCubic(double t) {
        double inv = 1.0 - t;
        return 1.0 - inv * inv * inv;
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

    // ============================== 闪电用无分配伪随机（xorshift64） ==============================

    /**
     * xorshift64 推进一步。
     *
     * @param s 当前状态（非 0）
     * @return 下一状态
     */
    private static long rngNext(long s) {
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        return s;
    }

    /**
     * 由状态取 [0,1) 浮点。
     *
     * @param s 已推进的状态
     * @return [0,1)
     */
    private static float rngFloat01(long s) {
        return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    /**
     * 由状态取 [-1,1) 浮点。
     *
     * @param s 已推进的状态
     * @return [-1,1)
     */
    private static double rngUnit(long s) {
        return rngFloat01(s) * 2.0 - 1.0;
    }

    /**
     * 绝对值夹取到 ±max。
     *
     * @param v   输入
     * @param max 上限（正）
     * @return 夹取结果
     */
    private static double clampAbs(double v, double max) {
        if (v > max) {
            return max;
        }
        if (v < -max) {
            return -max;
        }
        return v;
    }
}
