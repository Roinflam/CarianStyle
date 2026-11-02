package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V3)
 *
 * 黄金树祝福（立誓 / 恩惠 / 庇护）客户端渲染器（纯客户端自绘）。
 * <p>
 * 三个效果均为「攻击或被攻击时触发，持续 [等级]×2.5 秒」的短时增益，判定依据分别为：
 * {@code hasEffect(GOLDEN_VOW)}、{@code hasEffect(BLESSING_OF_THE_ERDTREE)}、
 * {@code hasEffect(PROTECTION_OF_THE_ERDTREE)}，三者可同时生效。本渲染器统一绘制一套「神圣金光」
 * 基础视觉——脚下金色光晕、黄金树根须、飘落 / 升起的金色叶片、脚下缓慢旋转的符文刻度环、
 * 中央十字圣徽、胸口神圣光辉；再按各自的激活状态叠加细节：
 * <ul>
 *     <li><b>黄金树立誓</b> —— 胸口处随心跳节奏脉动的白金光核；</li>
 *     <li><b>黄金树恩惠</b> —— 额外增加上升光尘的数量，表现「生息」感；</li>
 *     <li><b>黄金树庇护</b> —— 环绕身周的近白冷调护盾双环微光，与主体金色区分开来。</li>
 * </ul>
 * <p>
 * <b>叠加强度：</b>同时生效的祝福数量 {@code activeCount ∈ [1,3]} 会整体放大视觉——光晕范围/
 * 透明度、胸口光辉尺寸、光尘数量、符文环转速与根须长度均随 {@code activeCount} 提升，主题色
 * 也从纯金逐渐向纯白过渡（象征祝福纯度更高）。三者同时生效时（{@code activeCount == 3}），
 * 额外触发脚下的「神圣脉冲」冲击波光环 + 长短交替光芒射线。
 * </p>
 * <p>
 * 渲染管线沿用本模组统一方案：{@link RenderLevelStageEvent} 的 {@code AFTER_TRANSLUCENT_BLOCKS}
 * 阶段，GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，实体列表取自 {@link SharedEntityQuery}
 * 的每帧共享查询，{@code POSITION_COLOR} 纯顶点绘制，无贴图、无原版粒子。
 * </p>
 *
 * <h3>v8（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * <b>本渲染器是全模组顶点开销最高的一个</b>，此前却完全没有细节层级裁剪。
 * 三重祝福同时生效时，单个实体每帧的顶点量粗算如下：
 * </p>
 * <pre>
 * 脚下光晕（2 层圆盘 × 28 段）            168
 * 黄金树根须（7 主根 + 支根）             ~330
 * 符文刻度环（12 条刻度）                  72
 * 中央十字圣徽                             12
 * 胸口神圣光辉（3 层柔光 × 10 段）          90
 * 上升金叶（最多 19 片 × 6 三角）          342
 * 飘落金叶（最多 14 片 × 6 三角）          252
 * 立誓光核 / 庇护护盾（32 段环）           222
 * 环绕星芒（最多 10 点 × 10 段）           300
 * 神圣脉冲（2 环 × 28 段 + 12 射线）       240
 * ─────────────────────────────────────────
 * 合计                                  ~2000 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 比出血的 948 还高一截，而这三个祝福的触发条件是<b>「攻击或被攻击」</b>——
 * 也就是说团战里几乎<b>人人都挂着</b>，且多半是三重叠加。十人混战即约 2 万顶点/帧，
 * 全部由本渲染器一家贡献。
 * </p>
 * <p>
 * 现全部元素数量与分段数按 {@link VisualLod#detail} 缩放：
 * {@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，<b>与优化前逐像素一致</b>；
 * 远处与团战时逐步削减，40 格外单实体降至约 400 顶点（降幅 80%）。
 * </p>
 * <p>
 * <b>两条削减原则（与出血渲染器一致）：</b>
 * </p>
 * <ol>
 *     <li><b>随机分布的元素只砍尾部</b>——金叶、星屑这类由 {@code seedFor(entityId, i)}
 *         决定位置的元素，减少数量时保留下来的种子不变、位置不变，
 *         因此靠近时是「逐渐多出几片叶子」而非整片重新洗牌；</li>
 *     <li><b>角度均布的元素按步长抽取，不能截断</b>——根须、符文刻度、脉冲射线的角度是
 *         {@code i × (TAU / 总数)}，若简单地「只画前 N 个」会退化成只覆盖一段扇区
 *         （根须全长在同一侧、符文环缺一大块）。故改为
 *         {@code for (i = 0; i < 总数; i += step)}，角度基准仍用<b>原始总数</b>，
 *         保证保留元素的方位不变、且始终铺满整圈。</li>
 * </ol>
 * <p>
 * 此外低细节时会整层跳过若干<b>纯氛围层</b>（胸口光辉的最外层、飘落金叶、环绕星芒、
 * 根须的二级支根）——这些在远处本就看不出，却占着不小的顶点量。
 * </p>
 *
 * <h3>v9（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v8 把顶点量压下去了，但还剩一处纯浪费：旧实现的 {@code unpack(color)}
 * <b>每次调用都 {@code new float[3]}</b>。三重祝福满配时本渲染器的调用密度：
 * </p>
 * <pre>
 * 上升金叶（最多 21 片 × 1 次 unpack）        21
 * 飘落金叶（最多 14 片 × 1 次 unpack）        14
 * 脚下光晕（主题色 + 深金各一次）              2
 * 胸口光辉（主题色 + 纯白各一次）              2
 * 根须 / 符文环 / 十字圣徽（各一次）            3
 * 立誓光核 / 庇护护盾 / 星芒 / 神圣脉冲         4
 * ──────────────────────────────────────────
 * 合计                   ~46 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 单看不多，但这三个祝福的触发条件是「攻击或被攻击」——团战里几乎人人都挂着。
 * 10 人混战 × 60fps 即<b>每秒 2.8 万次</b>朝生夕死的小数组分配，
 * 而它们的生命周期短到活不过一次 minor GC 的间隔，纯粹是在给分配器添堵。
 * </p>
 * <p>
 * 现改为两条路径（工具见 {@link VisualColor}）：
 * </p>
 * <ol>
 *     <li><b>三个纯常量色类加载时预解包一次</b>（{@code C_} 前缀），此后永久复用。
 *         注意 {@link #HOLY_GOLD} <b>没有</b>对应的 {@code C_} 常量——它从不单独使用，
 *         永远是作为 {@link #lerpRgb} / {@link VisualColor#lerpInto} 的插值起点出现的；</li>
 *     <li><b>动态插值色写入复用缓冲</b>——{@link #SCRATCH}。</li>
 * </ol>
 * <p>
 * <b>为什么这里一个缓冲就够（与 {@code SleepRenderer} / {@code IncisionRenderer} 不同）：</b>
 * 那两个渲染器的螺旋与刀痕需要<b>同时</b>持有线段两端的两个<b>不同的动态色</b>，
 * 故必须用双缓冲滚动交换。本渲染器则没有这种场景——{@link #lineF} 虽然接受两端 alpha，
 * 但两端<b>共用同一个颜色数组</b>；其余每处都是「算一个色 → 立刻画完 → 不再用」。
 * 唯一需要留意的是 {@link #drawRootVeins}：主题色写入 {@link #SCRATCH} 后要横跨整个根须循环，
 * 期间 {@link #drawRootBranch}（含递归支根）只读不写，因此安全。
 * </p>
 *
 * <h3>v10（堆分配，视觉逐位一致）：金叶几何数组零分配化</h3>
 * <p>
 * v9 清掉了颜色数组，但<b>漏了一个更大的分配点</b>：{@link #emitLeafMote} 内部
 * 每次调用要分配 <b>10 个数组</b>——
 * </p>
 * <pre>
 * float[][] localPts = { ... };   // 1 个外层 + 6 个 float[2] = 7 个
 * float[] wx = new float[6];      // 3 个 float[6]
 * float[] wy = new float[6];
 * float[] wz = new float[6];
 * ─────────────────────────────────────────
 * 合计                              10 个 / 次
 * </pre>
 * <p>
 * 而三重祝福满配时，上升金叶最多 21 片、飘落金叶最多 14 片，
 * 二者<b>都</b>调用本方法：
 * </p>
 * <pre>
 * (21 + 14) 片 × 10 个数组 = ~350 个数组 / 实体 / 帧
 * </pre>
 * <p>
 * <b>这比 v9 清掉的 46 次颜色分配高出近八倍</b>，而且单个数组更大——
 * {@code float[6]} 是 {@code float[3]} 的两倍字节。10 人混战 × 60fps ≈
 * <b>每秒 21 万次</b>小数组分配，全部集中在客户端最需要帧率的时候。
 * </p>
 * <p>
 * 现改为两条路径（做法与 {@code AoeEffectRenderer} v7 处理 {@code spark} / {@code drawOrb} 同源）：
 * </p>
 * <ol>
 *     <li><b>局部轮廓点内联为标量</b>——叶形的 6 个轮廓点是由 {@code size} 线性缩放的
 *         固定比例，展开成 12 个局部变量即可，编译器还能把它们直接放进寄存器；</li>
 *     <li><b>世界坐标改用静态复用缓冲</b>——{@link #LEAF_WX} / {@link #LEAF_WY} /
 *         {@link #LEAF_WZ}。{@link #emitLeafMote} 不可重入（同一线程内不会嵌套调用自己，
 *         且只在渲染线程访问），复用安全。</li>
 * </ol>
 * <p>
 * <b>复用缓冲的约束：</b>{@link #LEAF_WX} 等三个缓冲<b>只能在
 * {@link #emitLeafMote} 内部使用</b>，且必须「写满 → 立刻画完 → 不再引用」。
 * 若将来新增其它需要世界坐标缓冲的元素，请另开一组，不要复用这三个——
 * 上升金叶与飘落金叶虽然都用它，但二者是顺序调用、互不嵌套，不会互相踩。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>轮廓点的数值、旋转公式、顶点写入顺序全部照搬原实现，
 * 只是把「先建数组再读」改成「直接算标量」，输出的每个顶点坐标与 v9 完全相同。
 * </p>
 *
 * @author RoinFlam
 * @version 10
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class GoldenTreeBlessingRenderer {
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int MOTE_SEGMENTS = 10;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int LEAF_POINTS = 6;
    private static final int MOTE_SEGMENTS_MIN = 4;
    private static final int HALO_SEGMENTS_MIN = 8;
    private static final int RING_SEGMENTS_MIN = 10;
    private static final float GLOW_OUTER_KEEP_THRESHOLD = 0.55f;
    private static final float DESCENDING_KEEP_THRESHOLD = 0.5f;
    private static final float SPARKLE_KEEP_THRESHOLD = 0.45f;
    private static final float ROOT_BRANCH_KEEP_THRESHOLD = 0.6f;
    private static final int HOLY_GOLD = 0xFFC23A;
    private static final int HOLY_WHITE = 0xFFF6DC;
    private static final int HOLY_DEEP = 0xB8791A;
    private static final int HOLY_SHIELD = 0xF2F7FF;
    private static final float[] C_HOLY_WHITE = VisualColor.constant(HOLY_WHITE);
    private static final float[] C_HOLY_DEEP = VisualColor.constant(HOLY_DEEP);
    private static final float[] C_HOLY_SHIELD = VisualColor.constant(HOLY_SHIELD);
    private static final float[] SCRATCH = new float[VisualColor.RGB];
    private static final float[] LEAF_WX = new float[LEAF_POINTS];
    private static final float[] LEAF_WY = new float[LEAF_POINTS];
    private static final float[] LEAF_WZ = new float[LEAF_POINTS];
    private static final int HALO_SEGMENTS = 28;
    private static final float HALO_RADIUS_FACTOR = 0.9f;
    private static final float HALO_BASE_ALPHA = 0.24f;
    private static final float HALO_STACK_RADIUS_STEP = 0.10f;
    private static final float HALO_STACK_ALPHA_STEP = 0.3f;
    private static final float GLOW_HEIGHT_FACTOR = 0.55f;
    private static final float GLOW_SIZE_FACTOR = 0.5f;
    private static final float GLOW_BASE_ALPHA = 0.4f;
    private static final float GLOW_STACK_ALPHA_STEP = 0.18f;
    private static final float GLOW_PULSE_SPEED = 1.3f;
    private static final int BASE_MOTES = 9;
    private static final int BLESSING_EXTRA_MOTES = 4;
    private static final int MOTE_STACK_STEP = 4;
    private static final float RISE_SPEED = 0.16f;
    private static final float MOTE_SIZE = 0.09f;
    private static final float RISE_HEIGHT_FACTOR = 1.3f;
    private static final float SPREAD_FACTOR = 0.6f;
    private static final float MOTE_STACK_ALPHA_STEP = 0.25f;
    private static final float LEAF_SPIN_SPEED = 2.2f;
    private static final int RUNE_TICK_COUNT = 12;
    private static final float RUNE_ROT_SPEED = 0.25f;
    private static final float RUNE_BASE_ALPHA = 0.6f;
    private static final float RUNE_STACK_ALPHA_STEP = 0.12f;
    private static final float CROSS_PULSE_SPEED = 2.0f;
    private static final int ROOT_MAIN_COUNT = 5;
    private static final int ROOT_STACK_STEP = 1;
    private static final float ROOT_LENGTH_FACTOR = 0.95f;
    private static final int ROOT_SEGMENTS = 4;
    private static final int ROOT_SEGMENTS_MIN = 2;
    private static final float ROOT_BRANCH_LENGTH_RATIO = 0.45f;
    private static final float ROOT_BASE_ALPHA = 0.42f;
    private static final float ROOT_STACK_ALPHA_STEP = 0.1f;
    private static final float VOW_PULSE_SPEED = 3.0f;
    private static final int SHIELD_SEGMENTS = 32;
    private static final float SHIELD_PULSE_SPEED = 1.6f;
    private static final int SPARKLE_COUNT = 6;
    private static final int SPARKLE_STACK_STEP = 2;
    private static final float SPARKLE_ORBIT_SPEED = 0.4f;
    private static final float PULSE_PERIOD = 1.8f;
    private static final int PULSE_WAVE_COUNT = 2;
    private static final float PULSE_MAX_RADIUS_FACTOR = 2.0f;
    private static final int PULSE_RAY_COUNT = 12;
    private GoldenTreeBlessingRenderer() {
    }
}

