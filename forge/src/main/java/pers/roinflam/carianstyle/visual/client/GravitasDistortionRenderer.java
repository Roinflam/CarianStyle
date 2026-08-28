package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.enchantment.combatskill.EnchantmentGravitas;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 重力「压制」客户端渲染器（纯客户端自绘）。
 * <p>
 * 视觉分两类，分别对应重力附魔的两种作用对象：
 * <ol>
 *     <li><b>个人压制视觉</b>——判定依据 {@code entity.hasEffect(CarianStylePotion.GRAVITAS.get())}。
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
 *         {@code EnchantmentGravitas.FIELD_RADIUS} 常量，不会出现「圈画的地方和实际生效范围
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
 * 这是本次改造唯一需要偏离常规做法的地方。{@link #FIELD_RADIUS} 取自
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
 * @author FlameForge
 * @version 5
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class GravitasDistortionRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== v3 LOD 下限 =====
    /** 脚下挤压环的最少分段数 */
    private static final int RING_SEGMENTS_MIN = 10;
    /** 浮尘柔光点的最少分段数：4 段仍是个饱满的菱形柔光块 */
    private static final int DUST_SEGMENTS_MIN = 4;
    /**
     * 力场圈各环 / 填充盘的最少分段数。
     * <p>比其它渲染器的下限高不少，因为力场圈半径大——多边形与真圆的偏离量正比于半径，
     * 20 段在 16 格半径下偏离约 20cm，再低就能看出明显的多边形棱角了。</p>
     */
    private static final int FIELD_SEGMENTS_MIN = 20;

    // ===== 配色（0xRRGGBB）=====
    private static final int GRAVITY_DEEP = 0x1B1030;
    private static final int GRAVITY_MID = 0x5A3AB0;
    private static final int GRAVITY_CORE = 0xC7B4FF;

    // ===== v4：预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    /** 重力深紫（力场底色填充；同时作为浮尘插值的起点端） */
    private static final float[] C_GRAVITY_DEEP = VisualColor.constant(GRAVITY_DEEP);
    /** 重力中紫（牢笼柱顶部 / 力场主环 / 压制柱底部） */
    private static final float[] C_GRAVITY_MID = VisualColor.constant(GRAVITY_MID);
    /** 重力亮紫（牢笼柱底部 / 力场辉光 / 塌陷环 / 压制柱顶部） */
    private static final float[] C_GRAVITY_CORE = VisualColor.constant(GRAVITY_CORE);

    /**
     * v4：动态插值色的复用缓冲（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * 仅用于两处随时间 / 逐元素变化的颜色：{@link #drawSqueezeRings} 的逐环收缩渐变、
     * {@link #drawInwardDust} 的逐粒汇聚渐变。二者顺序调用、互不嵌套，
     * 且都是「写入 → 紧接着被一次绘制调用消费」，故一个缓冲即可。
     * </p>
     * <p>
     * <b>{@link #cagePillar} 刻意不使用本缓冲</b>——牢笼柱与力场压制柱的两端色
     * 都是只读常量，无需动态计算，也就绕开了「两个动态色同时存活」这个坑（详见类注释）。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] SCRATCH = new float[VisualColor.RGB];

    // ===== 头顶收拢牢笼（个人压制视觉的核心立体形状）=====
    private static final int CAGE_PILLAR_COUNT = 8;
    /** 牢笼总高度系数（× 实体高度） */
    private static final float CAGE_HEIGHT_FACTOR = 1.9f;
    /** 收紧-重置的循环速度 */
    private static final float CAGE_RATE = 0.35f;
    private static final float CAGE_HALF_WIDTH = 0.06f;
    /** 牢笼起始半径系数（× 实体宽度，收紧终点约为该值的 35%） */
    private static final float CAGE_OUTER_FACTOR = 1.7f;

    // ===== 脚下挤压环（个人压制视觉，氛围补充）=====
    private static final int SQUEEZE_RING_COUNT = 2;
    private static final int RING_SEGMENTS = 28;
    private static final float SQUEEZE_RATE = 0.5f;
    private static final float SQUEEZE_OUTER_FACTOR = 1.2f;
    private static final float RING_HALF_WIDTH = 0.05f;
    private static final float SQUEEZE_ALPHA = 0.6f;

    // ===== 向心浮尘（个人压制视觉，氛围补充）=====
    private static final int DUST_COUNT = 16;
    private static final float DUST_RATE = 0.4f;
    private static final float DUST_SIZE = 0.08f;
    private static final int DUST_SEGMENTS = 8;
    private static final float DUST_ALPHA = 0.65f;

    // ===== 施法者力场范围圈 =====
    /** 范围圈半径（格），与 {@link EnchantmentGravitas#FIELD_RADIUS} 保持一致（int 隐式转 float） */
    private static final float FIELD_RADIUS = EnchantmentGravitas.FIELD_RADIUS;
    /** 范围圈分段数（半径较大，适当提高分段避免多边形感） */
    private static final int FIELD_RING_SEGMENTS = 48;
    private static final float FIELD_RING_HALF_WIDTH = 0.09f;
    /** 场地底色填充分段数与透明度：让整片受影响区域一眼可辨 */
    private static final int FIELD_FILL_SEGMENTS = 48;
    private static final float FIELD_FILL_ALPHA = 0.10f;
    /** 塌陷环数量与速度：从边界向心收拢并淡出，循环播放 */
    private static final int FIELD_COLLAPSE_RING_COUNT = 4;
    private static final float FIELD_COLLAPSE_RATE = 0.18f;
    /** 边界压制柱数量、旋转速度与高度（格） */
    private static final int FIELD_PYLON_COUNT = 20;
    private static final float FIELD_PYLON_ROT_SPEED = 0.1f;
    private static final float FIELD_PYLON_HEIGHT = 1.6f;
    /** 范围圈展开动画时长（秒） */
    private static final float FIELD_APPEAR_DURATION = 0.3f;
    /** 范围圈消失动画时长（秒） */
    private static final float FIELD_FADE_DURATION = 0.35f;

    /**
     * 力场范围圈的出现 / 消失动画状态（按施法者 entityId 索引）。
     * <p>与 {@code AuraGroundRenderer} 的 {@code AuraState} 同一思路：出现时展开 + 淡入，
     * 消失时收缩 + 淡出，而不是直接瞬间出现/消失；即便施法者暂时离开世界（卸载），
     * 也能用最近一次已知坐标原地把淡出动画播完。仅渲染线程访问。</p>
     */
    private static final class FieldAnimState {
        /** 出现时刻（秒，墙钟） */
        float appearTime;
        /** 开始消失的时刻（秒）；<0 表示仍激活（未开始淡出） */
        float fadeStart = -1f;
        /**
         * 上次「本帧仍在同步列表中」的帧号（v5 新增）。
         * <p>
         * 取代了原先每帧新建的 {@code Set<Integer> activeCasterIds}：
         * 刷新时写入 {@link VisualBatch#frameId()}，随后判断「本帧没出现」
         * 就退化成一次 {@code int} 比较，零分配、也不再有 {@code Integer} 装箱
         * （详见类注释「v5」小节）。
         * </p>
         * <p>
         * 初值 -1 是刻意的：{@link VisualBatch#frameId()} 从 0 起自增，
         * 用 -1 保证「刚 new 出来但还没被刷新过」的状态不会与任何真实帧号相等。
         * 实际上创建后会立刻被赋值，这只是防御。
         * </p>
         */
        int lastSeenFrame = -1;
        /** 最近一次的世界坐标（用于实体卸载后原地播完淡出） */
        double lastX;
        double lastY;
        double lastZ;
    }

    /** entityId -> 力场动画状态。 */
    private static final Map<Integer, FieldAnimState> FIELD_STATE = new HashMap<>();

    private GravitasDistortionRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有「受重力压制」与「正在施放重力场」的视觉。
     * <p>
     * v2：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，实体列表取自
     * {@link SharedEntityQuery} 的每帧共享查询（原先的两次范围查询改为对同一列表遍历两遍）。
     * v3：两类视觉各自按 {@link VisualLod} 的细节系数削减顶点；力场圈的系数按到边界的距离取
     * （详见类注释）。
     * v5：施法者的「本帧是否出现」改用帧号比对，不再每帧新建 {@code HashSet}。
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
        // 世界 / 玩家不可用：清空力场动画状态（与优化前一致），避免重进世界时实体 id 撞号残留
        if (mc.level == null || mc.player == null) {
            FIELD_STATE.clear();
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

        List<LivingEntity> candidates = SharedEntityQuery.livingEntitiesNearCamera(mc, cam);

        MobEffect gravitas = CarianStylePotion.GRAVITAS.get();

        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;
        // ⭐ v5：本帧帧号。VisualBatch 在同一阶段的 HIGHEST 优先级里自增，
        // 而本渲染器是默认的 NORMAL 优先级，故这里读到的必然是本帧的值
        int frameId = VisualBatch.frameId();

        // ===== 刷新力场范围圈的出现/消失状态（即使本帧没有受压制者也要做，
        // 否则「刚失去同步的力场」永远等不到淡出的机会）=====
        // v2：不再单独查询施法者列表，改为遍历共享列表筛选（共享列表已保证 isAlive）
        // v5：不再用 HashSet 记录「本帧出现过谁」，改为往状态里写帧号
        for (LivingEntity entity : candidates) {
            if (!ClientSyncEffectManager.shouldRenderEffect(
                    EnchantmentGravitas.GRAVITY_FIELD_SERIAL, entity.getId())) {
                continue;
            }
            int id = entity.getId();
            FieldAnimState st = FIELD_STATE.get(id);
            if (st == null) {
                st = new FieldAnimState();
                st.appearTime = time;
                FIELD_STATE.put(id, st);
            }
            st.fadeStart = -1f; // 仍激活：清除淡出标记（若此前在淡出会被“救回”）
            st.lastSeenFrame = frameId;
            st.lastX = entity.getX();
            st.lastY = entity.getY();
            st.lastZ = entity.getZ();
        }
        for (Map.Entry<Integer, FieldAnimState> e : FIELD_STATE.entrySet()) {
            FieldAnimState st = e.getValue();
            // ⭐ v5：「本帧没出现」等价于「lastSeenFrame 不是当前帧号」，
            // 一次 int 比较取代了原先的 HashSet 查找 + Integer 装箱
            if (st.fadeStart < 0f && st.lastSeenFrame != frameId) {
                st.fadeStart = time;
            }
        }

        // v2：原先此处有「受压制者为空且 FIELD_STATE 为空则提前返回」的分支，
        // 其唯一作用是跳过 GL 状态设置；GL 现由 VisualBatch 统一处理，该分支已无意义，故移除。
        // 下面两个循环在无对象时本就不做任何事，行为完全一致。

        Matrix4f matrix = VisualBatch.matrix();
        float rightX = VisualBatch.rightX();
        float rightY = VisualBatch.rightY();
        float rightZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        // ===== 1) 个人压制视觉：受重力压制的每个实体 =====
        // v2：原先作为查询谓词的 hasEffect 判定，现下沉为循环内筛选
        if (gravitas != null) {
            for (LivingEntity entity : candidates) {
                if (!entity.hasEffect(gravitas)) {
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

                // ⭐ v3：个人压制视觉尺寸与实体相当，按实体中心距离取细节系数即可
                // （与其它渲染器一致）。12 格内恒为 1.0，视觉与优化前一致
                float detail = VisualLod.detail(distSqr);
                VisualLod.countInstance();

                float width = entity.getBbWidth();
                float height = entity.getBbHeight();

                float rx = (float) dx;
                float ryFoot = (float) dy;
                float rz = (float) dz;

                drawCrushingCage(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId());
                drawSqueezeRings(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), detail);
                drawInwardDust(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                        rightX, rightY, rightZ, upX, upY, upZ, detail);
            }
        }

        // ===== 2) 施法者力场范围圈：按展开/淡出状态绘制（含已离开同步列表、正在淡出的） =====
        Iterator<Map.Entry<Integer, FieldAnimState>> fieldIt = FIELD_STATE.entrySet().iterator();
        while (fieldIt.hasNext()) {
            Map.Entry<Integer, FieldAnimState> entry = fieldIt.next();
            FieldAnimState st = entry.getValue();

            float radiusFactor;
            float animAlpha;
            if (st.fadeStart < 0f) {
                // 出现/稳定：展开 + 淡入
                float p = clamp01((time - st.appearTime) / FIELD_APPEAR_DURATION);
                float eased = easeOutCubic(p);
                radiusFactor = eased;
                animAlpha = eased;
            } else {
                // 消失：收缩 + 淡出
                float p = clamp01((time - st.fadeStart) / FIELD_FADE_DURATION);
                if (p >= 1f) {
                    fieldIt.remove(); // 淡出结束，移除状态
                    continue;
                }
                float v = 1f - p; // 1 -> 0
                radiusFactor = 0.6f + 0.4f * v; // 收缩到 ~60%
                animAlpha = v;
            }

            // 位置：施法者仍在世界中用实时插值坐标，否则用最近已知坐标原地播完淡出
            Entity rawEntity = mc.level.getEntity(entry.getKey());
            double wx, wy, wz;
            if (rawEntity instanceof LivingEntity liveEntity && liveEntity.isAlive()) {
                wx = Mth.lerp((double) partial, liveEntity.xo, liveEntity.getX());
                wy = Mth.lerp((double) partial, liveEntity.yo, liveEntity.getY());
                wz = Mth.lerp((double) partial, liveEntity.zo, liveEntity.getZ());
            } else {
                wx = st.lastX;
                wy = st.lastY;
                wz = st.lastZ;
            }

            double dx = wx - cam.x;
            double dy = wy - cam.y;
            double dz = wz - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue; // 太远：本帧不渲染，但保留状态，淡出计时继续
            }

            // ⭐ v3：力场圈的细节系数必须按「到圆环边界的距离」取，不能按到圈心的距离。
            // FIELD_RADIUS 可能大于 VisualLod.FULL_DETAIL_RANGE(12)，若按圈心距离算，
            // 玩家站在圈内侧边缘时（到圈心 ≈ 半径）会被判定为"远"并大幅削减，
            // 但那圈线其实就在脚边、削减清晰可见。改用到边界的近似距离后，
            // 贴着圈边时该值为 0、detail 恒为 1.0。开方成本可忽略（同屏力场圈通常 0~2 个）。
            float currentRadius = FIELD_RADIUS * radiusFactor;
            double edgeDist = Math.max(0.0, Math.sqrt(distSqr) - currentRadius);
            float detail = VisualLod.detail(edgeDist * edgeDist);
            VisualLod.countInstance();

            float rx = (float) dx;
            float ry = (float) dy + Y_OFFSET;
            float rz = (float) dz;

            drawFieldRange(builder, matrix, rx, ry, rz, time, entry.getKey(), radiusFactor, animAlpha, detail);
        }
    }

    // ==================== 个人压制视觉 ====================

    /**
     * 头顶收拢的立体牢笼：{@link #CAGE_PILLAR_COUNT} 根竖直立柱（十字双面三角面，
     * 与 {@code AoeEffectRenderer#drawRedLightning} 的电柱建模手法一致——沿世界 X、Z 轴
     * 各展开一个四边形，任意水平视角皆可见），半径随 {@link #CAGE_RATE} 周期性收紧后瞬间重置，
     * 象征重力场持续把周围空间压向实体所在的一点。柱身顶部偏暗、底部偏亮，突出「向下汇聚」。
     * <p>
     * <b>v3：完全不参与 LOD 削减，故不接收 detail 参数。</b>
     * 8 根柱共 96 顶点（仅占个人压制视觉的 12%）却是「重力压制」的核心立体形状；
     * 且角度是 {@code TAU × i / 8} 均布的，减到 4 根就不成「笼」了。顶点性价比这么高，削它是纯亏。
     * </p>
     * <p>v4：两个配色改用只读常量，本方法零分配。</p>
     */
    private static void drawCrushingCage(BufferBuilder b, Matrix4f m,
                                         float cx, float cyFoot, float cz, float width, float height,
                                         float time, int seedId) {
        float cageHeight = height * CAGE_HEIGHT_FACTOR;
        float t = frac(time * CAGE_RATE + seedId * 0.09f);
        // 半径随周期从较宽收拢到贴近身体，然后瞬间重置重新展开
        float outer = width * CAGE_OUTER_FACTOR;
        float radius = outer * (1f - 0.65f * easeOutCubic(t));
        float alpha = 0.5f + 0.35f * Mth.sin(t * (float) Math.PI);
        final float[] mid = C_GRAVITY_MID;
        final float[] core = C_GRAVITY_CORE;

        for (int i = 0; i < CAGE_PILLAR_COUNT; i++) {
            double ang = TAU * i / CAGE_PILLAR_COUNT + seedId * 0.2f;
            float cosA = (float) Math.cos(ang), sinA = (float) Math.sin(ang);
            float px = cx + cosA * radius;
            float pz = cz + sinA * radius;
            // 顶部略比底部宽一圈，营造「向内收拢」的锥笼感
            float topRadius = radius * 1.18f;
            float topX = cx + cosA * topRadius;
            float topZ = cz + sinA * topRadius;
            cagePillar(b, m, topX, cyFoot + cageHeight, topZ, px, cyFoot + Y_OFFSET, pz,
                    CAGE_HALF_WIDTH, mid, core, alpha);
        }
    }

    /**
     * 一根竖直牢笼柱：十字双面（沿世界 X、Z 轴各一个四边形），顶部用 {@code colTop} 且更暗淡，
     * 底部用 {@code colBottom} 且更亮，表现「重压自上而下汇聚」。
     * <p><b>注意：</b>本方法会同时读取 {@code colTop} 与 {@code colBottom}，
     * 调用方须保证二者不是同一个可写缓冲。当前两个调用点（牢笼柱、力场压制柱）
     * 传的都是只读常量，安全。</p>
     */
    private static void cagePillar(BufferBuilder b, Matrix4f m,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float hw, float[] colTop, float[] colBottom, float alpha) {
        // 面1：沿世界 X 轴展开
        cageQuad(b, m, x1 - hw, y1, z1, x1 + hw, y1, z1, x2 + hw, y2, z2, x2 - hw, y2, z2,
                colTop, colBottom, alpha);
        // 面2：沿世界 Z 轴展开
        cageQuad(b, m, x1, y1, z1 - hw, x1, y1, z1 + hw, x2, y2, z2 + hw, x2, y2, z2 - hw,
                colTop, colBottom, alpha);
    }

    private static void cageQuad(BufferBuilder b, Matrix4f m,
                                 float ax, float ay, float az, float bx, float by, float bz,
                                 float cxp, float cyp, float czp, float dx, float dy, float dz,
                                 float[] colTop, float[] colBottom, float alpha) {
        float topAlpha = alpha * 0.45f;
        b.vertex(m, ax, ay, az).color(colTop[0], colTop[1], colTop[2], topAlpha).endVertex();
        b.vertex(m, bx, by, bz).color(colTop[0], colTop[1], colTop[2], topAlpha).endVertex();
        b.vertex(m, cxp, cyp, czp).color(colBottom[0], colBottom[1], colBottom[2], alpha).endVertex();

        b.vertex(m, ax, ay, az).color(colTop[0], colTop[1], colTop[2], topAlpha).endVertex();
        b.vertex(m, cxp, cyp, czp).color(colBottom[0], colBottom[1], colBottom[2], alpha).endVertex();
        b.vertex(m, dx, dy, dz).color(colBottom[0], colBottom[1], colBottom[2], alpha).endVertex();
    }

    /**
     * 脚下反复收缩的挤压环：从外向内收拢并淡出，循环播放，作为牢笼之外的地面氛围补充。
     * <p>
     * <b>v3：环数与分段数按细节系数双削。</b>环的相位是 {@code i / count} 均布的，
     * 减少 count 会改变相位间隔——但它是「一圈接一圈往内收」的循环动画、没有固定方位，
     * 减环只表现为「波与波之间隔得更开」，观感自然，无需按步长抽取。
     * </p>
     * <p>v4：逐环颜色写入 {@link #SCRATCH} 后立即被 {@link #ring} 消费，零分配。</p>
     */
    private static void drawSqueezeRings(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz, float width,
                                         float time, int seedId, float detail) {
        float outer = width * SQUEEZE_OUTER_FACTOR;
        int count = VisualLod.scale(SQUEEZE_RING_COUNT, detail);
        int segments = VisualLod.scaleSegments(RING_SEGMENTS, RING_SEGMENTS_MIN, detail);

        for (int i = 0; i < count; i++) {
            float phase = (float) i / count;
            float t = frac(time * SQUEEZE_RATE + phase + seedId * 0.07f);
            float radius = outer * (1f - easeOutCubic(t));
            if (radius <= 0.06f) {
                continue;
            }
            float alpha = SQUEEZE_ALPHA * (1f - t) * smoothstep(0f, 0.15f, t);
            // v4：无分配插值（0~255 域取整，与旧 lerpRgb → unpack 链路逐位一致）
            VisualColor.lerpInto(SCRATCH, GRAVITY_MID, GRAVITY_CORE, 1f - t);
            ring(b, m, cx, cy, cz, radius, segments, RING_HALF_WIDTH, SCRATCH, alpha);
        }
    }

    /**
     * 向心汇聚的浮尘：从周围缓慢被吸向脚下中心点，抵达时淡出。
     * <p>
     * <b>v3：数量与分段数按细节系数双削。</b>浮尘是个人压制视觉里顶点量最大的一项（384，占 47%），
     * 位置由 {@code seedFor(entityId, i)} 决定且角度纯随机（与下标无关），
     * 截断尾部时保留浮尘的汇聚轨迹完全不变，靠近时是「逐渐多几粒尘」而非重新洗牌。
     * </p>
     * <p>
     * <b>v4：本方法是本渲染器分配最密集的一处</b>（16 次 / 实体 / 帧）。
     * 逐粒颜色写入 {@link #SCRATCH} 后立即被 {@link #emitSoftMote} 消费，零分配。
     * </p>
     */
    private static void drawInwardDust(BufferBuilder b, Matrix4f m,
                                       float cx, float cyFoot, float cz, float width, float height,
                                       float time, int seedId,
                                       float rightX, float rightY, float rightZ,
                                       float upX, float upY, float upZ, float detail) {
        float outerRadius = width * 1.4f;
        float riseSpan = height * 0.6f;
        int count = VisualLod.scale(DUST_COUNT, detail);
        int segments = VisualLod.scaleSegments(DUST_SEGMENTS, DUST_SEGMENTS_MIN, detail);

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float heightRand = rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);

            float t = frac(time * DUST_RATE + phase);
            float radius = outerRadius * (1f - t);
            float px = cx + (float) Math.cos(ang) * radius;
            float pz = cz + (float) Math.sin(ang) * radius;
            float py = cyFoot + riseSpan * heightRand * (1f - t) + Y_OFFSET;

            float alpha = DUST_ALPHA * (1f - smoothstep(0.7f, 1f, t)) * smoothstep(0f, 0.1f, t);
            if (alpha <= 0.01f) {
                continue;
            }

            // v4：无分配插值，写入复用缓冲后立即消费
            VisualColor.lerpInto(SCRATCH, GRAVITY_DEEP, GRAVITY_CORE, 1f - t);
            float size = DUST_SIZE * sizeRand;

            emitSoftMote(b, m, px, py, pz, size,
                    SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ, segments);
        }
    }

    // ==================== 施法者力场范围圈 ====================

    /**
     * 施法者力场范围圈：以施法者为中心、半径 {@link #FIELD_RADIUS}×{@code radiusFactor} 格的
     * 地面警示环。{@code radiusFactor} / {@code animAlpha} 由调用方按「出现展开 / 消失收缩淡出」
     * 的动画状态传入，不再跟随同步状态瞬间出现/消失。由「淡色底色填充」+「边界主环（双层辉光）」+
     * 「持续向心收拢的塌陷环」+「沿边界起伏明灭的立体压制柱栅栏」四部分组成。
     * <p>
     * <b>v3：全部元素按细节系数缩放。</b>本方法是全模组单个视觉对象顶点量最高的（约 2112），
     * 其中塌陷环一项就占 55%。注意 {@code detail} 由调用方按<b>到圆环边界的距离</b>算出
     * （而非到圈心），因此玩家贴着圈边站时仍是满细节（详见类注释）。
     * </p>
     * <p>
     * <b>边界压制柱必须按步长抽取。</b>20 根柱的角度是 {@code rot + TAU × i / 20} 均布的，
     * 截断前 N 根会让整圈栅栏只剩一段圆弧上有柱子、其余大半圈空着，
     * 明显破坏「边界警示」的语义。
     * </p>
     * <p>v4：三个配色改用只读常量，本方法零分配。</p>
     *
     * @param radiusFactor 半径缩放系数（出现时 0→1 展开，消失时 1→0.6 收缩）
     * @param animAlpha    整体透明度系数（出现淡入、消失淡出）
     * @param detail       细节系数（按到圆环边界的距离取，非到圈心）
     */
    private static void drawFieldRange(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz,
                                       float time, int seedId, float radiusFactor, float animAlpha,
                                       float detail) {
        if (animAlpha <= 0.01f || radiusFactor <= 0.02f) {
            return;
        }
        float radius = FIELD_RADIUS * radiusFactor;
        final float[] deep = C_GRAVITY_DEEP;
        final float[] mid = C_GRAVITY_MID;
        final float[] core = C_GRAVITY_CORE;

        // 力场圈半径大，分段下限比其它渲染器高（多边形偏离量正比于半径）
        int fillSegments = VisualLod.scaleSegments(FIELD_FILL_SEGMENTS, FIELD_SEGMENTS_MIN, detail);
        int ringSegments = VisualLod.scaleSegments(FIELD_RING_SEGMENTS, FIELD_SEGMENTS_MIN, detail);

        // 淡色底色填充：让整片受影响区域一眼可辨（否则大半径的环单独看太不明显）
        float fillBreath = 0.85f + 0.15f * Mth.sin(time * 0.7f + seedId * 0.3f);
        disc(b, m, cx, cy, cz, radius, fillSegments, deep, FIELD_FILL_ALPHA * fillBreath * animAlpha);

        // 边界主环：固定半径，随呼吸轻微明灭（内层实线 + 外层柔光）
        float breath = 0.8f + 0.2f * Mth.sin(time * 1.0f + seedId * 0.4f);
        ring(b, m, cx, cy, cz, radius, ringSegments, FIELD_RING_HALF_WIDTH, mid,
                0.7f * breath * animAlpha);
        ring(b, m, cx, cy, cz, radius, ringSegments, FIELD_RING_HALF_WIDTH * 3.2f, core,
                0.24f * breath * animAlpha);

        // 持续向心收拢的塌陷环：从边界向中心收拢并淡出，循环播放。
        // 这是本方法顶点量最大的一项（占 55%），故数量也参与缩放；
        // 与挤压环同理，它是循环推进的波、没有固定方位，减环数只表现为波间隔变大
        int collapseCount = VisualLod.scale(FIELD_COLLAPSE_RING_COUNT, detail);
        for (int i = 0; i < collapseCount; i++) {
            float phase = (float) i / collapseCount;
            float t = frac(time * FIELD_COLLAPSE_RATE + phase + seedId * 0.09f);
            float collapseRadius = radius * (1f - t);
            if (collapseRadius <= 0.3f) {
                continue;
            }
            float alpha = 0.5f * (1f - t) * smoothstep(0f, 0.1f, t) * animAlpha;
            ring(b, m, cx, cy, cz, collapseRadius, ringSegments, FIELD_RING_HALF_WIDTH * 0.75f,
                    core, alpha);
        }

        // 边界压制柱栅栏：均布的立体短柱，高度随时间起伏明灭，替代纯平面刻度线，
        // 给范围圈补上真正的立体轮廓。
        // ⭐ v3：均布角度按步长抽取，绝不能截断——否则整圈栅栏只剩一段圆弧上有柱子
        float rot = time * FIELD_PYLON_ROT_SPEED + seedId * 0.2f;
        int drawnPylons = VisualLod.scale(FIELD_PYLON_COUNT, detail);
        int pylonStep = Math.max(1, FIELD_PYLON_COUNT / drawnPylons);
        for (int i = 0; i < FIELD_PYLON_COUNT; i += pylonStep) {
            double ang = rot + TAU * i / FIELD_PYLON_COUNT;
            float px = cx + (float) Math.cos(ang) * radius;
            float pz = cz + (float) Math.sin(ang) * radius;
            float bob = 0.5f + 0.5f * Mth.sin(time * 1.6f + i * 0.9f + seedId);
            float h = FIELD_PYLON_HEIGHT * (0.45f + 0.65f * bob);
            float alpha = (0.5f + 0.3f * bob) * animAlpha;
            cagePillar(b, m, px, cy + h, pz, px, cy, pz, FIELD_RING_HALF_WIDTH, core, mid, alpha);
        }
    }

    // ==================== 几何 / billboard 基元 ====================

    /** 画一个水平径向渐变圆盘（中心 alpha、边缘更淡），用作力场底色填充。 */
    private static void disc(BufferBuilder b, Matrix4f m,
                             float cx, float cy, float cz, float radius, int segments,
                             float[] col, float centerAlpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = (TAU * i) / segments;
            double a1 = (TAU * (i + 1)) / segments;
            float x0 = cx + radius * (float) Math.cos(a0);
            float z0 = cz + radius * (float) Math.sin(a0);
            float x1 = cx + radius * (float) Math.cos(a1);
            float z1 = cz + radius * (float) Math.sin(a1);
            b.vertex(m, cx, cy, cz).color(col[0], col[1], col[2], centerAlpha).endVertex();
            b.vertex(m, x0, cy, z0).color(col[0], col[1], col[2], centerAlpha * 0.4f).endVertex();
            b.vertex(m, x1, cy, z1).color(col[0], col[1], col[2], centerAlpha * 0.4f).endVertex();
        }
    }

    /** 画一圈水平圆环（内外两侧渐隐的窄带）。 */
    private static void ring(BufferBuilder b, Matrix4f m,
                             float cx, float cy, float cz, float radius, int segments,
                             float halfWidth, float[] col, float alpha) {
        float rInner = Math.max(0f, radius - halfWidth);
        float rOuter = radius + halfWidth;
        for (int i = 0; i < segments; i++) {
            double a0 = (TAU * i) / segments;
            double a1 = (TAU * (i + 1)) / segments;
            float ox0 = cx + rOuter * (float) Math.cos(a0);
            float oz0 = cz + rOuter * (float) Math.sin(a0);
            float ox1 = cx + rOuter * (float) Math.cos(a1);
            float oz1 = cz + rOuter * (float) Math.sin(a1);
            float ix0 = cx + rInner * (float) Math.cos(a0);
            float iz0 = cz + rInner * (float) Math.sin(a0);
            float ix1 = cx + rInner * (float) Math.cos(a1);
            float iz1 = cz + rInner * (float) Math.sin(a1);

            b.vertex(m, ox0, cy, oz0).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ox1, cy, oz1).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(col[0], col[1], col[2], alpha).endVertex();

            b.vertex(m, ox0, cy, oz0).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(col[0], col[1], col[2], alpha).endVertex();
            b.vertex(m, ix0, cy, iz0).color(col[0], col[1], col[2], alpha).endVertex();
        }
    }

    /**
     * 绘制一颗面向相机的柔和圆形光点（径向渐变：中心 alpha、边缘 0）。
     *
     * @param segments 分段数。v3 起由调用方按细节系数传入，下限 {@link #DUST_SEGMENTS_MIN}；
     *                 全细节时即 {@link #DUST_SEGMENTS}。浮尘是个人压制视觉的主要顶点杠杆。
     */
    private static void emitSoftMote(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float size,
                                     float r, float g, float bl, float alpha,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ, int segments) {
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= segments; i++) {
            float ang = TAU * i / segments;
            float ca = (float) Math.cos(ang) * size;
            float sa = (float) Math.sin(ang) * size;
            float ex = cx + rightX * ca + upX * sa;
            float ey = cy + rightY * ca + upY * sa;
            float ez = cz + rightZ * ca + upZ * sa;
            if (i > 0) {
                b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
                b.vertex(m, pex, pey, pez).color(r, g, bl, 0f).endVertex();
                b.vertex(m, ex, ey, ez).color(r, g, bl, 0f).endVertex();
            }
            pex = ex;
            pey = ey;
            pez = ez;
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

    // ==================== 数学 / 颜色辅助 ====================
    // v4 说明：原先的 lerpRgb(int, int, float) 与 unpack(int) 已删除——
    // 二者是本类此前唯一的堆分配来源（unpack 每次 new float[3]），
    // 现全部由 VisualColor 的 constant() / lerpInto() 取代。
    // 若后续新增元素需要动态配色，请一律走 VisualColor.lerpInto(dst, from, to, t) + 复用缓冲，
    // 不要重新引入返回新数组的写法。

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = (x - e0) / (e1 - e0);
        if (t < 0f) {
            t = 0f;
        } else if (t > 1f) {
            t = 1f;
        }
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
