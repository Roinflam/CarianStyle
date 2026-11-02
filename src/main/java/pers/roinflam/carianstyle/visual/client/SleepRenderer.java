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
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.SleepSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 睡眠「沉眠」客户端渲染器（纯客户端自绘）。
 * <p>
 * 对应 {@code MobEffectSleep}：目标无法移动 / 跳跃 / 攻击，生物无法将其设为目标，
 * 首次受到生物伤害时「觉醒」并承受 ×2 + 等级×25% 的伤害。由催眠烟雾
 * （{@code EnchantmentHypnoticSmoke}）与托莉娜箭（{@code EnchantmentHypnoticArrow}）施加。
 * </p>
 * <p>
 * <b>判定采用双重冗余：</b>{@code hasEffect(SLEEP)} <b>或</b>
 * {@code ClientSyncEffectManager.shouldRenderEffect(SLEEP_SERIAL, id)}。
 * 对睡眠而言后者才是主力——睡眠几乎总是施加给正在交战的敌人，而这类目标必然已被观察者追踪，
 * 原版此时不再下发效果变化（详见 {@link SleepSyncHandler} 类注释）。
 * </p>
 * <p>
 * <b>受众是旁观者而非被睡者。</b>{@code MobEffectSleep} 会给沉睡实体持续施加原版失明，
 * 被睡的玩家自己其实什么都看不见；这套视觉真正服务的是<b>施法者与旁观者</b>——
 * 他们需要一眼确认「这个目标睡着了、现在打它有觉醒加成」。因此所有元素都设计为
 * <b>从外部远看就能辨识</b>：头顶的螺旋在任何角度都清晰，不依赖贴脸观察细节。
 * </p>
 * <p>
 * <b>三个元素（全场唯一的「慢速」演出，这是刻意的辨识手段）：</b>
 * <ol>
 *     <li><b>头顶催眠螺旋</b>（{@link #drawHypnoticSpiral}）——标志性主视觉。头顶上方水平悬浮的
 *         阿基米德螺线，极缓慢地整体旋转。螺旋是「催眠」这一概念最不可能被误读的符号，
 *         且本模组此前没有任何演出使用过螺线形状，同屏叠加时绝不会与别的效果混淆；</li>
 *     <li><b>托莉娜白花瓣</b>（{@link #drawPetals}）——极缓慢飘落并左右摇曳的乳白花瓣
 *         （billboard 六边形轮廓，非正圆），呼应睡眠女神托莉娜的白花意象；</li>
 *     <li><b>沉眠雾盘</b>（{@link #drawSlumberMist}）——脚下极淡的蓝灰雾盘，以近乎察觉不到的
 *         速度呼吸，为整体压住重量、避免只剩两个飘浮元素显得轻飘。</li>
 * </ol>
 * <b>所有动效速度都刻意压到其它特效的几分之一</b>——冻伤在闪烁、出血在迸溅、癫火在颤动，
 * 而睡眠几乎是静止的。这个「慢」本身就是最强的辨识特征。
 * </p>
 * <p>
 * 渲染管线沿用本模组统一方案：GL 状态与顶点缓冲由 {@link VisualBatch} 统一管理，
 * 实体列表取自 {@link SharedEntityQuery} 的每帧共享查询，{@code POSITION_COLOR} 纯顶点绘制。
 * </p>
 *
 * <h3>v2（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 单个沉睡实体每帧的顶点量粗算：
 * </p>
 * <pre>
 * 催眠螺旋（48 折线段 × 6 + 中心柔光 12）  300
 * 白花瓣（10 片 × 6 三角）                 180
 * 沉眠雾盘（24 段 × 3）                     72
 * ─────────────────────────────────────────
 * 合计                               ~552 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * <b>本渲染器是本批三个里最轻的</b>，单独看削减收益有限——接入 {@link VisualLod} 的
 * <b>首要目的其实是 {@link VisualLod#countInstance()}</b>：拥挤度是全局共享的，
 * 只要还有渲染器不登记，{@code crowdFactor} 就会被系统性高估，
 * 已接入的重量级渲染器（黄金树祝福、腐败女神）就削减不足。补齐这一环比它自己省的那点顶点重要。
 * </p>
 * <p>
 * 削减本身仍按 {@link VisualLod#detail} 走，{@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，
 * <b>与优化前逐像素一致</b>；40 格外单实体降至约 190 顶点。
 * </p>
 * <p>
 * <b>螺旋可以直接减段数（与均布圆环不同，这里不需要步长抽取）。</b>
 * 螺旋是一条连续折线，{@code u = i / segments} 无论 {@code segments} 取多少都完整覆盖 0→1，
 * {@code theta = totalAngle × u + rot} 也始终转满 {@link #SPIRAL_TURNS} 圈——
 * 减段数只让折线更粗糙，<b>不会改变螺旋的形状、圈数或起止位置</b>。
 * 这与根须、符文刻度那类「{@code i × (TAU / 总数)} 均布」的元素有本质区别，后者减总数会改变方位。
 * 但段数太少会明显棱角化（2.25 圈 20 段 ≈ 每圈 9 段），故设下限 {@link #SPIRAL_SEGMENTS_MIN}。
 * </p>
 *
 * <h3>v3（堆分配，视觉逐位一致）：颜色数组零分配化</h3>
 * <p>
 * v2 解决了顶点量，但本渲染器还藏着<b>全模组最密集的一处小对象分配</b>：
 * 旧实现里 {@code mix(a, b, t)} 与 {@code unpack(color)} <b>每次调用都 {@code new float[3]}</b>，
 * 而催眠螺旋的绘制循环里<b>每段要调两次</b>（本段末端色 + 上段末端色）：
 * </p>
 * <pre>
 * 催眠螺旋（48 段 × 2 次 mix）              96
 * 白花瓣（10 片 × 1 次 mix）                10
 * 螺旋起手的 petal / mist 解包               2
 * 沉眠雾盘（lerpRgb → unpack）               1
 * ────────────────────────────────────────
 * 合计                     ~109 次 new float[3] / 实体 / 帧
 * </pre>
 * <p>
 * 睡眠虽不像出血那样人人都挂，但催眠烟雾 / 托莉娜箭在群战中可同时睡住多个目标；
 * 5 个沉睡实体 × 60fps 已是<b>每秒 3.3 万次</b>朝生夕死的小数组分配。
 * </p>
 * <p>
 * 现改为两条路径（工具见 {@link VisualColor}）：
 * </p>
 * <ol>
 *     <li><b>三个主题色类加载时预解包一次</b>（{@code C_} 前缀常量），此后永久复用；</li>
 *     <li><b>动态插值色写入复用缓冲</b>——{@link #SCRATCH_A} / {@link #SCRATCH_B}。</li>
 * </ol>
 * <p>
 * <b>螺旋为什么需要两个缓冲、且要滚动交换：</b>{@link #lineGradient} 需要<b>同时</b>持有
 * 线段两端的颜色（起点色与终点色），这正是 {@link VisualColor} 类注释里点名的
 * 「两个动态色同时存活」场景——若两次都写同一个缓冲，后写的会覆盖先写的、整条螺旋退化成纯色。
 * 更进一步，由于「本段的末端色 == 下一段的起点色」，这里采用<b>滚动交换</b>：
 * 每段只算一次新颜色，用完把两个缓冲的引用对调。这样 96 次插值降为 48 次，
 * 且每段的两端颜色仍然各自正确。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#mixInto} 与旧的 {@code mix} 都是在归一化域直接线性插值，
 * {@link VisualColor#lerpInto} 则保留了旧 {@code lerpRgb} 在 0~255 整数域插值并取整的行为，
 * 因此输出与 v2 的每个颜色分量完全相同——不是「肉眼看不出」而是「数值相等」。
 * </p>
 *
 * <h3>v4（堆分配，视觉逐位一致）：花瓣与柔光几何数组零分配化</h3>
 * <p>
 * v3 清掉了颜色数组，但<b>漏了两处几何分配</b>：
 * </p>
 * <pre>
 * emitPetal（每次 1 个 float[][] 外层 + 6 个 float[2] + 3 个 float[6] = 10 个）
 *   × 10 片花瓣                                              = 100 个
 * spark（每次 1 个 float[][] 外层 + 4 个 float[2] = 5 个）
 *   × 1 次（螺旋中心柔光）                                     = 5 个
 * ──────────────────────────────────────────────────────────
 * 合计                                        ~105 个数组 / 实体 / 帧
 * </pre>
 * <p>
 * 数量与 v3 清掉的 109 次颜色分配几乎持平，而且 {@code float[6]} 比 {@code float[3]} 更大。
 * 也就是说 v3 只解决了一半问题——本次把另一半补上，本渲染器自此每帧堆分配为 <b>0</b>。
 * </p>
 * <p>
 * 现改为（做法与 {@code AoeEffectRenderer} v7 同源）：
 * </p>
 * <ol>
 *     <li><b>局部轮廓点内联为标量</b>——花瓣 6 点、柔光 4 点，都是由 {@code size} 线性缩放的
 *         固定比例，展开成局部变量即可；</li>
 *     <li><b>世界坐标改用静态复用缓冲</b>——{@link #PETAL_WX} / {@link #PETAL_WY} /
 *         {@link #PETAL_WZ}。{@link #emitPetal} 不可重入（同一线程内不会嵌套调用自己，
 *         且只在渲染线程访问），复用安全。</li>
 * </ol>
 * <p>
 * <b>复用缓冲的约束：</b>这三个缓冲<b>只能在 {@link #emitPetal} 内部使用</b>，
 * 且必须「写满 → 立刻画完 → 不再引用」。{@link #spark} 只有 4 个角点、直接内联成标量，
 * 不需要缓冲。
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
public final class SleepRenderer {
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int PETAL_POINTS = 6;
    private static final int SPIRAL_SEGMENTS_MIN = 20;
    private static final int MIST_SEGMENTS_MIN = 8;
    private static final int SLEEP_PETAL = 0xF2EDE0;
    private static final int SLEEP_MIST = 0xBFC8DE;
    private static final int SLEEP_DEEP = 0x6E7695;
    private static final float[] C_SLEEP_PETAL = VisualColor.constant(SLEEP_PETAL);
    private static final float[] C_SLEEP_MIST = VisualColor.constant(SLEEP_MIST);
    private static final float[] SCRATCH_A = new float[VisualColor.RGB];
    private static final float[] SCRATCH_B = new float[VisualColor.RGB];
    private static final float[] PETAL_WX = new float[PETAL_POINTS];
    private static final float[] PETAL_WY = new float[PETAL_POINTS];
    private static final float[] PETAL_WZ = new float[PETAL_POINTS];
    private static final float SPIRAL_HEIGHT_FACTOR = 1.35f;
    private static final float SPIRAL_TURNS = 2.25f;
    private static final float SPIRAL_RADIUS_FACTOR = 0.85f;
    private static final int SPIRAL_SEGMENTS = 48;
    private static final float SPIRAL_ROT_SPEED = 0.55f;
    private static final float SPIRAL_HALF_WIDTH = 0.035f;
    private static final float SPIRAL_BASE_ALPHA = 0.8f;
    private static final int PETAL_COUNT = 10;
    private static final float PETAL_FALL_SPEED = 0.085f;
    private static final float PETAL_START_HEIGHT_FACTOR = 1.6f;
    private static final float PETAL_SPREAD_FACTOR = 0.8f;
    private static final float PETAL_SIZE = 0.075f;
    private static final float PETAL_SPIN_SPEED = 0.7f;
    private static final float PETAL_BASE_ALPHA = 0.85f;
    private static final int MIST_SEGMENTS = 24;
    private static final float MIST_RADIUS_FACTOR = 1.35f;
    private static final float MIST_BASE_ALPHA = 0.28f;
    private static final float MIST_BREATH_SPEED = 0.35f;
    private SleepRenderer() {
    }
}

