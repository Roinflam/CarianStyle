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
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
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
 * <p>
 * <b>视觉逐位一致：</b>{@link VisualColor#lerpInto} 保留了旧 {@code lerpRgb → unpack} 链路
 * 在 0~255 整数域插值并 {@link Math#round} 取整的行为，输出的每个颜色分量与 v8 完全相同——
 * 不是「肉眼看不出」而是「数值相等」。
 * </p>
 *
 * @author FlameForge
 * @version 9
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class GoldenTreeBlessingRenderer {

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int MOTE_SEGMENTS = 10;
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== v8 LOD 下限与保留阈值 =====
    /** 柔光点的最少分段数：4 段仍是个饱满的菱形柔光块，再低就露馅 */
    private static final int MOTE_SEGMENTS_MIN = 4;
    /** 脚下光晕圆盘的最少分段数 */
    private static final int HALO_SEGMENTS_MIN = 8;
    /** 护盾 / 脉冲环的最少分段数 */
    private static final int RING_SEGMENTS_MIN = 10;
    /** 胸口光辉最外层（大而淡）的保留阈值：远处完全看不出 */
    private static final float GLOW_OUTER_KEEP_THRESHOLD = 0.55f;
    /** 飘落金叶层的保留阈值：升起的金叶已足以表达意象，飘落层是锦上添花 */
    private static final float DESCENDING_KEEP_THRESHOLD = 0.5f;
    /** 环绕星芒层的保留阈值：纯细节补光，尺寸极小 */
    private static final float SPARKLE_KEEP_THRESHOLD = 0.45f;
    /** 根须二级支根的保留阈值：低于此值只画主根 */
    private static final float ROOT_BRANCH_KEEP_THRESHOLD = 0.6f;

    // ===== 配色（0xRRGGBB）=====
    private static final int HOLY_GOLD = 0xFFC23A;
    private static final int HOLY_WHITE = 0xFFF6DC;
    private static final int HOLY_DEEP = 0xB8791A;
    /** 庇护护盾专用冷白色，与主体金色区分 */
    private static final int HOLY_SHIELD = 0xF2F7FF;

    // ===== v9：预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    //
    // 注意：HOLY_GOLD 没有对应的 C_ 常量。它从不单独使用，永远是作为插值起点
    // （lerpRgb / VisualColor.lerpInto 的 from 参数）出现的，那两个方法接收的是 int 而非 float[]。
    /** 神圣白（符文环 / 十字圣徽 / 立誓光核 / 星芒 / 神圣脉冲 / 胸口核心高光） */
    private static final float[] C_HOLY_WHITE = VisualColor.constant(HOLY_WHITE);
    /** 深金（脚下光晕的内层暗盘） */
    private static final float[] C_HOLY_DEEP = VisualColor.constant(HOLY_DEEP);
    /** 护盾冷白（庇护双环专用，与主体金色区分） */
    private static final float[] C_HOLY_SHIELD = VisualColor.constant(HOLY_SHIELD);

    /**
     * v9：动态插值色的复用缓冲（⚠ 写入后必须立即消费，不可跨调用留存）。
     * <p>
     * 用于全部随 {@code activeCount} / 时间变化的主题色：脚下光晕主色、根须主色、
     * 胸口光辉主色、上升金叶逐片色、飘落金叶逐片色。这些不会同时活跃
     * （各 draw 方法顺序调用、互不嵌套），故<b>一个缓冲即可</b>——
     * 本渲染器没有「两个动态色同时存活」的场景，详见类注释「为什么这里一个缓冲就够」。
     * </p>
     * <p>
     * <b>唯一需要留意的是 {@link #drawRootVeins}</b>：主题色写入后要横跨整个根须循环，
     * 期间 {@link #drawRootBranch}（含递归支根）只读不写，因此安全。
     * 若将来在该循环内新增任何写 {@link #SCRATCH} 的逻辑，必须改用双缓冲。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final float[] SCRATCH = new float[VisualColor.RGB];

    // ===== 脚下金色光晕 =====
    private static final int HALO_SEGMENTS = 28;
    private static final float HALO_RADIUS_FACTOR = 0.9f;
    private static final float HALO_BASE_ALPHA = 0.24f;
    /** 每多一层祝福，光晕半径额外放大的比例 */
    private static final float HALO_STACK_RADIUS_STEP = 0.10f;
    /** 每多一层祝福，光晕透明度额外放大的比例 */
    private static final float HALO_STACK_ALPHA_STEP = 0.3f;

    // ===== 胸口神圣光辉：由内而外分层柔光 =====
    private static final float GLOW_HEIGHT_FACTOR = 0.55f;
    private static final float GLOW_SIZE_FACTOR = 0.5f;
    private static final float GLOW_BASE_ALPHA = 0.4f;
    private static final float GLOW_STACK_ALPHA_STEP = 0.18f;
    private static final float GLOW_PULSE_SPEED = 1.3f;

    // ===== 上升金叶 =====
    private static final int BASE_MOTES = 9;
    private static final int BLESSING_EXTRA_MOTES = 4;
    private static final int MOTE_STACK_STEP = 4;
    private static final float RISE_SPEED = 0.16f;
    private static final float MOTE_SIZE = 0.09f;
    private static final float RISE_HEIGHT_FACTOR = 1.3f;
    private static final float SPREAD_FACTOR = 0.6f;
    private static final float MOTE_STACK_ALPHA_STEP = 0.25f;
    /** 金叶翻转速度（模拟叶片飘落时的自然转动） */
    private static final float LEAF_SPIN_SPEED = 2.2f;

    // ===== 脚下旋转符文环 =====
    private static final int RUNE_TICK_COUNT = 12;
    private static final float RUNE_ROT_SPEED = 0.25f;
    private static final float RUNE_BASE_ALPHA = 0.6f;
    private static final float RUNE_STACK_ALPHA_STEP = 0.12f;

    // ===== 中央十字圣徽 =====
    private static final float CROSS_PULSE_SPEED = 2.0f;

    // ===== 黄金树根须纹样（核心标志物）=====
    /** 主根数量（随祝福数量小幅增加） */
    private static final int ROOT_MAIN_COUNT = 5;
    private static final int ROOT_STACK_STEP = 1;
    /** 根须基础长度系数（× 实体宽度） */
    private static final float ROOT_LENGTH_FACTOR = 0.95f;
    /** 主根每段折线数（越多越自然弯曲，但顶点也越多） */
    private static final int ROOT_SEGMENTS = 4;
    /** 主根折线数下限（低于 2 段就不成"根"了） */
    private static final int ROOT_SEGMENTS_MIN = 2;
    /** 支根长度相对主根的比例 */
    private static final float ROOT_BRANCH_LENGTH_RATIO = 0.45f;
    private static final float ROOT_BASE_ALPHA = 0.42f;
    private static final float ROOT_STACK_ALPHA_STEP = 0.1f;

    // ===== 立誓：胸口脉动光核 =====
    private static final float VOW_PULSE_SPEED = 3.0f;

    // ===== 庇护：护盾双环 =====
    private static final int SHIELD_SEGMENTS = 32;
    private static final float SHIELD_PULSE_SPEED = 1.6f;

    // ===== 环绕的星芒微光 =====
    private static final int SPARKLE_COUNT = 6;
    private static final int SPARKLE_STACK_STEP = 2;
    private static final float SPARKLE_ORBIT_SPEED = 0.4f;

    // ===== 三重祝福同时生效时的额外「神圣脉冲」=====
    private static final float PULSE_PERIOD = 1.8f;
    private static final int PULSE_WAVE_COUNT = 2;
    private static final float PULSE_MAX_RADIUS_FACTOR = 2.0f;
    private static final int PULSE_RAY_COUNT = 12;

    private GoldenTreeBlessingRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有携带黄金树祝福生物的神圣光效。
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

        MobEffect vow = CarianStylePotion.GOLDEN_VOW.get();
        MobEffect blessing = CarianStylePotion.BLESSING_OF_THE_ERDTREE.get();
        MobEffect protection = CarianStylePotion.PROTECTION_OF_THE_ERDTREE.get();

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
            if (!hasAnyBlessing(entity, vow, blessing, protection)) {
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

            // ⭐ v8：本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0，视觉与优化前一致
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度（不影响本帧绘制）
            VisualLod.countInstance();

            boolean hasVow = vow != null && entity.hasEffect(vow);
            boolean hasBlessing = blessing != null && entity.hasEffect(blessing);
            boolean hasProtection = protection != null && entity.hasEffect(protection);
            // 同时生效的祝福数量（1~3），驱动整体视觉强度叠加
            int activeCount = (hasVow ? 1 : 0) + (hasBlessing ? 1 : 0) + (hasProtection ? 1 : 0);

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawGoldenHalo(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(),
                    activeCount, detail);
            drawRootVeins(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(),
                    activeCount, detail);
            drawRuneRing(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(),
                    activeCount, detail);
            drawCrossEmblem(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), activeCount);
            drawRadiantGlow(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), activeCount,
                    rightX, rightY, rightZ, upX, upY, upZ, detail);
            drawRisingMotes(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), hasBlessing,
                    activeCount, rightX, rightY, rightZ, upX, upY, upZ, detail);
            // 飘落金叶是纯氛围层，远处完全看不出，低细节时整层跳过（省约 250 顶点）
            if (VisualLod.keepLayer(detail, DESCENDING_KEEP_THRESHOLD)) {
                drawDescendingMotes(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), activeCount,
                        rightX, rightY, rightZ, upX, upY, upZ, detail);
            }

            if (hasVow) {
                drawVowCore(builder, matrix, rx, ryFoot, rz, height, time, entity.getId(),
                        rightX, rightY, rightZ, upX, upY, upZ, detail);
            }
            if (hasProtection) {
                drawProtectionShield(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(), detail);
            }
            // 星芒是极小的补光点，低细节时整层跳过（省约 300 顶点）
            if (VisualLod.keepLayer(detail, SPARKLE_KEEP_THRESHOLD)) {
                drawSparkles(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), activeCount,
                        rightX, rightY, rightZ, upX, upY, upZ, detail);
            }
            if (activeCount >= 3) {
                drawRadiantPulse(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(), detail);
            }
        }
    }

    /**
     * 判断实体是否携带任一黄金树祝福。
     *
     * @param entity     待判定实体
     * @param vow        黄金树立誓效果对象（可能为 null）
     * @param blessing   黄金树恩惠效果对象（可能为 null）
     * @param protection 黄金树庇护效果对象（可能为 null）
     * @return 携带任一祝福返回 true
     */
    private static boolean hasAnyBlessing(LivingEntity entity,
                                          @Nullable MobEffect vow,
                                          @Nullable MobEffect blessing,
                                          @Nullable MobEffect protection) {
        if (vow != null && entity.hasEffect(vow)) {
            return true;
        }
        if (blessing != null && entity.hasEffect(blessing)) {
            return true;
        }
        return protection != null && entity.hasEffect(protection);
    }

    /**
     * 脚下金色光晕：径向渐变圆盘（两层），缓慢呼吸。半径/透明度随 {@code activeCount} 小幅放大，
     * 主题色随 {@code activeCount} 从纯金向纯白过渡（象征祝福纯度更高）。
     * <p>v8：分段数按细节系数缩放（下限 {@link #HALO_SEGMENTS_MIN}）。</p>
     * <p>v9：主题色写入 {@link #SCRATCH} 后立即被首个 {@code drawDisc} 消费；
     * 内层暗盘直接用只读常量 {@link #C_HOLY_DEEP}，两者不冲突。</p>
     */
    private static void drawGoldenHalo(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz, float width,
                                       float time, int seedId, int activeCount, float detail) {
        float stackAlphaMul = 1f + (activeCount - 1) * HALO_STACK_ALPHA_STEP;
        float stackRadiusMul = 1f + (activeCount - 1) * HALO_STACK_RADIUS_STEP;
        float breath = 0.85f + 0.15f * Mth.sin(time * 1.1f + seedId * 0.5f);
        float radius = width * HALO_RADIUS_FACTOR * stackRadiusMul * breath;

        int segments = VisualLod.scaleSegments(HALO_SEGMENTS, HALO_SEGMENTS_MIN, detail);

        // v9：无分配插值（0~255 域取整，与旧 lerpRgb → unpack 链路逐位一致）
        VisualColor.lerpInto(SCRATCH, HOLY_GOLD, HOLY_WHITE, purityFactor(activeCount));
        drawDisc(b, m, cx, cy, cz, radius, segments, SCRATCH[0], SCRATCH[1], SCRATCH[2],
                clamp01(HALO_BASE_ALPHA * stackAlphaMul));

        drawDisc(b, m, cx, cy, cz, radius * 0.55f, segments, C_HOLY_DEEP[0], C_HOLY_DEEP[1], C_HOLY_DEEP[2],
                clamp01(HALO_BASE_ALPHA * stackAlphaMul * 0.55f));
    }

    /**
     * 黄金树根须纹样：从脚下向外递归分叉生长的金色根脉线条（主根 + 中途分出的支根，
     * 均带轻微自然弯曲、逐级变细变淡），直接对应法环环境美术里反复出现的黄金树根系意象。
     * 根须长度、数量、亮度随 {@code activeCount} 小幅提升。
     * <p>
     * v8：主根<b>按步长抽取</b>而非截断——根须角度是 {@code i × (TAU / mainCount)} 均布的，
     * 若只画前 N 条会全部挤在同一侧；步长抽取则保留元素的方位不变、且始终铺满整圈。
     * 折线段数按细节缩放，低细节时不再画二级支根。
     * </p>
     * <p>
     * <b>v9：主题色写入 {@link #SCRATCH} 后横跨整个根须循环。</b>
     * 这是本渲染器唯一「缓冲需要长期存活」的地方——{@link #drawRootBranch}（含递归支根）
     * 只读不写，因此安全。若将来在该循环内新增任何写 {@link #SCRATCH} 的逻辑，必须改用双缓冲。
     * </p>
     */
    private static void drawRootVeins(BufferBuilder b, Matrix4f m,
                                      float cx, float cy, float cz, float width,
                                      float time, int seedId, int activeCount, float detail) {
        int mainCount = ROOT_MAIN_COUNT + (activeCount - 1) * ROOT_STACK_STEP;
        float maxLen = width * ROOT_LENGTH_FACTOR * (1f + (activeCount - 1) * 0.1f);
        float alpha = clamp01((ROOT_BASE_ALPHA + (activeCount - 1) * ROOT_STACK_ALPHA_STEP)
                * (0.75f + 0.25f * Mth.sin(time * 0.7f + seedId)));
        float hw = Math.max(0.02f, width * 0.012f);
        // v9：无分配插值。SCRATCH 在下方整个循环期间保持有效（循环内只读不写）
        VisualColor.lerpInto(SCRATCH, HOLY_GOLD, HOLY_WHITE, purityFactor(activeCount));

        // ⭐ v8：均布角度必须按步长抽取，不能截断前 N 条（详见类注释）
        int drawnCount = VisualLod.scale(mainCount, detail);
        int step = Math.max(1, mainCount / drawnCount);
        // 折线段数缩放：段数越少根须越硬直，故设下限
        int rootSegments = VisualLod.scaleSegments(ROOT_SEGMENTS, ROOT_SEGMENTS_MIN, detail);
        // 二级支根是纯细节，低细节时整体不画（省约一半根须顶点）
        int depth = VisualLod.keepLayer(detail, ROOT_BRANCH_KEEP_THRESHOLD) ? 1 : 0;

        for (int i = 0; i < mainCount; i += step) {
            // 种子仍用原始下标 i，保证保留下来的根须形状与全细节时完全一致
            long s = seedFor(seedId, i + 2000);
            float baseAngle = i * (TAU / mainCount) + rngFloat(s) * 0.4f;
            s = rngNext(s);
            drawRootBranch(b, m, cx, cz, cy, baseAngle, maxLen, depth, hw, SCRATCH, alpha, s, rootSegments);
        }
    }

    /**
     * 绘制一条根须（主干为带轻微随机弯曲的折线，中段可能分出一条更细更暗的支根）。
     * 支根通过 {@code depth} 控制最多递归一层，避免顶点数失控。
     * <p><b>本方法及其递归调用只读取 {@code col}，绝不写入</b>——
     * 调用方传入的是 {@link #SCRATCH}，写入会破坏尚未画完的根须。</p>
     *
     * @param segments 本条根须的折线段数（由调用方按细节系数缩放后传入）
     */
    private static void drawRootBranch(BufferBuilder b, Matrix4f m, float cx, float cz, float cy,
                                       float angle, float length, int depth, float hw,
                                       float[] col, float alpha, long seed, int segments) {
        float px = cx, pz = cz;
        long s = seed;
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            s = rngNext(s);
            float wobble = (rngFloat(s) - 0.5f) * 0.35f;
            float ang = angle + wobble * t;
            float r = length * t;
            float x = cx + r * (float) Math.cos(ang);
            float z = cz + r * (float) Math.sin(ang);
            float segAlpha = alpha * (1f - t * 0.65f);
            float segHw = hw * (1f - t * 0.5f);
            lineF(b, m, px, pz, x, z, cy, segHw, col, segAlpha, segAlpha);
            px = x;
            pz = z;

            // 中段分出一条更细更暗的支根，只递归一层，避免顶点数爆炸
            if (depth > 0 && i == segments / 2) {
                s = rngNext(s);
                float branchAngle = ang + (rngFloat(s) - 0.5f) * 1.7f;
                drawRootBranch(b, m, x, z, cy, branchAngle, length * ROOT_BRANCH_LENGTH_RATIO,
                        depth - 1, hw * 0.6f, col, alpha * 0.7f, s, segments);
            }
        }
    }

    /**
     * 脚下缓慢旋转的金色符文刻度环，转速与亮度随 {@code activeCount} 小幅提升。
     * <p>v8：刻度按步长抽取（均布角度，不能截断）。</p>
     * <p>v9：改用只读常量 {@link #C_HOLY_WHITE}，零分配。</p>
     */
    private static void drawRuneRing(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float width,
                                     float time, int seedId, int activeCount, float detail) {
        float radius = width * HALO_RADIUS_FACTOR * 0.9f * (1f + (activeCount - 1) * HALO_STACK_RADIUS_STEP);
        float speedMul = 1f + (activeCount - 1) * 0.3f;
        float rot = time * RUNE_ROT_SPEED * speedMul + seedId * 0.3f;
        float alpha = clamp01(RUNE_BASE_ALPHA + (activeCount - 1) * RUNE_STACK_ALPHA_STEP);
        float hw = Math.max(0.022f, width * 0.01f);

        // ⭐ v8：均布刻度按步长抽取，保证仍铺满整圈
        int drawnCount = VisualLod.scale(RUNE_TICK_COUNT, detail);
        int step = Math.max(1, RUNE_TICK_COUNT / drawnCount);

        for (int i = 0; i < RUNE_TICK_COUNT; i += step) {
            double base = rot + TAU * i / RUNE_TICK_COUNT;
            float ix = cx + (float) Math.cos(base) * radius * 0.85f;
            float iz = cz + (float) Math.sin(base) * radius * 0.85f;
            float ox = cx + (float) Math.cos(base) * radius;
            float oz = cz + (float) Math.sin(base) * radius;
            line(b, m, ix, iz, ox, oz, cy, hw, C_HOLY_WHITE, alpha);
        }
    }

    /**
     * 脚下中央十字圣徽：沿用「圣域」的十字圣徽母题（两条垂直相交的粗线），随心跳缓慢明灭；
     * 长度、粗细、亮度随 {@code activeCount} 小幅提升，是三个祝福共享的核心标志性图案。
     * <p>v8：仅 12 个顶点，是全渲染器最廉价也最具辨识度的元素，<b>不做任何 LOD 削减</b>。</p>
     * <p>v9：改用只读常量 {@link #C_HOLY_WHITE}，零分配。</p>
     */
    private static void drawCrossEmblem(BufferBuilder b, Matrix4f m,
                                        float cx, float cy, float cz, float width,
                                        float time, int seedId, int activeCount) {
        float pulse = 0.6f + 0.4f * Mth.sin(time * CROSS_PULSE_SPEED + seedId);
        float len = width * (0.36f + (activeCount - 1) * 0.06f);
        float hw = Math.max(0.032f, width * 0.016f) * (1f + (activeCount - 1) * 0.15f);
        float alpha = clamp01((0.55f + (activeCount - 1) * 0.12f) * pulse);

        line(b, m, cx - len, cz, cx + len, cz, cy, hw, C_HOLY_WHITE, alpha);
        line(b, m, cx, cz - len, cx, cz + len, cy, hw, C_HOLY_WHITE, alpha);
    }

    /**
     * 胸口神圣光辉：由内而外三层叠加的柔光——外层大而淡、中层小而亮、核心一点纯白——
     * 集中在角色胸口位置，表达「这个人本身正被神圣之力笼罩、由内而外发光」。
     * <p>v8：分段数缩放；低细节时跳过最外层（大而淡，远处完全看不出，却与其余两层等顶点量）。</p>
     * <p>v9：主题色写入 {@link #SCRATCH} 供外 / 中两层使用，核心高光直接用只读常量
     * {@link #C_HOLY_WHITE}，两者不冲突。</p>
     */
    private static void drawRadiantGlow(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz, float width, float height,
                                        float time, int seedId, int activeCount,
                                        float rightX, float rightY, float rightZ,
                                        float upX, float upY, float upZ, float detail) {
        float chestY = cyFoot + height * GLOW_HEIGHT_FACTOR;
        float pulse = 0.7f + 0.3f * Mth.sin(time * GLOW_PULSE_SPEED + seedId);
        float alpha = clamp01((GLOW_BASE_ALPHA + (activeCount - 1) * GLOW_STACK_ALPHA_STEP) * pulse);
        float size = width * GLOW_SIZE_FACTOR * (1f + (activeCount - 1) * 0.12f);

        int segments = VisualLod.scaleSegments(MOTE_SEGMENTS, MOTE_SEGMENTS_MIN, detail);

        // v9：无分配插值。SCRATCH 在外层与中层之间保持有效（其间无其它写入）
        VisualColor.lerpInto(SCRATCH, HOLY_GOLD, HOLY_WHITE, 0.4f + purityFactor(activeCount) * 0.4f);

        // 外层：大而淡，铺垫整体光晕范围。远处看不出，低细节时跳过
        if (VisualLod.keepLayer(detail, GLOW_OUTER_KEEP_THRESHOLD)) {
            emitSoftMote(b, m, cx, chestY, cz, size, SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha * 0.45f,
                    rightX, rightY, rightZ, upX, upY, upZ, segments);
        }
        // 中层：更小更亮，收拢焦点
        emitSoftMote(b, m, cx, chestY, cz, size * 0.55f, SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha * 0.8f,
                rightX, rightY, rightZ, upX, upY, upZ, segments);
        // 核心：一点纯白高光，是"由内而外发光"的视觉锚点
        emitSoftMote(b, m, cx, chestY, cz, size * 0.22f, C_HOLY_WHITE[0], C_HOLY_WHITE[1], C_HOLY_WHITE[2], alpha,
                rightX, rightY, rightZ, upX, upY, upZ, segments);
    }

    /**
     * 徐徐升起的金叶（水滴形，非正圆）；拥有「黄金树恩惠」时额外增加数量（表现生息感），
     * 同时数量与亮度随 {@code activeCount} 小幅提升。
     * <p>v8：数量按细节系数缩放。金叶位置由 {@code seedFor(entityId, i)} 决定，
     * 截断尾部时保留元素的种子与轨迹完全不变，靠近时是「逐渐多出几片叶子」。</p>
     * <p>
     * <b>v9：本方法是全渲染器最密集的颜色计算点（最多 21 片 × 1 次）。</b>
     * 每片的颜色写入 {@link #SCRATCH} 后立即被 {@link #emitLeafMote} 消费，零分配。
     * 注意 {@code primary} <b>仍必须是 int</b>——它是循环内每片插值的<b>起点</b>，
     * 而 {@link VisualColor#lerpInto} 的 {@code from} 参数接收的正是 int，故这里保留
     * {@link #lerpRgb} 不动。
     * </p>
     */
    private static void drawRisingMotes(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz, float width, float height,
                                        float time, int seedId, boolean hasBlessing, int activeCount,
                                        float rightX, float rightY, float rightZ,
                                        float upX, float upY, float upZ, float detail) {
        int baseCount = BASE_MOTES + (hasBlessing ? BLESSING_EXTRA_MOTES : 0) + (activeCount - 1) * MOTE_STACK_STEP;
        int count = VisualLod.scale(baseCount, detail);
        float alphaMul = 1f + (activeCount - 1) * MOTE_STACK_ALPHA_STEP;
        float riseHeight = height * RISE_HEIGHT_FACTOR;
        float spread = width * SPREAD_FACTOR;
        // 保留为 int：这是下方循环内逐片插值的起点，lerpInto 的 from 参数即接收 int
        int primary = lerpRgb(HOLY_GOLD, HOLY_WHITE, purityFactor(activeCount));

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.3f + 0.7f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.7f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float twPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float spinPhase = rngFloat(s) * TAU;

            float t = frac(time * RISE_SPEED + phase);

            float env;
            if (t < 0.15f) {
                env = t / 0.15f;
            } else if (t > 0.65f) {
                env = 1f - (t - 0.65f) / 0.35f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }
            float twinkle = 0.7f + 0.3f * Mth.sin(time * 2.2f + twPhase);
            float alpha = clamp01(0.5f * env * twinkle * alphaMul);
            if (alpha <= 0.01f) {
                continue;
            }

            float curRad = spread * radFactor * (0.6f + 0.4f * t);
            float px = cx + (float) Math.cos(ang) * curRad;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float py = cyFoot + t * riseHeight + Y_OFFSET;

            // v9：无分配插值，写入复用缓冲后立即消费
            VisualColor.lerpInto(SCRATCH, primary, HOLY_WHITE, t);
            float size = MOTE_SIZE * sizeRand * (1.1f - 0.3f * t);
            float rot = time * LEAF_SPIN_SPEED + spinPhase;

            emitLeafMote(b, m, px, py, pz, size, rot, SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /**
     * 飘落金叶：自高处缓缓飘落的金色叶片，速度、飘荡幅度均比脚下升起的更慢更柔，
     * 还原「黄金树周围落叶飘散」的经典法环意象——与脚下上升的叶片方向相反、一升一降，
     * 让整体更有层次。数量随 {@code activeCount} 小幅增加。
     * <p>v8：数量按细节系数缩放；整层由调用方按 {@link #DESCENDING_KEEP_THRESHOLD} 决定是否绘制。</p>
     * <p>v9：每片的颜色写入 {@link #SCRATCH} 后立即被 {@link #emitLeafMote} 消费，零分配。</p>
     */
    private static void drawDescendingMotes(BufferBuilder b, Matrix4f m,
                                            float cx, float cyFoot, float cz, float width, float height,
                                            float time, int seedId, int activeCount,
                                            float rightX, float rightY, float rightZ,
                                            float upX, float upY, float upZ, float detail) {
        int baseCount = 8 + (activeCount - 1) * 3;
        int count = VisualLod.scale(baseCount, detail);
        float startHeight = height * 1.9f;
        float spread = width * 0.65f;

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 1200);
            float phase = rngFloat(s);
            s = rngNext(s);
            float ang = rngFloat(s) * TAU;
            s = rngNext(s);
            float radFactor = 0.3f + 0.7f * rngFloat(s);
            s = rngNext(s);
            float sizeRand = 0.6f + 0.7f * rngFloat(s);
            s = rngNext(s);
            float swayPhase = rngFloat(s) * TAU;
            s = rngNext(s);
            float spinPhase = rngFloat(s) * TAU;

            float t = frac(time * 0.1f + phase); // 0=高处 1=落地

            float env;
            if (t < 0.1f) {
                env = t / 0.1f;
            } else if (t > 0.85f) {
                env = 1f - (t - 0.85f) / 0.15f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }

            float sway = (float) Math.sin(time * 0.8f + swayPhase) * 0.15f;
            float curRad = spread * radFactor;
            float px = cx + (float) Math.cos(ang) * curRad + sway;
            float pz = cz + (float) Math.sin(ang) * curRad;
            float py = cyFoot + startHeight * (1f - t) + Y_OFFSET;

            float twinkle = 0.6f + 0.4f * Mth.sin(time * 3f + i * 1.1f);
            float alpha = clamp01(0.45f * env * twinkle);
            // v9：无分配插值，写入复用缓冲后立即消费
            VisualColor.lerpInto(SCRATCH, HOLY_GOLD, HOLY_WHITE, 0.3f + 0.4f * twinkle);
            float size = 0.075f * sizeRand;
            // 飘落的叶片翻转比升起的更慢，更有"打着转飘下来"的感觉
            float rot = time * (LEAF_SPIN_SPEED * 0.5f) + spinPhase;

            emitLeafMote(b, m, px, py, pz, size, rot, SCRATCH[0], SCRATCH[1], SCRATCH[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ);
        }
    }

    /**
     * 立誓：胸口处随心跳节奏脉动的白金光核。
     * <p>v8：分段数缩放。仅 1 个柔光块，是立誓的唯一专属标志，不做整层跳过。</p>
     * <p>v9：改用只读常量 {@link #C_HOLY_WHITE}，零分配。</p>
     */
    private static void drawVowCore(BufferBuilder b, Matrix4f m,
                                    float cx, float cyFoot, float cz, float height,
                                    float time, int seedId,
                                    float rightX, float rightY, float rightZ,
                                    float upX, float upY, float upZ, float detail) {
        float chestY = cyFoot + height * 0.6f;
        float pulse = 0.6f + 0.4f * Mth.sin(time * VOW_PULSE_SPEED + seedId);
        float size = 0.15f + 0.05f * pulse;
        float alpha = 0.5f + 0.3f * pulse;
        int segments = VisualLod.scaleSegments(MOTE_SEGMENTS, MOTE_SEGMENTS_MIN, detail);
        emitSoftMote(b, m, cx, chestY, cz, size, C_HOLY_WHITE[0], C_HOLY_WHITE[1], C_HOLY_WHITE[2], alpha,
                rightX, rightY, rightZ, upX, upY, upZ, segments);
    }

    /**
     * 庇护：环绕身周的护盾双环微光（近白冷调，与主体金色区分开来）。
     * <p>v8：环分段数缩放（下限 {@link #RING_SEGMENTS_MIN}）。</p>
     * <p>v9：改用只读常量 {@link #C_HOLY_SHIELD}，零分配。</p>
     */
    private static void drawProtectionShield(BufferBuilder b, Matrix4f m,
                                             float cx, float cyFoot, float cz, float width, float height,
                                             float time, int seedId, float detail) {
        float midY = cyFoot + height * 0.5f;
        float pulse = 0.5f + 0.5f * Mth.sin(time * SHIELD_PULSE_SPEED + seedId * 0.8f);
        float radius = width * 0.7f + 0.05f * pulse;
        int segments = VisualLod.scaleSegments(SHIELD_SEGMENTS, RING_SEGMENTS_MIN, detail);
        ringVertical(b, m, cx, cz, midY, radius, segments, 0.04f, C_HOLY_SHIELD, clamp01(0.32f * pulse + 0.14f));
    }

    /**
     * 环绕脚下的星芒微光：一圈缓慢公转、随机闪烁的小光点，尺寸很小（不遮挡视野），
     * 用来补足细节亮度、提升精致感。数量随 {@code activeCount} 小幅增加。
     * <p>v8：数量与分段数均缩放；整层由调用方按 {@link #SPARKLE_KEEP_THRESHOLD} 决定是否绘制。
     * 星芒是均布公转的，故同样按步长抽取而非截断。</p>
     * <p>v9：改用只读常量 {@link #C_HOLY_WHITE}，零分配。</p>
     */
    private static void drawSparkles(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float width,
                                     float time, int seedId, int activeCount,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ, float detail) {
        float radius = width * HALO_RADIUS_FACTOR * 0.95f;
        float rot = time * SPARKLE_ORBIT_SPEED + seedId * 0.4f;
        int baseCount = SPARKLE_COUNT + (activeCount - 1) * SPARKLE_STACK_STEP;
        int segments = VisualLod.scaleSegments(MOTE_SEGMENTS, MOTE_SEGMENTS_MIN, detail);

        int drawnCount = VisualLod.scale(baseCount, detail);
        int step = Math.max(1, baseCount / drawnCount);

        for (int i = 0; i < baseCount; i += step) {
            double ang = rot + TAU * i / baseCount;
            float px = cx + (float) Math.cos(ang) * radius;
            float pz = cz + (float) Math.sin(ang) * radius;
            float twinkle = 0.4f + 0.6f * (0.5f + 0.5f * Mth.sin(time * 5f + i * 1.3f + seedId));
            float size = 0.045f + 0.025f * twinkle;
            emitSoftMote(b, m, px, cy + 0.05f, pz, size,
                    C_HOLY_WHITE[0], C_HOLY_WHITE[1], C_HOLY_WHITE[2], 0.75f * twinkle,
                    rightX, rightY, rightZ, upX, upY, upZ, segments);
        }
    }

    /**
     * 立誓 + 恩惠 + 庇护三重同时生效时的额外「神圣脉冲」：脚下周期性向外扩张并快速淡出的
     * 冲击波光环，首波额外叠加一圈长短交替的光芒射线，用于强调「三重祝福」这一叠加状态。
     * <p>v8：环分段数缩放；射线按步长抽取（均布角度，截断会只喷向一侧）。</p>
     * <p>v9：改用只读常量 {@link #C_HOLY_WHITE}，零分配。</p>
     */
    private static void drawRadiantPulse(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz, float width,
                                         float time, int seedId, float detail) {
        int segments = VisualLod.scaleSegments(HALO_SEGMENTS, RING_SEGMENTS_MIN, detail);

        for (int i = 0; i < PULSE_WAVE_COUNT; i++) {
            float phase = (float) i / PULSE_WAVE_COUNT;
            float t = frac(time / PULSE_PERIOD + phase + seedId * 0.05f);
            float radius = width * PULSE_MAX_RADIUS_FACTOR * easeOutCubic(t);
            float alpha = clamp01((1f - t) * 0.45f);
            if (alpha <= 0.01f || radius <= 0.05f) {
                continue;
            }
            ringVertical(b, m, cx, cz, cy, radius, segments, 0.07f, C_HOLY_WHITE, alpha);

            // 长短交替光芒：仅首波叠加射线，强化「神圣爆发」的观感
            if (i == 0) {
                float hw = Math.max(0.035f, width * 0.016f);
                int drawnRays = VisualLod.scale(PULSE_RAY_COUNT, detail);
                int rayStep = Math.max(1, PULSE_RAY_COUNT / drawnRays);
                for (int r = 0; r < PULSE_RAY_COUNT; r += rayStep) {
                    float rayLen = radius * ((r % 2 == 0) ? 1f : 0.6f);
                    double ang = TAU * r / PULSE_RAY_COUNT + seedId * 0.3f;
                    float ox = cx + (float) Math.cos(ang) * rayLen;
                    float oz = cz + (float) Math.sin(ang) * rayLen;
                    line(b, m, cx, cz, ox, oz, cy, hw, C_HOLY_WHITE, alpha * 0.6f);
                }
            }
        }
    }

    /** 一圈水平护盾/脉冲环（简化为固定高度处的圆环，随脉冲呼吸）。 */
    private static void ringVertical(BufferBuilder b, Matrix4f m,
                                     float cx, float cz, float cy, float radius, int segments,
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

    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float z1, float x2, float z2, float y,
                             float hw, float[] col, float alpha) {
        lineF(b, m, x1, z1, x2, z2, y, hw, col, alpha, alpha);
    }

    /**
     * 两端 alpha 可分别指定的线段（浮点颜色版，供根须等需要逐段渐隐的场景使用）。
     * <p><b>注意：</b>两端<b>共用同一个颜色数组</b>，只有 alpha 不同——
     * 这正是本渲染器只需一个 {@link #SCRATCH} 缓冲、无须像螺旋 / 刀痕那样做双缓冲的原因。</p>
     */
    private static void lineF(BufferBuilder b, Matrix4f m,
                              float x1, float z1, float x2, float z2, float y,
                              float hw, float[] col, float a1, float a2) {
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

        b.vertex(m, ax1, y, az1).color(col[0], col[1], col[2], a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(col[0], col[1], col[2], a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(col[0], col[1], col[2], a2).endVertex();

        b.vertex(m, ax1, y, az1).color(col[0], col[1], col[2], a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(col[0], col[1], col[2], a2).endVertex();
        b.vertex(m, ax2, y, az2).color(col[0], col[1], col[2], a1).endVertex();
    }

    /**
     * 绘制一颗面向相机的柔和圆形光点（径向渐变：中心 alpha、边缘 0）。
     * 用于立誓光核、胸口光辉、星芒等非「金叶」意象的元素。
     *
     * @param segments 分段数。v8 起由调用方按细节系数传入，下限 {@link #MOTE_SEGMENTS_MIN}；
     *                 全细节时即 {@link #MOTE_SEGMENTS}。柔光点数量多，是本渲染器的主要顶点杠杆之一。
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

    /**
     * 绘制一片面向相机的柔和「金叶」光点：billboard 平面内用 6 个轮廓点近似出水滴 / 杏仁形
     * （比正圆更贴合「叶片」的联想），支持绕视线方向旋转 {@code rot}，用于模拟叶片飘落时的
     * 自然翻转。中心不透明、边缘渐隐为 0。
     * <p><b>轮廓点数固定为 6，不参与 LOD 缩放</b>——再少就不成叶形了，
     * 金叶的削减完全通过「减少片数」实现。</p>
     *
     * @param rot 叶片在 billboard 平面内的旋转角（弧度）
     */
    private static void emitLeafMote(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float size, float rot,
                                     float r, float g, float bl, float alpha,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ) {
        float[][] localPts = {
                {0f, size * 1.3f}, {size * 0.55f, size * 0.35f}, {size * 0.4f, -size * 0.7f},
                {0f, -size * 1.1f}, {-size * 0.4f, -size * 0.7f}, {-size * 0.55f, size * 0.35f}
        };
        float cosR = (float) Math.cos(rot), sinR = (float) Math.sin(rot);
        float[] wx = new float[6];
        float[] wy = new float[6];
        float[] wz = new float[6];
        for (int i = 0; i < 6; i++) {
            float lu = localPts[i][0] * cosR - localPts[i][1] * sinR;
            float lv = localPts[i][0] * sinR + localPts[i][1] * cosR;
            wx[i] = cx + rightX * lu + upX * lv;
            wy[i] = cy + rightY * lu + upY * lv;
            wz[i] = cz + rightZ * lu + upZ * lv;
        }
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
            b.vertex(m, wx[i], wy[i], wz[i]).color(r, g, bl, 0f).endVertex();
            b.vertex(m, wx[j], wy[j], wz[j]).color(r, g, bl, 0f).endVertex();
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

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    /**
     * 「祝福纯度」系数：{@code activeCount} 越多，主题色越从纯金（{@link #HOLY_GOLD}）
     * 偏向纯白（{@link #HOLY_WHITE}），象征祝福叠得越满、能量越纯粹。
     *
     * @param activeCount 同时生效的祝福数量（1~3）
     * @return 0~1 的插值系数，供 {@link #lerpRgb} / {@link VisualColor#lerpInto} 使用
     */
    private static float purityFactor(int activeCount) {
        return clamp01((activeCount - 1) * 0.35f);
    }

    /**
     * 在 0~255 整数域对两个 0xRRGGBB 做线性插值并取整，返回打包后的 int。
     * <p>
     * <b>v9 保留说明：</b>其余调用点都已改为 {@link VisualColor#lerpInto}（直接出 float[]，零分配），
     * 但 {@link #drawRisingMotes} 里的 {@code primary} <b>必须是 int</b>——它是循环内逐片插值的
     * <b>起点</b>，而 {@link VisualColor#lerpInto} 的 {@code from} 参数接收的正是 int，
     * 故本方法仍被需要。它每帧每实体只调用一次、且不分配任何对象。
     * </p>
     *
     * @param from 起点色（0xRRGGBB）
     * @param to   终点色（0xRRGGBB）
     * @param t    插值系数（自动夹取到 0~1）
     * @return 插值结果（0xRRGGBB）
     */
    private static int lerpRgb(int from, int to, float t) {
        t = clamp01(t);
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int bl = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
