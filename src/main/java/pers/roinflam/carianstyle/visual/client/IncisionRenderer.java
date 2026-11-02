package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.IncisionSyncHandler;
import pers.roinflam.carianstyle.potion.MobEffectIncision;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 切腹「狂化」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@link MobEffectIncision}：消耗 50% 最大生命进入 10 秒状态，期间每秒流失 12.5% 最大生命、
 * 移速 +120%（持续衰减至 +30%）、伤害 +40%、攻速 +80%、护甲 -75%、恢复 +60%，造成伤害回复 25%，
 * 击杀延长持续时间。机制上是「自残换爆发」的血战状态。
 * </p>
 * <p>
 * <b>判定采用双重冗余：</b>{@code entity.hasEffect(CarianStylePotion.INCISION)}
 * <b>或</b> {@code ClientSyncEffectManager.shouldRenderEffect(IncisionSyncHandler.INCISION_SERIAL, id)}。
 * 与出血 / 冻伤不同的是，切腹是<b>自身增益</b>——玩家自己进入切腹时原版同步完整可靠，
 * 单靠前者即可正确显示；后者只负责补上「观察其它实体的切腹状态」这一档
 * （详见 {@link IncisionSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>与出血渲染器的视觉区分（关键设计约束）：</b>{@code HemorrhageBloodRenderer} 已经占用了
 * 「血泊 + 垂落血线 + 抛物线血滴 + 迸溅射线 + 血雾」这一整套语言。两者可能同时挂在同一实体上，
 * 若视觉雷同则完全无法分辨。故本渲染器刻意采用<b>反向</b>的语言：
 * <ul>
 *     <li><b>方向相反</b>——出血是向下的（血泊沉积、血线垂落、血滴坠地）；切腹是向上的
 *         （血气上涌、碎片升腾、疾走外散）；</li>
 *     <li><b>色调更暗</b>——出血用鲜红 {@code 0xE0202F}；切腹用氧化暗红 {@link #INCISION_BLOOD}
 *         与近黑红 {@link #INCISION_DARK}，只有刀痕本身是高亮的；</li>
 *     <li><b>语义相反</b>——出血是被动失血的负面态；切腹是主动狂化的增益态。</li>
 * </ul>
 * </p>
 * <p>
 * <b>四个元素：</b>
 * <ol>
 *     <li><b>腹部横向刀痕</b>（{@link #drawGash}）——唯一的标志性主视觉，也是唯一的高亮元素；</li>
 *     <li><b>上升血刃碎片</b>（{@link #drawRisingShards}）——自刀痕处向上飘散的<b>细长菱形</b>碎片
 *         （刻意不用圆形柔光点，与出血的血滴区分）；</li>
 *     <li><b>疾走涟漪</b>（{@link #drawSprintRipples}）——脚下向外扩散的扁平环。
 *         <b>这是唯一与机制直接联动的元素</b>：强度读取 {@code MOVEMENT_SPEED} 上切腹修正器的实时数值
 *         （见 {@link #speedIntensity}），因此会跟着 +120%→+30% 的衰减一起变弱；</li>
 *     <li><b>躯干血气笼罩</b>（{@link #drawBodyHaze}）——贴身的大号暗红雾团，补足体积感。</li>
 * </ol>
 * </p>
 *
 * <h3>v2（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个切腹实体每帧的顶点量粗算：
 * </p>
 * <pre>
 * 疾走涟漪（最多 3 环 × 28 段 × 6）      504
 * 躯干血气（8 雾团 × 10 段 × 3）         240
 * 上升血刃（最多 20 片 × 4 三角）        240
 * 腹部刀痕（10 段 × 6）                   60
 * ─────────────────────────────────────────
 * 合计                              ~1044 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 切腹是玩家主动开启的爆发状态，团战里往往<b>多人同时开</b>，且与出血、黄金树祝福大量共存。
 * 现按 {@link VisualLod#detail} 缩放元素数量与分段数：
 * {@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，<b>与优化前逐像素一致</b>；
 * 40 格外单实体降至约 200 顶点。
 * </p>
 * <p>
 * <b>削减策略：</b>
 * </p>
 * <ul>
 *     <li><b>刀痕不削数量、只削分段</b>——它是切腹唯一的辨识符号，且仅 60 顶点，
 *         哪怕最低细节也必须完整可见，只把沿长度的细分从 10 段降到 4 段（轮廓略方，远处不可见）；</li>
 *     <li><b>血刃碎片与雾团按种子截断尾部</b>——位置由 {@code seedFor(entityId, i)} 决定，
 *         保留元素的轨迹完全不变，靠近时是「逐渐多出几片碎片」而非重新洗牌；</li>
 *     <li><b>涟漪减圈数</b>——涟漪的相位是 {@code i / count} 均布的，减少 count 会改变相位间隔，
 *         但涟漪本身就是「一圈接一圈往外推」的循环动画、没有固定方位，
 *         减圈只表现为「波与波之间隔得更开」，观感自然；</li>
 *     <li><b>血气雾团整层可跳过</b>——它是纯体积氛围层，远处完全糊成一片，
 *         低于 {@link #HAZE_KEEP_THRESHOLD} 时整层不画（省 240 顶点，占总量近四分之一）。</li>
 * </ul>
 *
 * <h3>v3（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v2 之后仍有一处纯浪费：旧实现的 {@code mix(a, b, t)} 与 {@code unpack(color)}
 * <b>每次调用都 {@code new float[3]}</b>。本渲染器的调用密度：
 * </p>
 * <pre>
 * 刀痕（10 段 × 2 次 gashColor→mix）        20
 * 上升血刃（最多 20 片 × 1 次 mix）          20
 * 躯干血气（8 团 × 1 次 mix）                 8
 * 疾走涟漪（最多 3 环 × 1 次 mix）            3
 * 各方法开头的 unpack 常量解包                9
 * ────────────────────────────────────────
 * 合计                    ~60 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 切腹是团战中多人同开的爆发状态，5 人同时开启即<b>每秒 1.8 万次</b>朝生夕死的小数组分配。
 * </p>
 * <p>
 * 现改为两条路径（工具见 {@link VisualColor}）：三个主题色类加载时预解包为 {@code C_} 常量；
 * 动态插值色写入 {@link #SCRATCH_A} / {@link #SCRATCH_B}。
 * </p>
 * <p>
 * <b>刀痕为什么需要两个缓冲、且要滚动交换：</b>{@link #emitBillboardQuad} 需要<b>同时</b>
 * 持有带状四边形两端的颜色，这正是 {@link VisualColor} 类注释里点名的「两个动态色同时存活」
 * 场景——若两次都写同一个缓冲，后写的会覆盖先写的、整条刀痕退化成纯色。
 * 又因「本段的末端色 == 下一段的起点色」，这里采用<b>滚动交换</b>：每段只算一次新颜色，
 * 用完把两个缓冲的引用对调，20 次插值降为 10 次。
 * </p>
 *
 * <h3>v4（堆分配，视觉逐位一致）：血刃碎片几何数组零分配化</h3>
 * <p>
 * v3 清掉了颜色数组，但<b>漏了 {@link #emitShard} 内部的几何分配</b>——
 * 每次调用要分配 <b>8 个数组</b>：
 * </p>
 * <pre>
 * float[][] local = { ... };   // 1 个外层 + 4 个 float[2] = 5 个
 * float[] wx = new float[4];   // 3 个 float[4]
 * float[] wy = new float[4];
 * float[] wz = new float[4];
 * ────────────────────────────────────────
 * 合计                            8 个 / 次
 * </pre>
 * <p>
 * 而血刃碎片满配 20 片，即 <b>160 个数组 / 实体 / 帧</b>——比 v3 清掉的 60 次颜色分配
 * 高出近三倍。5 人同时开切腹 × 60fps ≈ <b>每秒 4.8 万次</b>小数组分配，
 * 而这恰好发生在团战爆发、客户端最需要帧率的时候。
 * </p>
 * <p>
 * 现改为（做法与 {@code AoeEffectRenderer} v7 处理 {@code spark} / {@code drawOrb} 同源）：
 * </p>
 * <ol>
 *     <li><b>局部轮廓点内联为标量</b>——菱形只有 4 个点，且是由 {@code size} /
 *         {@code longAxis} 线性缩放的固定比例，展开成 8 个局部变量即可；</li>
 *     <li><b>世界坐标改用静态复用缓冲</b>——{@link #SHARD_WX} / {@link #SHARD_WY} /
 *         {@link #SHARD_WZ}。{@link #emitShard} 不可重入（同一线程内不会嵌套调用自己，
 *         且只在渲染线程访问），复用安全。</li>
 * </ol>
 * <p>
 * <b>复用缓冲的约束：</b>这三个缓冲<b>只能在 {@link #emitShard} 内部使用</b>，
 * 且必须「写满 → 立刻画完 → 不再引用」。若将来新增其它需要世界坐标缓冲的元素，请另开一组。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>轮廓点数值、旋转公式、顶点写入顺序全部照搬原实现，
 * 输出的每个顶点坐标与 v3 完全相同。
 * </p>
 *
 * @author RoinFlam
 * @version 4
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class IncisionRenderer {
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int SHARD_POINTS = 4;
    private static final int GASH_SEGMENTS_MIN = 4;
    private static final int RIPPLE_SEGMENTS_MIN = 8;
    private static final int HAZE_SEGMENTS_MIN = 4;
    private static final float HAZE_KEEP_THRESHOLD = 0.5f;
    private static final int INCISION_GASH = 0xFF4A55;
    private static final int INCISION_BLOOD = 0xA01824;
    private static final int INCISION_DARK = 0x2A0408;
    private static final float[] C_INCISION_GASH = VisualColor.constant(INCISION_GASH);
    private static final float[] C_INCISION_BLOOD = VisualColor.constant(INCISION_BLOOD);
    private static final float[] C_INCISION_DARK = VisualColor.constant(INCISION_DARK);
    private static final float[] SCRATCH_A = new float[VisualColor.RGB];
    private static final float[] SCRATCH_B = new float[VisualColor.RGB];
    private static final float[] SHARD_WX = new float[SHARD_POINTS];
    private static final float[] SHARD_WY = new float[SHARD_POINTS];
    private static final float[] SHARD_WZ = new float[SHARD_POINTS];
    private static final float GASH_HEIGHT_FACTOR = 0.45f;
    private static final float GASH_HALF_LENGTH_FACTOR = 0.85f;
    private static final float GASH_HALF_THICK_FACTOR = 0.055f;
    private static final int GASH_SEGMENTS = 10;
    private static final float GASH_PULSE_SPEED = 4.5f;
    private static final float GASH_BASE_ALPHA = 0.9f;
    private static final int SHARD_COUNT = 20;
    private static final float SHARD_RISE_SPEED = 0.5f;
    private static final float SHARD_RISE_HEIGHT_FACTOR = 1.15f;
    private static final float SHARD_SPREAD_FACTOR = 0.75f;
    private static final float SHARD_SIZE = 0.10f;
    private static final float SHARD_SPIN_SPEED = 3.2f;
    private static final float SHARD_BASE_ALPHA = 0.85f;
    private static final int RIPPLE_COUNT = 3;
    private static final float RIPPLE_RATE = 0.9f;
    private static final float RIPPLE_MAX_RADIUS_FACTOR = 2.3f;
    private static final int RIPPLE_SEGMENTS = 28;
    private static final float RIPPLE_HALF_WIDTH = 0.055f;
    private static final float RIPPLE_BASE_ALPHA = 0.55f;
    private static final float SPEED_INTENSITY_FALLBACK = 0.5f;
    private static final int HAZE_COUNT = 8;
    private static final float HAZE_SIZE_FACTOR = 0.38f;
    private static final float HAZE_HEIGHT_FACTOR = 0.7f;
    private static final float HAZE_BASE_ALPHA = 0.34f;
    private static final float HAZE_DRIFT_SPEED = 0.45f;
    private static final int HAZE_SEGMENTS = 10;
    private IncisionRenderer() {
    }
}

