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
 * 暗月「月华」客户端渲染器（纯客户端自绘，<b>零网络包</b>）。
 * <p>
 * 对应 {@code EnchantmentDarkMoon}：夜晚时魔法伤害 +25%、魔法减伤 +25%、恢复 +25%、获得夜视；
 * 对以持有者为目标的生物额外造成 5% 当前生命的魔法伤害并吸血。
 * <b>当护甲同时带有满月（{@code EnchantmentFullMoon}）时全部数值提升到 +37.5%</b>
 * ——即「月之共鸣」，本渲染器会为此额外加强视觉。
 * </p>
 *
 * <h3>为什么不需要任何网络包</h3>
 * <p>
 * 暗月的生效条件只有两个，而<b>客户端两样都能自己读到</b>：
 * </p>
 * <ol>
 *     <li><b>夜晚</b> —— {@code level.isDay()} 在客户端同样可用（世界时间是同步的）；</li>
 *     <li><b>主手带 dark_moon</b> —— 附魔存在于物品 NBT 中，而装备栏物品对所有观察者都正常同步，
 *         客户端可直接 {@code EnchantmentHelper.getItemEnchantmentLevel} 读出。</li>
 * </ol>
 * <p>
 * 这与 {@code ScarletRotMistRenderer} 判定腐败女神持有者、{@code AuraDisplayRegistry}
 * 判定光环激活是同一手法。相比之下，猩红腐败 / 冻伤 / 出血那些必须走
 * {@code ClientSyncEffectManager} 的原因是它们基于 {@code MobEffect}——
 * 原版只在观察者<b>开始追踪</b>某实体的那一刻同步一次效果列表，战斗中途新加的效果收不到。
 * 附魔没有这个问题。
 * </p>
 * <p>
 * 因此本渲染器<b>不占用任何效果序列号、不新增任何包、服务端零开销</b>，
 * 也不需要改动 {@code EnchantmentDarkMoon} 一行代码。
 * </p>
 *
 * <h3>v2 修复一：月轮改 billboard（原先平视只看得到一条线）</h3>
 * <p>
 * v1 的月轮是<b>水平放置</b>的圆盘（画在固定 y 高度上）。这在俯视时是个圆，
 * 但玩家绝大多数时候是<b>平视</b>——看到的是一条扁线，完全读不出「月亮」。
 * </p>
 * <p>
 * 现改为 <b>billboard 面向相机</b>（{@link #bbDisc} / {@link #bbRing} / {@link #bbLine}，
 * 平面基取 {@link VisualBatch#rightX()} 与 {@link VisualBatch#upX()} 一组），
 * 于是从任意角度看都是一个正圆。
 * </p>
 *
 * <h3>v2 修复二：加月海——「像月亮」最关键的一笔</h3>
 * <p>
 * 光是个亮圆盘还不够，那更像「法阵的光球」。因此在盘面上叠了几块稍暗的圆斑当
 * <b>月海</b>（{@link #MOON_MARIA}），真实月面的月海正是这种不规则的暗色区域。
 * </p>
 * <p>
 * <b>月海不随符文环自转</b>：真实月球是潮汐锁定的、月面朝向恒定；
 * 而且「静止的月面 + 缓慢转动的外圈符文」这个对比本身也更好看。
 * </p>
 *
 * <h3>v3 修复：月轮从「圆」变成「球」，月相由光源方向自然产生</h3>
 * <p>
 * v2 已经把月轮改成 billboard（解决了「平视看是一条线」），并用一条沿真实明暗界线
 * 构造的月牙带表现月相。但盘面本身仍是<b>平的</b>——中心到边缘只有单调的 alpha 渐变，
 * 读起来像「一枚发光的硬币贴了块阴影」，而不是一颗悬着的天体。
 * </p>
 * <p>
 * 现在改用 {@link #bbSphere} 画：把圆盘拆成同心环带，<b>逐顶点计算球面法线并做兰伯特着色</b>。
 * 平面内归一化坐标 {@code (u, v)} 对应的球面法线是
 * </p>
 * <pre>
 * n = (u, v, sqrt(1 - u² - v²))
 * </pre>
 * <p>
 * 第三个分量朝向相机。与光源方向点乘即得该点亮度，于是在<b>没有任何光照管线</b>的
 * {@code POSITION_COLOR} 纯顶点绘制下，也能得到真正的球体明暗过渡与边缘暗化。
 * </p>
 * <p>
 * <b>最大的附带收益：月相不必再画了。</b>球面着色的明暗界线本来就是月相的成因——
 * 光源朝向相机即满月，越偏向侧面越接近新月。于是 v2 那个单独绘制的月牙带
 * （{@code bbMoonShadow}）连同它的月相系数一起删除，改由两个光源常量表达：
 * </p>
 * <ul>
 *     <li>普通状态：{@link #LIGHT_U_GIBBOUS} / {@link #LIGHT_W_GIBBOUS} 明显侧照 → <b>亏凸月</b>；</li>
 *     <li>月之共鸣：{@link #LIGHT_U_RESONANCE} / {@link #LIGHT_W_RESONANCE} 近正照 → <b>接近满月</b>。</li>
 * </ul>
 * <p>
 * 同时<b>去掉了球体外圈的硬轮廓线</b>，只保留一层向外渐隐的月晕——
 * 硬边框会把球压回成一枚有描边的硬币，破坏刚建立起来的立体感。
 * </p>
 *
 * <h3>形状语言</h3>
 * <p>
 * 本模组现有的十余种演出已经占满了大部分形状：法阵是多边形 / 星形、光环是同心圆、
 * 刀光是弧带、螺旋归睡眠、尖刺归噩兆、根须归黄金树、电柱归龙雷。
 * <b>「带月海与月相的圆盘」这一组合尚未被使用</b>，且它对「月」的指向性极强，
 * 因此同屏叠加时不存在辨识歧义。
 * </p>
 * <p>
 * <b>三个元素：</b>
 * </p>
 * <ol>
 *     <li><b>头顶月轮</b>（{@link #drawMoonDisc}）——核心标志。面向相机的冷蓝白圆盘，
 *         盘面上有月海与真实的月相暗面，外圈一环细密刻度缓慢自转；</li>
 *     <li><b>周身月尘</b>（{@link #drawMoonDust}）——极缓慢上升的冷蓝小光点，
 *         表现「沐浴在月光里」；上升速度刻意压得很慢，与暗月「静谧」的气质一致；</li>
 *     <li><b>脚下月光盘</b>（{@link #drawMoonPool}）——极淡的径向渐变圆盘，压住重量。
 *         这一个<b>仍是水平的</b>，因为它表达的是「月光洒在地上」，本就该贴地。</li>
 * </ol>
 *
 * <h3>月之共鸣的视觉加强</h3>
 * <p>
 * 检测到护甲带满月时（{@link #hasFullMoon}），机制数值全线 +50%（0.25 → 0.375），
 * 视觉同步加强，让玩家<b>不看 HUD 徽标也能从世界里读出「共鸣生效中」</b>：
 * </p>
 * <ul>
 *     <li>月轮直径放大 {@link #RESONANCE_SCALE} 倍、亮度提升；</li>
 *     <li>月轮外<b>再加一圈光晕环</b>（普通状态没有），是最直接的辨识差异；</li>
 *     <li>月相从亏凸月收成接近满月（近正照光源）；</li>
 *     <li>月尘数量与亮度提升。</li>
 * </ul>
 *
 * <h3>性能</h3>
 * <p>
 * 单个实体每帧顶点量：
 * </p>
 * <pre>
 * 月轮盘面（32 段 × 3）                     96
 * 月海（5 块 × 10 段 × 3）                 150
 * 月相暗面（16 段 × 6）                     96
 * 月轮外环（32 段 × 6）                    192
 * 外缘刻度（16 条 × 6）                     96
 * 共鸣光晕环（32 段 × 6，仅共鸣时）        192
 * 周身月尘（10~14 颗 × 8 段 × 3）      240~336
 * 脚下月光盘（24 段 × 3）                   72
 * ─────────────────────────────────────────
 * 合计                     ~700（普通）/ ~990（共鸣）
 * </pre>
 * <p>
 * 暗月是武器附魔、且只在夜晚生效，同屏并发量远低于出血 / 黄金树祝福那类，
 * 但仍完整接入 {@link VisualLod}（含 {@link VisualLod#countInstance()}——
 * 少登记一个渲染器就会让全局 {@code crowdFactor} 被系统性高估，
 * 已接入的重量级渲染器就削减不足）。
 * </p>
 * <p>
 * <b>三个主题色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值</b>，
 * 故全部预解包为 {@code C_} 常量，本渲染器颜色相关堆分配恒为 0，无需 {@code SCRATCH} 缓冲。
 * 几何上也全部用标量内联，无任何临时数组。
 * </p>
 *
 * @author FlameForge
 * @version 3.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class DarkMoonRenderer {

    /** 暗月附魔的注册 id（按 {@code carianstyle:<id>} 解析） */
    private static final String DARK_MOON_ID = "dark_moon";
    /** 满月附魔的注册 id（用于月之共鸣检测） */
    private static final String FULL_MOON_ID = "full_moon";

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float PI = (float) Math.PI;
    private static final float Y_OFFSET = 0.02f;

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ===== LOD 下限与保留阈值 =====
    /** 月轮环 / 盘的最少分段数：月轮是核心标志，多边形化最不能容忍，故下限偏高 */
    private static final int DISC_SEGMENTS_MIN = 14;
    /** 脚下月光盘的最少分段数 */
    private static final int POOL_SEGMENTS_MIN = 8;
    /** 月尘柔光点的最少分段数 */
    private static final int DUST_SEGMENTS_MIN = 4;
    /** 外缘刻度层的保留阈值：细密小段，远处糊成一圈 */
    private static final float TICK_KEEP_THRESHOLD = 0.5f;
    /** 周身月尘层的保留阈值：纯氛围层 */
    private static final float DUST_KEEP_THRESHOLD = 0.45f;

    // ===== 配色（0xRRGGBB）=====
    /** 月白：月轮盘面与高光的主色 */
    private static final int MOON_CORE = 0xF0F6FF;
    /** 月华蓝：月轮外环、月尘、光晕的主色 */
    private static final int MOON_GLOW = 0xA8C4F0;
    /** 夜蓝：月海、月相暗面、脚下月光盘 */
    private static final int MOON_DEEP = 0x3F5C99;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    //
    // 本渲染器的三个主题色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值，
    // 因此不需要任何 SCRATCH 复用缓冲，颜色相关分配恒为 0。
    /** 月白（盘面 / 高光） */
    private static final float[] C_MOON_CORE = VisualColor.constant(MOON_CORE);
    /** 月华蓝（外环 / 月尘 / 共鸣光晕） */
    private static final float[] C_MOON_GLOW = VisualColor.constant(MOON_GLOW);
    /** 夜蓝（月海 / 月相暗面 / 脚下盘） */
    private static final float[] C_MOON_DEEP = VisualColor.constant(MOON_DEEP);

    // ===== 头顶月轮（核心标志）=====
    /** 月轮悬浮高度系数（× 实体高度）：略高于头顶 */
    private static final float DISC_HEIGHT_FACTOR = 1.45f;
    /** 月轮半径系数（× 实体宽度） */
    private static final float DISC_RADIUS_FACTOR = 0.62f;
    /** 月轮盘面分段数 */
    private static final int DISC_SEGMENTS = 32;
    /** 月轮自转速度（弧度/秒）——刻意缓慢，与暗月的静谧气质一致 */
    private static final float DISC_ROT_SPEED = 0.32f;
    /** 月轮呼吸速度 */
    private static final float DISC_BREATH_SPEED = 0.85f;
    /** 月轮盘面中心不透明度 */
    private static final float DISC_FILL_ALPHA = 0.62f;

    /** 月轮球体的径向环数（明暗过渡的平滑度，是球体的主要顶点杠杆） */
    private static final int SPHERE_RINGS = 6;
    /** 月轮球体的最少径向环数：3 环仍能读出明暗渐变，再低会出现明显色带 */
    private static final int SPHERE_RINGS_MIN = 3;

    /**
     * 普通状态（亏凸月）的光源 right 分量。
     * <p>
     * <b>v2 起月相不再单独绘制暗面，而是由光源方向自然产生</b>
     * （详见 {@link #bbSphere} 的「月相 = 光源方向」小节）。
     * 明显偏侧即得亏凸月：明暗界线出现在球面偏右处，左侧沉入阴影。
     * </p>
     */
    private static final float LIGHT_U_GIBBOUS = 0.62f;
    /** 普通状态的光源「朝向相机」分量（配合 {@link #LIGHT_U_GIBBOUS} 构成侧照） */
    private static final float LIGHT_W_GIBBOUS = 0.72f;
    /**
     * 月之共鸣时的光源 right 分量：转向近正照、接近满月，
     * 与「满月 + 暗月 = 月之共鸣」的名字直接呼应。
     */
    private static final float LIGHT_U_RESONANCE = 0.20f;
    /** 共鸣时的光源「朝向相机」分量（接近 1 = 接近满月） */
    private static final float LIGHT_W_RESONANCE = 0.95f;
    /** 光源的 up 分量（略偏上，符合「月光自上而来」的直觉） */
    private static final float LIGHT_V = 0.24f;
    /** 环境光下限：0 会让暗侧纯黑、像挖了个洞，留一点更像被地球反照的月面 */
    private static final float MOON_AMBIENT = 0.34f;

    /** 月晕不透明度（球体外圈的向外渐隐柔光） */
    private static final float HALO_ALPHA = 0.34f;

    /** 外缘刻度数量 */
    private static final int TICK_COUNT = 16;
    /** 外缘刻度长度相对半径的比例 */
    private static final double TICK_LENGTH_RATIO = 0.18;
    /** 外缘刻度起点相对半径的比例 */
    private static final double TICK_START_RATIO = 1.26;
    private static final float TICK_ALPHA = 0.45f;

    /**
     * 月海斑块（盘面上稍暗的圆斑），每 3 个 float 一组：
     * {@code [平面内 right 分量, 平面内 up 分量, 半径]}，三者均为<b>相对月轮半径的比例</b>。
     * <p>
     * <b>这是「像月亮」最关键的一笔。</b>纯亮圆盘读起来是「法阵的光球」，
     * 叠上几块深色斑块之后才会被认成月亮——真实月面的月海正是这种不规则的暗色区域。
     * 坐标是手调的，刻意做成大小不一、分布不对称（对称会显得像图案而非天体）。
     * </p>
     * <p>
     * <b>⚠ 只读常量数组</b>，绘制时只按下标读取、绝不写入。
     * </p>
     */
    private static final float[] MOON_MARIA = {
            -0.25f, 0.30f, 0.30f,
            0.28f, 0.12f, 0.22f,
            -0.05f, -0.28f, 0.26f,
            0.35f, -0.35f, 0.16f,
            -0.42f, -0.08f, 0.14f
    };

    /** 月海不透明度 */
    private static final float MARIA_ALPHA = 0.34f;

    // ===== 月之共鸣加强 =====
    /** 共鸣时月轮直径放大倍数 */
    private static final float RESONANCE_SCALE = 1.35f;
    /** 共鸣时整体亮度倍数 */
    private static final float RESONANCE_ALPHA_MUL = 1.3f;
    /** 共鸣光晕环相对月轮半径的倍数 */
    private static final float RESONANCE_HALO_FACTOR = 1.28f;
    /** 共鸣光晕环半宽相对半径的比例 */
    private static final double RESONANCE_HALO_HALF_RATIO = 0.14;
    /** 共鸣光晕环不透明度 */
    private static final float RESONANCE_HALO_ALPHA = 0.32f;

    // ===== 周身月尘 =====
    /** 普通状态的月尘数量 */
    private static final int DUST_COUNT = 10;
    /** 共鸣时额外增加的月尘数量 */
    private static final int DUST_RESONANCE_EXTRA = 4;
    /** 月尘上升循环速度（每秒推进的归一化进度）——极慢 */
    private static final float DUST_RISE_SPEED = 0.13f;
    /** 月尘上升高度系数（× 实体高度） */
    private static final float DUST_RISE_HEIGHT_FACTOR = 1.25f;
    /** 月尘水平分布半径系数（× 实体宽度） */
    private static final float DUST_SPREAD_FACTOR = 0.55f;
    /** 月尘基准尺寸（格） */
    private static final float DUST_SIZE = 0.055f;
    /** 月尘 billboard 圆的分段数 */
    private static final int DUST_SEGMENTS = 8;
    private static final float DUST_BASE_ALPHA = 0.7f;

    // ===== 脚下月光盘 =====
    private static final int POOL_SEGMENTS = 24;
    private static final float POOL_RADIUS_FACTOR = 1.15f;
    private static final float POOL_ALPHA = 0.2f;
    private static final float POOL_BREATH_SPEED = 0.4f;

    /** 暗月附魔懒解析缓存（注册表在 mod 加载后才可用，首次解析成功后固定） */
    private static Enchantment darkMoonCache;
    /** 暗月附魔是否已成功解析 */
    private static boolean darkMoonResolved;

    /** 满月附魔懒解析缓存 */
    private static Enchantment fullMoonCache;
    /** 满月附魔是否已成功解析 */
    private static boolean fullMoonResolved;

    private DarkMoonRenderer() {
    }

    /**
     * 世界渲染回调：在半透明方块之后，绘制相机附近所有「夜晚 + 主手带暗月」实体的月华视觉。
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

        // ⭐ 白天直接整个跳过——暗月只在夜晚生效，这一行省掉全部后续开销。
        // level.isDay() 在客户端同样可用（世界时间随原版正常同步）
        if (mc.level.isDay()) {
            return;
        }

        Enchantment darkMoon = resolveDarkMoon();
        if (darkMoon == null) {
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

        Enchantment fullMoon = resolveFullMoon();

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
            // 共享列表已保证 isAlive，此处只做附魔判定
            if (!hasDarkMoon(entity, darkMoon)) {
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
            // 登记实例，供下一帧估算拥挤度——少登记一个渲染器就会让全局 crowdFactor 被高估
            VisualLod.countInstance();

            // 月之共鸣：护甲带满月时机制数值全线 +50%，视觉同步加强
            boolean resonance = hasFullMoon(entity, fullMoon);

            float width = entity.getBbWidth();
            float height = entity.getBbHeight();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            drawMoonPool(builder, matrix, rx, ryFoot + Y_OFFSET, rz, width, time, entity.getId(),
                    resonance, detail);
            // 月尘是纯氛围层，远处完全看不出，低细节时整层跳过
            if (VisualLod.keepLayer(detail, DUST_KEEP_THRESHOLD)) {
                drawMoonDust(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                        resonance, rightX, rightY, rightZ, upX, upY, upZ, detail);
            }
            drawMoonDisc(builder, matrix, rx, ryFoot, rz, width, height, time, entity.getId(),
                    resonance, rightX, rightY, rightZ, upX, upY, upZ, detail);
        }
    }

    // ==================== 附魔判定 ====================

    /**
     * 判断实体主手是否带暗月附魔。
     * <p>附魔随物品 NBT 正常同步，对所有实体（含怪物）都可靠，无需任何网络包。</p>
     * <p>
     * 用 {@link ItemStack#isEnchanted()} 做廉价前置过滤——该方法只检查 NBT 标签是否存在、
     * 不做任何反序列化，而绝大多数实体主手是空手或未附魔武器，这一行能砍掉
     * 循环内绝大部分的 {@code getItemEnchantmentLevel} 调用（后者会逐条遍历附魔 NBT
     * 并做 {@code ResourceLocation} 解析）。
     * </p>
     *
     * @param entity   待判定实体
     * @param darkMoon 暗月附魔（非 null，调用方已判空）
     * @return 主手带暗月返回 true
     */
    private static boolean hasDarkMoon(LivingEntity entity, Enchantment darkMoon) {
        ItemStack main = entity.getMainHandItem();
        if (main.isEmpty() || !main.isEnchanted()) {
            return false;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(darkMoon, main) > 0;
    }

    /**
     * 判断实体是否触发「月之共鸣」——任一护甲带满月附魔。
     * <p>与 {@code EnchantmentDarkMoon.hasFullMoonEnchantment} 的口径一致（只看护甲槽）。</p>
     *
     * @param entity   待判定实体
     * @param fullMoon 满月附魔（可能为 null）
     * @return 任一护甲带满月返回 true
     */
    private static boolean hasFullMoon(LivingEntity entity, @Nullable Enchantment fullMoon) {
        if (fullMoon == null) {
            return false;
        }
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor.isEmpty() || !armor.isEnchanted()) {
                continue;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 懒解析暗月附魔对象（注册表在 mod 加载后才可用，故首次调用时解析并缓存）。
     * <p>仅在成功解析后才标记完成，否则下次重试——避免在注册表尚未就绪时把 null 固化下来。</p>
     *
     * @return 暗月附魔；未注册（如被配置禁用）时返回 null
     */
    @Nullable
    private static Enchantment resolveDarkMoon() {
        if (!darkMoonResolved) {
            darkMoonCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, DARK_MOON_ID));
            darkMoonResolved = (darkMoonCache != null);
        }
        return darkMoonCache;
    }

    /**
     * 懒解析满月附魔对象（同上）。
     *
     * @return 满月附魔；未注册时返回 null
     */
    @Nullable
    private static Enchantment resolveFullMoon() {
        if (!fullMoonResolved) {
            fullMoonCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, FULL_MOON_ID));
            fullMoonResolved = (fullMoonCache != null);
        }
        return fullMoonCache;
    }

    // ==================== 头顶月轮（核心标志）====================

    /**
     * 头顶悬浮的月轮：面向相机的圆盘 + 月海 + 月相暗面 + 外环 + 外缘刻度，
     * 整体缓慢自转并呼吸。
     * <p>
     * <b>v2：改为 billboard。</b>v1 是水平圆盘，平视只能看到一条线，完全不像月亮
     * （详见类注释「v2 修复一」小节）。
     * </p>
     * <p>
     * <b>三个「月亮感」的来源，缺一不可：</b>
     * </p>
     * <ol>
     *     <li><b>正圆</b>——billboard 保证任意角度都是圆；</li>
     *     <li><b>月海</b>——盘面上的暗色斑块，把「光球」变成「天体」；</li>
     *     <li><b>月相</b>——真实的明暗界线月牙，而不是从圆心切一刀的扇形。</li>
     * </ol>
     * <p>
     * <b>月海与月相暗面都不随符文环自转</b>：真实月球潮汐锁定、月面朝向恒定，
     * 且「静止月面 + 转动符文环」的对比更好看。只有外缘刻度在转。
     * </p>
     * <p>
     * <b>共鸣加强：</b>直径 × {@link #RESONANCE_SCALE}、亮度 × {@link #RESONANCE_ALPHA_MUL}、
     * 光源转向近正照使月相接近满月、
     * 外加一圈普通状态没有的光晕环——四项叠加，玩家不看 HUD 也能一眼分辨。
     * </p>
     * <p>
     * <b>削减：</b>盘 / 环 / 暗面的分段数缩放；月海与外缘刻度按步长抽取并可整层跳过。
     * <b>盘面、月相暗面与外环无论细节多低都完整绘制</b>——它们是「这是月亮」的全部依据。
     * </p>
     *
     * @param resonance 是否触发月之共鸣（护甲带满月）
     * @param detail    本帧细节系数
     */
    private static void drawMoonDisc(BufferBuilder b, Matrix4f m,
                                     float cx, float cyFoot, float cz,
                                     float width, float height,
                                     float time, int seedId, boolean resonance,
                                     float rgX, float rgY, float rgZ,
                                     float upX, float upY, float upZ, float detail) {
        // 缓慢自转 + 各实体错相（只作用于外缘刻度）
        float rot = time * DISC_ROT_SPEED + seedId * 0.6f;
        // 缓慢呼吸（尺寸与亮度同步微动）
        float breath = 0.92f + 0.08f * Mth.sin(time * DISC_BREATH_SPEED + seedId * 0.4f);
        // 极缓慢的上下浮动，避免像贴图一样死板
        float bob = Mth.sin(time * 0.45f + seedId * 0.3f) * 0.07f;

        float scale = resonance ? RESONANCE_SCALE : 1f;
        float alphaMul = resonance ? RESONANCE_ALPHA_MUL : 1f;
        double radius = width * DISC_RADIUS_FACTOR * scale * breath;
        if (radius <= 0.02) {
            return;
        }
        float discY = cyFoot + height * DISC_HEIGHT_FACTOR + bob;

        int segments = VisualLod.scaleSegments(DISC_SEGMENTS, DISC_SEGMENTS_MIN, detail);

        // ===== 球体：逐顶点兰伯特着色，月相由光源方向决定（详见 bbSphere 注释）=====
        // 暗月画的是【亏凸月】，故光源明显偏侧；共鸣时转向近正照、接近满月
        int rings = VisualLod.scaleSegments(SPHERE_RINGS, SPHERE_RINGS_MIN, detail);
        float lu = resonance ? LIGHT_U_RESONANCE : LIGHT_U_GIBBOUS;
        float lw = resonance ? LIGHT_W_RESONANCE : LIGHT_W_GIBBOUS;
        bbSphere(b, m, cx, discY, cz, radius, rings, segments,
                rgX, rgY, rgZ, upX, upY, upZ, C_MOON_CORE,
                lu, LIGHT_V, lw, MOON_AMBIENT,
                clamp01(DISC_FILL_ALPHA * alphaMul * breath));

        // ===== 月海：盘面上几块稍暗的圆斑，「像月亮」最关键的一笔 =====
        // 刻意不随符文环自转——真实月球潮汐锁定、月面朝向恒定
        int mariaSegs = Math.max(6, segments / 3);
        int mariaCount = MOON_MARIA.length / 3;
        int mariaDrawn = VisualLod.scale(mariaCount, detail);
        int mariaStep = Math.max(1, mariaCount / mariaDrawn);
        for (int i = 0; i < mariaCount; i += mariaStep) {
            double mu = MOON_MARIA[i * 3] * radius;
            double mv = MOON_MARIA[i * 3 + 1] * radius;
            double mr = MOON_MARIA[i * 3 + 2] * radius;
            // 该斑块所在球面点的亮度——与球体本身用同一光源，暗侧的月海自然融进阴影
            float nu = MOON_MARIA[i * 3];
            float nv = MOON_MARIA[i * 3 + 1];
            float shade = sphereShade(nu, nv, lu, LIGHT_V, lw, MOON_AMBIENT);
            // 球面透视：越靠边缘的斑块视觉上越窄
            float edgeSquash = Mth.sqrt(Math.max(0f, 1f - nu * nu - nv * nv));
            float mx = cx + rgX * (float) mu + upX * (float) mv;
            float my = discY + rgY * (float) mu + upY * (float) mv;
            float mz = cz + rgZ * (float) mu + upZ * (float) mv;
            bbDisc(b, m, mx, my, mz, mr * (0.55 + 0.45 * edgeSquash), mariaSegs,
                    rgX, rgY, rgZ, upX, upY, upZ,
                    C_MOON_DEEP, MARIA_ALPHA * shade * breath, 0f);
        }

        // ===== 月晕：球体外圈一层向外渐隐的柔光 =====
        // 不画硬轮廓线——那会把球压回成一枚有边框的硬币，破坏刚建立起来的立体感
        bbRing(b, m, cx, discY, cz, radius, radius * 1.22, segments, 0f,
                rgX, rgY, rgZ, upX, upY, upZ, C_MOON_GLOW,
                clamp01(HALO_ALPHA * alphaMul * breath), 0f);

        // ===== 共鸣光晕环：普通状态没有，是最直接的辨识差异 =====
        if (resonance) {
            double haloR = radius * RESONANCE_HALO_FACTOR;
            double haloHalf = radius * RESONANCE_HALO_HALF_RATIO;
            bbRing(b, m, cx, discY, cz, haloR - haloHalf, haloR + haloHalf, segments, 0f,
                    rgX, rgY, rgZ, upX, upY, upZ, C_MOON_GLOW,
                    RESONANCE_HALO_ALPHA * breath, RESONANCE_HALO_ALPHA * breath);
        }

        // ===== 外缘刻度：均布角度，按步长抽取（截断会让刻度只剩一段圆弧）=====
        if (VisualLod.keepLayer(detail, TICK_KEEP_THRESHOLD)) {
            int drawn = VisualLod.scale(TICK_COUNT, detail);
            int step = Math.max(1, TICK_COUNT / drawn);
            double rStart = radius * TICK_START_RATIO;
            double rEnd = rStart + radius * TICK_LENGTH_RATIO;
            double tickHalf = radius * 0.03;
            for (int i = 0; i < TICK_COUNT; i += step) {
                // 角度基准用原始 TICK_COUNT，保证保留刻度的方位与全细节时一致
                float a = rot + TAU * i / TICK_COUNT;
                float ca = Mth.cos(a);
                float sa = Mth.sin(a);
                bbLine(b, m, cx, discY, cz, rgX, rgY, rgZ, upX, upY, upZ,
                        rStart * ca, rStart * sa, rEnd * ca, rEnd * sa,
                        tickHalf, C_MOON_GLOW, TICK_ALPHA * alphaMul * breath, 0f);
            }
        }
    }

    // ==================== 周身月尘 ====================

    /**
     * 极缓慢上升的冷蓝月尘：自脚下生成，缓缓上飘至头顶以上后淡出。
     * <p>
     * 上升速度只有本模组其它上升类元素的几分之一——「慢」与暗月的静谧气质一致，
     * 也与切腹血刃的急促上冲形成对比（二者色调也完全不同，不会混淆）。
     * </p>
     * <p>
     * <b>数量按细节系数缩放。</b>月尘位置由 {@code seedFor(entityId, i)} 决定，
     * 且角度是纯随机（与下标无关），截断尾部时保留月尘的轨迹完全不变，
     * 靠近时是「逐渐多几粒」而非重新洗牌。
     * </p>
     *
     * @param resonance 是否触发月之共鸣（增加数量与亮度）
     */
    private static void drawMoonDust(BufferBuilder b, Matrix4f m,
                                     float cx, float cyFoot, float cz,
                                     float width, float height,
                                     float time, int seedId, boolean resonance,
                                     float rightX, float rightY, float rightZ,
                                     float upX, float upY, float upZ, float detail) {
        int baseCount = DUST_COUNT + (resonance ? DUST_RESONANCE_EXTRA : 0);
        int count = VisualLod.scale(baseCount, detail);
        int segments = VisualLod.scaleSegments(DUST_SEGMENTS, DUST_SEGMENTS_MIN, detail);
        float alphaMul = resonance ? RESONANCE_ALPHA_MUL : 1f;
        float riseHeight = height * DUST_RISE_HEIGHT_FACTOR;
        float spread = width * DUST_SPREAD_FACTOR;

        for (int i = 0; i < count; i++) {
            long s = seedFor(seedId, i + 300);
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
            float swirl = (rngFloat(s) - 0.5f) * 1.2f;

            float t = frac(time * DUST_RISE_SPEED + phase);

            // 包络：底部淡入、顶部淡出
            float env;
            if (t < 0.15f) {
                env = t / 0.15f;
            } else if (t > 0.6f) {
                env = 1f - (t - 0.6f) / 0.4f;
            } else {
                env = 1f;
            }
            if (env <= 0f) {
                continue;
            }

            // 极缓慢的闪烁
            float twinkle = 0.75f + 0.25f * Mth.sin(time * 1.8f + twPhase);
            float alpha = clamp01(DUST_BASE_ALPHA * env * twinkle * alphaMul);
            if (alpha <= 0.01f) {
                continue;
            }

            // 随高度轻微螺旋外散
            float curRad = spread * radFactor * (0.5f + 0.5f * t);
            float a = ang + t * swirl;
            float px = cx + (float) Math.cos(a) * curRad;
            float pz = cz + (float) Math.sin(a) * curRad;
            float py = cyFoot + t * riseHeight + Y_OFFSET;

            // 上升过程中由月华蓝转月白（越接近月轮越亮）
            float[] col = (t < 0.5f) ? C_MOON_GLOW : C_MOON_CORE;
            float size = DUST_SIZE * sizeRand * (1.1f - 0.3f * t);

            emitSoftMote(b, m, px, py, pz, size, col[0], col[1], col[2], alpha,
                    rightX, rightY, rightZ, upX, upY, upZ, segments);
        }
    }

    // ==================== 脚下月光盘 ====================

    /**
     * 脚下的淡冷蓝月光盘：中心稍实、边缘渐隐，缓慢呼吸。
     * <p>
     * 作用是压住整体重量——只有头顶月轮与飘散月尘会显得上轻下空。
     * </p>
     * <p>
     * <b>这一个仍然是水平的</b>，而且应该是水平的：它表达的是「月光洒在地上」，
     * 本就该贴地。只有月轮那个「天体」才需要 billboard。
     * </p>
     *
     * @param resonance 是否触发月之共鸣（提升亮度与范围）
     */
    private static void drawMoonPool(BufferBuilder b, Matrix4f m,
                                     float cx, float cy, float cz, float width,
                                     float time, int seedId, boolean resonance, float detail) {
        float breath = 0.9f + 0.1f * Mth.sin(time * POOL_BREATH_SPEED + seedId * 0.5f);
        float scale = resonance ? RESONANCE_SCALE : 1f;
        float alphaMul = resonance ? RESONANCE_ALPHA_MUL : 1f;
        float radius = width * POOL_RADIUS_FACTOR * scale * breath;
        int segments = VisualLod.scaleSegments(POOL_SEGMENTS, POOL_SEGMENTS_MIN, detail);

        drawHorizontalDisc(b, m, cx, cy, cz, radius, segments,
                C_MOON_GLOW[0], C_MOON_GLOW[1], C_MOON_GLOW[2],
                clamp01(POOL_ALPHA * alphaMul * breath));
    }

    // ==================== billboard 几何基元（面向相机）====================

    /**
     * 面向相机的<b>着色球体</b>：用同心环带 + 逐顶点兰伯特光照，
     * 在没有任何光照管线的 {@code POSITION_COLOR} 纯顶点绘制下做出真正的球感。
     *
     * <h4>原理</h4>
     * <p>
     * 球体正对相机时轮廓是一个圆；圆内任意一点 {@code (u, v)}（归一化到单位圆）
     * 对应的球面法线为 {@code n = (u, v, sqrt(1 - u² - v²))}，第三个分量朝向相机。
     * 把它与光源方向点乘即得该点的兰伯特亮度，逐顶点写入不同颜色，
     * 就能得到球体的明暗过渡与边缘暗化——这两样正是「圆盘」与「球」的分野。
     * </p>
     *
     * <h4>为什么这比「中心亮边缘暗的径向渐变」好</h4>
     * <p>
     * 径向渐变是<b>各向同性</b>的：亮斑永远在正中心，看起来像一枚发光的硬币。
     * 兰伯特着色的亮斑<b>偏向光源一侧</b>，明暗界线是一条椭圆弧而非同心圆，
     * 大脑会立刻把它读成三维物体。
     * </p>
     *
     * <h4>月相 = 光源方向</h4>
     * <p>
     * 这是本方法最大的附带收益：<b>不再需要单独绘制月相暗面</b>。
     * {@code L = (0,0,1)}（正照）即满月；L 越偏向侧面，明暗界线越往中间推，
     * 依次经过亏凸月、半月、蛾眉月——与现实中月相的成因完全一致。
     * 暗月取 {@link #LIGHT_U_GIBBOUS} 的明显侧照得到亏凸月，
     * 共鸣时转向 {@link #LIGHT_U_RESONANCE} 的近正照、接近满月。
     * </p>
     *
     * @param radius   球体半径（格）
     * @param rings    径向环数（决定明暗过渡的平滑度，是主要顶点杠杆）
     * @param segments 每环的角度分段数
     * @param col      基色（只读；实际顶点色为基色 × 该点亮度）
     * @param lu       光源方向的 right 分量
     * @param lv       光源方向的 up 分量
     * @param lw       光源方向的「朝向相机」分量（1 = 正照 = 满月）
     * @param ambient  环境光下限（0 会让暗面纯黑、像挖了个洞；留一点更像被地球反照的月面）
     * @param alpha    整体不透明度
     */
    private static void bbSphere(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz, double radius,
                                 int rings, int segments,
                                 float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                                 float[] col, float lu, float lv, float lw,
                                 float ambient, float alpha) {
        if (alpha <= 0.004f || radius <= 1.0e-4 || rings < 1 || segments < 3) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rad = (float) radius;

        for (int i = 0; i < rings; i++) {
            float t0 = (float) i / rings;
            float t1 = (float) (i + 1) / rings;
            for (int j = 0; j < segments; j++) {
                float a0 = TAU * j / segments;
                float a1 = TAU * (j + 1) / segments;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

                // 四个角点的平面归一化坐标
                float u00 = t0 * c0, v00 = t0 * s0;
                float u01 = t0 * c1, v01 = t0 * s1;
                float u11 = t1 * c1, v11 = t1 * s1;
                float u10 = t1 * c0, v10 = t1 * s0;

                // 逐顶点兰伯特亮度
                float sh00 = sphereShade(u00, v00, lu, lv, lw, ambient);
                float sh01 = sphereShade(u01, v01, lu, lv, lw, ambient);
                float sh11 = sphereShade(u11, v11, lu, lv, lw, ambient);
                float sh10 = sphereShade(u10, v10, lu, lv, lw, ambient);

                // 平面坐标 → 世界坐标
                float x00 = cx + rgX * (u00 * rad) + upX * (v00 * rad);
                float y00 = cy + rgY * (u00 * rad) + upY * (v00 * rad);
                float z00 = cz + rgZ * (u00 * rad) + upZ * (v00 * rad);
                float x01 = cx + rgX * (u01 * rad) + upX * (v01 * rad);
                float y01 = cy + rgY * (u01 * rad) + upY * (v01 * rad);
                float z01 = cz + rgZ * (u01 * rad) + upZ * (v01 * rad);
                float x11 = cx + rgX * (u11 * rad) + upX * (v11 * rad);
                float y11 = cy + rgY * (u11 * rad) + upY * (v11 * rad);
                float z11 = cz + rgZ * (u11 * rad) + upZ * (v11 * rad);
                float x10 = cx + rgX * (u10 * rad) + upX * (v10 * rad);
                float y10 = cy + rgY * (u10 * rad) + upY * (v10 * rad);
                float z10 = cz + rgZ * (u10 * rad) + upZ * (v10 * rad);

                // 最外环稍降 alpha，柔化轮廓、避免硬锯齿边
                float aIn = alpha;
                float aOut = (i == rings - 1) ? alpha * 0.82f : alpha;

                b.vertex(m, x00, y00, z00).color(r * sh00, g * sh00, bl * sh00, aIn).endVertex();
                b.vertex(m, x01, y01, z01).color(r * sh01, g * sh01, bl * sh01, aIn).endVertex();
                b.vertex(m, x11, y11, z11).color(r * sh11, g * sh11, bl * sh11, aOut).endVertex();

                b.vertex(m, x00, y00, z00).color(r * sh00, g * sh00, bl * sh00, aIn).endVertex();
                b.vertex(m, x11, y11, z11).color(r * sh11, g * sh11, bl * sh11, aOut).endVertex();
                b.vertex(m, x10, y10, z10).color(r * sh10, g * sh10, bl * sh10, aOut).endVertex();
            }
        }
    }

    /**
     * 球面某点的兰伯特亮度系数。
     * <p>
     * 给定球体正对相机时平面内的归一化坐标 {@code (u, v)}（单位圆内），
     * 其球面法线为 {@code (u, v, sqrt(1 - u² - v²))}；与光源方向点乘、
     * 夹取到非负后，混入环境光下限即得最终亮度。
     * </p>
     *
     * @param u       平面内 right 分量（归一化，|(u,v)| ≤ 1）
     * @param v       平面内 up 分量
     * @param lu      光源方向 right 分量
     * @param lv      光源方向 up 分量
     * @param lw      光源方向「朝向相机」分量
     * @param ambient 环境光下限
     * @return 亮度系数（{@code ambient} ~ 1.0）
     */
    private static float sphereShade(float u, float v, float lu, float lv, float lw, float ambient) {
        float nzSq = 1f - u * u - v * v;
        float nz = (nzSq <= 0f) ? 0f : Mth.sqrt(nzSq);
        float lam = u * lu + v * lv + nz * lw;
        if (lam < 0f) {
            lam = 0f;
        }
        return ambient + (1f - ambient) * lam;
    }

    /**
     * 面向相机的径向渐变圆盘。
     *
     * @param radius      半径（格）
     * @param segments    分段数
     * @param centerAlpha 中心不透明度
     * @param edgeAlpha   边缘不透明度（取 0 即完全渐隐）
     */
    private static void bbDisc(BufferBuilder b, Matrix4f m,
                               float cx, float cy, float cz, double radius, int segments,
                               float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                               float[] col, float centerAlpha, float edgeAlpha) {
        if (centerAlpha <= 0.004f && edgeAlpha <= 0.004f) {
            return;
        }
        if (radius <= 1.0e-4) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rad = (float) radius;
        float pex = 0f, pey = 0f, pez = 0f;
        for (int i = 0; i <= segments; i++) {
            float ang = TAU * i / segments;
            float ca = Mth.cos(ang) * rad;
            float sa = Mth.sin(ang) * rad;
            float ex = cx + rgX * ca + upX * sa;
            float ey = cy + rgY * ca + upY * sa;
            float ez = cz + rgZ * ca + upZ * sa;
            if (i > 0) {
                b.vertex(m, cx, cy, cz).color(r, g, bl, centerAlpha).endVertex();
                b.vertex(m, pex, pey, pez).color(r, g, bl, edgeAlpha).endVertex();
                b.vertex(m, ex, ey, ez).color(r, g, bl, edgeAlpha).endVertex();
            }
            pex = ex;
            pey = ey;
            pez = ez;
        }
    }

    /**
     * 面向相机的圆环带（annulus），内外边缘可分别指定 alpha。
     *
     * @param rInner   内半径
     * @param rOuter   外半径
     * @param segments 分段数
     * @param rot      整环旋转角（弧度）
     */
    private static void bbRing(BufferBuilder b, Matrix4f m,
                               float cx, float cy, float cz,
                               double rInner, double rOuter, int segments, float rot,
                               float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                               float[] col, float alphaInner, float alphaOuter) {
        if (rOuter <= rInner || segments < 3) {
            return;
        }
        if (alphaInner <= 0.004f && alphaOuter <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float ri = (float) rInner, ro = (float) rOuter;
        float prevCos = Mth.cos(rot);
        float prevSin = Mth.sin(rot);
        for (int i = 1; i <= segments; i++) {
            float a = rot + TAU * i / segments;
            float ca = Mth.cos(a);
            float sa = Mth.sin(a);

            float ox0 = cx + (rgX * prevCos + upX * prevSin) * ro;
            float oy0 = cy + (rgY * prevCos + upY * prevSin) * ro;
            float oz0 = cz + (rgZ * prevCos + upZ * prevSin) * ro;
            float ox1 = cx + (rgX * ca + upX * sa) * ro;
            float oy1 = cy + (rgY * ca + upY * sa) * ro;
            float oz1 = cz + (rgZ * ca + upZ * sa) * ro;
            float ix0 = cx + (rgX * prevCos + upX * prevSin) * ri;
            float iy0 = cy + (rgY * prevCos + upY * prevSin) * ri;
            float iz0 = cz + (rgZ * prevCos + upZ * prevSin) * ri;
            float ix1 = cx + (rgX * ca + upX * sa) * ri;
            float iy1 = cy + (rgY * ca + upY * sa) * ri;
            float iz1 = cz + (rgZ * ca + upZ * sa) * ri;

            b.vertex(m, ox0, oy0, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ox1, oy1, oz1).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, iy1, iz1).color(r, g, bl, alphaInner).endVertex();

            b.vertex(m, ox0, oy0, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, iy1, iz1).color(r, g, bl, alphaInner).endVertex();
            b.vertex(m, ix0, iy0, iz0).color(r, g, bl, alphaInner).endVertex();

            // 推进到下一段：本段末端即下一段起点（漏掉这两行会让整环塌成一个扇形）
            prevCos = ca;
            prevSin = sa;
        }
    }

    /**
     * 在面向相机的平面内绘制一条带宽度的线段（用平面二维坐标表达端点）。
     *
     * @param px1 起点在平面内的 right 分量
     * @param py1 起点在平面内的 up 分量
     * @param px2 终点在平面内的 right 分量
     * @param py2 终点在平面内的 up 分量
     * @param hw  线半宽
     */
    private static void bbLine(BufferBuilder b, Matrix4f m,
                               float cx, float cy, float cz,
                               float rgX, float rgY, float rgZ, float upX, float upY, float upZ,
                               double px1, double py1, double px2, double py2,
                               double hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        double ddx = px2 - px1;
        double ddy = py2 - py1;
        double len = Math.sqrt(ddx * ddx + ddy * ddy);
        if (len < 1.0e-6) {
            return;
        }
        // 平面内的法线 × 半宽
        double nx = -ddy / len * hw;
        double ny = ddx / len * hw;

        float r = col[0], g = col[1], bl = col[2];

        float a1u = (float) (px1 + nx), a1w = (float) (py1 + ny);
        float a2u = (float) (px1 - nx), a2w = (float) (py1 - ny);
        float b1u = (float) (px2 + nx), b1w = (float) (py2 + ny);
        float b2u = (float) (px2 - nx), b2w = (float) (py2 - ny);

        float ax1 = cx + rgX * a1u + upX * a1w;
        float ay1 = cy + rgY * a1u + upY * a1w;
        float az1 = cz + rgZ * a1u + upZ * a1w;
        float ax2 = cx + rgX * a2u + upX * a2w;
        float ay2 = cy + rgY * a2u + upY * a2w;
        float az2 = cz + rgZ * a2u + upZ * a2w;
        float bx1 = cx + rgX * b1u + upX * b1w;
        float by1 = cy + rgY * b1u + upY * b1w;
        float bz1 = cz + rgZ * b1u + upZ * b1w;
        float bx2 = cx + rgX * b2u + upX * b2w;
        float by2 = cy + rgY * b2u + upY * b2w;
        float bz2 = cz + rgZ * b2u + upZ * b2w;

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, by1, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, ay2, az2).color(r, g, bl, a1).endVertex();
    }


    // ==================== 水平几何基元 ====================

    /**
     * 水平径向渐变圆盘（中心 alpha、边缘 0）。
     * <p>只有脚下月光盘用它——那个表达的是「月光洒在地上」，本就该贴地。
     * 月轮请用 {@link #bbDisc}。</p>
     */
    private static void drawHorizontalDisc(BufferBuilder b, Matrix4f m,
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

    /**
     * 绘制一颗面向相机的柔和圆形光点（径向渐变：中心 alpha、边缘 0）。
     *
     * @param segments 分段数。由调用方按细节系数传入，下限 {@link #DUST_SEGMENTS_MIN}。
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

    // ==================== 数学辅助 ====================

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
