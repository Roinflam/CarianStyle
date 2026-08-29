package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 狄蒂卡之祸「提线人偶」客户端渲染器（纯客户端自绘，<b>零网络包</b>）。
 * <p>
 * 对应 {@code EnchantmentDaedicarWoe}：受到的伤害 ×3，受伤无敌时间 -50%。
 * 这是全模组最重的诅咒——伤害三倍还砍掉一半无敌帧，
 * 意味着连续挨打时几乎没有喘息，实战中往往是「一套连招直接送走」。
 * </p>
 *
 * <h3>为什么是人偶</h3>
 * <p>
 * 狄蒂卡在原作里就是<b>被封在人偶里的活祭品</b>，这个诅咒的护符外形正是一具小人偶。
 * 而本模组现有的十余种演出里<b>没有任何一个用过人形轮廓</b>——
 * 法阵是多边形 / 星形、光环是同心圆、丝线归灾祸、尖刺归噩兆、
 * 螺旋归睡眠、月轮归暗月、根须归黄金树。
 * 一具吊在头顶、缺胳膊少腿、随风摇晃的破人偶，在任何角度、任何距离下都不可能被认成别的东西。
 * </p>
 * <p>
 * <b>抽搐是必须的那一笔。</b>只是摇晃的话读作「挂了个饰品」；
 * 每隔几秒猛地抽一下，才读得出「这东西是活的，而且它在替你受罪」——
 * 正好对应「受伤无敌时间 -50%」那半条机制：你被打的时候恢复不过来。
 * </p>
 * <p>
 * <b>配色</b>用惨白骨色 + 暗紫提线，与灾祸的暗血红完全岔开——
 * 两个诅咒同时挂在身上时（这在整合包里并非不可能）必须一眼分得清。
 * </p>
 *
 * <h3>为什么不需要任何网络包</h3>
 * <p>
 * 生效条件只有一个：<b>身上装着带狄蒂卡之祸的护甲</b>。附魔存在于 {@code ItemStack} 的
 * NBT 里、随装备槽位正常同步给全部观察者，客户端直接读得到。
 * 与 {@link CalamityRenderer} 判定灾祸、{@link DarkMoonRenderer} 判定暗月是同一手法。
 * </p>
 * <p>
 * 因此本渲染器<b>不占用任何效果序列号、不新增任何包、服务端零开销</b>，
 * 也不需要改动 {@code EnchantmentDaedicarWoe} 一行代码。
 * </p>
 *
 * <h3>⚠ 装备槽口径</h3>
 * <p>
 * {@link #hasWoe} 目前只扫描<b>4 件护甲槽</b>——与 {@link CalamityRenderer} 同一判断依据。
 * 若该附魔实际不是护甲附魔，把 {@code getArmorSlots()} 换成遍历
 * {@code EquipmentSlot.values()} 即可，其余代码一行不用动；
 * 判定错了的后果是「装上了却不显示」，不会崩溃、也不影响任何机制。
 * </p>
 *
 * <h3>人偶画在 billboard 平面内</h3>
 * <p>
 * 人形轮廓必须<b>始终正对观察者</b>才读得出是人形——侧着看会退化成几根竖线。
 * 故整具人偶活在由相机右向量 {@code right} 与上向量 {@code up} 张成的平面里，
 * 用平面二维坐标 {@code (u, v)} 描述各个关节，再由
 * {@code P = center + right·u + up·v} 映射到世界。
 * 这样描述比逐点算三维方便得多，也不易出错。
 * </p>
 * <p>
 * <b>摇晃与抽搐都作用在这个平面内</b>：绕吊点做二维旋转即可，
 * 见 {@link #rotU} / {@link #rotV}。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 人偶骨架（躯干 + 双臂 + 双腿 + 2 道缝合线 = 7 条线 × 6）   84
 * 头部（12 段圆盘 × 3）                                       36
 * 吊线 + 双股牵连线（3 条 × 6）                               18
 * 脚下惨白圈（20 段 × 3）                                     60
 * ───────────────────────────────────────────────────────
 * 合计                                        ~198 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * <b>这是全模组最轻的世界演出</b>，比噩兆的 270 还低。
 * 因此接入 {@link VisualLod} 的<b>首要目的是
 * {@link VisualLod#countInstance()}</b>——拥挤度是全局共享的，
 * 只要还有渲染器不登记，{@code crowdFactor} 就会被系统性高估，
 * 已接入的重量级渲染器（黄金树祝福 2000、重力力场圈 2112）就削减不足。
 * 它自己能省的那点顶点反而是次要的。
 * </p>
 * <p>
 * <b>人偶骨架完全不削。</b>七条线加一个头共 120 顶点，却承担全部辨识度；
 * 少画任何一条（比如砍掉一条腿）都会让「人形」这个读法崩掉。
 * 只有头部圆盘的分段数与脚下圈的分段数参与缩放。
 * </p>
 * <p>
 * 三个配色全是编译期常量、演出中只有 alpha 与姿态在变、色相从不插值，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需复用缓冲。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class DaedicarWoeRenderer {

    /** 狄蒂卡之祸附魔的注册 id（按 {@code carianstyle:<id>} 解析） */
    private static final String WOE_ID = "daedicar_woe";

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== 配色（0xRRGGBB）=====
    /** 惨白骨色：人偶本体。刻意不用纯白——纯白读起来太「干净」，不像被诅咒的旧人偶 */
    private static final int WOE_PALE = 0xE8E0CE;
    /** 暗紫：提线与牵连线。与灾祸的暗血红完全岔开，两个诅咒同挂时一眼可辨 */
    private static final int WOE_THREAD = 0x8E74B4;
    /** 深紫：缝合线与脚下圈的暗部 */
    private static final int WOE_DEEP = 0x2E2242;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定：类加载时解包一次、此后永久复用的常量颜色数组。
    private static final float[] C_PALE = VisualColor.constant(WOE_PALE);
    private static final float[] C_THREAD = VisualColor.constant(WOE_THREAD);
    private static final float[] C_DEEP = VisualColor.constant(WOE_DEEP);

    // ===== 人偶 =====
    /** 人偶中心高度系数（× 实体高度）：头顶上方 */
    private static final float DOLL_HEIGHT_FACTOR = 1.5f;
    /** 人偶整体尺寸系数（× 实体宽度） */
    private static final float DOLL_SIZE_FACTOR = 0.52f;
    /** 骨架线半宽（格） */
    private static final float DOLL_LIMB_HALF = 0.022f;
    /** 头部圆盘分段数 */
    private static final int DOLL_HEAD_SEGMENTS = 12;
    /** 头部圆盘最少分段数：6 段仍是个圆头，再低会读成方块 */
    private static final int DOLL_HEAD_SEGMENTS_MIN = 6;
    private static final float DOLL_ALPHA = 0.88f;

    // ===== 摇晃与抽搐 =====
    /** 摇晃速度（弧度/秒的角频率）——很慢，像被风吹着 */
    private static final float SWING_SPEED = 0.9f;
    /** 摇晃幅度（弧度） */
    private static final float SWING_AMOUNT = 0.17f;
    /** 抽搐周期（秒） */
    private static final float TWITCH_PERIOD = 3.1f;
    /** 抽搐动作占周期的比例——极短，就是「猛地抽一下」 */
    private static final float TWITCH_WINDOW = 0.09f;
    /** 抽搐时的额外抖动频率 */
    private static final float TWITCH_SHAKE_SPEED = 38f;
    /** 抽搐抖动幅度（弧度） */
    private static final float TWITCH_AMOUNT = 0.22f;

    // ===== 提线 =====
    /** 吊线自人偶头顶再向上延伸的长度（× 人偶尺寸），末端淡出没入空中 */
    private static final float HANG_LINE_LENGTH = 1.1f;
    /** 提线半宽（格） */
    private static final float THREAD_HALF = 0.012f;
    /** 提线层的保留阈值：极细的线，远处几乎不可见 */
    private static final float THREAD_KEEP_THRESHOLD = 0.45f;

    // ===== 脚下惨白圈 =====
    private static final int RING_SEGMENTS = 20;
    private static final int RING_SEGMENTS_MIN = 8;
    private static final float RING_RADIUS_FACTOR = 1.0f;
    private static final float RING_HALF_WIDTH = 0.035f;
    private static final float RING_ALPHA = 0.38f;
    private static final float RING_BREATH_SPEED = 0.7f;

    /** 狄蒂卡之祸附魔懒解析缓存（注册表在 mod 加载后才可用，首次解析成功后固定） */
    private static Enchantment woeCache;
    /** 是否已成功解析 */
    private static boolean woeResolved;

    private DaedicarWoeRenderer() {
    }

    /**
     * 世界渲染回调：绘制相机附近所有「护甲带狄蒂卡之祸」实体的提线人偶。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        // 共享批次未开启（世界未加载等）：直接跳过
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Enchantment woe = resolveWoe();
        if (woe == null) {
            // 附魔未注册（如被 uninstallEnchantment 配置禁用）：不绘制
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

        Matrix4f matrix = VisualBatch.matrix();
        float rgX = VisualBatch.rightX();
        float rgY = VisualBatch.rightY();
        float rgZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            // 共享列表已保证 isAlive，此处只做附魔判定
            if (!hasWoe(entity, woe)) {
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

            // 本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度——本渲染器接入 LOD 的首要意义就在这一行
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawCursedRing(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width,
                    time, entity.getId(), detail);
            drawDoll(builder, matrix, rx, ryFoot, rz, width, height,
                    time, entity.getId(), rgX, rgY, rgZ, upX, upY, upZ, detail);
        }
    }

    // ==================== 附魔判定 ====================

    /**
     * 判断实体的<b>护甲</b>上是否带有狄蒂卡之祸附魔。
     * <p>
     * 用 {@link ItemStack#isEnchanted()} 做廉价前置过滤——该方法只检查 NBT 标签是否存在、
     * 不做任何反序列化，能砍掉裸装与未附魔护甲的解析开销。
     * </p>
     * <p><b>⚠ 若该附魔实际不是护甲附魔</b>，改用 {@code EquipmentSlot.values()} 遍历即可
     * （详见类注释）。</p>
     *
     * @param entity 待判定实体
     * @param woe    狄蒂卡之祸附魔（非 null，调用方已判空）
     * @return 任一护甲带该附魔返回 true
     */
    private static boolean hasWoe(LivingEntity entity, Enchantment woe) {
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor.isEmpty() || !armor.isEnchanted()) {
                continue;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(woe, armor) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 懒解析狄蒂卡之祸附魔对象（注册表在 mod 加载后才可用，故首次调用时解析并缓存）。
     * <p>仅在成功解析后才标记完成，否则下次重试——避免在注册表尚未就绪时把 null 固化下来。</p>
     *
     * @return 附魔对象；未注册（如被配置禁用）时返回 null
     */
    @Nullable
    private static Enchantment resolveWoe() {
        if (!woeResolved) {
            woeCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, WOE_ID));
            woeResolved = (woeCache != null);
        }
        return woeCache;
    }

    // ==================== 提线人偶（核心标志）====================

    /**
     * 头顶悬吊的破损人偶：头 + 躯干 + 双臂 + 双腿 + 两道缝合线，
     * 绕吊点缓慢摇晃，并周期性猛地抽搐一下；上方连一根吊线没入空中。
     * <p>
     * <b>「破损」体现在两处</b>：右臂比左臂垂得更低（脱臼），
     * 以及躯干上两道交叉的缝合线。这两笔让它从「一个火柴人」变成
     * 「一个被缝补过、还在被吊着的东西」。
     * </p>
     * <p>
     * <b>坐标系：</b>整具人偶活在 billboard 平面内（详见类注释），
     * 局部坐标以人偶中心为原点、{@code size} 为单位；
     * 摇晃与抽搐是绕吊点（{@code (0, PIVOT_V)}）的二维旋转。
     * </p>
     * <p>
     * <b>削减：</b>只有头部圆盘的分段数与提线层参与缩放，
     * <b>骨架七条线无论细节多低都完整绘制</b>——少画一条腿，「人形」这个读法就崩了。
     * </p>
     */
    private static void drawDoll(BufferBuilder b, Matrix4f m,
                                 float cx, float cyFoot, float cz,
                                 float width, float height,
                                 float time, int seedId,
                                 float rgX, float rgY, float rgZ,
                                 float upX, float upY, float upZ, float detail) {
        float size = width * DOLL_SIZE_FACTOR;
        if (size <= 0.02f) {
            return;
        }
        // 人偶中心（billboard 平面的原点）
        float ox = cx;
        float oy = cyFoot + height * DOLL_HEIGHT_FACTOR;
        float oz = cz;

        // 摇晃：绕吊点的缓慢往复
        float swing = Mth.sin(time * SWING_SPEED + seedId * 0.7f) * SWING_AMOUNT;

        // 抽搐：周期内极短的一段，高频抖动叠加到摇晃角上。
        // 「猛地抽一下」才读得出人偶是活的——纯摇晃只会读成挂了个饰品
        float twitchCycle = frac(time / TWITCH_PERIOD + seedId * 0.21f);
        float twitch = 0f;
        if (twitchCycle < TWITCH_WINDOW) {
            float k = 1f - twitchCycle / TWITCH_WINDOW;
            twitch = Mth.sin(time * TWITCH_SHAKE_SPEED) * TWITCH_AMOUNT * k * k;
        }
        float angle = swing + twitch;
        float cosA = Mth.cos(angle);
        float sinA = Mth.sin(angle);

        // 吊点：人偶头顶正上方（局部坐标）
        final float pivotV = 1.05f;
        float alpha = DOLL_ALPHA;

        // ===== 骨架关节（局部坐标，单位为 size）=====
        // 头心
        final float headU = 0f, headV = 0.72f;
        // 颈 / 胯
        final float neckU = 0f, neckV = 0.46f;
        final float hipU = 0f, hipV = -0.16f;
        // 双肩（躯干上段）
        final float shoulderU = 0f, shoulderV = 0.30f;
        // 左臂末端（自然下垂）
        final float armLU = -0.42f, armLV = 0.02f;
        // 右臂末端：垂得更低、更外张 —— 「脱臼」的那一笔
        final float armRU = 0.48f, armRV = -0.16f;
        // 双腿末端
        final float legLU = -0.26f, legLV = -0.78f;
        final float legRU = 0.24f, legRV = -0.80f;

        // ===== 骨架（不参与削减）=====
        // 躯干
        limb(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ, size, cosA, sinA, pivotV,
                neckU, neckV, hipU, hipV, C_PALE, alpha);
        // 双臂
        limb(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ, size, cosA, sinA, pivotV,
                shoulderU, shoulderV, armLU, armLV, C_PALE, alpha);
        limb(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ, size, cosA, sinA, pivotV,
                shoulderU, shoulderV, armRU, armRV, C_PALE, alpha * 0.85f);
        // 双腿
        limb(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ, size, cosA, sinA, pivotV,
                hipU, hipV, legLU, legLV, C_PALE, alpha);
        limb(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ, size, cosA, sinA, pivotV,
                hipU, hipV, legRU, legRV, C_PALE, alpha);

        // 两道交叉缝合线：把「火柴人」变成「被缝补过的人偶」
        limb(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ, size, cosA, sinA, pivotV,
                -0.14f, 0.28f, 0.14f, 0.14f, C_DEEP, alpha * 0.9f);
        limb(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ, size, cosA, sinA, pivotV,
                -0.14f, 0.14f, 0.14f, 0.28f, C_DEEP, alpha * 0.9f);

        // ===== 头部（圆盘，分段数按细节缩放）=====
        int headSegments = VisualLod.scaleSegments(
                DOLL_HEAD_SEGMENTS, DOLL_HEAD_SEGMENTS_MIN, detail);
        float hu = rotU(headU, headV, cosA, sinA, pivotV);
        float hv = rotV(headU, headV, cosA, sinA, pivotV);
        planeDisc(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                hu * size, hv * size, size * 0.22f, headSegments,
                C_PALE, alpha, alpha * 0.35f);

        // ===== 提线：自吊点向上没入空中，另有两股细线牵向宿主头顶 =====
        if (VisualLod.keepLayer(detail, THREAD_KEEP_THRESHOLD)) {
            // 吊线（吊点本身不随摇晃移动，故直接用局部坐标）
            planeLine(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                    0f, pivotV * size, 0f, (pivotV + HANG_LINE_LENGTH) * size,
                    THREAD_HALF, C_THREAD, alpha * 0.8f, 0f);

            // 牵连线：从人偶双脚各拉一根向下连到宿主头顶，
            // 表达「这具人偶替你受着，而你和它连在一起」
            float headTopY = cyFoot + height * 1.02f;
            float lu = rotU(legLU, legLV, cosA, sinA, pivotV) * size;
            float lv = rotV(legLU, legLV, cosA, sinA, pivotV) * size;
            float ru = rotU(legRU, legRV, cosA, sinA, pivotV) * size;
            float rv = rotV(legRU, legRV, cosA, sinA, pivotV) * size;
            float lx = ox + rgX * lu + upX * lv;
            float ly = oy + rgY * lu + upY * lv;
            float lz = oz + rgZ * lu + upZ * lv;
            float rx2 = ox + rgX * ru + upX * rv;
            float ry2 = oy + rgY * ru + upY * rv;
            float rz2 = oz + rgZ * ru + upZ * rv;
            worldLine(b, m, lx, ly, lz, cx - width * 0.18f, headTopY, cz,
                    THREAD_HALF, C_THREAD, alpha * 0.55f, 0f);
            worldLine(b, m, rx2, ry2, rz2, cx + width * 0.18f, headTopY, cz,
                    THREAD_HALF, C_THREAD, alpha * 0.55f, 0f);
        }
    }

    /**
     * 绘制人偶的一段肢体：把两个<b>局部关节坐标</b>绕吊点旋转后转成世界坐标画线。
     * <p>把旋转折进来是为了让 {@link #drawDoll} 里的关节表保持纯粹的姿态数据，
     * 不被一堆 {@code rotU/rotV} 调用淹没。</p>
     *
     * @param size 人偶尺寸（局部坐标的单位）
     * @param cosA 摇晃角余弦
     * @param sinA 摇晃角正弦
     * @param pivotV 吊点的局部纵坐标
     */
    private static void limb(BufferBuilder b, Matrix4f m,
                             float ox, float oy, float oz,
                             float rgX, float rgY, float rgZ,
                             float upX, float upY, float upZ,
                             float size, float cosA, float sinA, float pivotV,
                             float u1, float v1, float u2, float v2,
                             float[] col, float alpha) {
        float a1u = rotU(u1, v1, cosA, sinA, pivotV) * size;
        float a1v = rotV(u1, v1, cosA, sinA, pivotV) * size;
        float a2u = rotU(u2, v2, cosA, sinA, pivotV) * size;
        float a2v = rotV(u2, v2, cosA, sinA, pivotV) * size;
        planeLine(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                a1u, a1v, a2u, a2v, DOLL_LIMB_HALF, col, alpha, alpha);
    }

    /**
     * 局部坐标绕吊点旋转后的横坐标。
     * <p>吊点固定在 {@code (0, pivotV)}，因此只需对 {@code v} 做平移。</p>
     */
    private static float rotU(float u, float v, float cosA, float sinA, float pivotV) {
        float dv = v - pivotV;
        return u * cosA - dv * sinA;
    }

    /**
     * 局部坐标绕吊点旋转后的纵坐标。
     */
    private static float rotV(float u, float v, float cosA, float sinA, float pivotV) {
        float dv = v - pivotV;
        return pivotV + u * sinA + dv * cosA;
    }

    // ==================== 脚下惨白圈 ====================

    /**
     * 脚下的惨白细圈：缓慢呼吸，作为整体的重量锚点。
     * <p>只有一具悬在头顶的人偶会显得上重下空；加一圈贴地的白，
     * 才把「这个诅咒是绑在这个人身上的」说清楚。</p>
     */
    private static void drawCursedRing(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz, float width,
                                       float time, int seedId, float detail) {
        float breath = 0.9f + 0.1f * Mth.sin(time * RING_BREATH_SPEED + seedId * 0.4f);
        float radius = width * RING_RADIUS_FACTOR * breath;
        int segments = VisualLod.scaleSegments(RING_SEGMENTS, RING_SEGMENTS_MIN, detail);
        ring(b, m, cx, cy, cz, radius, segments, RING_HALF_WIDTH,
                C_PALE, RING_ALPHA * breath);
    }

    // ==================== billboard 平面几何基元 ====================
    // 人偶的全部图案都活在「面向相机的平面」里，用平面二维坐标 (u, v) 描述比逐点算三维方便得多。
    // 映射关系：P = center + right·u + up·v

    /**
     * 在 billboard 平面内绘制一条带宽度的线段（用平面二维坐标表达端点）。
     *
     * @param u1 起点的平面横坐标（相对 billboard 中心，单位：格）
     * @param v1 起点的平面纵坐标
     * @param u2 终点的平面横坐标
     * @param v2 终点的平面纵坐标
     * @param hw 线半宽（格）
     */
    private static void planeLine(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float u1, float v1, float u2, float v2,
                                  float hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        float du = u2 - u1;
        float dv = v2 - v1;
        float len = Mth.sqrt(du * du + dv * dv);
        if (len < 1.0e-6f) {
            return;
        }
        // 平面内的法线 × 半宽
        float nu = -dv / len * hw;
        float nv = du / len * hw;

        float r = col[0], g = col[1], bl = col[2];

        float au1 = u1 + nu, av1 = v1 + nv;
        float au2 = u1 - nu, av2 = v1 - nv;
        float bu1 = u2 + nu, bv1 = v2 + nv;
        float bu2 = u2 - nu, bv2 = v2 - nv;

        float ax1 = cx + rgX * au1 + upX * av1;
        float ay1 = cy + rgY * au1 + upY * av1;
        float az1 = cz + rgZ * au1 + upZ * av1;
        float ax2 = cx + rgX * au2 + upX * av2;
        float ay2 = cy + rgY * au2 + upY * av2;
        float az2 = cz + rgZ * au2 + upZ * av2;
        float bx1 = cx + rgX * bu1 + upX * bv1;
        float by1 = cy + rgY * bu1 + upY * bv1;
        float bz1 = cz + rgZ * bu1 + upZ * bv1;
        float bx2 = cx + rgX * bu2 + upX * bv2;
        float by2 = cy + rgY * bu2 + upY * bv2;
        float bz2 = cz + rgZ * bu2 + upZ * bv2;

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, by1, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, ay2, az2).color(r, g, bl, a1).endVertex();
    }

    /**
     * 在 billboard 平面内绘制一个径向渐变圆盘（中心 alpha、边缘 alpha 可不同）。
     *
     * @param cu       圆心的平面横坐标（相对 billboard 中心）
     * @param cv       圆心的平面纵坐标
     * @param radius   半径（格）
     * @param segments 分段数
     */
    private static void planeDisc(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float cu, float cv, float radius, int segments,
                                  float[] col, float centerAlpha, float edgeAlpha) {
        if (radius <= 1.0e-4f || (centerAlpha <= 0.004f && edgeAlpha <= 0.004f)) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float ox = cx + rgX * cu + upX * cv;
        float oy = cy + rgY * cu + upY * cv;
        float oz = cz + rgZ * cu + upZ * cv;

        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= segments; i++) {
            float ang = TAU * i / segments;
            float eu = Mth.cos(ang) * radius;
            float ev = Mth.sin(ang) * radius;
            float ex = ox + rgX * eu + upX * ev;
            float ey = oy + rgY * eu + upY * ev;
            float ez = oz + rgZ * eu + upZ * ev;
            if (i > 0) {
                b.vertex(m, ox, oy, oz).color(r, g, bl, centerAlpha).endVertex();
                b.vertex(m, pex, pey, pez).color(r, g, bl, edgeAlpha).endVertex();
                b.vertex(m, ex, ey, ez).color(r, g, bl, edgeAlpha).endVertex();
            }
            pex = ex;
            pey = ey;
            pez = ez;
        }
    }

    // ==================== 世界空间几何基元 ====================

    /**
     * 世界空间的「十字双面」线段：沿世界 X、Z 轴各画一个四边形，
     * 使线段从任意水平视角皆可见。
     * <p>牵连线连接人偶与宿主头顶，两端都在世界空间里、不在同一 billboard 平面上，
     * 因此必须用世界空间版本而非 {@link #planeLine}。</p>
     *
     * @param hw 线半宽（格）
     */
    private static void worldLine(BufferBuilder b, Matrix4f m,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        worldQuad(b, m, x1 - hw, y1, z1, x1 + hw, y1, z1,
                x2 + hw, y2, z2, x2 - hw, y2, z2, r, g, bl, a1, a2);
        worldQuad(b, m, x1, y1, z1 - hw, x1, y1, z1 + hw,
                x2, y2, z2 + hw, x2, y2, z2 - hw, r, g, bl, a1, a2);
    }

    /**
     * 画一个世界空间四边形（拆成两三角形）：a→b 用 alpha {@code aAB}，c→d 用 alpha {@code aCD}。
     */
    private static void worldQuad(BufferBuilder b, Matrix4f m,
                                  float ax, float ay, float az, float bx, float by, float bz,
                                  float cx, float cy, float cz, float dx, float dy, float dz,
                                  float r, float g, float bl, float aAB, float aCD) {
        b.vertex(m, ax, ay, az).color(r, g, bl, aAB).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bl, aAB).endVertex();
        b.vertex(m, cx, cy, cz).color(r, g, bl, aCD).endVertex();

        b.vertex(m, ax, ay, az).color(r, g, bl, aAB).endVertex();
        b.vertex(m, cx, cy, cz).color(r, g, bl, aCD).endVertex();
        b.vertex(m, dx, dy, dz).color(r, g, bl, aCD).endVertex();
    }

    /**
     * 水平圆环（内外两侧为同一 alpha 的窄带）。
     *
     * @param halfWidth 环带半宽（格）
     */
    private static void ring(BufferBuilder b, Matrix4f m,
                             float cx, float cy, float cz, float radius, int segments,
                             float halfWidth, float[] col, float alpha) {
        if (alpha <= 0.004f || radius <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
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

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ox1, cy, oz1).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix0, cy, iz0).color(r, g, bl, alpha).endVertex();
        }
    }

    // ==================== 数学辅助 ====================

    /** 取小数部分（结果恒在 [0,1)）。 */
    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }
}
