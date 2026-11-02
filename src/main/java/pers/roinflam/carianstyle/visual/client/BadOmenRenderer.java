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
import pers.roinflam.carianstyle.network.BadOmenSyncHandler;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 噩兆「诅咒」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@code MobEffectBadOmen}：目标攻击力 -20%、攻速 -20%、受到伤害 +25%、治疗量 -50%。
 * 由 {@code EnchantmentBadOmen} 攻击时施加，且该附魔的核心机制是
 * <b>「对已带噩兆的目标额外造成 50% 伤害」</b>——因此这套视觉的首要职责是让玩家
 * <b>一眼看出目标身上有没有噩兆</b>，从而知道下一击能否吃到加成。
 * </p>
 * <p>
 * <b>判定采用双重冗余：</b>{@code hasEffect(BAD_OMEN)} <b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(BAD_OMEN_SERIAL, id)}。
 * 与睡眠同理，噩兆几乎总是施加给正在交战的敌人，后者才是主力链路
 * （详见 {@link BadOmenSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>视觉区分（关键设计约束）：</b>本模组已有的红色系演出相当密集——出血是鲜红飞溅、
 * 切腹是暗红上涌、猩红腐败是深红孢子、龙雷是血红电柱。若噩兆再用红色主体，
 * 同屏叠加时完全无法分辨。故本渲染器：
 * <ul>
 *     <li><b>以墨黑为主体</b>，暗锈红只用于描边与尖端——远看是「一团黑」而非「一团红」；</li>
 *     <li><b>运动方向向下沉降</b>（污浊下滴、荆棘扎地），与出血的向外迸溅、
 *         切腹的向上蒸腾、腐败的向上升腾全部相反；</li>
 *     <li><b>标志物是尖锐的黑荆棘</b>——直线尖刺造型在本模组独一无二
 *         （其余演出都是圆环、弧带、星形、雾团）。</li>
 * </ul>
 * </p>
 * <p>
 * <b>四个元素：</b>
 * <ol>
 *     <li><b>身周黑荆棘</b>（{@link #drawThorns}）——核心标志。自地面向上并向外倾斜扎出的
 *         尖锐黑刺，底宽顶尖，随时间缓慢起伏（像在呼吸的活物）；</li>
 *     <li><b>头顶诅咒断环</b>（{@link #drawCurseRing}）——悬浮于头顶、逆向缓慢旋转的
 *         黑色断裂符文环，尖端染病态暗金；</li>
 *     <li><b>污浊滴落</b>（{@link #drawOoze}）——自躯干向下滴落的黑色短竖线，循环下沉、
 *         近地面淡出；</li>
 *     <li><b>不祥浸染盘</b>（{@link #drawBlightPool}）——脚下的墨黑渐变盘，压住整体重量。</li>
 * </ol>
 * </p>
 * <p>
 * 渲染管线沿用本模组统一方案：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，
 * 实体列表取自 {@link SharedEntityQuery} 的每帧共享查询，{@code POSITION_COLOR} 纯顶点绘制。
 * </p>
 *
 * <h3>v2（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个噩兆实体每帧的顶点量粗算：
 * </p>
 * <pre>
 * 诅咒断环（6 段 × 3 细分 × 6）            108
 * 不祥浸染盘（24 段 × 3）                   72
 * 污浊滴落（8 滴 × 6）                      48
 * 身周黑荆棘（7 根 × 2 三角）               42
 * ─────────────────────────────────────────
 * 合计                               ~270 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * <b>本渲染器是全模组最轻的一个</b>——比出血的 948、黄金树祝福的 2000 低了整整一个数量级。
 * 因此接入 {@link VisualLod} 的<b>主要目的是 {@link VisualLod#countInstance()}</b>：
 * 拥挤度是全局共享的，只要还有渲染器不登记，{@code crowdFactor} 就会被系统性高估，
 * 已接入的重量级渲染器就削减不足。它自己能省的那点顶点反而是次要的。
 * </p>
 * <p>
 * 削减本身仍按 {@link VisualLod#detail} 走，{@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，
 * <b>与优化前逐像素一致</b>；40 格外单实体降至约 140 顶点。
 * </p>
 * <p>
 * <b>荆棘完全不参与削减。</b>7 根刺一共才 42 顶点，却是「这是噩兆不是别的负面状态」的
 * 唯一依据（本模组只有这里用尖刺造型），且角度是
 * {@code orbit + TAU × i / 7} 均布的——削掉任意一根都会让环绕变得不对称。
 * <b>顶点性价比这么高的元素，削它是纯亏。</b>
 * 同理断环的 {@link #RING_DASHES} 也保持 6 段不变（断环的「断」本身就靠这 6 段的规律间隔表达，
 * 抽掉一半会读成「碎环」而非「断环」），只缩每段弧内部的细分数。
 * </p>
 *
 * <h3>v3（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v2 之后本渲染器的顶点量已经很低，但<b>颜色分配密度反而是同量级渲染器里偏高的</b>——
 * 旧实现的 {@code mix(a, b, t)} 与 {@code unpack(color)} <b>每次调用都 {@code new float[3]}</b>：
 * </p>
 * <pre>
 * 诅咒断环（6 段 × 3 细分 = 18 次 mix）      18
 * 污浊滴落（8 滴 × 1 次 mix）                 8
 * 各方法开头的 unpack（2+2+2+1）              7
 * ────────────────────────────────────────
 * 合计                     ~33 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 也就是说本渲染器<b>每 8 个顶点就要分配一个临时数组</b>，比例是全模组最差的。
 * 噩兆由 {@code EnchantmentBadOmen} 在每次攻击时施加，群战中同屏挂十几个目标很常见。
 * </p>
 * <p>
 * 现改为三条路径（工具见 {@link VisualColor}）：
 * </p>
 * <ol>
 *     <li><b>三个主题色类加载时预解包一次</b>（{@code C_} 前缀常量）；</li>
 *     <li><b>固定比例的混色也预算一次</b>——{@link #C_OOZE_HEAD} 是
 *         {@code mix(黑, 锈, 0.45)} 的结果，比例是<b>写死的常量</b>，
 *         旧实现却在每滴污浊的循环里重算重分配。现用静态初始化块算一次、永久复用；</li>
 *     <li><b>真正动态的插值色写入复用缓冲</b>——{@link #SCRATCH}（断环逐段的
 *         「边缘染金」渐变、浸染盘随时间的色相脉动）。</li>
 * </ol>
 * <p>
 * <b>为什么 {@link #C_OOZE_HEAD} 值得单列一条：</b>它是这轮优化里唯一「靠观察发现的常量」。
 * {@link #drawOoze} 每滴都调 {@code mix(black, rust, 0.45f)}，
 * 三个参数全是编译期常量、结果恒定不变，却被放在了循环体里——
 * 这类「看起来动态、其实恒定」的表达式是最容易漏掉的分配点。
 * </p>
 * <p>
 * <b>{@link #drawOoze} 为什么不需要双缓冲：</b>{@link #verticalLine} 确实要
 * <b>同时</b>持有顶端色与底端色，但这两个现在<b>都是只读常量</b>
 * （{@link #C_OMEN_BLACK} 与 {@link #C_OOZE_HEAD}），彼此不会互相覆盖。
 * 只有当两端都是<b>动态</b>色时才必须开两个缓冲——那是螺旋 / 刀痕 / 花瓣的情形。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#mixInto} 与旧 {@code mix} 都是归一化域线性插值 +
 * clamp01，{@link VisualColor#lerpInto} 保留了旧 {@code lerpRgb} 在 0~255 整数域取整的行为，
 * {@link VisualColor#constant} 与旧 {@code unpack} 是同一个 {@code /255f} 公式——
 * 输出的每个颜色分量与 v2 数值相等。
 * </p>
 *
 * @author RoinFlam
 * @version 3
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class BadOmenRenderer {
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int POOL_SEGMENTS_MIN = 8;
    private static final int RING_SUB_MIN = 2;
    private static final int OMEN_BLACK = 0x140A12;
    private static final int OMEN_RUST = 0x6E1F1A;
    private static final int OMEN_SICK_GOLD = 0x8A7A2E;
    private static final float OOZE_HEAD_MIX = 0.45f;
    private static final float[] C_OMEN_BLACK = VisualColor.constant(OMEN_BLACK);
    private static final float[] C_OMEN_RUST = VisualColor.constant(OMEN_RUST);
    private static final float[] C_OMEN_SICK_GOLD = VisualColor.constant(OMEN_SICK_GOLD);
    private static final float[] C_OOZE_HEAD = new float[VisualColor.RGB];
    private static final float[] SCRATCH = new float[VisualColor.RGB];
    private static final int THORN_COUNT = 7;
    private static final float THORN_RADIUS_FACTOR = 0.72f;
    private static final float THORN_HEIGHT_FACTOR = 0.62f;
    private static final float THORN_BASE_HALF = 0.075f;
    private static final float THORN_LEAN = 0.32f;
    private static final float THORN_PULSE_SPEED = 0.9f;
    private static final float THORN_ORBIT_SPEED = 0.12f;
    private static final float THORN_BASE_ALPHA = 0.82f;
    private static final float RING_HEIGHT_FACTOR = 1.25f;
    private static final float RING_RADIUS_FACTOR = 0.6f;
    private static final int RING_DASHES = 6;
    private static final float RING_FILL_RATIO = 0.5f;
    private static final float RING_ROT_SPEED = -0.45f;
    private static final float RING_BASE_ALPHA = 0.7f;
    private static final int RING_SUB = 3;
    private static final int OOZE_COUNT = 8;
    private static final float OOZE_FALL_SPEED = 0.42f;
    private static final float OOZE_START_HEIGHT_FACTOR = 0.75f;
    private static final float OOZE_HALF_WIDTH = 0.032f;
    private static final float OOZE_BASE_ALPHA = 0.75f;
    private static final int POOL_SEGMENTS = 24;
    private static final float POOL_RADIUS_FACTOR = 1.25f;
    private static final float POOL_BASE_ALPHA = 0.42f;
    private BadOmenRenderer() {
    }
}

