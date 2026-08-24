package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.SleepSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
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
 * <b>⚠ 复用缓冲的约束：</b>这三个缓冲<b>只能在 {@link #emitPetal} 内部使用</b>，
 * 且必须「写满 → 立刻画完 → 不再引用」。{@link #spark} 只有 4 个角点、直接内联成标量，
 * 不需要缓冲。
 * </p>
 * <p>
 * <b>视觉逐位一致：</b>轮廓点数值、旋转公式、顶点写入顺序全部照搬原实现，
 * 输出的每个顶点坐标与 v3 完全相同。
 * </p>
 *
 * @author FlameForge
 * @version 4
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class SleepRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();

    /**
     * 花瓣轮廓的顶点数（固定 6 点，不参与 LOD 缩放）。
     * <p>同时也是 {@link #PETAL_WX} / {@link #PETAL_WY} / {@link #PETAL_WZ}
     * 三个复用缓冲的长度依据。改动此值必须同步改三个缓冲的长度与
     * {@link #emitPetal} 内的展开代码。</p>
     */
    private static final int PETAL_POINTS = 6;

    // ===== v2 LOD 下限 =====
    /**
     * 螺旋折线的最少段数。
     * <p>螺旋共 {@link #SPIRAL_TURNS}(2.25) 圈，20 段 ≈ 每圈 9 段——再少就会从「平滑螺线」
     * 退化成肉眼可辨的多边形折线，而螺旋是睡眠唯一的标志符号，不能牺牲形状。</p>
     */
    private static final int SPIRAL_SEGMENTS_MIN = 20;
    /** 沉眠雾盘的最少分段数 */
    private static final int MIST_SEGMENTS_MIN = 8;

    // ===== 配色（0xRRGGBB）=====
    /** 花瓣乳白：托莉娜白花的主色 */
    private static final int SLEEP_PETAL = 0xF2EDE0;
    /** 催眠淡蓝灰：螺旋与雾盘的主色 */
    private static final int SLEEP_MIST = 0xBFC8DE;
    /** 沉眠暗蓝灰：雾盘外缘与螺旋末端的暗部 */
    private static final int SLEEP_DEEP = 0x6E7695;

    // ===== v3：预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    /** 花瓣乳白（螺旋内端 / 花瓣起点 / 中心柔光） */
    private static final float[] C_SLEEP_PETAL = VisualColor.constant(SLEEP_PETAL);
    /** 催眠淡蓝灰（螺旋外端 / 花瓣末期） */
    private static final float[] C_SLEEP_MIST = VisualColor.constant(SLEEP_MIST);

    /**
     * v3：动态插值色的复用缓冲 A（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * 用于：螺旋的滚动双缓冲之一、花瓣颜色、雾盘颜色。这三处不会同时活跃
     * （{@link #drawSlumberMist} / {@link #drawPetals} / {@link #drawHypnoticSpiral}
     * 是顺序调用、互不嵌套），故可共用。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] SCRATCH_A = new float[VisualColor.RGB];

    /**
     * v3：动态插值色的复用缓冲 B（⚠ 同上）。
     * <p>
     * <b>专供 {@link #drawHypnoticSpiral} 与 {@link #SCRATCH_A} 配对使用</b>——
     * {@link #lineGradient} 需要同时持有线段两端的颜色，一个缓冲不够
     * （详见类注释「螺旋为什么需要两个缓冲」）。
     * </p>
     */
    private static final float[] SCRATCH_B = new float[VisualColor.RGB];

    /**
     * v4：花瓣世界坐标的复用缓冲 X（⚠ 仅供 {@link #emitPetal} 内部使用）。
     * <p>
     * 旧实现每片花瓣 {@code new float[6]} 三次 + 局部轮廓点数组七个，
     * 10 片即 <b>100 个临时数组 / 实体 / 帧</b>——与 v3 清掉的颜色分配几乎等量，
     * 且单个数组更大（详见类注释的「v4」小节）。
     * </p>
     * <p>
     * {@link #emitPetal} 不可重入（同一线程内不会嵌套调用自己），且只在渲染线程访问，
     * 故提为静态定长缓冲复用，分配归零。
     * </p>
     * <p>
     * <b>⚠ 约束：</b>只能在 {@link #emitPetal} 内部使用，且必须
     * 「写满 → 立刻画完 → 不再引用」。
     * </p>
     */
    private static final float[] PETAL_WX = new float[PETAL_POINTS];

    /** v4：花瓣世界坐标的复用缓冲 Y（⚠ 同上，与 {@link #PETAL_WX} 配对）。 */
    private static final float[] PETAL_WY = new float[PETAL_POINTS];

    /** v4：花瓣世界坐标的复用缓冲 Z（⚠ 同上，与 {@link #PETAL_WX} 配对）。 */
    private static final float[] PETAL_WZ = new float[PETAL_POINTS];

    // ===== 头顶催眠螺旋 =====
    /** 螺旋悬浮高度系数（× 实体高度）：略高于头顶 */
    private static final float SPIRAL_HEIGHT_FACTOR = 1.35f;
    /** 螺旋圈数 */
    private static final float SPIRAL_TURNS = 2.25f;
    /** 螺旋最大半径系数（× 实体宽度） */
    private static final float SPIRAL_RADIUS_FACTOR = 0.85f;
    /** 螺旋细分段数（越大线条越平滑） */
    private static final int SPIRAL_SEGMENTS = 48;
    /** 螺旋整体旋转速度（弧度/秒）——刻意极慢 */
    private static final float SPIRAL_ROT_SPEED = 0.55f;
    /** 螺旋线半宽（格） */
    private static final float SPIRAL_HALF_WIDTH = 0.035f;
    private static final float SPIRAL_BASE_ALPHA = 0.8f;

    // ===== 托莉娜白花瓣 =====
    /** 同时存在的花瓣数量 */
    private static final int PETAL_COUNT = 10;
    /** 单片花瓣从生成到落地的循环速度（每秒推进的归一化进度）——极慢 */
    private static final float PETAL_FALL_SPEED = 0.085f;
    /** 花瓣起始高度系数（× 实体高度） */
    private static final float PETAL_START_HEIGHT_FACTOR = 1.6f;
    /** 花瓣水平分布半径系数（× 实体宽度） */
    private static final float PETAL_SPREAD_FACTOR = 0.8f;
    /** 花瓣基准尺寸（格） */
    private static final float PETAL_SIZE = 0.075f;
    /** 花瓣自旋速度——同样很慢 */
    private static final float PETAL_SPIN_SPEED = 0.7f;
    private static final float PETAL_BASE_ALPHA = 0.85f;

    // ===== 沉眠雾盘 =====
    private static final int MIST_SEGMENTS = 24;
    private static final float MIST_RADIUS_FACTOR = 1.35f;
    private static final float MIST_BASE_ALPHA = 0.28f;
    /** 雾盘呼吸速度——近乎察觉不到 */
    private static final float MIST_BREATH_SPEED = 0.35f;

    private SleepRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有沉睡生物的睡眠视觉。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }
        List<LivingEntity> candidates = SharedEntityQuery.livingEntitiesNearCamera(mc, cam);
        if (candidates.isEmpty()) {
            return;
        }

        MobEffect sleep = CarianStylePotion.SLEEP.get();

        Matrix4f matrix = VisualBatch.matrix();
        float rightX = VisualBatch.rightX();
        float rightY = VisualBatch.rightY();
        float rightZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            if (!isAsleep(entity, sleep)) {
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

            // ⭐ v2：本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0，视觉与优化前一致
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度。本渲染器接入 VisualLod 的首要意义就在这一行——
            // 只要还有渲染器不登记，全局 crowdFactor 就会被系统性高估
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawSlumberMist(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), detail);
            drawPetals(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    rightX, rightY, rightZ, upX, upY, upZ, detail);
            drawHypnoticSpiral(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), detail);
        }
    }

    /**
     * 判断实体是否处于睡眠状态（双重冗余判定）。
     *
     * @param entity 待判定实体
     * @param sleep  睡眠效果对象（可能为 null）
     * @return 沉睡中返回 true
     */
    private static boolean isAsleep(LivingEntity entity, @Nullable MobEffect sleep) {
        if (sleep != null && entity.hasEffect(sleep)) {
            return true;
        }
        return ClientSyncEffectManager.shouldRenderEffect(SleepSyncHandler.SLEEP_SERIAL, entity.getId());
    }

    // ==================== 头顶催眠螺旋（核心标志）====================

    /**
     * 头顶悬浮的水平催眠螺旋：一条阿基米德螺线（{@code r = k·θ}），沿 {@link #SPIRAL_TURNS} 圈
     * 由内向外展开，整体极缓慢旋转；线宽与亮度由内向外递减，末端渐隐。
     * <p>
     * <b>为什么用螺旋：</b>「螺旋 = 催眠」是跨文化都成立的直觉符号，远看一眼就能读出语义；
     * 而且本模组此前的十余种演出里没有任何一个使用螺线形状（法阵是多边形/星形、
     * 光环是同心圆、刀光是弧带），因此同屏叠加时不存在混淆风险。
     * </p>
     * <p>
     * 高度取实体头顶再往上一点，且<b>随呼吸极缓慢上下浮动</b>，避免像贴图一样死板。
     * </p>
     * <p>
     * <b>v2：段数按细节系数缩放（下限 {@link #SPIRAL_SEGMENTS_MIN}）。</b>
     * 螺旋是连续折线而非均布圆环，{@code u = i / segments} 无论段数多少都完整覆盖 0→1、
     * 转满 {@link #SPIRAL_TURNS} 圈，因此<b>直接减段数是安全的</b>——只是折线更粗糙，
     * 形状、圈数、起止位置全部不变。中心那点柔光锚点仅 12 顶点，不参与削减。
     * </p>
     * <p>
     * <b>v3：颜色改用滚动双缓冲，每段只插值一次。</b>本方法是全模组最密集的颜色计算点
     * （旧实现每段 2 次 {@code new float[3]}）。由于「本段末端色 == 下一段起点色」，
     * 算完一段后把 {@link #SCRATCH_A} / {@link #SCRATCH_B} 的引用对调即可复用，
     * 插值次数减半、分配归零。<b>两个缓冲缺一不可</b>——{@link #lineGradient}
     * 要同时读两端颜色，共用一个会让整条螺旋退化成纯色。
     * </p>
     */
    private static void drawHypnoticSpiral(BufferBuilder b, Matrix4f m,
                                           float cx, float cyFoot, float cz,
                                           float width, float height,
                                           float time, int seedId, float detail) {
        // 极缓慢的整体旋转 + 各实体错相
        float rot = time * SPIRAL_ROT_SPEED + seedId * 0.7f;
        // 极缓慢的上下浮动
        float bob = Mth.sin(time * 0.5f + seedId * 0.4f) * 0.09f;
        float spiralY = cyFoot + height * SPIRAL_HEIGHT_FACTOR + bob;
        float maxRadius = width * SPIRAL_RADIUS_FACTOR;

        int segments = VisualLod.scaleSegments(SPIRAL_SEGMENTS, SPIRAL_SEGMENTS_MIN, detail);

        float totalAngle = TAU * SPIRAL_TURNS;
        float prevX = cx;
        float prevZ = cz;

        // ⭐ v3 滚动双缓冲：prevCol 持有上一段末端色，curCol 持有本段末端色。
        // 每段只做一次插值，段末把两个引用对调——本段末端色即成为下一段的起点色。
        float[] prevCol = SCRATCH_A;
        float[] curCol = SCRATCH_B;
        // 循环外先算出 u=0（螺旋内端）的颜色，作为第一段的起点色
        VisualColor.mixInto(prevCol, C_SLEEP_PETAL, C_SLEEP_MIST, 0f);

        for (int i = 1; i <= segments; i++) {
            float u = (float) i / segments;   // 0=中心, 1=外端
            float theta = totalAngle * u + rot;
            float r = maxRadius * u;
            float x = cx + r * (float) Math.cos(theta);
            float z = cz + r * (float) Math.sin(theta);

            // 由内向外：线变细、变淡、由乳白转蓝灰
            float hw = SPIRAL_HALF_WIDTH * (1f - u * 0.45f);
            float alpha = SPIRAL_BASE_ALPHA * (1f - u * 0.75f);
            VisualColor.mixInto(curCol, C_SLEEP_PETAL, C_SLEEP_MIST, u);

            float uPrev = (float) (i - 1) / segments;
            float alphaPrev = SPIRAL_BASE_ALPHA * (1f - uPrev * 0.75f);

            lineGradient(b, m, prevX, prevZ, x, z, spiralY, hw, prevCol, alphaPrev, curCol, alpha);

            // 交换缓冲：本段末端色成为下一段的起点色，省掉一次插值
            float[] tmp = prevCol;
            prevCol = curCol;
            curCol = tmp;

            prevX = x;
            prevZ = z;
        }

        // 螺旋中心的一点柔光，作为视觉锚点。仅 12 顶点，不做削减；用只读常量色
        spark(b, m, cx, cz, spiralY, width * 0.12f + 0.05f, C_SLEEP_PETAL, 0.65f);
    }

    // ==================== 托莉娜白花瓣 ====================

    /**
     * 极缓慢飘落的乳白花瓣：自实体上方生成，缓缓下沉并左右摇曳，接近地面时淡出。
     * <p>
     * 花瓣用 billboard 六边形轮廓近似（而非正圆），带缓慢自旋，呼应睡眠女神托莉娜的白花意象。
     * 下落速度只有本模组其它上升 / 飘落类元素的几分之一——「慢」是睡眠的核心语言。
     * </p>
     * <p>
     * <b>v2：数量按细节系数缩放。</b>花瓣位置由 {@code seedFor(entityId, i + 200)} 决定，
     * 截断尾部时保留花瓣的下落轨迹完全不变，靠近时是「逐渐多飘下几片」而非重新洗牌。
     * 花瓣轮廓固定 6 点（{@link #emitPetal}），不参与缩放——再少就不成花瓣形了。
     * </p>
     * <p>
     * <b>v3：颜色写入 {@link #SCRATCH_A} 后立即被 {@link #emitPetal} 消费，零分配。</b>
     * 本方法与螺旋不嵌套（顺序调用），故可安全复用同一缓冲。
     * </p>
     * <p>
     * <b>v4：{@link #emitPetal} 内部亦已零分配</b>，本循环整体不再产生任何临时数组。
     * </p>
     */
    private static void drawPetals(BufferBuilder b, Matrix4f m,
                                   float cx, float cyFoot, float cz,
                                   float width, float height,
                                   float time, int seedId,
                                   float rightX, float rightY, float rightZ,
                                   float upX, float upY, float upZ, float detail) {
        float startHeight = height * PETAL_START_HEIGHT_FACTOR;
        float spread = width * PETAL_SPREAD_FACTOR;

        int count = VisualLod.scale(PETAL_COUNT, detail);

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 200);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.25f + 0.75f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float swayPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float spinPhase = rngFloat(s) * TAU;

            float t = frac(time * PETAL_FALL_SPEED + phase); // 0=最高处 1=落地

            // 包络：起落两端淡入淡出
            float env;
            if (t < 0.12f) {
                env = t / 0.12f;
            } else if (t > 0.8f) {
                env = 1f - (t - 0.8f) / 0.2f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }

            // 缓慢的左右摇曳（落叶感）
            float sway = Mth.sin(time * 0.45f + swayPhase) * 0.22f;
            float curRad = spread * radFactor;
            float px = cx + (float) Math.cos(ang) * curRad + sway;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float py = cyFoot + startHeight * (1f - t) + Y_OFFSET;

            float alpha = PETAL_BASE_ALPHA * env;
            if (alpha <= 0.01f) {
                continue;
            }

            // v3：无分配插值，写入复用缓冲后立即消费
            VisualColor.mixInto(SCRATCH_A, C_SLEEP_PETAL, C_SLEEP_MIST, t * 0.5f);
            float size = PETAL_SIZE * sizeRand;
            float rot = time * PETAL_SPIN_SPEED + spinPhase;

            emitPetal(b, m, px, py, pz, size, rot,
                    SCRATCH_A[0], SCRATCH_A[1], SCRATCH_A[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    // ==================== 沉眠雾盘 ====================

    /**
     * 脚下的淡蓝灰雾盘：中心稍实、边缘渐隐，以近乎察觉不到的速度呼吸。
     * <p>作用是给整体压住重量——只有螺旋与花瓣两个悬浮元素会显得轻飘，
     * 加一层贴地的雾才有「沉下去了」的分量感。</p>
     * <p>v2：分段数按细节系数缩放（下限 {@link #MIST_SEGMENTS_MIN}）。</p>
     * <p>v3：颜色改用 {@link VisualColor#lerpInto} 写入复用缓冲，省掉中间 int 与新数组；
     * 取整行为与旧的 {@code lerpRgb → unpack} 链路逐位一致。</p>
     */
    private static void drawSlumberMist(BufferBuilder b, Matrix4f m,
                                        float cx, float cy, float cz, float width,
                                        float time, int seedId, float detail) {
        float breath = 0.9f + 0.1f * Mth.sin(time * MIST_BREATH_SPEED + seedId * 0.6f);
        float radius = width * MIST_RADIUS_FACTOR * breath;
        // v3：无分配插值（0~255 域取整，与旧 lerpRgb 逐位一致）
        VisualColor.lerpInto(SCRATCH_A, SLEEP_DEEP, SLEEP_MIST,
                0.5f + 0.5f * Mth.sin(time * 0.3f + seedId));
        int segments = VisualLod.scaleSegments(MIST_SEGMENTS, MIST_SEGMENTS_MIN, detail);
        drawDisc(b, m, cx, cy, cz, radius, segments,
                SCRATCH_A[0], SCRATCH_A[1], SCRATCH_A[2], MIST_BASE_ALPHA * breath);
    }

    // ==================== 几何基元 ====================

    /**
     * 绘制一片面向相机的花瓣：billboard 平面内用 6 个轮廓点近似出圆润的椭圆花瓣形，
     * 支持绕视线方向旋转。中心不透明、边缘渐隐为 0。
     * <p><b>轮廓点数固定为 6，不参与 LOD 缩放</b>——再少就不成花瓣形了，
     * 花瓣的削减完全通过「减少片数」实现。</p>
     * <p>
     * <b>v4：本方法此前每次调用要分配 10 个数组</b>
     * （1 个 {@code float[][]} 外层 + 6 个 {@code float[2]} 轮廓点 + 3 个 {@code float[6]}
     * 世界坐标），10 片即 <b>100 个临时数组 / 实体 / 帧</b>
     * （详见类注释的「v4」小节）。
     * </p>
     * <p>
     * 现改为：轮廓点内联为 12 个标量、世界坐标写入静态复用缓冲
     * {@link #PETAL_WX} / {@link #PETAL_WY} / {@link #PETAL_WZ}。
     * 本方法不可重入（同一线程内不会嵌套调用自己，且只在渲染线程访问），复用安全。
     * </p>
     * <p>
     * <b>视觉逐位一致：</b>轮廓点数值、旋转公式、顶点写入顺序全部照搬原实现，
     * 输出的每个顶点坐标与 v3 完全相同。
     * </p>
     *
     * @param size 花瓣半尺寸
     * @param rot  在 billboard 平面内的旋转角（弧度）
     */
    private static void emitPetal(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz, float size, float rot,
                                  float r, float g, float bl, float alpha,
                                  float rightX, float rightY, float rightZ,
                                  float upX, float upY, float upZ) {
        // ⭐ v4：圆润椭圆花瓣的 6 个局部轮廓点内联为标量。
        // 数值与原 local 字面量逐位相同：
        //   {0, +1.05}, {+0.85, +0.50}, {+0.85, -0.50},
        //   {0, -1.05}, {-0.85, -0.50}, {-0.85, +0.50}   （均 × size）
        // 横向略宽的椭圆——比正圆更像花瓣、比尖菱形更柔和
        final float l0u = 0f, l0v = size * 1.05f;
        final float l1u = size * 0.85f, l1v = size * 0.5f;
        final float l2u = size * 0.85f, l2v = -size * 0.5f;
        final float l3u = 0f, l3v = -size * 1.05f;
        final float l4u = -size * 0.85f, l4v = -size * 0.5f;
        final float l5u = -size * 0.85f, l5v = size * 0.5f;

        final float cosR = (float) Math.cos(rot);
        final float sinR = (float) Math.sin(rot);

        // ⭐ v4：世界坐标写入静态复用缓冲，不再每片 new float[6] 三次
        final float[] wx = PETAL_WX;
        final float[] wy = PETAL_WY;
        final float[] wz = PETAL_WZ;

        // 逐点：局部二维坐标 → 绕视线旋转 → 沿相机右 / 上向量展开为世界坐标
        wx[0] = cx + rightX * (l0u * cosR - l0v * sinR) + upX * (l0u * sinR + l0v * cosR);
        wy[0] = cy + rightY * (l0u * cosR - l0v * sinR) + upY * (l0u * sinR + l0v * cosR);
        wz[0] = cz + rightZ * (l0u * cosR - l0v * sinR) + upZ * (l0u * sinR + l0v * cosR);

        wx[1] = cx + rightX * (l1u * cosR - l1v * sinR) + upX * (l1u * sinR + l1v * cosR);
        wy[1] = cy + rightY * (l1u * cosR - l1v * sinR) + upY * (l1u * sinR + l1v * cosR);
        wz[1] = cz + rightZ * (l1u * cosR - l1v * sinR) + upZ * (l1u * sinR + l1v * cosR);

        wx[2] = cx + rightX * (l2u * cosR - l2v * sinR) + upX * (l2u * sinR + l2v * cosR);
        wy[2] = cy + rightY * (l2u * cosR - l2v * sinR) + upY * (l2u * sinR + l2v * cosR);
        wz[2] = cz + rightZ * (l2u * cosR - l2v * sinR) + upZ * (l2u * sinR + l2v * cosR);

        wx[3] = cx + rightX * (l3u * cosR - l3v * sinR) + upX * (l3u * sinR + l3v * cosR);
        wy[3] = cy + rightY * (l3u * cosR - l3v * sinR) + upY * (l3u * sinR + l3v * cosR);
        wz[3] = cz + rightZ * (l3u * cosR - l3v * sinR) + upZ * (l3u * sinR + l3v * cosR);

        wx[4] = cx + rightX * (l4u * cosR - l4v * sinR) + upX * (l4u * sinR + l4v * cosR);
        wy[4] = cy + rightY * (l4u * cosR - l4v * sinR) + upY * (l4u * sinR + l4v * cosR);
        wz[4] = cz + rightZ * (l4u * cosR - l4v * sinR) + upZ * (l4u * sinR + l4v * cosR);

        wx[5] = cx + rightX * (l5u * cosR - l5v * sinR) + upX * (l5u * sinR + l5v * cosR);
        wy[5] = cy + rightY * (l5u * cosR - l5v * sinR) + upY * (l5u * sinR + l5v * cosR);
        wz[5] = cz + rightZ * (l5u * cosR - l5v * sinR) + upZ * (l5u * sinR + l5v * cosR);

        // 三角扇：中心不透明 + 相邻两轮廓点渐隐为 0（顺序与原实现完全一致）
        for (int i = 0; i < PETAL_POINTS; i++) {
            int j = (i + 1) % PETAL_POINTS;
            b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
            b.vertex(m, wx[i], wy[i], wz[i]).color(r, g, bl, 0f).endVertex();
            b.vertex(m, wx[j], wy[j], wz[j]).color(r, g, bl, 0f).endVertex();
        }
    }

    /**
     * 带宽度的水平线段，<b>两端颜色与 alpha 均可分别指定</b>（螺旋需要沿弧长做色彩梯度）。
     * <p><b>注意：</b>本方法会<b>同时读取</b> {@code col1} 与 {@code col2}，
     * 因此调用方必须保证这两个数组不是同一个缓冲——这正是
     * {@link #drawHypnoticSpiral} 使用双缓冲的原因。</p>
     *
     * @param hw 线半宽（格）
     */
    private static void lineGradient(BufferBuilder b, Matrix4f m,
                                     float x1, float z1, float x2, float z2, float y,
                                     float hw, float[] col1, float a1, float[] col2, float a2) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        double nx = -dz / len * hw;
        double nz = dx / len * hw;
        float ax1 = (float) (x1 + nx), az1 = (float) (z1 + nz);
        float ax2 = (float) (x1 - nx), az2 = (float) (z1 - nz);
        float bx1 = (float) (x2 + nx), bz1 = (float) (z2 + nz);
        float bx2 = (float) (x2 - nx), bz2 = (float) (z2 - nz);

        b.vertex(m, ax1, y, az1).color(col1[0], col1[1], col1[2], a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(col2[0], col2[1], col2[2], a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(col2[0], col2[1], col2[2], a2).endVertex();

        b.vertex(m, ax1, y, az1).color(col1[0], col1[1], col1[2], a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(col2[0], col2[1], col2[2], a2).endVertex();
        b.vertex(m, ax2, y, az2).color(col1[0], col1[1], col1[2], a1).endVertex();
    }

    /**
     * 小菱形光点（柔光），中心最亮、四角渐隐。水平面。
     * <p>
     * <b>v4：四个角点内联为标量。</b>原实现用 {@code float[][] pts} 字面量表达角点，
     * 每次调用分配 <b>5 个临时数组</b>（1 个外层 + 4 个 {@code float[2]}）。
     * 本方法虽然每实体每帧只调用一次（螺旋中心锚点），但清理方式与
     * {@link #emitPetal} 完全同源，一并处理；顶点输出与顺序逐字不变。
     * </p>
     */
    private static void spark(BufferBuilder b, Matrix4f m, float px, float pz, float y,
                              float size, float[] col, float alpha) {
        float r = col[0], g = col[1], bl = col[2];

        // 四个角点（顺序与原 pts[0..3] 一致：北 → 东 → 南 → 西）
        float p0x = px, p0z = pz - size;
        float p1x = px + size, p1z = pz;
        float p2x = px, p2z = pz + size;
        float p3x = px - size, p3z = pz;

        sparkTri(b, m, px, y, pz, p0x, p0z, p1x, p1z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p1x, p1z, p2x, p2z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p2x, p2z, p3x, p3z, r, g, bl, alpha);
        sparkTri(b, m, px, y, pz, p3x, p3z, p0x, p0z, r, g, bl, alpha);
    }

    /**
     * 柔光光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     *
     * @param cx 中心 X（相对相机）
     * @param y  水平面高度
     * @param cz 中心 Z
     * @param ax 第一个外角 X
     * @param az 第一个外角 Z
     * @param bx 第二个外角 X
     * @param bz 第二个外角 Z
     */
    private static void sparkTri(BufferBuilder b, Matrix4f m,
                                 float cx, float y, float cz,
                                 float ax, float az, float bx, float bz,
                                 float r, float g, float bl, float alpha) {
        b.vertex(m, cx, y, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, y, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, y, bz).color(r, g, bl, 0f).endVertex();
    }

    /**
     * 水平径向渐变圆盘（中心 alpha、边缘 0）。
     */
    private static void drawDisc(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz, float radius, int segments,
                                 float r, float g, float bl, float centerAlpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = (TAU * i) / segments;
            double a1 = (TAU * (i + 1)) / segments;
            float x0 = cx + radius * (float) Math.cos(a0);
            float z0 = cz + radius * (float) Math.sin(a0);
            float x1 = cx + radius * (float) Math.cos(a1);
            float z1 = cz + radius * (float) Math.sin(a1);
            b.vertex(m, cx, cy, cz).color(r, g, bl, centerAlpha).endVertex();
            b.vertex(m, x0, cy, z0).color(r, g, bl, 0f).endVertex();
            b.vertex(m, x1, cy, z1).color(r, g, bl, 0f).endVertex();
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

    // ==================== 数学辅助 ====================

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }
}
