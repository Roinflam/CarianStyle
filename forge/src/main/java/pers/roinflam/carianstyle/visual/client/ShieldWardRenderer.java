package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 护盾类附魔的<b>物品局部空间</b>特效渲染器（纯客户端自绘）。
 * <p>
 * 负责魔力盾牌（魔力蓝护鞘）与不变盾牌（黄金树金护鞘）两个演出。
 * 由 {@code MixinItemInHandLayer}（第三人称）与
 * {@code MixinItemInHandRenderer}（第一人称）共同调用，两边视觉完全一致。
 * </p>
 *
 * <h3>v5.2 改动：补两样「魔力感」</h3>
 * <p>
 * v5.1 之前的演出只有三种元素：半透明面、流动条纹、亮边框。
 * 它读作「能量护盾」是够的，但读不出<b>卡利亚辉石魔法</b>——
 * 那套东西的标志是<b>朦胧的雾</b>与<b>结晶的闪</b>，两样都缺。
 * </p>
 * <ol>
 *     <li><b>外发光晕</b>（{@link #drawHalo}）——护鞘外侧一圈向外衰减的柔边。
 *         它不覆盖盾牌本体，只在盾周围罩一层雾，因此不会吃掉盾的轮廓；</li>
 *     <li><b>辉石闪点</b>（{@link #drawSparks}）——盾面上几个位置固定、
 *         明灭相位错开的十字星，短促地亮一下再长时间暗着。
 *         <b>这是「结晶感」的唯一来源</b>，连续流动的条纹给不了：
 *         条纹表达的是「流体」，闪点表达的是「固体的棱面反光」。</li>
 * </ol>
 * <p>
 * 代价是近距离从约 40 个 quad 涨到约 60，仍比 v5.0 少六成。
 * </p>
 *
 * <h4>发光晕在远距离也画，这是刻意的</h4>
 * <p>
 * 距离分级里正面网格与闪点都被砍掉，<b>但发光晕保留</b>。
 * 它只有 8 个 quad，而远处屏幕占比很小、overdraw 可以忽略；
 * 留着它，远看是「一面裹在雾里的盾」而不是「一个线框」。
 * </p>
 * <p>
 * 若嫌远处仍然糊，把 {@link #drawWardSheath} 里 {@code drawHalo} 那行
 * 移进 {@code if (detailed)} 块即可，一行的事。
 * </p>
 *
 * <h3>配色：一层壳 + 金蓝插值</h3>
 * <ul>
 *     <li>只有魔力盾牌 —— 全程魔力蓝；</li>
 *     <li>只有不变盾牌 —— 全程黄金树金；</li>
 *     <li><b>两个都有</b> —— 配色在金与蓝之间来回插值，像呼吸灯一样缓慢切换。</li>
 * </ul>
 * <p>
 * 不变盾牌原来的「完全静止石壁」在 v5.0 被换掉了，
 * 两个附魔的区分<b>只剩颜色</b>。金与蓝色相差得够远，日常够用，
 * 但色弱或强环境光下辨识度不如从前——这是当时明确换掉的东西。
 * </p>
 *
 * <h3>性能：这是常驻开销</h3>
 * <p>
 * 触发条件是「拿在手上」而非「举盾」，因此视野里每个持附魔盾的实体每帧都要画一份。
 * 半透明的 overdraw 比顶点数更疼。当前的控制手段：
 * </p>
 * <ul>
 *     <li>没有背面网格（v5.1 去掉，它 alpha 只有 0.1 又被盾挡着）；</li>
 *     <li>辉光走顶点查表，{@link #glint} 每面盾每帧只调 {@code (COLS+1)×(ROWS+1)} 次；</li>
 *     <li>锐度硬编码成六次方（{@link #pow6}），不走 {@code Math.pow}；</li>
 *     <li>距离分级：{@link #SIMPLIFY_DISTANCE} 外去掉网格与闪点，
 *         {@link #CULL_DISTANCE} 外整个不画。</li>
 * </ul>
 * <table border="1">
 *   <caption>每面盾的 quad 数</caption>
 *   <tr><th>档位</th><th>quad</th></tr>
 *   <tr><td>近距离 / 第一人称</td><td>约 60</td></tr>
 *   <tr><td>远距离（&gt; 12 格）</td><td>20</td></tr>
 * </table>
 *
 * <h3>护鞘尺寸从哪来</h3>
 * <ul>
 *     <li><b>普通 JSON 模型</b>（{@code isCustomRenderer() == false}）——
 *         遍历 {@link BakedModel#getQuads} 的全部顶点求<b>包围盒</b>；</li>
 *     <li><b>BEWLR 模型</b>（{@code isCustomRenderer() == true}）——
 *         回落到 {@link #VANILLA_SHAPE}，即原版 {@code ShieldModel} 的实测尺寸。</li>
 * </ul>
 * <p>
 * <b>注意这是「尺寸自适应」不是「形状自适应」。</b>包围盒是个长方体，
 * 圆盾、鸢形盾套上来四角会露空；盾心凸起会撑高 {@code zMax}，
 * 让护鞘正面浮到凸起顶上而不是贴着盾面。
 * </p>
 *
 * <h4>⚠ 为什么 BEWLR 量不到</h4>
 * <p>
 * <b>原版盾牌就走 BEWLR。</b>{@code shield.json} 是 {@code builtin/entity}，
 * 没有任何 element；真正的几何在 {@code BlockEntityWithoutLevelRenderer} 里
 * 由 {@code ShieldModel} 的 {@code ModelPart} 画出来。
 * {@code ModelPart} 不是 {@code BakedQuad}，<b>{@code getQuads()} 返回空表</b>。
 * 不少模组盾牌为了复用原版格挡表现也照抄这套，同样量不到。
 * </p>
 *
 * <h4>⚠ 手柄会污染包围盒，所以 Z 轴要钳制</h4>
 * <p>
 * 原版手柄在本空间里占 {@code z ∈ [-0.3125, 0.0625]}，挡板只占
 * {@code z ∈ [0.0625, 0.125]}——直接用包围盒会得到一个深达 0.44 格的大盒子。
 * 故 X / Y 用完整包围盒，Z 只取 {@code zMax} 作正面，
 * 背面钳到 {@code zMax - }{@link #MAX_SHELL_DEPTH}。
 * </p>
 *
 * <h3>观感微调入口</h3>
 * <p>
 * 护鞘比盾大一圈 → {@link #SHELL_MARGIN} 调小（0.022 会贴得紧不少）。<br>
 * 斜看时轮廓分成两条平行线 → {@link #BACK_OUTLINE_RATIO} 设为 0。<br>
 * 雾太浓 / 太淡 → {@link #HALO_ALPHA}、{@link #HALO_WIDTH}。<br>
 * 闪点太密 / 太稀 → {@link #SPARK_COUNT}、{@link #SPARK_PERIOD_SECONDS}。<br>
 * 还要再省 → {@link #SIMPLIFY_DISTANCE} 调小，或 {@link #FACE_COLS} / {@link #FACE_ROWS} 再降。
 * </p>
 *
 * @author FlameForge
 * @version 5.2
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ShieldWardRenderer {

    /** 魔力盾牌附魔的注册 id */
    private static final String SCHOLAR_SHIELD_ID = "scholar_shield";
    /** 不变盾牌附魔的注册 id */
    private static final String IMMUTABLE_SHIELD_ID = "immutable_shield";

    private static final float TAU = (float) (Math.PI * 2.0);

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    // ==================== 距离分级 ====================

    /**
     * 超过这个距离（格）就只画发光晕、侧缘与轮廓框，去掉正面网格与闪点。
     * <p>
     * 正面网格是整个演出里最贵的部分（顶点最多、overdraw 最重），
     * 而它在远处本来就糊成一片；闪点在远处则细到看不见，画了也白画。
     * 亮线与发光晕留着——远距离恰恰是靠它们在传达「带附魔、什么颜色」。
     * </p>
     * <p>取 12 格：近战交手距离之内保持完整效果，之外降级。</p>
     */
    private static final float SIMPLIFY_DISTANCE = 12f;
    /** {@link #SIMPLIFY_DISTANCE} 的平方，用于免开方比较 */
    private static final double SIMPLIFY_DISTANCE_SQR = SIMPLIFY_DISTANCE * SIMPLIFY_DISTANCE;

    /**
     * 超过这个距离（格）整个特效不画。
     * <p>取 40 格——再远轮廓线也只有亚像素宽。
     * 注意这只是特效自己的剔除，实体本身的视距由原版控制。</p>
     */
    private static final float CULL_DISTANCE = 40f;
    /** {@link #CULL_DISTANCE} 的平方，用于免开方比较 */
    private static final double CULL_DISTANCE_SQR = CULL_DISTANCE * CULL_DISTANCE;

    // ==================== 护鞘外形 ====================

    /**
     * 外形描述符的字段下标：{@code {中心 X, 中心 Y, 半宽, 半高, 正面 Z, 背面 Z}}，单位为格。
     * <p>用定长数组而非小对象，是因为它每帧被读取、且缓存里可能有几十份。</p>
     */
    private static final int SHAPE_CX = 0;
    private static final int SHAPE_CY = 1;
    private static final int SHAPE_HALF_W = 2;
    private static final int SHAPE_HALF_H = 3;
    private static final int SHAPE_FRONT_Z = 4;
    private static final int SHAPE_BACK_Z = 5;
    private static final int SHAPE_LENGTH = 6;

    /**
     * 原版 {@code ShieldModel} 的实测外形，用作 BEWLR 模型（含原版盾牌本身）的回落值。
     * <p>
     * 推导自 {@code ShieldModel.createLayer()} 的
     * {@code plate: addBox(-6, -11, -2, 12, 22, 1)}：
     * {@code ModelPart.Cube} 写顶点时统一 {@code /16}，
     * 而 {@code BlockEntityWithoutLevelRenderer.renderByItem} 渲染盾牌前会
     * {@code poseStack.scale(1, -1, -1)} 把 Z 取反。两步之后挡板占据
     * {@code x ∈ [-0.375, 0.375]}、{@code y ∈ [-0.6875, 0.6875]}、
     * {@code z ∈ [0.0625, 0.125]}。
     * </p>
     */
    private static final float[] VANILLA_SHAPE = {
            0f,          // 中心 X
            0f,          // 中心 Y
            6f / 16f,    // 半宽   0.375
            11f / 16f,   // 半高   0.6875
            2f / 16f,    // 正面 Z 0.125
            1f / 16f     // 背面 Z 0.0625
    };

    /**
     * 从包围盒推算护鞘时，允许的最大厚度（格）。
     * <p>用来把手柄从 Z 方向的包围盒里排除掉。
     * 取 3/16——比原版挡板的 1/16 宽裕，容得下做得厚一些的模组盾牌，
     * 又远小于原版手柄的 5/16，能有效切掉它。</p>
     */
    private static final float MAX_SHELL_DEPTH = 3f / 16f;

    /** 包围盒退化（近乎为零）的判定阈值（格），低于此值视为量测失败 */
    private static final float DEGENERATE_EPSILON = 1.0e-4f;

    /**
     * 护鞘相对盾牌表面向外扩出的余量（格）：展开完成时的最终厚度。
     * <p>取 0.038（约 0.6 个模型单位）。<b>嫌护鞘比盾大一圈就调小这个值</b>，
     * 0.022 左右会贴得紧不少。</p>
     */
    private static final float SHELL_MARGIN = 0.038f;

    /**
     * 展开度为 0 时仍保留的最小余量比例。
     * <p>纯 0 会让护鞘与盾面完全共面、产生 z-fighting。</p>
     */
    private static final float MARGIN_FLOOR_RATIO = 0.18f;

    // ==================== 面网格细分 ====================

    /** 前面横向网格段数 */
    private static final int FACE_COLS = 4;
    /** 前面纵向网格段数（盾是竖长的，纵向多分几段让渐变更均匀） */
    private static final int FACE_ROWS = 7;

    /**
     * 辉光强度查表的格点数：网格段数各加一。
     * <p>相邻网格块共享边，若每块各算四角，共享顶点会被重复计算——
     * {@code 4×7} 的网格有 28 块 × 4 角 = 112 次调用，而格点只有 {@code 5×8 = 40} 个。
     * 先填表再查，{@link #glint} 的调用次数直接降到格点数。</p>
     */
    private static final int GLINT_GRID_COLS = FACE_COLS + 1;
    private static final int GLINT_GRID_ROWS = FACE_ROWS + 1;

    // ==================== 登场动画 ====================

    /** 切出盾牌后展开到满所需时间（秒） */
    private static final float APPEAR_SECONDS = 0.3f;
    /** 单帧允许推进的最大时间（秒）：防止卡顿后一帧跳完动画 */
    private static final float MAX_STEP_SECONDS = 0.1f;

    // ==================== 配色 ====================

    /** 魔力白心：辉光条纹、闪点与轮廓高光 */
    private static final int MANA_CORE = 0xE4F2FF;
    /** 魔力蓝：主色。比卡利亚辉石蓝 0x8FD2FF 更深更饱和 */
    private static final int MANA_MAIN = 0x4E9BFF;

    /** 黄金白心：偏暖的高光，不用纯白，否则和魔力那套的白心分不开 */
    private static final int GOLD_CORE = 0xFFF3D2;
    /** 黄金树金：主色 */
    private static final int GOLD_MAIN = 0xF0C246;

    /**
     * 金蓝切换的完整周期（秒）：金 → 蓝 → 金算一轮。
     * <p>取 5 秒——呼吸灯的节奏，慢到不抢注意力，又快到能看出它在变。</p>
     */
    private static final float SWITCH_PERIOD_SECONDS = 5f;
    /** 由周期换算出的角速度（弧度/秒） */
    private static final float SWITCH_OMEGA = TAU / SWITCH_PERIOD_SECONDS;

    // ==================== 不透明度与线宽 ====================

    /** 正面底色不透明度（不含辉光条纹） */
    private static final float FACE_ALPHA = 0.2f;
    /** 侧缘带不透明度：整个演出里最亮的一圈，「包住」的观感全靠它 */
    private static final float RIM_ALPHA = 0.55f;
    /** 侧缘后缘相对前缘的亮度比：前亮后暗，侧看才有光泽 */
    private static final float RIM_BACK_RATIO = 0.35f;
    /** 轮廓亮线不透明度 */
    private static final float OUTLINE_ALPHA = 0.85f;
    /**
     * 背面轮廓框相对正面的亮度比。
     * <p>第一人称大角度斜看时，前后两圈会分离成两条平行亮线。
     * 不想要这个观感就把本值设为 0，背面那圈直接不画。</p>
     */
    private static final float BACK_OUTLINE_RATIO = 0.5f;
    /** 轮廓线半宽（格） */
    private static final float OUTLINE_HALF = 0.016f;

    // ==================== 外发光晕（v5.2）====================

    /**
     * 发光晕向外延伸的宽度（格）。
     * <p>取 0.07——约一个模型单位多一点。比这再宽就不像「贴着盾的雾」
     * 而像「盾外面另有一层东西」了。</p>
     */
    private static final float HALO_WIDTH = 0.07f;
    /**
     * 发光晕贴着护鞘那一侧的不透明度，向外线性衰减到 0。
     * <p>刻意压得比侧缘低不少：它的作用是<b>softening</b>，
     * 让盾的边界不那么硬，而不是再加一圈亮边。调高会盖掉盾牌本体的轮廓。</p>
     */
    private static final float HALO_ALPHA = 0.3f;

    // ==================== 辉石闪点（v5.2）====================

    /**
     * 盾面上的闪点数量。
     * <p>取 3——多了就变成满天星，反而失去「偶尔闪一下」的贵重感。</p>
     */
    private static final int SPARK_COUNT = 3;
    /** 单个闪点的臂长（格） */
    private static final float SPARK_ARM = 0.075f;
    /** 闪点线半宽（格） */
    private static final float SPARK_HALF = 0.008f;
    /** 闪点峰值亮度 */
    private static final float SPARK_ALPHA = 0.95f;
    /**
     * 闪点完整明灭周期（秒）。
     * <p>亮度用「正弦的正半周再取六次方」，因此一个周期里只有很短一段是亮的，
     * 其余时间几乎全暗——短促地闪一下，而不是连续呼吸。</p>
     */
    private static final float SPARK_PERIOD_SECONDS = 1.9f;
    /** 由周期换算出的角速度（弧度/秒） */
    private static final float SPARK_OMEGA = TAU / SPARK_PERIOD_SECONDS;
    /**
     * 闪点位置相对护鞘半宽 / 半高的收缩系数。
     * <p>不收的话闪点会骑在轮廓线上，十字星的臂会戳出盾外。</p>
     */
    private static final float SPARK_INSET = 0.72f;

    // ==================== 辉光条纹 ====================

    /** 辉光条纹的空间周期（格）：越小条纹越密 */
    private static final float GLINT_PERIOD = 0.62f;
    /** 辉光条纹流动速度（周期/秒） */
    private static final float GLINT_SPEED = 0.42f;
    /** 第二层条纹的相对速度：与第一层不成整数比，叠出来才不像单调的扫描线 */
    private static final float GLINT_SPEED_2 = -0.27f;
    /** 条纹峰值亮度 */
    private static final float GLINT_STRENGTH = 0.75f;
    /** 第二层条纹的权重 */
    private static final float GLINT_SECOND_WEIGHT = 0.6f;
    /** 条纹强度低于此值的网格块不提交顶点 */
    private static final float GLINT_CUTOFF = 0.03f;
    /** 边缘呼吸速度（与金蓝切换无关，这是亮度上的轻微起伏） */
    private static final float BREATH_SPEED = 1.5f;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定：类加载时解包一次、此后永久复用的常量颜色数组。
    private static final float[] C_MANA_CORE = VisualColor.constant(MANA_CORE);
    private static final float[] C_MANA_MAIN = VisualColor.constant(MANA_MAIN);
    private static final float[] C_GOLD_CORE = VisualColor.constant(GOLD_CORE);
    private static final float[] C_GOLD_MAIN = VisualColor.constant(GOLD_MAIN);

    // ===== 每帧复用的暂存区（⚠ 可写）=====
    // 绘制只在渲染线程发生，且每次绘制内部读完就用完，不存在跨帧持有，
    // 因此复用同一组数组即可，避免每帧的数组分配。

    /** 金蓝插值后的高光色 */
    private static final float[] MIX_CORE = new float[3];
    /** 金蓝插值后的主色 */
    private static final float[] MIX_MAIN = new float[3];
    /**
     * 辉光强度查表。索引 {@code row * GLINT_GRID_COLS + col}。
     * <p>见 {@link #GLINT_GRID_COLS} 的说明。</p>
     */
    private static final float[] GLINT_TABLE = new float[GLINT_GRID_COLS * GLINT_GRID_ROWS];

    /** 魔力盾牌附魔懒解析缓存 */
    private static Enchantment scholarCache;
    private static boolean scholarResolved;
    /** 不变盾牌附魔懒解析缓存 */
    private static Enchantment immutableCache;
    private static boolean immutableResolved;

    /**
     * 外形量测缓存。按 {@link BakedModel} 的<b>身份</b>索引——
     * 烘焙后的模型实例在资源包重载前是稳定的，用 {@code IdentityHashMap}
     * 既避免调用可能很昂贵的 {@code equals}，也不会误合并两个内容相同的模型。
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final Map<BakedModel, float[]> SHAPE_CACHE = new IdentityHashMap<>();

    /**
     * 外形缓存的容量上限。
     * <p>资源包重载（F3+T）会重新烘焙出全新的 {@code BakedModel} 实例，
     * 旧条目再也不会被命中却仍被 map 强引用着。超过上限直接整表清空，下一帧重新量即可。</p>
     */
    private static final int MAX_SHAPE_CACHE = 64;

    /**
     * 量测 {@code getQuads} 时传入的随机源。
     * <p>复用一个实例即可，不需要每次新建。</p>
     */
    private static final RandomSource SHAPE_RANDOM = RandomSource.create();

    /**
     * 每面盾的展开状态。
     * <p>
     * <b>键是「实体 id × 16 + 显示上下文序号」而不是单纯的实体 id</b>：
     * 主手与副手可能<b>同时</b>各挂一面附魔盾，两者在同一帧里都会走到这里。
     * 若共用一个状态条目，每帧会被推进两次，展开速度平白翻倍。
     * {@code ItemDisplayContext} 的序号恒小于 16，故乘 16 后不会与相邻实体串号。
     * </p>
     * <p>仅渲染线程访问，无并发问题。由 {@link #onClientTick} 定期清理过期条目。</p>
     */
    private static final Map<Integer, WardState> STATE = new HashMap<>();

    /** 状态条目多久没被访问就算过期（毫秒） */
    private static final long STATE_EXPIRY_MS = 3000L;

    /**
     * 一面盾的展开状态。
     */
    private static final class WardState {
        /** 展开度 0~1 */
        float value;
        /** 上次更新的墙钟毫秒 */
        long lastUpdate;
    }

    private ShieldWardRenderer() {
    }

    /**
     * 由 Mixin 调用：若该物品带护盾类附魔，就在物品局部空间画上包住盾牌的护鞘。
     * <p>
     * <b>不要求正在举盾</b>——带附魔且拿在手上即可。
     * 「拿在手上」这个条件本身由调用点保证：两个 Mixin 分别注入第一 / 第三人称的
     * 手持物品渲染，物品栏、掉落物、展示框等其它渲染路径根本不会调到这里。
     * </p>
     * <p>
     * <b>调用时 PoseStack 必须仍处于「手臂空间」</b>
     * （两个 Mixin 都注入在 {@code PoseStack.popPose()} 之前）。
     * 内部会自己套用物品模型变换，并自行保证 push / pop 配平。
     * </p>
     *
     * @param entity         持有者
     * @param stack          正在渲染的物品
     * @param displayContext 显示上下文（第一 / 第三人称、左 / 右手），用于取正确的模型变换
     * @param leftHand       是否为左手（模型变换需要据此镜像）
     * @param poseStack      当前姿态栈（手臂空间）
     * @param buffer         缓冲源
     */
    public static void renderOnItem(LivingEntity entity, ItemStack stack,
                                    ItemDisplayContext displayContext, boolean leftHand,
                                    PoseStack poseStack, MultiBufferSource buffer) {
        if (stack.isEmpty() || !stack.isEnchanted()) {
            return;
        }

        // 判定这面盾带的是哪一种（可能两个都带）
        Enchantment scholar = resolveScholar();
        Enchantment immutable = resolveImmutable();
        boolean hasScholar = scholar != null
                && EnchantmentHelper.getItemEnchantmentLevel(scholar, stack) > 0;
        boolean hasImmutable = immutable != null
                && EnchantmentHelper.getItemEnchantmentLevel(immutable, stack) > 0;
        if (!hasScholar && !hasImmutable) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        // ===== 距离分级 =====
        // 第一人称是玩家自己的盾，看得最清楚，永远走完整档
        boolean firstPerson = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean detailed = true;
        if (!firstPerson) {
            double distSqr = cameraDistanceSqr(mc, entity);
            if (distSqr > CULL_DISTANCE_SQR) {
                // 太远，整个不画。注意此处提前返回不会推进状态机——
                // 条目会自然过期，重新靠近时从头展开一次，这是可以接受的
                return;
            }
            detailed = distSqr <= SIMPLIFY_DISTANCE_SQR;
        }

        // 状态键：实体与手必须分开计数，否则双持附魔盾时展开速度翻倍
        int stateKey = entity.getId() * 16 + displayContext.ordinal();
        float value = advanceState(stateKey);
        if (value <= 0.004f) {
            return;
        }

        BakedModel model = mc.getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
        if (model == null) {
            return;
        }
        float[] shape = resolveShape(model);

        poseStack.pushPose();
        try {
            // 与原版 ItemRenderer.render 的前两步完全一致（BEWLR 路径同样走这两步）。
            // ⚠ translate(-0.5,-0.5,-0.5) 是把模型「居中到原点」——
            // 此后模型占据 [-0.5, 0.5]³，中心在 (0,0,0)，不是 (0.5,0.5,0.5)
            model.applyTransform(displayContext, poseStack, leftHand);
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            // 再平移到盾牌自身的几何中心。这样下面所有绘制都能按「以原点为中心」来写，
            // 不必给每个基元都传一遍中心偏移
            poseStack.translate(shape[SHAPE_CX], shape[SHAPE_CY], 0f);

            VertexConsumer vc = buffer.getBuffer(CarianStyleRenderTypes.SHIELD_WARD);
            Matrix4f matrix = poseStack.last().pose();
            float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;
            int seedId = entity.getId();

            // 一层壳，配色由两个附魔的组合决定
            float blend = resolveBlend(hasScholar, hasImmutable, time, seedId);
            mixPalette(blend);
            drawWardSheath(vc, matrix, shape, value, time, seedId, detailed);
        } finally {
            // 无论绘制中途发生什么都必须配平，否则整个实体渲染的姿态栈会错乱
            poseStack.popPose();
        }
    }

    /**
     * 求实体到相机的距离平方（格）。
     * <p>用平方比较可以省掉每帧的开方；本方法只用于分级判定，不需要真实距离。</p>
     *
     * @param mc     客户端实例
     * @param entity 持有者
     * @return 距离平方；取不到相机时返回 0（保守地按最近处理，宁可多画不可漏画）
     */
    private static double cameraDistanceSqr(Minecraft mc, LivingEntity entity) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return 0.0;
        }
        Vec3 pos = camera.getPosition();
        return entity.distanceToSqr(pos.x, pos.y, pos.z);
    }

    // ==================== 配色插值 ====================

    /**
     * 求当前的金蓝混合度。
     * <p>
     * <b>只有两个附魔同时存在时才会随时间变化。</b>单独装备任何一个都是恒定色——
     * 让单附魔也呼吸变色会白白牺牲「一眼认出是哪个附魔」的能力。
     * </p>
     * <p>
     * 正弦本身在两端就比中间停留得久（端点处导数为零），再叠一层 smoothstep
     * 把这个特性放大：金和蓝各自停留更久，中间那段脏兮兮的青绿色过渡尽快掠过。
     * </p>
     * <p>
     * 相位混入实体 id，各个玩家的切换不同步——否则一群人举盾时会齐刷刷一起变色，
     * 像同一个开关控制的，很假。
     * </p>
     *
     * @param hasScholar   是否带魔力盾牌
     * @param hasImmutable 是否带不变盾牌
     * @param time         动画时间（秒）
     * @param seedId       实体网络 id（错开各实体的切换相位）
     * @return 0 = 纯魔力蓝，1 = 纯黄金树金
     */
    private static float resolveBlend(boolean hasScholar, boolean hasImmutable,
                                      float time, int seedId) {
        if (!hasScholar) {
            return 1f;
        }
        if (!hasImmutable) {
            return 0f;
        }
        float t = 0.5f + 0.5f * Mth.sin(time * SWITCH_OMEGA + seedId * 0.41f);
        // smoothstep：在金与蓝两端多停留，中间过渡更干脆
        return t * t * (3f - 2f * t);
    }

    /**
     * 按混合度把配色插值进暂存区 {@link #MIX_CORE} / {@link #MIX_MAIN}。
     * <p>必须在每次绘制前调用——暂存区是复用的，里面留的是上一次的结果。</p>
     *
     * @param blend 0 = 纯魔力蓝，1 = 纯黄金树金
     */
    private static void mixPalette(float blend) {
        lerpColor(MIX_CORE, C_MANA_CORE, C_GOLD_CORE, blend);
        lerpColor(MIX_MAIN, C_MANA_MAIN, C_GOLD_MAIN, blend);
    }

    /**
     * 逐分量线性插值两个颜色。
     * <p>在线性 RGB 上插值即可：金与蓝虽然色相跨度大，但中间那段本来就要快速掠过
     * （见 {@link #resolveBlend} 的 smoothstep），不值得为它引入 HSV 转换的开销。</p>
     *
     * @param dst 结果暂存区（会被覆写）
     * @param a   {@code t = 0} 时的颜色（只读）
     * @param b   {@code t = 1} 时的颜色（只读）
     * @param t   混合度 0~1
     */
    private static void lerpColor(float[] dst, float[] a, float[] b, float t) {
        dst[0] = a[0] + (b[0] - a[0]) * t;
        dst[1] = a[1] + (b[1] - a[1]) * t;
        dst[2] = a[2] + (b[2] - a[2]) * t;
    }

    // ==================== 外形量测 ====================

    /**
     * 取某个烘焙模型的护鞘外形，量测结果按模型身份缓存。
     * <p>
     * 走 {@link #VANILLA_SHAPE} 回落的两种情形：模型是 BEWLR（量不到顶点），
     * 或量出来的包围盒退化（模型为空、只有零面积的面）。
     * </p>
     *
     * @param model 当前物品的烘焙模型
     * @return 外形描述符（只读，长度 {@link #SHAPE_LENGTH}）
     */
    private static float[] resolveShape(BakedModel model) {
        float[] cached = SHAPE_CACHE.get(model);
        if (cached != null) {
            return cached;
        }
        float[] shape;
        if (model.isCustomRenderer()) {
            // BEWLR：几何在 ModelPart 里，getQuads 返回空表，量不到
            shape = VANILLA_SHAPE;
        } else {
            float[] measured = measureShape(model);
            shape = (measured != null) ? measured : VANILLA_SHAPE;
        }
        // 资源包重载会产生全新实例，旧条目永不命中；超过上限直接清空重来
        if (SHAPE_CACHE.size() >= MAX_SHAPE_CACHE) {
            SHAPE_CACHE.clear();
        }
        SHAPE_CACHE.put(model, shape);
        return shape;
    }

    /**
     * 遍历模型的全部四边形，求出护鞘外形。
     * <p>
     * 顶点坐标在方块模型空间 {@code [0, 1]³} 内，而本渲染器工作在
     * {@code applyTransform + translate(-0.5,-0.5,-0.5)} 之后的居中空间，
     * 故量完统一减去 0.5。
     * </p>
     * <p>
     * <b>本方法每个模型只跑一次</b>（结果由 {@link #resolveShape} 缓存），
     * 不在热路径上，故写法以清晰为先。
     * </p>
     *
     * @param model 烘焙模型（调用方已确认不是 BEWLR）
     * @return 外形描述符；模型为空或包围盒退化时返回 null
     */
    @Nullable
    private static float[] measureShape(BakedModel model) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean any = false;

        // side == null 的一批是不归属于任何朝向的面，必须单独取一次，
        // 不能只遍历六个方向——物品模型的面绝大多数都落在这一批里
        for (int i = -1; i < 6; i++) {
            Direction side = (i < 0) ? null : Direction.values()[i];
            List<BakedQuad> quads = model.getQuads(null, side, SHAPE_RANDOM);
            if (quads == null || quads.isEmpty()) {
                continue;
            }
            for (int q = 0; q < quads.size(); q++) {
                int[] data = quads.get(q).getVertices();
                // 顶点数据是打平的 int[]，四个顶点等长分布；位置为每个顶点的前三个 int
                // （float 的位模式，需用 intBitsToFloat 还原）
                int stride = data.length / 4;
                if (stride < 3) {
                    continue;
                }
                for (int v = 0; v < 4; v++) {
                    int o = v * stride;
                    float x = Float.intBitsToFloat(data[o]);
                    float y = Float.intBitsToFloat(data[o + 1]);
                    float z = Float.intBitsToFloat(data[o + 2]);
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (z < minZ) {
                        minZ = z;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                    if (z > maxZ) {
                        maxZ = z;
                    }
                    any = true;
                }
            }
        }
        if (!any) {
            return null;
        }

        // 方块模型空间 [0,1] → 居中空间 [-0.5, 0.5]
        minX -= 0.5f;
        maxX -= 0.5f;
        minY -= 0.5f;
        maxY -= 0.5f;
        minZ -= 0.5f;
        maxZ -= 0.5f;

        float width = maxX - minX;
        float height = maxY - minY;
        if (width < DEGENERATE_EPSILON || height < DEGENERATE_EPSILON) {
            // 退化成一条线或一个点，套护鞘没有意义
            return null;
        }

        float frontZ = maxZ;
        // 把手柄从厚度里切掉：背面最多只到正面往里 MAX_SHELL_DEPTH
        float backZ = Math.max(minZ, frontZ - MAX_SHELL_DEPTH);
        if (frontZ - backZ < DEGENERATE_EPSILON) {
            // 完全扁平的模型（如纯 2D 物品）：给一个最小厚度，否则侧缘会退化成零面积
            backZ = frontZ - DEGENERATE_EPSILON * 10f;
        }

        float[] shape = new float[SHAPE_LENGTH];
        shape[SHAPE_CX] = (minX + maxX) * 0.5f;
        shape[SHAPE_CY] = (minY + maxY) * 0.5f;
        shape[SHAPE_HALF_W] = width * 0.5f;
        shape[SHAPE_HALF_H] = height * 0.5f;
        shape[SHAPE_FRONT_Z] = frontZ;
        shape[SHAPE_BACK_Z] = backZ;
        return shape;
    }

    // ==================== 状态机 ====================

    /**
     * 推进并返回某面盾的展开度。
     * <p>
     * 用<b>墙钟差值</b>而非 tick 驱动，与本模组其它演出一致——
     * TPS 波动时展开速度不该跟着变。单帧推进量夹在
     * {@link #MAX_STEP_SECONDS} 以内，防止卡顿后一帧跳完。
     * </p>
     * <p>
     * <b>这里只升不降。</b>能走到本方法就意味着盾正拿在手上、效果就该亮着；
     * 而收起盾牌时 {@code renderOnItem} 根本不会被调用，
     * 没有任何时机可以驱动淡出——条目会由 {@link #onClientTick} 按过期时间清掉。
     * </p>
     *
     * @param stateKey 状态键（实体 id × 16 + 显示上下文序号）
     * @return 展开度 0~1
     */
    private static float advanceState(int stateKey) {
        long now = System.currentTimeMillis();
        WardState st = STATE.get(stateKey);
        if (st == null) {
            st = new WardState();
            st.value = 0f;
            st.lastUpdate = now;
            STATE.put(stateKey, st);
        }
        float dt = (now - st.lastUpdate) / 1000f;
        if (dt < 0f) {
            dt = 0f;
        } else if (dt > MAX_STEP_SECONDS) {
            dt = MAX_STEP_SECONDS;
        }
        st.lastUpdate = now;

        if (st.value < 1f) {
            st.value = Math.min(1f, st.value + dt / APPEAR_SECONDS);
        }
        return st.value;
    }

    /**
     * 客户端 tick：离开世界时清空全部缓存；否则定期清理长时间未被访问的状态条目。
     * <p>实体死亡 / 卸载、玩家换下盾牌、或走出 {@link #CULL_DISTANCE} 之后
     * 都不会再触发渲染，其条目会永远留在表里，故需要这条按最后访问时间的兜底清理。</p>
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            STATE.clear();
            SHAPE_CACHE.clear();
            return;
        }
        if (STATE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, WardState>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            WardState st = it.next().getValue();
            if (now - st.lastUpdate > STATE_EXPIRY_MS) {
                it.remove();
            }
        }
    }

    // ==================== 附魔解析 ====================

    /**
     * 懒解析魔力盾牌附魔（注册表在 mod 加载后才可用）。
     * <p>仅在成功解析后才标记完成，否则下次重试——避免把 null 固化下来。</p>
     *
     * @return 附魔对象；未注册（如被配置禁用）时返回 null
     */
    @Nullable
    private static Enchantment resolveScholar() {
        if (!scholarResolved) {
            scholarCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, SCHOLAR_SHIELD_ID));
            scholarResolved = (scholarCache != null);
        }
        return scholarCache;
    }

    /**
     * 懒解析不变盾牌附魔。
     *
     * @return 附魔对象；未注册时返回 null
     */
    @Nullable
    private static Enchantment resolveImmutable() {
        if (!immutableResolved) {
            immutableCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, IMMUTABLE_SHIELD_ID));
            immutableResolved = (immutableCache != null);
        }
        return immutableCache;
    }

    // ==================== 护鞘绘制 ====================

    /**
     * 绘制护鞘。
     * <p>
     * 配色取自 {@link #MIX_CORE} / {@link #MIX_MAIN}，
     * <b>调用前必须先调 {@link #mixPalette}</b>，否则用到的是上一次绘制留下的颜色。
     * </p>
     * <p>
     * <b>{@code detailed = false} 时去掉正面网格与闪点</b>，
     * 保留发光晕、侧缘与轮廓框。前两者是最贵的部分，
     * 而它们在远处一个糊成色块、一个细到看不见；后三者是亮线与雾，
     * 远距离恰恰是靠它们在传达「带附魔、什么颜色」。
     * </p>
     * <p>
     * <b>没有背面网格。</b>v5.1 去掉了——它 alpha 只有 0.1、又被盾牌本体挡着，
     * 绝大多数视角看不见，却占约四成顶点。壳的厚度感由侧缘与背面轮廓框承担。
     * </p>
     *
     * @param shape    盾牌外形描述符（只读）
     * @param value    展开度 0~1，同时作用于余量厚度与不透明度
     * @param time     动画时间（秒）
     * @param seedId   实体网络 id（错开各实体的动画相位）
     * @param detailed 是否画正面网格与闪点（近距离与第一人称为 true）
     */
    private static void drawWardSheath(VertexConsumer vc, Matrix4f m, float[] shape,
                                       float value, float time, int seedId, boolean detailed) {
        // 余量随展开度从近乎 0 长到满值 —— 魔力从盾面上「渗出来」，而不是整体缩放
        float margin = SHELL_MARGIN * (MARGIN_FLOOR_RATIO + (1f - MARGIN_FLOOR_RATIO) * value);
        float hw = shape[SHAPE_HALF_W] + margin;
        float hh = shape[SHAPE_HALF_H] + margin;
        float zf = shape[SHAPE_FRONT_Z] + margin;
        float zb = shape[SHAPE_BACK_Z] - margin;
        float alpha = value;
        // 边缘极缓慢呼吸，只是让护鞘「活着」，不是在跳动
        float breath = 0.88f + 0.12f * Mth.sin(time * BREATH_SPEED + seedId * 0.7f);

        // ===== 外发光晕：先画，它在最外层也最淡，压在别的元素下面 =====
        // 远近都画：只有 8 个 quad，而远处屏幕占比小、overdraw 可忽略。
        // 留着它，远看是「一面裹在雾里的盾」而不是「一个线框」。
        // 嫌远处糊就把这行移进下面的 if (detailed) 块。
        drawHalo(vc, m, hw, hh, zf, MIX_MAIN, HALO_ALPHA * alpha * breath);

        if (detailed) {
            // ===== 正面底色：中心透、边缘实 =====
            drawFace(vc, m, hw, hh, zf, MIX_MAIN, FACE_ALPHA * alpha);
            // ===== 正面辉光条纹：流体感 =====
            drawGlint(vc, m, hw, hh, zf + 0.0015f, MIX_CORE,
                    GLINT_STRENGTH * alpha, time, seedId * 0.137f);
            // ===== 辉石闪点：结晶感。必须压在条纹之上，否则会被条纹糊掉 =====
            drawSparks(vc, m, hw, hh, zf + 0.003f, MIX_CORE,
                    SPARK_ALPHA * alpha, time, seedId);
        }
        // ===== 侧缘：把前后连起来，护鞘才有厚度。远近都画 =====
        drawRim(vc, m, hw, hh, zf, zb, MIX_CORE,
                RIM_ALPHA * alpha * breath, RIM_ALPHA * RIM_BACK_RATIO * alpha);
        // ===== 轮廓框：勾出盒子的边，正背各一圈。远近都画 =====
        drawOutline(vc, m, hw, hh, zf, OUTLINE_HALF,
                MIX_CORE, OUTLINE_ALPHA * alpha * breath);
        drawOutline(vc, m, hw, hh, zb, OUTLINE_HALF,
                MIX_MAIN, OUTLINE_ALPHA * BACK_OUTLINE_RATIO * alpha * breath);
    }

    /**
     * 绘制护鞘外侧的一圈发光晕：四条边带 + 四个角块，向外线性衰减到全透明。
     * <p>
     * <b>作用是软化边界，不是再加一圈亮边。</b>护盾之所以看着「硬」，
     * 是因为轮廓线之外立刻就是背景，没有过渡。补一层向外散开的雾，
     * 盾就从「一块发光的板」变成「一团裹着盾的魔力」。
     * </p>
     * <p>
     * {@link #HALO_ALPHA} 刻意压得比侧缘低不少——调高会盖掉盾牌本体的轮廓，
     * 那时看到的就是一坨发光的东西，而不是一面盾。
     * </p>
     * <p>
     * 角块用一个四边形近似：内角顶点是亮的，另外三个顶点全透。
     * 严格来说该是四分之一圆的径向渐变，但在这个尺寸下看不出差别，
     * 而近似版只要一个 quad。
     * </p>
     *
     * @param hw    护鞘半宽
     * @param hh    护鞘半高
     * @param z     所在 Z 平面
     * @param col   颜色（只读）
     * @param alpha 贴着护鞘那一侧的不透明度
     */
    private static void drawHalo(VertexConsumer vc, Matrix4f m,
                                 float hw, float hh, float z,
                                 float[] col, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        float ow = hw + HALO_WIDTH;
        float oh = hh + HALO_WIDTH;

        // ===== 四条边带：内侧亮、外侧透 =====
        // 上
        quad3(vc, m,
                -hw, hh, z, alpha,
                hw, hh, z, alpha,
                hw, oh, z, 0f,
                -hw, oh, z, 0f, col);
        // 下
        quad3(vc, m,
                -hw, -hh, z, alpha,
                hw, -hh, z, alpha,
                hw, -oh, z, 0f,
                -hw, -oh, z, 0f, col);
        // 左
        quad3(vc, m,
                -hw, -hh, z, alpha,
                -hw, hh, z, alpha,
                -ow, hh, z, 0f,
                -ow, -hh, z, 0f, col);
        // 右
        quad3(vc, m,
                hw, -hh, z, alpha,
                hw, hh, z, alpha,
                ow, hh, z, 0f,
                ow, -hh, z, 0f, col);

        // ===== 四个角块：只有贴着护鞘的那个内角是亮的 =====
        // 右上
        quad3(vc, m,
                hw, hh, z, alpha,
                ow, hh, z, 0f,
                ow, oh, z, 0f,
                hw, oh, z, 0f, col);
        // 左上
        quad3(vc, m,
                -hw, hh, z, alpha,
                -ow, hh, z, 0f,
                -ow, oh, z, 0f,
                -hw, oh, z, 0f, col);
        // 右下
        quad3(vc, m,
                hw, -hh, z, alpha,
                ow, -hh, z, 0f,
                ow, -oh, z, 0f,
                hw, -oh, z, 0f, col);
        // 左下
        quad3(vc, m,
                -hw, -hh, z, alpha,
                -ow, -hh, z, 0f,
                -ow, -oh, z, 0f,
                -hw, -oh, z, 0f, col);
    }

    /**
     * 在盾面上画几个明灭的辉石闪点（十字星）。
     * <p>
     * <b>这是「结晶感」的唯一来源。</b>流动条纹表达的是流体——它连续、有方向、不间断；
     * 而辉石是固体，它的视觉特征是<b>棱面偶然对上光时的一瞬爆闪</b>。
     * 两者叠在一起，护鞘才既像能量又像结晶。
     * </p>
     * <p>
     * 亮度用「正弦的正半周再取六次方」：一个周期里只有很短一段亮着，
     * 其余时间几乎全暗。<b>刻意不做成呼吸</b>——连续起伏会读成「灯」，
     * 短促爆闪才读成「反光」。
     * </p>
     * <p>
     * 位置由 {@link #hash01} 从实体 id 与序号推出，因此<b>同一面盾上的闪点位置固定</b>
     * （不会乱飘），而不同实体各不相同。相位同样错开，三个点不会一起闪。
     * </p>
     * <p>
     * 每个闪点是四条从中心射向四方的短线（中心亮、尖端透），即 4 个 quad。
     * 用四条半线而不是两条整线，是因为 {@link #line} 的不透明度只能给两个端点，
     * 整线做不出「中间最亮」。
     * </p>
     *
     * @param hw       护鞘半宽
     * @param hh       护鞘半高
     * @param z        所在 Z 平面
     * @param col      颜色（只读）
     * @param maxAlpha 闪点峰值不透明度
     * @param time     动画时间（秒）
     * @param seedId   实体网络 id
     */
    private static void drawSparks(VertexConsumer vc, Matrix4f m,
                                   float hw, float hh, float z,
                                   float[] col, float maxAlpha, float time, int seedId) {
        if (maxAlpha <= 0.004f) {
            return;
        }
        for (int i = 0; i < SPARK_COUNT; i++) {
            // 相位错开，三个点不会一起闪
            float phase = hash01(seedId * 3 + i * 541) * TAU;
            float s = Mth.sin(time * SPARK_OMEGA + phase);
            if (s <= 0f) {
                // 正弦负半周整段不亮：暗的时间远长于亮的时间，这正是要的节奏
                continue;
            }
            float bright = pow6(s) * maxAlpha;
            if (bright <= 0.004f) {
                continue;
            }
            // 位置固定：同一面盾的闪点不会乱飘，不同实体各不相同
            float cx = (hash01(seedId * 7 + i * 131) * 2f - 1f) * hw * SPARK_INSET;
            float cy = (hash01(seedId * 13 + i * 977) * 2f - 1f) * hh * SPARK_INSET;

            // 四条半线：中心亮、尖端透
            line(vc, m, cx, cy, cx, cy + SPARK_ARM, z, SPARK_HALF, col, bright, 0f);
            line(vc, m, cx, cy, cx, cy - SPARK_ARM, z, SPARK_HALF, col, bright, 0f);
            line(vc, m, cx, cy, cx + SPARK_ARM, cy, z, SPARK_HALF, col, bright, 0f);
            line(vc, m, cx, cy, cx - SPARK_ARM, cy, z, SPARK_HALF, col, bright, 0f);
        }
    }

    /**
     * 把一个整数打散成 {@code [0, 1)} 的伪随机数。
     * <p>
     * 用于给闪点定位与定相位。要的是<b>确定性</b>——同一个输入永远得到同一个输出，
     * 这样闪点位置在帧与帧之间才是稳的；用 {@code Random} 反而要维护实例状态。
     * </p>
     * <p>整数位运算混合，无分支、无浮点除法以外的开销。</p>
     *
     * @param n 输入
     * @return {@code [0, 1)} 内的伪随机数
     */
    private static float hash01(int n) {
        n = (n ^ 61) ^ (n >>> 16);
        n = n + (n << 3);
        n = n ^ (n >>> 4);
        n = n * 0x27D4EB2D;
        n = n ^ (n >>> 15);
        return (n & 0xFFFF) / 65536f;
    }

    /**
     * 六次方。
     * <p>
     * 展开成三次乘法：{@code x² → x⁴ → x⁶}。
     * 这是为了替掉热路径上的 {@code Math.pow}——后者要处理任意实数指数，
     * 在每帧几十次调用的位置上是笔不必要的开销。
     * </p>
     * <p>
     * 条纹锐度与闪点的明灭曲线都固定用它。
     * <b>改锐度就得改这个方法</b>，这是拿可调性换性能，是刻意的。
     * </p>
     *
     * @param x 底数
     * @return {@code x} 的六次方
     */
    private static float pow6(float x) {
        float x2 = x * x;
        float x4 = x2 * x2;
        return x4 * x2;
    }

    /**
     * 计算某点上的辉光条纹强度（0~1）。
     * <p>
     * 两层斜向条纹叠加：一层向右上流、一层反向且更慢。
     * 两者速度不成整数比，因此叠出来的图样永远不重复，
     * 观感上就是原版附魔光效那种「说不清在往哪流」的闪动。
     * </p>
     * <p>
     * 单条纹用正弦的六次幂做窄峰——次数越高条纹越细越锐，
     * 直接用 {@code sin} 会得到一片糊开的渐变，读不出「条纹」。
     * </p>
     *
     * @param x    表面横坐标（格，相对护鞘中心）
     * @param y    表面纵坐标（格，相对护鞘中心）
     * @param time 动画时间（秒）
     * @param seed 相位种子
     * @return 条纹强度 0~1
     */
    private static float glint(float x, float y, float time, float seed) {
        // 第一层：沿右上方向流动
        float p1 = (x * 0.7f + y) / GLINT_PERIOD - time * GLINT_SPEED + seed;
        float s1 = Math.abs(Mth.sin((float) Math.PI * p1));
        // 第二层：沿左上方向、更慢
        float p2 = (-x * 0.5f + y * 1.3f) / (GLINT_PERIOD * 1.7f) - time * GLINT_SPEED_2 + seed * 0.6f;
        float s2 = Math.abs(Mth.sin((float) Math.PI * p2));
        float g = pow6(s1) + GLINT_SECOND_WEIGHT * pow6(s2);
        return Math.min(1f, g);
    }

    // ==================== 护鞘几何 ====================

    /**
     * 在某个 Z 平面上铺一张矩形网格面，逐顶点按「到边缘的距离」增强不透明度。
     * <p>
     * <b>边缘比中心实</b>是「膜」这个读法成立的关键：肥皂泡、能量罩、护盾，
     * 凡是薄膜类的东西都是边缘厚、中间透。若做成中心最亮，
     * 立刻就读回「一块发光的板子」了。
     * </p>
     * <p>调用方已把姿态栈平移到盾牌几何中心，故此处按以原点为中心来写。</p>
     *
     * @param hw        半宽
     * @param hh        半高
     * @param z         该面所在的 Z
     * @param col       颜色（只读）
     * @param baseAlpha 中心处的底色不透明度
     */
    private static void drawFace(VertexConsumer vc, Matrix4f m,
                                 float hw, float hh, float z,
                                 float[] col, float baseAlpha) {
        if (baseAlpha <= 0.004f || hw <= 0.005f || hh <= 0.005f) {
            return;
        }
        for (int r = 0; r < FACE_ROWS; r++) {
            float ty0 = -1f + 2f * r / FACE_ROWS;
            float ty1 = -1f + 2f * (r + 1) / FACE_ROWS;
            float y0 = ty0 * hh;
            float y1 = ty1 * hh;
            for (int c = 0; c < FACE_COLS; c++) {
                float tx0 = -1f + 2f * c / FACE_COLS;
                float tx1 = -1f + 2f * (c + 1) / FACE_COLS;
                float x0 = tx0 * hw;
                float x1 = tx1 * hw;
                quad3(vc, m,
                        x0, y0, z, faceAlpha(tx0, ty0, baseAlpha),
                        x1, y0, z, faceAlpha(tx1, ty0, baseAlpha),
                        x1, y1, z, faceAlpha(tx1, ty1, baseAlpha),
                        x0, y1, z, faceAlpha(tx0, ty1, baseAlpha),
                        col);
            }
        }
    }

    /**
     * 面上某点的底色不透明度：越靠近轮廓越实。
     * <p>
     * 取横纵两个方向里更靠边的那个作为「贴边程度」——盾是竖长的，
     * 若按「到中心的欧氏距离」算，上下两端会过早变实、左右两侧偏透，边缘亮度不匀。
     * </p>
     *
     * @param tx        归一化横坐标 -1~1
     * @param ty        归一化纵坐标 -1~1
     * @param baseAlpha 中心处的底色不透明度
     * @return 该点的不透明度
     */
    private static float faceAlpha(float tx, float ty, float baseAlpha) {
        float edge = Math.max(Math.abs(tx), Math.abs(ty));
        return baseAlpha * (0.5f + 1.3f * edge * edge);
    }

    /**
     * 在正面叠一层流动的辉光条纹。
     * <p>
     * <b>先填 {@link #GLINT_TABLE} 再铺格子。</b>相邻网格块共享边，
     * 若每块各算四角，共享顶点会被算两遍；先把格点算好查表，
     * {@link #glint} 的调用次数从「块数 × 4」降到「格点数」。
     * </p>
     * <p>只在条纹强度足够时才提交顶点——绝大多数网格块处于条纹之间的暗区，
     * 提交它们只会白白增加顶点量。</p>
     *
     * @param strength 条纹峰值亮度（已含展开度）
     * @param time     动画时间（秒）
     * @param seed     相位种子
     */
    private static void drawGlint(VertexConsumer vc, Matrix4f m,
                                  float hw, float hh, float z,
                                  float[] col, float strength, float time, float seed) {
        if (strength <= 0.004f) {
            return;
        }
        // ===== 1) 填表：每个格点只算一次 =====
        for (int r = 0; r < GLINT_GRID_ROWS; r++) {
            float y = (-1f + 2f * r / FACE_ROWS) * hh;
            int rowBase = r * GLINT_GRID_COLS;
            for (int c = 0; c < GLINT_GRID_COLS; c++) {
                float x = (-1f + 2f * c / FACE_COLS) * hw;
                GLINT_TABLE[rowBase + c] = glint(x, y, time, seed);
            }
        }
        // ===== 2) 铺格子：四角直接查表 =====
        for (int r = 0; r < FACE_ROWS; r++) {
            float y0 = (-1f + 2f * r / FACE_ROWS) * hh;
            float y1 = (-1f + 2f * (r + 1) / FACE_ROWS) * hh;
            int base0 = r * GLINT_GRID_COLS;
            int base1 = (r + 1) * GLINT_GRID_COLS;
            for (int c = 0; c < FACE_COLS; c++) {
                float g00 = GLINT_TABLE[base0 + c];
                float g10 = GLINT_TABLE[base0 + c + 1];
                float g11 = GLINT_TABLE[base1 + c + 1];
                float g01 = GLINT_TABLE[base1 + c];
                float peak = Math.max(Math.max(g00, g10), Math.max(g11, g01));
                if (peak <= GLINT_CUTOFF) {
                    continue;
                }
                float x0 = (-1f + 2f * c / FACE_COLS) * hw;
                float x1 = (-1f + 2f * (c + 1) / FACE_COLS) * hw;
                quad3(vc, m,
                        x0, y0, z, g00 * strength,
                        x1, y0, z, g10 * strength,
                        x1, y1, z, g11 * strength,
                        x0, y1, z, g01 * strength,
                        col);
            }
        }
    }

    /**
     * 绘制盒子四条边上的侧缘：把前面与后面沿盒子的四条边连成一个有厚度的壳。
     * <p>
     * <b>这一圈是「包住」观感的来源。</b>没有它，护鞘只是一片贴在盾面上的贴纸；
     * 有了它才读作一个套在盾外面的壳。也因此它是整个演出最亮的部分，
     * 并且在远距离简化档里被保留。
     * </p>
     * <p>前缘（靠 {@code zf} 一侧）更亮、后缘更暗，从侧面看有光泽。</p>
     *
     * @param zf         前面所在 Z
     * @param zb         后面所在 Z
     * @param alphaFront 前缘不透明度
     * @param alphaBack  后缘不透明度
     */
    private static void drawRim(VertexConsumer vc, Matrix4f m,
                                float hw, float hh, float zf, float zb,
                                float[] col, float alphaFront, float alphaBack) {
        if (alphaFront <= 0.004f && alphaBack <= 0.004f) {
            return;
        }
        // 右侧（x = +hw）
        quad3(vc, m,
                hw, -hh, zb, alphaBack,
                hw, -hh, zf, alphaFront,
                hw, hh, zf, alphaFront,
                hw, hh, zb, alphaBack, col);
        // 左侧（x = -hw）
        quad3(vc, m,
                -hw, -hh, zb, alphaBack,
                -hw, -hh, zf, alphaFront,
                -hw, hh, zf, alphaFront,
                -hw, hh, zb, alphaBack, col);
        // 上沿（y = +hh）
        quad3(vc, m,
                -hw, hh, zb, alphaBack,
                -hw, hh, zf, alphaFront,
                hw, hh, zf, alphaFront,
                hw, hh, zb, alphaBack, col);
        // 下沿（y = -hh）
        quad3(vc, m,
                -hw, -hh, zb, alphaBack,
                -hw, -hh, zf, alphaFront,
                hw, -hh, zf, alphaFront,
                hw, -hh, zb, alphaBack, col);
    }

    /**
     * 在某个 Z 平面上画一圈矩形轮廓线，勾出盒子的边。
     *
     * @param half 线半宽
     */
    private static void drawOutline(VertexConsumer vc, Matrix4f m,
                                    float hw, float hh, float z, float half,
                                    float[] col, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        line(vc, m, -hw, hh, hw, hh, z, half, col, alpha, alpha);      // 上
        line(vc, m, -hw, -hh, hw, -hh, z, half, col, alpha, alpha);    // 下
        line(vc, m, -hw, -hh, -hw, hh, z, half, col, alpha, alpha);    // 左
        line(vc, m, hw, -hh, hw, hh, z, half, col, alpha, alpha);      // 右
    }

    // ==================== 基础几何基元（物品模型空间）====================
    // 坐标以盾牌几何中心为原点，1 单位 = 1 格，+Z 为盾牌正面（外侧）。

    /**
     * 任意三维四边形，四个顶点的 alpha 可分别指定。
     * <p>渲染类型已开启双面（{@code NO_CULL}），缠绕方向无所谓。</p>
     */
    private static void quad3(VertexConsumer vc, Matrix4f m,
                              float x1, float y1, float z1, float a1,
                              float x2, float y2, float z2, float a2,
                              float x3, float y3, float z3, float a3,
                              float x4, float y4, float z4, float a4,
                              float[] col) {
        if (a1 <= 0.004f && a2 <= 0.004f && a3 <= 0.004f && a4 <= 0.004f) {
            return;
        }
        float r = col[0], g = col[1], b = col[2];
        vc.vertex(m, x1, y1, z1).color(r, g, b, a1).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a2).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a3).endVertex();

        vc.vertex(m, x1, y1, z1).color(r, g, b, a1).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a3).endVertex();
        vc.vertex(m, x4, y4, z4).color(r, g, b, a4).endVertex();
    }

    /**
     * 同一 Z 平面上带宽度的线段（两端 alpha 可不同）。
     *
     * @param half 线半宽
     */
    private static void line(VertexConsumer vc, Matrix4f m,
                             float x1, float y1, float x2, float y2, float z,
                             float half, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-6f) {
            return;
        }
        // 平面内的法线 × 半宽
        float nx = -dy / len * half;
        float ny = dx / len * half;
        quad3(vc, m,
                x1 + nx, y1 + ny, z, a1,
                x2 + nx, y2 + ny, z, a2,
                x2 - nx, y2 - ny, z, a2,
                x1 - nx, y1 - ny, z, a1,
                col);
    }
}