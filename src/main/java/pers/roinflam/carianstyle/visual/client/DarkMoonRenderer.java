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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import pers.roinflam.carianstyle.utils.util.DayNightUtil;
import javax.annotation.Nullable;
import java.util.List;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V3)
 *
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
 *     <li><b>夜晚</b> —— 用 {@code DayNightUtil.isDay} 按世界时间与天气现算（原版 {@code level.isDay()}
 *         在客户端永远是白天，见该类说明）；</li>
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
 * @author RoinFlam
 * @version 3.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class DarkMoonRenderer {
    private static final String DARK_MOON_ID = "dark_moon";
    private static final String FULL_MOON_ID = "full_moon";
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float PI = (float) Math.PI;
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int DISC_SEGMENTS_MIN = 14;
    private static final int POOL_SEGMENTS_MIN = 8;
    private static final int DUST_SEGMENTS_MIN = 4;
    private static final float TICK_KEEP_THRESHOLD = 0.5f;
    private static final float DUST_KEEP_THRESHOLD = 0.45f;
    private static final int MOON_CORE = 0xF0F6FF;
    private static final int MOON_GLOW = 0xA8C4F0;
    private static final int MOON_DEEP = 0x3F5C99;
    private static final float[] C_MOON_CORE = VisualColor.constant(MOON_CORE);
    private static final float[] C_MOON_GLOW = VisualColor.constant(MOON_GLOW);
    private static final float[] C_MOON_DEEP = VisualColor.constant(MOON_DEEP);
    private static final float DISC_HEIGHT_FACTOR = 1.45f;
    private static final float DISC_RADIUS_FACTOR = 0.62f;
    private static final int DISC_SEGMENTS = 32;
    private static final float DISC_ROT_SPEED = 0.32f;
    private static final float DISC_BREATH_SPEED = 0.85f;
    private static final float DISC_FILL_ALPHA = 0.62f;
    private static final int SPHERE_RINGS = 6;
    private static final int SPHERE_RINGS_MIN = 3;
    private static final float LIGHT_U_GIBBOUS = 0.62f;
    private static final float LIGHT_W_GIBBOUS = 0.72f;
    private static final float LIGHT_U_RESONANCE = 0.20f;
    private static final float LIGHT_W_RESONANCE = 0.95f;
    private static final float LIGHT_V = 0.24f;
    private static final float MOON_AMBIENT = 0.34f;
    private static final float HALO_ALPHA = 0.34f;
    private static final int TICK_COUNT = 16;
    private static final double TICK_LENGTH_RATIO = 0.18;
    private static final double TICK_START_RATIO = 1.26;
    private static final float TICK_ALPHA = 0.45f;
    private static final float[] MOON_MARIA = {
            -0.25f, 0.30f, 0.30f,
            0.28f, 0.12f, 0.22f,
            -0.05f, -0.28f, 0.26f,
            0.35f, -0.35f, 0.16f,
            -0.42f, -0.08f, 0.14f
    };
    private static final float MARIA_ALPHA = 0.34f;
    private static final float RESONANCE_SCALE = 1.35f;
    private static final float RESONANCE_ALPHA_MUL = 1.3f;
    private static final float RESONANCE_HALO_FACTOR = 1.28f;
    private static final double RESONANCE_HALO_HALF_RATIO = 0.14;
    private static final float RESONANCE_HALO_ALPHA = 0.32f;
    private static final int DUST_COUNT = 10;
    private static final int DUST_RESONANCE_EXTRA = 4;
    private static final float DUST_RISE_SPEED = 0.13f;
    private static final float DUST_RISE_HEIGHT_FACTOR = 1.25f;
    private static final float DUST_SPREAD_FACTOR = 0.55f;
    private static final float DUST_SIZE = 0.055f;
    private static final int DUST_SEGMENTS = 8;
    private static final float DUST_BASE_ALPHA = 0.7f;
    private static final int POOL_SEGMENTS = 24;
    private static final float POOL_RADIUS_FACTOR = 1.15f;
    private static final float POOL_ALPHA = 0.2f;
    private static final float POOL_BREATH_SPEED = 0.4f;
    private DarkMoonRenderer() {
    }
}

