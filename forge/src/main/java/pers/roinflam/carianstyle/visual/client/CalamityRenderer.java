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
 * 夏玻利利的灾祸「癫火之眼」客户端渲染器（纯客户端自绘，<b>零网络包</b>）。
 * <p>
 * 对应 {@code EnchantmentCalamity}：受到的伤害 ×1.5，且<b>怪物强烈倾向于攻击你</b>。
 * 这是一个诅咒——玩家不是主动装它的，而是被它缠上的。
 * </p>
 *
 * <h3>v2.0：把眼睛和癫火加回来</h3>
 * <p>
 * v1.0 只有牵引丝线，刻意避开了眼睛，理由是「嘶吼已经用掉眼睛这个符号」。
 * <b>这个取舍是错的。</b>夏玻利利的核心就是癫火与那只充血之眼——
 * 为了避让另一个附魔而把它的招牌拿掉，结果是八条红线谁也认不出是什么，
 * 既不像灾祸，也不像夏玻利利，只是「有个人身上挂了点红东西」。
 * </p>
 * <p>
 * v2.0 的做法是<b>把眼睛做成癫火本身</b>，靠癫火与嘶吼拉开距离：
 * </p>
 * <table border="1">
 *   <caption>与嘶吼的区分</caption>
 *   <tr><th></th><th>嘶吼（既有）</th><th>灾祸（本类）</th></tr>
 *   <tr><td>位置</td><td>敌人头顶</td><td><b>宿主自己的胸口</b></td></tr>
 *   <tr><td>眼型</td><td>充血之眼</td><td><b>竖瞳</b>，眼眶是焦黑的</td></tr>
 *   <tr><td>周围</td><td>无火</td><td><b>整个人在烧癫火</b>——眼上冒火、身周舔火舌</td></tr>
 *   <tr><td>其它</td><td>—</td><td>八条燃烧的癫火丝向外牵</td></tr>
 * </table>
 * <p>
 * <b>「整个人在烧」是最关键的一条。</b>嘶吼是敌人头上飘一只眼，
 * 灾祸是这个人从胸口到脚下全在冒黄火。哪怕两者同屏，
 * 也不会有人把「一只飘着的眼」和「一个烧起来的人」搞混。
 * </p>
 * <p>
 * <b>牵引丝线保留了</b>，但改成燃烧的癫火丝——它承担的是嘲讽机制
 * （怪物强烈倾向于攻击你）那半条语义，周期性「绷紧」表达被拽的力道。
 * 只是现在它不再是主角，而是从那只眼里烧出去的东西。
 * </p>
 *
 * <h3>配色</h3>
 * <p>
 * 主色是癫火黄 {@value #FRENZY_YELLOW}，配焦黑与一点充血红。
 * <b>癫火黄必须是主色</b>——之前用暗血红做主色是把「灾祸」理解成了流血，
 * 但它其实是癫火系的诅咒，黄色才对。血红只留在眼眶的血丝上，
 * 那是「充血之眼」的那点意思，不喧宾夺主。
 * </p>
 *
 * <h3>为什么不需要任何网络包</h3>
 * <p>
 * 生效条件只有一个：<b>身上装着带灾祸的护甲</b>。而附魔存在于 {@code ItemStack} 的 NBT 里、
 * 随装备槽位正常同步给全部观察者，客户端直接读得到。
 * 与 {@link DarkMoonRenderer} 判定暗月、{@link CarianRetaliationRenderer} 判定奉还是同一手法。
 * </p>
 * <p>
 * 因此本渲染器<b>不占用任何效果序列号、不新增任何包、服务端零开销</b>，
 * 也不需要改动 {@code EnchantmentCalamity} 一行代码。
 * </p>
 *
 * <h3>⚠ 装备槽口径</h3>
 * <p>
 * {@link #hasCalamity} 目前只扫描<b>4 件护甲槽</b>——灾祸是诅咒类附魔，
 * 按本模组既有惯例（以及原版诅咒的做法）应当是护甲附魔。
 * </p>
 * <p>
 * <b>如果实际不是</b>，只需把该方法里的 {@code getArmorSlots()} 换成遍历
 * {@code EquipmentSlot.values()} 即可，其余代码一行不用动。
 * 这个判定错了的后果是「装上了却不显示」，不会崩溃、也不影响任何机制。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 癫火之眼：眼眶填充 72 + 双弧轮廓 144 + 竖瞳 24 + 血丝 24    264
 * 眼上火苗（3 簇 × 6 段 × 12）                               216
 * 癫火丝（8 条 × 3 段 × 十字双面 × 6）                       288
 * 丝端火簇（8 个 × 4 段 × 12）                               384
 * 身周火舌（6 条 × 6 段 × 12）                               432
 * 脚下焦痕 + 余烬（60 + 72）                                 132
 * ────────────────────────────────────────────────────────
 * 合计                                    ~1716 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 比 v1.0 的 444 重了近四倍——火焰是顶点大户，这是让它「够味道」的代价。
 * 因此本版对 LOD 的依赖比 v1.0 强得多，各层的保留阈值都调过：
 * </p>
 * <ul>
 *     <li><b>眼睛完全不削</b>（只缩弧线分段）——它是全部辨识度所在；</li>
 *     <li>身周火舌与丝端火簇是最贵的两层，也是最先被砍的；</li>
 *     <li>火焰的段数随细节缩放，远处的火苗退化成一小撮也仍是火。</li>
 * </ul>
 * <p>
 * 丝线与火舌都按<b>步长抽取</b>而非截断——它们的角度是均布的，
 * 截断前 N 条会让火只朝一侧烧。
 * </p>
 * <p>
 * 四个配色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需复用缓冲。
 * </p>
 *
 * @author FlameForge
 * @version 2.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CalamityRenderer {

    /** 夏玻利利的灾祸附魔的注册 id（按 {@code carianstyle:<id>} 解析） */
    private static final String CALAMITY_ID = "calamity";

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
    /** 癫火白心：火焰内芯与瞳孔中心的白热点 */
    private static final int FRENZY_CORE = 0xFFF6C8;
    /**
     * 癫火黄：<b>主色</b>。
     * <p>v1.0 用暗血红做主色是把「灾祸」理解成了流血，
     * 但它是癫火系的诅咒——黄色才对（详见类注释「配色」小节）。</p>
     */
    private static final int FRENZY_YELLOW = 0xFFD21E;
    /** 焦黑：眼眶内部与脚下焦痕。癫火烧过的东西是黑的 */
    private static final int FRENZY_CHAR = 0x2A1608;
    /** 充血红：只用在眼眶血丝上，是「充血之眼」的那点意思，不喧宾夺主 */
    private static final int CALAMITY_BLOOD = 0x9E1A18;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定：类加载时解包一次、此后永久复用的常量颜色数组。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    private static final float[] C_CORE = VisualColor.constant(FRENZY_CORE);
    private static final float[] C_YELLOW = VisualColor.constant(FRENZY_YELLOW);
    private static final float[] C_CHAR = VisualColor.constant(FRENZY_CHAR);
    private static final float[] C_BLOOD = VisualColor.constant(CALAMITY_BLOOD);

    // ===== 癫火之眼（核心标志）=====
    /** 眼睛中心高度系数（× 实体高度）：胸口 */
    private static final float EYE_HEIGHT_FACTOR = 0.68f;
    /** 眼睛整体尺寸系数（× 实体宽度） */
    private static final float EYE_SIZE_FACTOR = 0.62f;
    /** 眼眶横向半宽（× 眼睛尺寸） */
    private static final float EYE_HALF_WIDTH = 0.5f;
    /** 眼眶纵向半高（× 眼睛尺寸）。明显小于半宽，才是「眼」而不是「圆」 */
    private static final float EYE_HALF_HEIGHT = 0.26f;
    /** 眼眶弧线分段数 */
    private static final int EYE_ARC_SEGMENTS = 12;
    /** 眼眶弧线最少分段数：6 段仍能看出是弧，再低会读成折线 */
    private static final int EYE_ARC_SEGMENTS_MIN = 6;
    /** 眼眶轮廓线半宽（格） */
    private static final float EYE_RIM_HALF = 0.018f;
    /** 眼眶轮廓的燃烧抖动幅度（× 眼睛尺寸） */
    private static final float EYE_RIM_FLICKER = 0.035f;
    /** 眼眶轮廓的燃烧抖动速度 */
    private static final float EYE_RIM_FLICKER_SPEED = 9.5f;
    private static final float EYE_ALPHA = 0.95f;

    // ===== 竖瞳 =====
    /** 瞳孔半宽（× 眼睛尺寸），会随收缩脉动 */
    private static final float PUPIL_HALF_WIDTH = 0.045f;
    /** 瞳孔纵向占眼眶半高的比例 */
    private static final float PUPIL_HEIGHT_RATIO = 0.86f;
    /**
     * 瞳孔收缩的两个频率。
     * <p><b>用两个不成整数比的频率叠加</b>，得到的脉动没有可预期的周期——
     * 这正是「不安」的来源。单一正弦会读成规律的呼吸，太安详了。</p>
     */
    private static final float PUPIL_PULSE_A = 1.7f;
    private static final float PUPIL_PULSE_B = 4.3f;

    // ===== 眼眶血丝 =====
    /** 血丝条数 */
    private static final int VEIN_COUNT = 4;
    /** 血丝线半宽（格） */
    private static final float VEIN_HALF = 0.009f;
    private static final float VEIN_ALPHA = 0.7f;
    /** 血丝层的保留阈值：极细，远处看不出 */
    private static final float VEIN_KEEP_THRESHOLD = 0.55f;

    // ===== 眼上火苗 =====
    /** 眼上火苗簇数 */
    private static final int EYE_FLAME_COUNT = 3;
    /** 眼上火苗高度（× 眼睛尺寸） */
    private static final float EYE_FLAME_HEIGHT = 0.75f;
    /** 眼上火苗宽度（× 眼睛尺寸） */
    private static final float EYE_FLAME_WIDTH = 0.2f;
    /** 眼上火苗层的保留阈值 */
    private static final float EYE_FLAME_KEEP_THRESHOLD = 0.35f;

    // ===== 癫火丝（嘲讽机制的表达）=====
    /** 丝线数量 */
    private static final int THREAD_COUNT = 8;
    /** 每条丝线的折线段数 */
    private static final int THREAD_SEGMENTS = 3;
    /** 丝线折线段数下限：2 段仍是一条有折角的线，1 段会退化成直棍 */
    private static final int THREAD_SEGMENTS_MIN = 2;
    /** 丝线起点距身体中轴的距离系数（× 实体宽度） */
    private static final float THREAD_ORIGIN_FACTOR = 0.3f;
    /** 丝线长度系数（× 实体宽度） */
    private static final float THREAD_LENGTH_FACTOR = 1.85f;
    /** 丝线末端相对长度的下垂比例（重量感，不能是纯水平的） */
    private static final float THREAD_SAG = 0.32f;
    /** 丝线根部半宽（格），向末端收细 */
    private static final float THREAD_HALF_WIDTH = 0.024f;
    /** 丝线整体极缓慢绕行速度（弧度/秒） */
    private static final float THREAD_ORBIT_SPEED = 0.14f;
    /** 丝线横向摆动速度 */
    private static final float THREAD_WAVE_SPEED = 1.35f;
    /** 丝线横向摆动幅度（× 实体宽度） */
    private static final float THREAD_WAVE_AMOUNT = 0.16f;
    /** 丝线沿线的燃烧抖动速度（让丝看起来在烧，而不是一根静止的线） */
    private static final float THREAD_BURN_SPEED = 11f;
    private static final float THREAD_BASE_ALPHA = 0.82f;
    /** 丝端火簇的尺寸系数（× 实体宽度） */
    private static final float THREAD_TIP_FLAME = 0.34f;
    /** 丝端火簇层的保留阈值：最贵的一层之一 */
    private static final float THREAD_TIP_KEEP_THRESHOLD = 0.5f;

    // ===== 绷紧脉冲（表达「被拽了一下」）=====
    /** 绷紧周期（秒） */
    private static final float TUG_PERIOD = 2.4f;
    /** 绷紧动作占周期的比例，其余时间松弛 */
    private static final float TUG_WINDOW = 0.22f;
    /** 绷紧时丝线缩短的最大比例 */
    private static final float TUG_SHORTEN = 0.28f;

    // ===== 身周火舌 =====
    /** 火舌条数（绕身体一圈均布） */
    private static final int BODY_FLAME_COUNT = 6;
    /** 火舌所在半径系数（× 实体宽度） */
    private static final float BODY_FLAME_RADIUS = 0.5f;
    /** 火舌高度系数（× 实体高度） */
    private static final float BODY_FLAME_HEIGHT = 0.62f;
    /** 火舌宽度系数（× 实体宽度） */
    private static final float BODY_FLAME_WIDTH = 0.34f;
    /** 火舌层的保留阈值：最贵的一层 */
    private static final float BODY_FLAME_KEEP_THRESHOLD = 0.42f;

    // ===== 火焰通用 =====
    /** 火焰细分段数 */
    private static final int FLAME_SEGMENTS = 6;
    /** 火焰最少分段数：3 段仍是一撮尖头的火，再低就成了三角形 */
    private static final int FLAME_SEGMENTS_MIN = 3;
    /** 火焰左右摇曳速度 */
    private static final float FLAME_SWAY_SPEED = 5.2f;
    /** 火焰左右摇曳幅度（× 火焰宽度） */
    private static final float FLAME_SWAY_AMOUNT = 0.55f;

    // ===== 脚下焦痕 =====
    private static final int CHAR_SEGMENTS = 20;
    private static final int CHAR_SEGMENTS_MIN = 8;
    private static final float CHAR_RADIUS_FACTOR = 1.15f;
    private static final float CHAR_ALPHA = 0.42f;
    /** 焦痕边缘的余烬亮点数量 */
    private static final int EMBER_COUNT = 6;
    /** 余烬亮点半尺寸（格） */
    private static final float EMBER_SIZE = 0.04f;
    /** 余烬层的保留阈值 */
    private static final float EMBER_KEEP_THRESHOLD = 0.5f;

    /** 灾祸附魔懒解析缓存（注册表在 mod 加载后才可用，首次解析成功后固定） */
    private static Enchantment calamityCache;
    /** 是否已成功解析 */
    private static boolean calamityResolved;

    private CalamityRenderer() {
    }

    /**
     * 世界渲染回调：绘制相机附近所有「护甲带灾祸」实体的癫火之眼。
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
        Enchantment calamity = resolveCalamity();
        if (calamity == null) {
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
        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        for (LivingEntity entity : candidates) {
            // 共享列表已保证 isAlive，此处只做附魔判定
            if (!hasCalamity(entity, calamity)) {
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

            // 本实体的细节系数（距离 × 同屏拥挤度）。12 格内恒为 1.0，视觉与不做 LOD 时一致
            float detail = VisualLod.detail(distSqr);
            // 登记实例，供下一帧估算拥挤度——少登记一个渲染器就会让全局 crowdFactor 被高估
            VisualLod.countInstance();

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;
            int seedId = entity.getId();

            drawCharPool(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width,
                    time, seedId, detail);
            drawBodyFlames(builder, matrix, rx, ryFoot, rz, width, height,
                    time, seedId, detail);
            drawThreads(builder, matrix, rx, ryFoot, rz, width, height,
                    time, seedId, detail);
            drawFrenzyEye(builder, matrix, rx, ryFoot, rz, width, height,
                    time, seedId, detail);
        }
    }

    // ==================== 附魔判定 ====================

    /**
     * 判断实体的<b>护甲</b>上是否带有灾祸附魔。
     * <p>
     * 用 {@link ItemStack#isEnchanted()} 做廉价前置过滤——该方法只检查 NBT 标签是否存在、
     * 不做任何反序列化，而绝大多数实体穿的是裸装或未附魔护甲，这一行能砍掉
     * 循环内绝大部分的 {@code getItemEnchantmentLevel} 调用。
     * </p>
     * <p>
     * <b>⚠ 若灾祸实际不是护甲附魔</b>，把 {@code getArmorSlots()} 换成遍历
     * {@code EquipmentSlot.values()} 即可，其余代码无需改动（详见类注释）。
     * </p>
     *
     * @param entity   待判定实体
     * @param calamity 灾祸附魔（非 null，调用方已判空）
     * @return 任一护甲带灾祸返回 true
     */
    private static boolean hasCalamity(LivingEntity entity, Enchantment calamity) {
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor.isEmpty() || !armor.isEnchanted()) {
                continue;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(calamity, armor) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 懒解析灾祸附魔对象（注册表在 mod 加载后才可用，故首次调用时解析并缓存）。
     * <p>仅在成功解析后才标记完成，否则下次重试——避免在注册表尚未就绪时把 null 固化下来。</p>
     *
     * @return 灾祸附魔；未注册（如被配置禁用）时返回 null
     */
    @Nullable
    private static Enchantment resolveCalamity() {
        if (!calamityResolved) {
            calamityCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, CALAMITY_ID));
            calamityResolved = (calamityCache != null);
        }
        return calamityCache;
    }

    // ==================== 癫火之眼（核心标志）====================

    /**
     * 胸口的癫火之眼：焦黑眼眶 + 燃烧的黄色轮廓 + 竖瞳 + 血丝 + 眼上冒出的火苗。
     * <p>
     * <b>眼眶是「透镜形」</b>（两条对称的抛物弧夹出来的形状），
     * 半宽明显大于半高——这是眼睛与圆的分野，做成正圆会读成一个发光球。
     * </p>
     * <p>
     * <b>竖瞳是这只眼「不是人的」的关键。</b>圆瞳读作人或动物，
     * 竖瞳读作蛇、恶魔、或者被什么东西附身的东西，正合灾祸的语气。
     * 它的收缩用两个不成整数比的频率叠加（{@link #PUPIL_PULSE_A} 与
     * {@link #PUPIL_PULSE_B}），没有可预期的周期，看久了会不舒服——这是刻意的。
     * </p>
     * <p>
     * <b>轮廓在烧。</b>上下两条弧的每个采样点都叠了一个高频抖动，
     * 于是眼眶边缘不断地跳、像被火舔着。静止的轮廓会读成一个贴纸。
     * </p>
     * <p>
     * 整只眼活在 billboard 平面内（始终正对相机）——
     * 眼睛这种图形侧着看会彻底垮掉，必须正面朝人。
     * </p>
     * <p>
     * <b>削减：</b>眼眶填充、轮廓、竖瞳<b>完全不削</b>（只缩弧线分段数），
     * 它们是全部辨识度所在；血丝与眼上火苗可整层跳过。
     * </p>
     */
    private static void drawFrenzyEye(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz,
                                      float width, float height,
                                      float time, int seedId, float detail) {
        float size = width * EYE_SIZE_FACTOR;
        if (size <= 0.02f) {
            return;
        }
        float ox = cx;
        float oy = cyFoot + height * EYE_HEIGHT_FACTOR;
        float oz = cz;

        float rgX = VisualBatch.rightX();
        float rgY = VisualBatch.rightY();
        float rgZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float a = size * EYE_HALF_WIDTH;
        float h = size * EYE_HALF_HEIGHT;
        int segments = VisualLod.scaleSegments(
                EYE_ARC_SEGMENTS, EYE_ARC_SEGMENTS_MIN, detail);
        float phase = seedId * 0.37f;

        // ===== 1) 眼眶填充：焦黑。癫火烧出来的洞 =====
        for (int i = 0; i < segments; i++) {
            float u0 = -a + 2f * a * i / segments;
            float u1 = -a + 2f * a * (i + 1) / segments;
            float v0 = lensV(u0, a, h);
            float v1 = lensV(u1, a, h);
            // 上下对称：一段填充就是一个上下夹出来的四边形
            planeQuad(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                    u0, v0, u1, v1, u1, -v1, u0, -v0,
                    C_CHAR, EYE_ALPHA * 0.92f);
        }

        // ===== 2) 眼眶轮廓：燃烧的癫火黄，边缘不断抖动 =====
        float prevUpV = 0f;
        float prevLoV = 0f;
        float prevU = -a;
        for (int i = 1; i <= segments; i++) {
            float u = -a + 2f * a * i / segments;
            float t = (float) i / segments;
            // 高频抖动：让轮廓像被火舔着，而不是一条静止的曲线
            float jitter = Mth.sin(time * EYE_RIM_FLICKER_SPEED + phase + t * 7.3f)
                    * size * EYE_RIM_FLICKER;
            float upV = lensV(u, a, h) + jitter;
            float loV = -lensV(u, a, h) - jitter * 0.8f;

            planeLine(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                    prevU, prevUpV, u, upV, EYE_RIM_HALF, C_YELLOW,
                    EYE_ALPHA, EYE_ALPHA);
            planeLine(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                    prevU, prevLoV, u, loV, EYE_RIM_HALF, C_YELLOW,
                    EYE_ALPHA, EYE_ALPHA);

            prevU = u;
            prevUpV = upV;
            prevLoV = loV;
        }

        // ===== 3) 血丝：从两个眼角向内伸的几道暗红细线 =====
        // 「充血之眼」的那点意思。用暗红而不是亮红——亮红会跟癫火黄打架
        if (VisualLod.keepLayer(detail, VEIN_KEEP_THRESHOLD)) {
            for (int i = 0; i < VEIN_COUNT; i++) {
                boolean left = (i & 1) == 0;
                float side = left ? -1f : 1f;
                float k = (i < 2) ? 0.55f : 0.35f;
                float startU = side * a * 0.92f;
                float endU = side * a * (0.92f - k);
                float vOff = ((i & 2) == 0) ? h * 0.3f : -h * 0.3f;
                planeLine(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                        startU, 0f, endU, vOff, VEIN_HALF, C_BLOOD,
                        VEIN_ALPHA, 0f);
            }
        }

        // ===== 4) 竖瞳：不规则收缩，是这只眼「不是人的」的关键 =====
        float dilate = 0.55f + 0.45f * (
                0.5f + 0.5f * Mth.sin(time * PUPIL_PULSE_A + phase))
                * (0.6f + 0.4f * Mth.sin(time * PUPIL_PULSE_B + phase * 2.1f));
        float pw = size * PUPIL_HALF_WIDTH * dilate;
        float ph = h * PUPIL_HEIGHT_RATIO;
        planeLine(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                0f, -ph, 0f, ph, pw, C_YELLOW, EYE_ALPHA, EYE_ALPHA);
        // 瞳心白热点：一条更细的芯
        planeLine(b, m, ox, oy, oz, rgX, rgY, rgZ, upX, upY, upZ,
                0f, -ph * 0.7f, 0f, ph * 0.7f, pw * 0.42f, C_CORE,
                EYE_ALPHA, EYE_ALPHA);

        // ===== 5) 眼上火苗：三簇自眼眶上缘窜起的癫火 =====
        if (VisualLod.keepLayer(detail, EYE_FLAME_KEEP_THRESHOLD)) {
            int flameSegments = VisualLod.scaleSegments(
                    FLAME_SEGMENTS, FLAME_SEGMENTS_MIN, detail);
            for (int i = 0; i < EYE_FLAME_COUNT; i++) {
                float fu = -a * 0.55f + a * 1.1f * i / (EYE_FLAME_COUNT - 1f);
                float fv = lensV(fu, a, h);
                float fx = ox + rgX * fu + upX * fv;
                float fy = oy + rgY * fu + upY * fv;
                float fz = oz + rgZ * fu + upZ * fv;
                // 中间那簇最高——火焰簇高度不一才自然
                float mul = (i == 1) ? 1f : 0.72f;
                drawFlame(b, m, fx, fy, fz,
                        size * EYE_FLAME_HEIGHT * mul, size * EYE_FLAME_WIDTH,
                        time, phase + i * 2.3f, flameSegments, 0.9f);
            }
        }
    }

    /**
     * 透镜形（眼眶）上弧在横坐标 {@code u} 处的纵坐标。
     * <p>用抛物线 {@code v = h·(1 - (u/a)²)}——两端归零、中间最高，
     * 上下对称即得眼形。比用圆弧简单，且两端天然收成尖角，正是眼角该有的样子。</p>
     *
     * @param u 横坐标
     * @param a 半宽
     * @param h 半高
     * @return 上弧纵坐标（下弧取其相反数）
     */
    private static float lensV(float u, float a, float h) {
        float k = u / a;
        float v = h * (1f - k * k);
        return Math.max(0f, v);
    }

    // ==================== 癫火丝 ====================

    /**
     * 自胸口向四周牵出的癫火丝：燃烧的黄色折线向外延伸、末端下垂并烧着一簇癫火，
     * 整体极缓慢绕行、横向轻微摆动，并周期性「绷紧」一下（缩短 + 变亮）。
     * <p>
     * <b>绷紧脉冲承担嘲讽机制的语义。</b>松弛时丝只是垂着，一旦绷紧就读作
     * 「有东西在那一头拽你」——正对应「怪物强烈倾向于攻击你」。
     * 它也是丝线唯一的节奏来源。
     * </p>
     * <p>
     * <b>v2.0 的改动：</b>颜色从暗血红改为癫火黄，并给每一段叠了高频的宽度抖动
     * （{@link #THREAD_BURN_SPEED}），于是丝看起来是在烧，而不是一根被拉直的绳。
     * 末端从「倒钩光点」改成了一小簇火。
     * </p>
     * <p>
     * 每条丝用「十字双面」绘制（沿世界 X、Z 轴各展开一个四边形），
     * 从任意水平视角皆可见，无需 billboard 计算。
     * </p>
     * <p>
     * <b>削减：</b>条数按步长抽取（均布角度，截断会让丝只朝一侧）；
     * 折线段数按细节缩放；丝端火簇是较贵的一层，可整层跳过。
     * </p>
     */
    private static void drawThreads(BufferBuilder b, Matrix4f m,
                                    float cx, float cyFoot, float cz,
                                    float width, float height,
                                    float time, int seedId, float detail) {
        float originY = cyFoot + height * EYE_HEIGHT_FACTOR;
        float originRadius = width * THREAD_ORIGIN_FACTOR;
        float baseLength = width * THREAD_LENGTH_FACTOR;
        float orbit = time * THREAD_ORBIT_SPEED + seedId * 0.5f;

        // 绷紧脉冲：窗口内由 1 快速衰减到 0，其余时间为 0（松弛）
        float tugCycle = frac(time / TUG_PERIOD + seedId * 0.13f);
        float tug = (tugCycle < TUG_WINDOW)
                ? easeOutCubic(1f - tugCycle / TUG_WINDOW) : 0f;

        int drawn = VisualLod.scale(THREAD_COUNT, detail);
        int step = Math.max(1, THREAD_COUNT / drawn);
        int segments = VisualLod.scaleSegments(THREAD_SEGMENTS, THREAD_SEGMENTS_MIN, detail);
        boolean drawTip = VisualLod.keepLayer(detail, THREAD_TIP_KEEP_THRESHOLD);
        int flameSegments = VisualLod.scaleSegments(4, FLAME_SEGMENTS_MIN, detail);

        for (int i = 0; i < THREAD_COUNT; i += step) {
            // 种子与角度基准都用原始下标 i，保证保留下来的丝与全细节时逐点一致
            long s = seedFor(seedId, i + 100);
            float lenRand = 0.7f + 0.6f * rngFloat(s);
            s = rngNext(s);
            float angJitter = (rngFloat(s) - 0.5f) * 0.3f;
            s = rngNext(s);
            float wavePhase = rngFloat(s) * TAU;

            float ang = orbit + TAU * i / THREAD_COUNT + angJitter;
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);

            // 绷紧时整体缩短：视觉上就是「被猛地拽了一下」
            float len = baseLength * lenRand * (1f - TUG_SHORTEN * tug);
            float alpha = THREAD_BASE_ALPHA * (0.75f + 0.45f * tug);

            // 起点贴在身体表面
            float px = cx + ca * originRadius;
            float py = originY;
            float pz = cz + sa * originRadius;

            for (int seg = 1; seg <= segments; seg++) {
                float u = (float) seg / segments;
                float r = originRadius + (len - originRadius) * u;
                // 横向摆动：垂直于径向，逐段错相，绷紧时摆幅收小（绷直了自然不晃）
                float wave = Mth.sin(time * THREAD_WAVE_SPEED + wavePhase + seg * 1.3f)
                        * width * THREAD_WAVE_AMOUNT * u * (1f - 0.7f * tug);
                float qx = cx + ca * r - sa * wave;
                float qz = cz + sa * r + ca * wave;
                float qy = originY - len * THREAD_SAG * u * u;

                // 燃烧抖动：宽度高频跳动，让丝看起来在烧
                float burn = 0.7f + 0.5f
                        * Mth.sin(time * THREAD_BURN_SPEED + wavePhase + seg * 2.7f);
                float hw = THREAD_HALF_WIDTH * (1f - 0.5f * u) * burn;
                float aPrev = alpha * (1f - 0.45f * (u - 1f / segments));
                float aCur = alpha * (1f - 0.45f * u);
                // 根部黄、末端向焦黑过渡：像一根烧到尽头的引线
                float[] col = (u < 0.6f) ? C_YELLOW : C_CHAR;

                worldLine(b, m, px, py, pz, qx, qy, qz, hw, col, aPrev, aCur);

                px = qx;
                py = qy;
                pz = qz;
            }

            // 丝端火簇：癫火烧在丝的尽头，是「钩住了东西」的表达
            if (drawTip) {
                float flare = 0.8f + 0.4f * tug;
                drawFlame(b, m, px, py, pz,
                        width * THREAD_TIP_FLAME * flare, width * THREAD_TIP_FLAME * 0.42f,
                        time, wavePhase, flameSegments, alpha);
            }
        }
    }

    // ==================== 身周火舌 ====================

    /**
     * 绕身体一圈向上舔的癫火火舌。
     * <p>
     * <b>这一层是「整个人在烧」这个读法的主要来源</b>，也是与嘶吼拉开距离的关键：
     * 嘶吼是敌人头上飘一只眼，灾祸是这个人从脚到胸都在冒黄火。
     * </p>
     * <p>
     * 火舌用 billboard 绘制（始终正对相机）——这是火焰的常规做法，
     * 侧看变成一条线的火是不可接受的。绕身体均布的一圈 billboard 火焰
     * 在视觉上会自然叠成「一团包住人的火」。
     * </p>
     * <p>
     * <b>削减：</b>这是全渲染器最贵的一层（约 432 顶点），
     * 也是保留阈值最激进的一层；条数按步长抽取，火焰段数按细节缩放。
     * </p>
     */
    private static void drawBodyFlames(BufferBuilder b, Matrix4f m,
                                       float cx, float cyFoot, float cz,
                                       float width, float height,
                                       float time, int seedId, float detail) {
        if (!VisualLod.keepLayer(detail, BODY_FLAME_KEEP_THRESHOLD)) {
            return;
        }
        int drawn = VisualLod.scale(BODY_FLAME_COUNT, detail);
        int step = Math.max(1, BODY_FLAME_COUNT / drawn);
        int flameSegments = VisualLod.scaleSegments(
                FLAME_SEGMENTS, FLAME_SEGMENTS_MIN, detail);
        float radius = width * BODY_FLAME_RADIUS;

        for (int i = 0; i < BODY_FLAME_COUNT; i += step) {
            // 角度基准用原始 BODY_FLAME_COUNT，保证保留火舌的方位与全细节时一致
            float ang = TAU * i / BODY_FLAME_COUNT + seedId * 0.31f;
            float fx = cx + Mth.cos(ang) * radius;
            float fz = cz + Mth.sin(ang) * radius;
            // 各条高度不一，且随时间起伏——齐刷刷一样高的火假得很
            float bob = 0.75f + 0.35f * Mth.sin(time * 2.6f + i * 1.9f + seedId * 0.4f);
            drawFlame(b, m, fx, cyFoot + Y_OFFSET, fz,
                    height * BODY_FLAME_HEIGHT * bob, width * BODY_FLAME_WIDTH,
                    time, i * 1.7f + seedId * 0.6f, flameSegments, 0.8f);
        }
    }

    // ==================== 脚下焦痕 ====================

    /**
     * 脚下的焦黑痕迹 + 边缘余烬亮点。
     * <p>癫火烧过的地面是焦的。这一层压住整体重量——
     * 只有悬空的火与丝会显得轻飘，加一层贴地的黑才有「这个人被钉在这儿烧」的分量。</p>
     */
    private static void drawCharPool(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float width,
                                     float time, int seedId, float detail) {
        float breath = 0.9f + 0.1f * Mth.sin(time * 0.8f + seedId * 0.45f);
        float radius = width * CHAR_RADIUS_FACTOR * breath;
        int segments = VisualLod.scaleSegments(CHAR_SEGMENTS, CHAR_SEGMENTS_MIN, detail);
        drawDisc(b, m, cx, cy, cz, radius, segments,
                C_CHAR[0], C_CHAR[1], C_CHAR[2], CHAR_ALPHA * breath);

        // 余烬：焦痕边缘上明灭的小亮点
        if (VisualLod.keepLayer(detail, EMBER_KEEP_THRESHOLD)) {
            for (int i = 0; i < EMBER_COUNT; i++) {
                float ang = TAU * i / EMBER_COUNT + time * 0.12f + seedId * 0.7f;
                float twinkle = 0.5f + 0.5f * Mth.sin(time * 4.1f + i * 2.3f + seedId);
                if (twinkle <= 0.08f) {
                    continue;
                }
                float ex = cx + Mth.cos(ang) * radius * 0.88f;
                float ez = cz + Mth.sin(ang) * radius * 0.88f;
                billboardDiamond(b, m, ex, cy + 0.02f, ez,
                        EMBER_SIZE * (0.6f + 0.7f * twinkle), C_YELLOW, 0.85f * twinkle);
            }
        }
    }

    // ==================== 火焰基元 ====================

    /**
     * 画一簇 billboard 癫火：自底部升起、逐段收窄、左右摇曳，顶端渐隐。
     * <p>
     * <b>火焰是本渲染器的主要顶点开销</b>（每簇 {@code segments × 12} 顶点，
     * 外层 + 内芯各一遍），故段数完全交给调用方按细节决定。
     * </p>
     * <p>
     * <b>两层结构</b>：外层癫火黄、内芯白热且更细更矮。
     * 单层火焰会读成一片没有厚度的黄色贴纸；有了内芯才有「烧得很旺」的层次。
     * </p>
     * <p>
     * 摇曳用沿高度推进的正弦（每段相位递增），于是火苗是<b>整条在扭</b>，
     * 而不是整簇在左右平移——后者会读成一面被风吹的旗子。
     * </p>
     *
     * @param bx       火焰底部世界 X（相对相机）
     * @param by       火焰底部世界 Y
     * @param bz       火焰底部世界 Z
     * @param flHeight 火焰高度（格）
     * @param flWidth  火焰底部宽度（格）
     * @param time     动画时间（秒）
     * @param phase    相位偏移（区分各簇火）
     * @param segments 细分段数
     * @param alpha    整体不透明度
     */
    private static void drawFlame(BufferBuilder b, Matrix4f m,
                                  float bx, float by, float bz,
                                  float flHeight, float flWidth,
                                  float time, float phase, int segments, float alpha) {
        if (alpha <= 0.01f || flHeight <= 1.0e-3f || flWidth <= 1.0e-3f) {
            return;
        }
        float rgX = VisualBatch.rightX();
        float rgY = VisualBatch.rightY();
        float rgZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        // 外层：癫火黄
        flameLayer(b, m, bx, by, bz, rgX, rgY, rgZ, upX, upY, upZ,
                flHeight, flWidth, time, phase, segments, C_YELLOW, alpha);
        // 内芯：白热，更细更矮
        flameLayer(b, m, bx, by, bz, rgX, rgY, rgZ, upX, upY, upZ,
                flHeight * 0.62f, flWidth * 0.45f, time, phase, segments, C_CORE, alpha * 0.9f);
    }

    /**
     * 火焰的单层：一串逐段收窄的四边形，构成一条扭动的尖头火苗。
     *
     * @param flHeight 该层高度
     * @param flWidth  该层底部宽度
     */
    private static void flameLayer(BufferBuilder b, Matrix4f m,
                                   float bx, float by, float bz,
                                   float rgX, float rgY, float rgZ,
                                   float upX, float upY, float upZ,
                                   float flHeight, float flWidth,
                                   float time, float phase, int segments,
                                   float[] col, float alpha) {
        float prevU = 0f;
        float prevHalf = flWidth * 0.5f;
        float prevV = 0f;
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            // 摇曳：沿高度推进的正弦，火苗整条在扭而不是整簇平移
            float sway = Mth.sin(time * FLAME_SWAY_SPEED + phase + t * 3.6f)
                    * flWidth * FLAME_SWAY_AMOUNT * t;
            float v = flHeight * t;
            // 逐段收窄，顶端收成尖
            float half = flWidth * 0.5f * (1f - t) * (1f - t * 0.35f);
            float aPrev = alpha * (1f - (float) (i - 1) / segments);
            float aCur = alpha * (1f - t);

            planeQuad(b, m, bx, by, bz, rgX, rgY, rgZ, upX, upY, upZ,
                    prevU - prevHalf, prevV, prevU + prevHalf, prevV,
                    sway + half, v, sway - half, v,
                    col, aPrev, aCur);

            prevU = sway;
            prevV = v;
            prevHalf = half;
        }
    }

    // ==================== billboard 平面几何基元 ====================
    // 眼睛与火焰都活在「面向相机的平面」里，用平面二维坐标 (u, v) 描述比逐点算三维方便得多。
    // 映射关系：P = center + right·u + up·v

    /**
     * 在 billboard 平面内绘制一个四边形（四个角用平面二维坐标给出），单一 alpha。
     */
    private static void planeQuad(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float u1, float v1, float u2, float v2,
                                  float u3, float v3, float u4, float v4,
                                  float[] col, float alpha) {
        planeQuad(b, m, cx, cy, cz, rgX, rgY, rgZ, upX, upY, upZ,
                u1, v1, u2, v2, u3, v3, u4, v4, col, alpha, alpha);
    }

    /**
     * 在 billboard 平面内绘制一个四边形，前两角与后两角可用不同 alpha。
     * <p>火焰逐段收窄时靠这个做纵向渐隐。</p>
     *
     * @param alphaA 角 1、2 的不透明度
     * @param alphaB 角 3、4 的不透明度
     */
    private static void planeQuad(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float rgX, float rgY, float rgZ,
                                  float upX, float upY, float upZ,
                                  float u1, float v1, float u2, float v2,
                                  float u3, float v3, float u4, float v4,
                                  float[] col, float alphaA, float alphaB) {
        if (alphaA <= 0.004f && alphaB <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];

        float x1 = cx + rgX * u1 + upX * v1;
        float y1 = cy + rgY * u1 + upY * v1;
        float z1 = cz + rgZ * u1 + upZ * v1;
        float x2 = cx + rgX * u2 + upX * v2;
        float y2 = cy + rgY * u2 + upY * v2;
        float z2 = cz + rgZ * u2 + upZ * v2;
        float x3 = cx + rgX * u3 + upX * v3;
        float y3 = cy + rgY * u3 + upY * v3;
        float z3 = cz + rgZ * u3 + upZ * v3;
        float x4 = cx + rgX * u4 + upX * v4;
        float y4 = cy + rgY * u4 + upY * v4;
        float z4 = cz + rgZ * u4 + upZ * v4;

        b.vertex(m, x1, y1, z1).color(r, g, bl, alphaA).endVertex();
        b.vertex(m, x2, y2, z2).color(r, g, bl, alphaA).endVertex();
        b.vertex(m, x3, y3, z3).color(r, g, bl, alphaB).endVertex();

        b.vertex(m, x1, y1, z1).color(r, g, bl, alphaA).endVertex();
        b.vertex(m, x3, y3, z3).color(r, g, bl, alphaB).endVertex();
        b.vertex(m, x4, y4, z4).color(r, g, bl, alphaB).endVertex();
    }

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
     * 面向相机的小菱形光点：中心最亮、四角渐隐。
     * <p>仅 12 顶点，不参与分段缩放；是否绘制由调用方按保留阈值决定。</p>
     */
    private static void billboardDiamond(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz, float size,
                                         float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rx = VisualBatch.rightX() * size;
        float ry = VisualBatch.rightY() * size;
        float rz = VisualBatch.rightZ() * size;
        float upx = VisualBatch.upX() * size;
        float upy = VisualBatch.upY() * size;
        float upz = VisualBatch.upZ() * size;

        // 四个角点：上、右、下、左（相机平面内）
        float p0x = cx + upx, p0y = cy + upy, p0z = cz + upz;
        float p1x = cx + rx, p1y = cy + ry, p1z = cz + rz;
        float p2x = cx - upx, p2y = cy - upy, p2z = cz - upz;
        float p3x = cx - rx, p3y = cy - ry, p3z = cz - rz;

        diamondTri(b, m, cx, cy, cz, p0x, p0y, p0z, p1x, p1y, p1z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p1x, p1y, p1z, p2x, p2y, p2z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p2x, p2y, p2z, p3x, p3y, p3z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p3x, p3y, p3z, p0x, p0y, p0z, r, g, bl, alpha);
    }

    /**
     * 菱形光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     */
    private static void diamondTri(BufferBuilder b, Matrix4f m,
                                   float cx, float cy, float cz,
                                   float ax, float ay, float az,
                                   float bx, float by, float bz,
                                   float r, float g, float bl, float alpha) {
        b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, ay, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bl, 0f).endVertex();
    }

    // ==================== 世界空间几何基元 ====================

    /**
     * 世界空间的「十字双面」线段：沿世界 X、Z 轴各画一个四边形，
     * 使线段从任意水平视角皆可见、无需 billboard 计算。
     * <p>癫火丝绕着身体一圈，若做成 billboard 会全部正对相机、失去环绕感，故用十字双面。</p>
     *
     * @param hw 线半宽（格）
     * @param a1 起点端 alpha
     * @param a2 终点端 alpha
     */
    private static void worldLine(BufferBuilder b, Matrix4f m,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        // 面 1：沿世界 X 轴加宽
        worldQuad(b, m, x1 - hw, y1, z1, x1 + hw, y1, z1,
                x2 + hw, y2, z2, x2 - hw, y2, z2, r, g, bl, a1, a2);
        // 面 2：沿世界 Z 轴加宽
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
     * 水平径向渐变圆盘（中心 alpha、边缘 0）。
     */
    private static void drawDisc(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz, float radius, int segments,
                                 float r, float g, float bl, float centerAlpha) {
        if (centerAlpha <= 0.004f || radius <= 1.0e-4f) {
            return;
        }
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

    /** 取小数部分（结果恒在 [0,1)）。 */
    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /** 缓出（cubic）。 */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }
}
