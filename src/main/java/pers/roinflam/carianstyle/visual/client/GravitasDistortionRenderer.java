package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.enchantment.combatskill.EnchantmentGravitas;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 重力「压制」客户端渲染器（纯客户端自绘）。
 * <p>
 * 视觉分两类，分别对应重力附魔的两种作用对象：
 * <ol>
 *     <li><b>个人压制视觉</b>——判定依据 {@code entity.hasEffect(CarianStylePotion.GRAVITAS)}。
 *         核心是 {@link #drawCrushingCage}：一圈竖直立柱（十字双面三角面，与
 *         {@code AoeEffectRenderer#drawRedLightning} 的电柱建模手法一致）从头顶收拢到脚下，
 *         周期性收紧再重置；另配合脚下反复收缩的挤压环 + 向心汇聚的浮尘作为氛围补充；</li>
 *     <li><b>施法者力场范围圈</b>——判定依据 {@code ClientSyncEffectManager.shouldRenderEffect(
 *         EnchantmentGravitas.GRAVITY_FIELD_SERIAL, entityId)}（由 {@code EnchantmentGravitas}
 *         在力场激活 / 结束时主动同步），绘制以施法者为中心、半径
 *         {@code EnchantmentGravitas.FIELD_RADIUS} 格的地面范围圈。<b>出现 / 消失均带展开缓出 /
 *         收缩淡出动画</b>（{@link #FIELD_STATE} + {@link FieldAnimState}），与
 *         {@code AuraGroundRenderer} 里「圣域」等装备光环的出现/消失动画同一套逻辑——不再像
 *         早期版本那样跟随同步状态瞬间蹦出/消失。半径与实际判定半径共用同一个
 *         {@code EnchantmentGravitas.FIELD_RADIUS} 句柄（其值由服务端在登录时下发），不会出现「圈画的地方和实际生效范围
 *         对不上」的情况。</li>
 * </ol>
 * 两类视觉互不依赖、可同时出现。
 * </p>
 * <p>
 * <b>v2（性能，视觉零变化）：</b>接入 {@link VisualBatch} 与 {@link SharedEntityQuery}——
 * <ul>
 *     <li>不再自行设置 / 恢复 GL 状态、不再自行 {@code begin/end} 顶点缓冲，改为向
 *         {@link VisualBatch} 提供的共享缓冲写顶点，由其在本帧末统一提交；</li>
 *     <li><b>原先每帧要做两次范围实体查询</b>（受压制者 + 施法者，是全模组渲染器里唯一查两次的），
 *         现改为遍历 {@link SharedEntityQuery} 的每帧共享列表两遍、在循环内 {@code continue} 筛选，
 *         零额外列表分配；</li>
 *     <li>原先「受压制者为空且 FIELD_STATE 为空则提前返回」的判断，其唯一目的是跳过 GL 状态设置；
 *         GL 现由 {@link VisualBatch} 统一处理，该分支已无意义，故移除——两个循环在无对象时
 *         本就不做任何事，行为完全一致。</li>
 * </ul>
 * 判定条件、{@link #FIELD_STATE} 状态机的刷新时序、精确平方距离裁剪、绘制顺序
 * （先全部受压制者、再全部力场范围圈）与全部几何参数均未改动。
 * </p>
 * <p>
 * 渲染管线与 {@code ScarletRotMistRenderer} 同款：{@link RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，{@code POSITION_COLOR} 纯顶点绘制，无贴图、无原版粒子；
 * 顶点格式与着色器现由 {@link VisualBatch} 统一设置。
 * </p>
 *
 * <h3>v3（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 本渲染器的两类视觉<b>顶点量差了一个数量级</b>，必须分开看：
 * </p>
 * <pre>
 * 个人压制视觉（每个受压制实体）：
 *   向心浮尘（16 颗 × 8 段 × 3）           384
 *   脚下挤压环（2 环 × 28 段 × 6）         336
 *   收拢牢笼（8 柱 × 2 面 × 6）             96
 *   ─────────────────────────────────────────
 *   小计                                 ~816
 *
 * 施法者力场范围圈（每个施法者）：
 *   塌陷环（4 环 × 48 段 × 6）            1152
 *   边界主环（2 层 × 48 段 × 6）           576
 *   边界压制柱（20 根 × 2 面 × 6）         240
 *   底色填充（48 段 × 3）                  144
 *   ─────────────────────────────────────────
 *   小计                                ~2112
 * </pre>
 * <p>
 * 力场圈单个就顶得上两个半三重黄金树祝福，是全模组<b>单个视觉对象顶点量最高的</b>。
 * 好在它只属于正在施放力场的施法者，同屏数量通常是 0~2 个；而个人压制视觉则会
 * 覆盖力场半径内的<b>所有</b>敌人，群体交战时数量可观。
 * </p>
 *
 * <h4>力场圈的细节系数必须按「到边界的距离」算，不能按到中心的距离</h4>
 * <p>
 * 这是本次改造唯一需要偏离常规做法的地方。{@link #fieldRadius()} 取自
 * {@code EnchantmentGravitas.FIELD_RADIUS}，<b>可能大于 {@link VisualLod#FULL_DETAIL_RANGE}(12)</b>。
 * 若照搬其它渲染器「按实体中心的平方距离算 detail」的写法，会出现这样的荒谬情况：
 * </p>
 * <pre>
 * 玩家站在自己力场圈的内侧边缘
 *   → 到圈心距离 ≈ 半径（比如 16 格）
 *   → detail 按 16 格计算，被判定为"远"、大幅削减
 *   → 但那圈线就在玩家脚边，削减清晰可见
 * </pre>
 * <p>
 * 故力场圈改用<b>到圆环边界的近似距离</b>（{@code max(0, 到圈心距离 - 当前半径)}）来取 detail：
 * 人站在圈边缘时该值为 0、detail 恒为 1.0，看到的是满细节；只有整个圈都离得很远时才削减。
 * 这里需要一次 {@link Math#sqrt}，但力场圈同屏通常 0~2 个，开方成本可忽略。
 * </p>
 *
 * <h4>其余削减策略</h4>
 * <ul>
 *     <li><b>牢笼立柱完全不削</b>——8 根柱共 96 顶点却是「重力压制」的核心立体形状，
 *         且角度是 {@code TAU × i / 8} 均布的，减到 4 根就不成「笼」了。顶点性价比极高，削它是纯亏；</li>
 *     <li><b>浮尘与挤压环按数量 + 分段双削</b>——这两项占个人压制视觉的 88%，是主要杠杆。
 *         浮尘位置由 {@code seedFor(entityId, i)} 决定（角度纯随机、与下标无关），截断尾部安全；
 *         挤压环的相位是 {@code i / count} 均布，但它是「一圈接一圈往内收」的循环动画、
 *         没有固定方位，减环数只表现为「波与波之间隔得更开」，观感自然；</li>
 *     <li><b>边界压制柱按步长抽取</b>——20 根柱的角度是 {@code rot + TAU × i / 20} 均布的，
 *         截断前 N 根会让整圈栅栏<b>只剩一段圆弧上有柱子</b>、其余大半圈空着，
 *         明显破坏「边界警示」的语义。故按步长抽取，保证始终铺满整圈。</li>
 * </ul>
 *
 * <h3>v4（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v3 之后剩下的是旧 {@code unpack(color)} 的堆分配（<b>每次调用都 {@code new float[3]}</b>）：
 * </p>
 * <pre>
 * 向心浮尘（16 颗 × 1 次 lerpRgb→unpack）    16
 * 收拢牢笼（mid / core 各解包一次）            2
 * 脚下挤压环（2 环 × 1 次 lerpRgb→unpack）     2
 * 力场范围圈（deep / mid / core 各一次）        3
 * ──────────────────────────────────────────
 * 合计            ~20 次（个人）+ 3 次（力场）/ 帧
 * </pre>
 * <p>
 * 重力的施加面是<b>范围性</b>的——{@code EnchantmentGravitas} 一次触发就给 12 格内
 * 全部生物挂上压制效果，因此同屏受压制实体数量往往不是个位数。
 * 20 个受压制实体 × 60fps ≈ <b>每秒 2.4 万次</b>小数组分配。
 * </p>
 * <p>
 * 现改为两条路径（工具见 {@link VisualColor}）：三个主题色类加载时预解包为 {@code C_} 常量；
 * 两处真正随时间 / 逐元素变化的插值色写入 {@link #SCRATCH}。
 * </p>
 * <p>
 * <b>为什么本渲染器只需要一个缓冲：</b>挤压环与浮尘的颜色都是
 * 「算出来紧接着就被一次绘制调用消费掉」，任一时刻只有一个动态色存活。
 * 而 {@link #cagePillar} / {@link #cageQuad} 虽然<b>同时</b>持有顶部色与底部色，
 * 但那两个现在<b>都是只读常量</b>（力场柱传 {@code core}/{@code mid}，
 * 牢笼柱传 {@code mid}/{@code core}），彼此不会互相覆盖——
 * 只有当两端都是动态色时才必须开双缓冲。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#lerpInto} 保留了旧 {@code lerpRgb} 在 0~255 整数域
 * 插值并 {@link Math#round} 取整的行为，{@link VisualColor#constant} 与旧 {@code unpack}
 * 是同一个 {@code /255f} 公式，输出的每个颜色分量与 v3 数值相等。
 * </p>
 *
 * <h3>v5（堆分配，行为逐帧一致）：用帧号比对取代每帧的 HashSet</h3>
 * <p>
 * v4 把逐元素的颜色数组清干净了，但每帧开头还剩一个<b>结构性</b>的分配：
 * </p>
 * <pre>
 * Set&lt;Integer&gt; activeCasterIds = new HashSet&lt;&gt;();   // 每帧一个 HashSet
 * activeCasterIds.add(entity.getId());                    // 每次 add 装箱一个 Integer
 * </pre>
 * <p>
 * 它的用途只有一个：记住「本帧哪些施法者还在同步列表里」，好在下一段循环里判断
 * 谁该开始淡出。为此每帧要付出一个 HashSet（含桶数组）加若干 {@code Integer} 装箱——
 * 实体网络 id 通常远超 127，{@code Integer.valueOf} 的小值缓存<b>命中不了</b>，每个都是真分配。
 * </p>
 * <p>
 * <b>换个想法就完全不需要这个集合：</b>与其在外面维护一份「本帧出现过谁」的名单，
 * 不如让每个 {@link FieldAnimState} <b>自己记住上次被看到是哪一帧</b>
 * （{@link FieldAnimState#lastSeenFrame}）。刷新时写入当前帧号，
 * 随后判断「本帧没出现」就退化成一次 {@code int} 比较：
 * </p>
 * <pre>
 * // 旧：查集合（HashSet 分配 + Integer 装箱 + 哈希查找）
 * if (st.fadeStart &lt; 0f &amp;&amp; !activeCasterIds.contains(e.getKey())) { ... }
 *
 * // 新：比帧号（零分配，一次 int 比较）
 * if (st.fadeStart &lt; 0f &amp;&amp; st.lastSeenFrame != frameId) { ... }
 * </pre>
 * <p>
 * 帧号取自 {@link VisualBatch#frameId()}——它在同一阶段的 HIGHEST 优先级里自增，
 * 而本渲染器是默认的 NORMAL 优先级，因此读到的<b>必然是本帧的值</b>。
 * </p>
 * <p>
 * <b>行为完全一致：</b>「本帧出现过」与「lastSeenFrame == 当前帧号」是等价命题，
 * 淡出的触发时机、持续时间、状态机的其余部分一字未动。
 * </p>
 *
 * @author RoinFlam
 * @version 5
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class GravitasDistortionRenderer {
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int RING_SEGMENTS_MIN = 10;
    private static final int DUST_SEGMENTS_MIN = 4;
    private static final int FIELD_SEGMENTS_MIN = 20;
    private static final int GRAVITY_DEEP = 0x1B1030;
    private static final int GRAVITY_MID = 0x5A3AB0;
    private static final int GRAVITY_CORE = 0xC7B4FF;
    private static final float[] C_GRAVITY_DEEP = VisualColor.constant(GRAVITY_DEEP);
    private static final float[] C_GRAVITY_MID = VisualColor.constant(GRAVITY_MID);
    private static final float[] C_GRAVITY_CORE = VisualColor.constant(GRAVITY_CORE);
    private static final float[] SCRATCH = new float[VisualColor.RGB];
    private static final int CAGE_PILLAR_COUNT = 8;
    private static final float CAGE_HEIGHT_FACTOR = 1.9f;
    private static final float CAGE_RATE = 0.35f;
    private static final float CAGE_HALF_WIDTH = 0.06f;
    private static final float CAGE_OUTER_FACTOR = 1.7f;
    private static final int SQUEEZE_RING_COUNT = 2;
    private static final int RING_SEGMENTS = 28;
    private static final float SQUEEZE_RATE = 0.5f;
    private static final float SQUEEZE_OUTER_FACTOR = 1.2f;
    private static final float RING_HALF_WIDTH = 0.05f;
    private static final float SQUEEZE_ALPHA = 0.6f;
    private static final int DUST_COUNT = 16;
    private static final float DUST_RATE = 0.4f;
    private static final float DUST_SIZE = 0.08f;
    private static final int DUST_SEGMENTS = 8;
    private static final float DUST_ALPHA = 0.65f;
    private static final int FIELD_RING_SEGMENTS = 48;
    private static final float FIELD_RING_HALF_WIDTH = 0.09f;
    private static final int FIELD_FILL_SEGMENTS = 48;
    private static final float FIELD_FILL_ALPHA = 0.10f;
    private static final int FIELD_COLLAPSE_RING_COUNT = 4;
    private static final float FIELD_COLLAPSE_RATE = 0.18f;
    private static final int FIELD_PYLON_COUNT = 20;
    private static final float FIELD_PYLON_ROT_SPEED = 0.1f;
    private static final float FIELD_PYLON_HEIGHT = 1.6f;
    private static final float FIELD_APPEAR_DURATION = 0.3f;
    private static final float FIELD_FADE_DURATION = 0.35f;

    private static final class FieldAnimState {
        float appearTime;
        float fadeStart;
        int lastSeenFrame;
        double lastX;
        double lastY;
        double lastZ;
    }
    private static final Map<Integer, FieldAnimState> FIELD_STATE = new HashMap<>();
    private GravitasDistortionRenderer() {
    }
}

