package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.network.CombatArtEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.List;

/**
 * 六个新增战技演出的独立渲染器（纯客户端自绘）。
 * <p>
 * 负责 {@link CombatArtEffectPacket} 的类型 4~9：不屈壁障、狮子斩、二连斩、
 * 箭步上砍、格挡窗口、盾牌冲击。
 * </p>
 *
 * <h3>v2.0：全面加强可见性（实测反馈）</h3>
 * <p>
 * 实测反馈是「狮子斩没看到」「盾牌冲击好像没效果」。复盘下来 v1.0 犯了同一个错：
 * <b>把这六个当成了「不该抢戏的小反馈」来做</b>——尺寸贴着身体、时长半秒、
 * 线宽两三厘米、盾击还是贴地的。
 * </p>
 * <p>
 * 但战斗中玩家的注意力全在血条、目标模型和自己的挥击动画上，
 * 一个又小又薄又短的图形<b>会被整个漏掉</b>，谈不上抢戏不抢戏。
 * 先得让人看得见一次，才谈得上担心看见太多次。本版三管齐下：
 * </p>
 * <ol>
 *     <li><b>统一的起手爆闪</b>（{@link #drawImpactFlash}）——六个演出在最初 18%
 *         都在胸口打一记大号白闪。这是最便宜也最有效的一招：
 *         哪怕玩家完全没看主体图形，余光也会捕捉到那一下亮；</li>
 *     <li><b>线宽普遍加倍、尺度上调</b>（半径已在
 *         {@code CarianStyleCombatArtEffects} 放大 1.3~1.6 倍，
 *         本类的线宽、碎片、火花同步跟上）；</li>
 *     <li><b>盾牌冲击从贴地改为竖直冲击墙</b>——这是本版改动最大的一个，
 *         详见 {@link #drawShieldBash}。</li>
 * </ol>
 * <p>
 * <b>形状语言全部保持不变</b>，只是放大加亮。区分依据没有被牺牲。
 * </p>
 *
 * <h3>为什么是一个新文件，而不是改 CombatArtEffectRenderer</h3>
 * <p>
 * {@code CombatArtEffectRenderer} 的类型分发 switch 末尾是
 * {@code default -> { }}，<b>会安静地忽略自己不认识的类型</b>。
 * 因此本渲染器只要订阅同一个 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段、
 * 自己过滤出这六个类型，就能与它并存，<b>那个一千多行的文件一行都不用改</b>。
 * {@code WaterfowlFlurryRenderer} 已经验证过这个加法。
 * </p>
 * <p>
 * 两者共同向 {@link VisualBatch} 的共享缓冲写顶点，最终仍是一次 GL 状态切换 + 一次 draw call。
 * </p>
 *
 * <h3>共同的形状语言约束</h3>
 * <table border="1">
 *   <caption>形状与运动方向</caption>
 *   <tr><th>演出</th><th>形状</th><th>运动</th><th>主色</th></tr>
 *   <tr><td>居合（既有）</td><td>单道宽弧 150°</td><td>水平横扫</td><td>银白 + 冷蓝影</td></tr>
 *   <tr><td>回旋（既有）</td><td>整圈 360°</td><td>水平横扫</td><td>银白 + 琥珀尘</td></tr>
 *   <tr><td>水鸟（既有）</td><td>多道窄弧 110°</td><td>水平交叉</td><td>银白 + 猩红边</td></tr>
 *   <tr><td>不屈壁障</td><td>六片竖直碎片 + 双环</td><td><b>向外推开</b></td><td>钢白（无彩度）</td></tr>
 *   <tr><td>狮子斩</td><td><b>三道平行斜痕</b></td><td>斜向下切</td><td>兽金 + 棕红</td></tr>
 *   <tr><td>二连斩</td><td><b>两道交叉 X</b></td><td>斜向对切</td><td>银白 + 钢蓝</td></tr>
 *   <tr><td>箭步上砍</td><td>单道上挑弧</td><td><b>竖直向上</b></td><td>刃白 + 土黄尘</td></tr>
 *   <tr><td>格挡窗口</td><td><b>收缩的准星</b></td><td>向内收</td><td>白 + 火星金</td></tr>
 *   <tr><td>盾牌冲击</td><td><b>前向竖直扇面墙</b></td><td>向前推</td><td>钢灰白</td></tr>
 * </table>
 * <p>
 * 三个既有战技全是「水平横扫的弧」，因此新增的六个刻意<b>一个都不用水平弧</b>：
 * 要么改方向（向上 / 向外 / 向内 / 向前），要么改形状（平行斜痕 / 交叉 X / 扇面 / 准星）。
 * </p>
 *
 * <h3>关于胸口高度</h3>
 * <p>
 * 包里只有脚底坐标，<b>没有实体高度</b>——因为这些特效是定点的，
 * 创建之后与实体再无关系。因此需要「大约在胸口」的演出统一用
 * {@link #CHEST_HEIGHT} 这个名义高度。对玩家与多数人形怪是准的；
 * 对特别高大或矮小的生物会偏一点，但这些演出都只活半秒多，
 * 偏差不值得为此在包里多塞一个字段。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 起手爆闪（3 层 × 12）                          36（六个演出共用）
 * 不屈壁障（6 碎片十字双面 + 2 环 + 爆闪）      ~520
 * 狮子斩（3 道 × 8 段 × 6 × 2 层 + 残影）       ~430
 * 二连斩（2 道 × 8 段 × 6 × 2 层）               192
 * 箭步上砍（12 段弧 × 2 层 + 尘环 + 火花）      ~380
 * 格挡窗口（准星环 + 十字 + 8 火星 + 冲击环）   ~420
 * 盾牌冲击（竖直墙 24 段 × 6 × 2 + 地面扇形
 *          + 3 推力线）                        ~900
 * </pre>
 * <p>
 * 盾牌冲击是新的开销大头（竖直墙是双层的）。全部接入 {@link VisualLod}，
 * 含 {@link VisualLod#countInstance()}——拥挤度是全局共享的，
 * 少登记一个渲染器就会让 {@code crowdFactor} 被系统性高估。
 * </p>
 * <p>
 * <b>削减遵循两条既有原则：</b>角度均布的元素（碎片、火星、推力线）按<b>步长抽取</b>
 * 而非截断，否则会只朝一侧；而形状本身承载辨识度的元素
 * （狮子斩的三道、二连斩的两道、盾击的竖直墙）<b>完全不削</b>——
 * 三道变两道就成了二连斩，那是把一个附魔画成另一个。
 * </p>
 * <p>
 * <b>起手爆闪永不削减。</b>它只有 36 顶点，却是「能不能被看见」的最后保险。
 * </p>
 *
 * @author FlameForge
 * @version 2.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatArtExtraRenderer {

    /** 距离裁剪（格） */
    private static final double CULL = 64.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;

    /**
     * 名义胸口高度（格，自脚底算起）。
     * <p>包里没有实体高度，故需要「大约在胸口」的演出统一用此值（详见类注释）。</p>
     */
    private static final float CHEST_HEIGHT = 1.0f;

    /**
     * 细节系数计算时的最小视觉半径（格）。
     * <p>避免玩家贴脸时因「到中心距离」略大而被误判为远、削掉近处清晰可见的细节。</p>
     */
    private static final double MIN_VISUAL_RADIUS = 2.0;

    // ==================== 起手爆闪（v2.0 新增，六个演出共用）====================

    /** 爆闪持续的进度比例：只在最初这一小段出现 */
    private static final float FLASH_WINDOW = 0.18f;
    /** 爆闪最大尺寸系数（× 半径） */
    private static final float FLASH_SIZE_RATIO = 0.62f;
    /** 爆闪不透明度 */
    private static final float FLASH_ALPHA = 0.95f;

    // ==================== 配色（0xRRGGBB）====================

    /** 通用爆闪白：六个演出的起手闪光共用，不带任何色相以免干扰各自主色 */
    private static final int FLASH_WHITE = 0xFFFFFF;

    // ===== 不屈壁障：全模组唯一的无彩度战技演出 =====
    /** 钢白：爆闪与碎片高光 */
    private static final int STEEL_WHITE = 0xF0F4F8;
    /** 钢灰：碎片主体 */
    private static final int STEEL_GRAY = 0x9AA4B0;
    /** 深钢：冲击环外缘 */
    private static final int STEEL_DEEP = 0x3C4450;

    // ===== 狮子斩：兽性的暖色 =====
    /** 爪光白：爪痕核心 */
    private static final int CLAW_WHITE = 0xFFE8C8;
    /** 兽金：爪痕主体 */
    private static final int CLAW_GOLD = 0xE08030;
    /** 暗棕红：爪痕末端与残影 */
    private static final int CLAW_DEEP = 0x7A2A10;

    // ===== 二连斩：冷硬的金属 =====
    /** 刀锋纯白 */
    private static final int SLASH_EDGE = 0xFFFFFF;
    /** 钢蓝：刀身 */
    private static final int SLASH_STEEL = 0xB8C8DC;
    /** 影蓝：刀光末端 */
    private static final int SLASH_SHADOW = 0x5A6E8A;

    // ===== 箭步上砍：刃白 + 扬尘 =====
    /** 刃白：上挑弧核心 */
    private static final int LUNGE_EDGE = 0xFFF0DC;
    /** 土黄：地面急停尘环 */
    private static final int LUNGE_DUST = 0xD8A860;
    /** 深褐：尘环外缘 */
    private static final int LUNGE_DEEP = 0x8A6A3A;

    // ===== 格挡窗口：金属撞击的火星 =====
    /** 火星白 */
    private static final int PARRY_SPARK = 0xFFFFFF;
    /** 火星金：准星主色 */
    private static final int PARRY_GOLD = 0xFFC96B;
    /** 暗金：准星外环 */
    private static final int PARRY_DEEP = 0x9A6A20;

    // ===== 盾牌冲击：钢灰白 =====
    /** 冲击白 */
    private static final int BASH_WHITE = 0xF4F4F0;
    /** 钢灰：扇面主体 */
    private static final int BASH_GRAY = 0xA8A49A;
    /** 深灰：扇面外缘与推力线 */
    private static final int BASH_DEEP = 0x5A564E;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定：类加载时解包一次、此后永久复用的常量颜色数组。
    private static final float[] C_FLASH = VisualColor.constant(FLASH_WHITE);
    private static final float[] C_STEEL_WHITE = VisualColor.constant(STEEL_WHITE);
    private static final float[] C_STEEL_GRAY = VisualColor.constant(STEEL_GRAY);
    private static final float[] C_STEEL_DEEP = VisualColor.constant(STEEL_DEEP);
    private static final float[] C_CLAW_WHITE = VisualColor.constant(CLAW_WHITE);
    private static final float[] C_CLAW_GOLD = VisualColor.constant(CLAW_GOLD);
    private static final float[] C_CLAW_DEEP = VisualColor.constant(CLAW_DEEP);
    private static final float[] C_SLASH_EDGE = VisualColor.constant(SLASH_EDGE);
    private static final float[] C_SLASH_STEEL = VisualColor.constant(SLASH_STEEL);
    private static final float[] C_SLASH_SHADOW = VisualColor.constant(SLASH_SHADOW);
    private static final float[] C_LUNGE_EDGE = VisualColor.constant(LUNGE_EDGE);
    private static final float[] C_LUNGE_DUST = VisualColor.constant(LUNGE_DUST);
    private static final float[] C_LUNGE_DEEP = VisualColor.constant(LUNGE_DEEP);
    private static final float[] C_PARRY_SPARK = VisualColor.constant(PARRY_SPARK);
    private static final float[] C_PARRY_GOLD = VisualColor.constant(PARRY_GOLD);
    private static final float[] C_PARRY_DEEP = VisualColor.constant(PARRY_DEEP);
    private static final float[] C_BASH_WHITE = VisualColor.constant(BASH_WHITE);
    private static final float[] C_BASH_GRAY = VisualColor.constant(BASH_GRAY);
    private static final float[] C_BASH_DEEP = VisualColor.constant(BASH_DEEP);

    // ==================== LOD 下限与保留阈值 ====================

    /** 环 / 扇形带的最少分段数 */
    private static final int RING_SEGMENTS_MIN = 12;
    /** 平面环（准星）的最少分段数 */
    private static final int PLANE_RING_SEGMENTS_MIN = 10;
    /** 不屈壁障碎片层的保留阈值 */
    private static final float SHARD_KEEP_THRESHOLD = 0.35f;
    /** 格挡火星层的保留阈值 */
    private static final float SPARK_KEEP_THRESHOLD = 0.4f;
    /** 盾牌冲击推力线层的保留阈值 */
    private static final float THRUST_KEEP_THRESHOLD = 0.45f;
    /** 狮子斩残影层的保留阈值 */
    private static final float AFTERIMAGE_KEEP_THRESHOLD = 0.5f;

    // ==================== 各演出的几何参数 ====================

    // ===== 不屈壁障 =====
    /** 壁障碎片数量（均布一圈） */
    private static final int SHARD_COUNT = 6;
    /** 碎片竖直半高（格）。v2.0：0.40 → 0.62 */
    private static final float SHARD_HALF_HEIGHT = 0.62f;
    /** 碎片半宽（格）。v2.0：0.09 → 0.16 */
    private static final float SHARD_HALF_WIDTH = 0.16f;
    /** 碎片起始距离系数（× 半径） */
    private static final float SHARD_START_RATIO = 0.3f;
    /** 碎片推开距离系数（× 半径） */
    private static final float SHARD_PUSH_RATIO = 0.85f;
    /** 碎片推开动作占总进度的比例 */
    private static final float SHARD_PUSH_WINDOW = 0.55f;
    /** 冲击环带半宽（格）。v2.0：0.07 → 0.14 */
    private static final float INDOMITABLE_RING_HALF = 0.14f;

    // ===== 狮子斩 =====
    /** 爪痕道数。<b>三道是狮子斩的定义，不参与任何削减</b> */
    private static final int CLAW_COUNT = 3;
    /** 每道爪痕的细分段数。v2.0：6 → 8（放大后需要更细的分段才不显折） */
    private static final int CLAW_SEGMENTS = 8;
    /** 相邻爪痕的间距（平面内，× 半径） */
    private static final float CLAW_GAP = 0.26f;
    /** 相邻爪痕的起手延迟（占总进度） */
    private static final float CLAW_STAGGER = 0.05f;
    /** 爪痕划过所占的进度比例 */
    private static final float CLAW_SWEEP_RATIO = 0.3f;
    /** 爪痕最大半宽（格）。v2.0：0.05 → 0.12 */
    private static final float CLAW_HALF = 0.12f;
    /**
     * 爪痕平面朝攻击者方向的偏移系数（× 半径）。
     * <p>v2.0：0.22 → 0.45。爪痕要落在目标<b>身前</b>，
     * 偏移不足会被目标模型挡掉一半——这是「没看到」的直接成因之一。</p>
     */
    private static final float CLAW_FORWARD_RATIO = 0.45f;

    // ===== 二连斩 =====
    /** 每道刀光的细分段数。v2.0：6 → 8 */
    private static final int SLASH_SEGMENTS = 8;
    /** 第二道相对第一道的延迟（占总进度） */
    private static final float SLASH_SECOND_DELAY = 0.14f;
    /** 刀光划过所占的进度比例 */
    private static final float SLASH_SWEEP_RATIO = 0.26f;
    /** 刀光最大半宽（格）。v2.0：0.055 → 0.13 */
    private static final float SLASH_HALF = 0.13f;
    /** 刀光平面朝攻击者方向的偏移系数（× 半径） */
    private static final float SLASH_FORWARD_RATIO = 0.42f;

    // ===== 箭步上砍 =====
    /** 上挑弧的细分段数。v2.0：10 → 12 */
    private static final int LUNGE_SEGMENTS = 12;
    /** 上挑弧扫完所占的进度比例 */
    private static final float LUNGE_SWEEP_RATIO = 0.35f;
    /** 上挑弧最大半宽（格）。v2.0：0.07 → 0.15 */
    private static final float LUNGE_HALF = 0.15f;
    /** 地面尘环扩散所占的进度比例 */
    private static final float LUNGE_DUST_RATIO = 0.55f;
    /** 尘环带半宽（格）。v2.0：0.12 → 0.2 */
    private static final float LUNGE_DUST_HALF = 0.2f;

    // ===== 格挡窗口 =====
    /** 准星所在平面距实体中心的前方距离（格） */
    private static final double PARRY_FORWARD = 0.65;
    /** 准星外环起始半径系数（× 半径） */
    private static final float PARRY_RETICLE_RATIO = 0.8f;
    /** 准星线半宽（格）。v2.0：0.018 → 0.04 */
    private static final double PARRY_LINE_HALF = 0.04;
    /** 弹开火星数量。v2.0：6 → 8 */
    private static final int PARRY_SPARK_COUNT = 8;
    /** 火星飞散所占的进度比例 */
    private static final float PARRY_SPARK_WINDOW = 0.32f;
    /** 火星光点半尺寸（格）。v2.0：0.06 → 0.11 */
    private static final float PARRY_SPARK_SIZE = 0.11f;

    // ===== 盾牌冲击 =====
    /** 扇面跨度（弧度）：约 130°，覆盖「正面」而非整圈 */
    private static final float BASH_SPAN = 2.27f;
    /** 扇面分段数 */
    private static final int BASH_SEGMENTS = 24;
    /** 扇面推出所占的进度比例 */
    private static final float BASH_PUSH_RATIO = 0.6f;
    /**
     * 竖直冲击墙的底部高度（格，自脚底算起）。
     * <p>略高于地面，避免与地形 z-fighting，也让墙看起来是「推出去的」而不是「长出来的」。</p>
     */
    private static final float BASH_WALL_BOTTOM = 0.08f;
    /**
     * 竖直冲击墙的顶部高度（格，自脚底算起）。
     * <p>取 2.3 —— 比玩家高一头。<b>这是 v2.0 让盾击变可见的核心</b>：
     * 原来贴地的扇形在举盾视角下正好落在死角，抬到这个高度后
     * 它会直接从视野中央横过去。</p>
     */
    private static final float BASH_WALL_TOP = 2.3f;
    /** 推力线条数 */
    private static final int BASH_THRUST_COUNT = 3;
    /** 推力线半宽（格）。v2.0：0.05 → 0.09 */
    private static final double BASH_THRUST_HALF = 0.09;
    /** 推力线高度（格，自脚底算起）：胸口，与冲击墙同层，不再贴地 */
    private static final float BASH_THRUST_HEIGHT = 1.15f;

    private CombatArtExtraRenderer() {
    }

    /**
     * 渲染回调：只处理类型 4~9，其余交给 {@code CombatArtEffectRenderer} 与
     * {@code WaterfowlFlurryRenderer}。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        List<CombatArtEffectManager.CombatArtEffect> list = CombatArtEffectManager.getActive();
        if (list.isEmpty()) {
            return;
        }
        // 共享批次未开启（世界未加载等）：直接跳过
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        Matrix4f matrix = VisualBatch.matrix();
        long now = System.currentTimeMillis();

        for (CombatArtEffectManager.CombatArtEffect fx : list) {
            if (!isHandled(fx.type)) {
                continue;
            }

            double dx = fx.x - cam.x;
            double dy = fx.y - cam.y;
            double dz = fx.z - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue;
            }

            float p = CombatArtEffectManager.progressFor(fx, now);
            if (p <= 0.0005f || p >= 1f) {
                continue;
            }

            // 细节系数按「到演出视觉边界的距离」取，而非到中心——
            // 这几个演出的竖直体量比名义半径大，贴脸时不应被判定为远
            double visualRadius = Math.max(fx.radius, MIN_VISUAL_RADIUS);
            double edge = Math.max(0.0, Math.sqrt(distSqr) - visualRadius);
            float detail = VisualLod.detail(edge * edge);
            VisualLod.countInstance();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            // ⭐ v2.0：统一的起手爆闪。放在各演出之前画，
            // 保证哪怕主体图形被模型挡住，玩家余光也能捕捉到「这里发生了什么」
            drawImpactFlash(builder, matrix, rx, ryFoot, rz, fx.radius, p);

            switch (fx.type) {
                case CombatArtEffectPacket.TYPE_INDOMITABLE ->
                        drawIndomitable(builder, matrix, rx, ryFoot, rz,
                                fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_LION_CLAW ->
                        drawLionClaw(builder, matrix, rx, ryFoot, rz,
                                fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_DOUBLE_SLASH ->
                        drawDoubleSlash(builder, matrix, rx, ryFoot, rz,
                                fx.radius, fx.baseAngle, p);
                case CombatArtEffectPacket.TYPE_LUNGE_UP ->
                        drawLungeUp(builder, matrix, rx, ryFoot, rz,
                                fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_PARRY_WINDOW ->
                        drawParryWindow(builder, matrix, rx, ryFoot, rz,
                                fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_SHIELD_BASH ->
                        drawShieldBash(builder, matrix, rx, ryFoot, rz,
                                fx.radius, fx.baseAngle, p, detail);
                default -> {
                    // isHandled 已经筛过，走不到这里；留着只是让 switch 完备
                }
            }
        }
    }

    /**
     * 本渲染器是否负责该类型。
     * <p>集中判断而不是散在 switch 里，是为了让主循环能在做距离裁剪<b>之前</b>就跳过
     * 不属于自己的特效——同屏可能同时存在既有三个战技与水鸟的实例，
     * 没必要为它们算一遍平方距离。</p>
     *
     * @param type 特效类型
     * @return 由本渲染器绘制返回 true
     */
    private static boolean isHandled(int type) {
        return type == CombatArtEffectPacket.TYPE_INDOMITABLE
                || type == CombatArtEffectPacket.TYPE_LION_CLAW
                || type == CombatArtEffectPacket.TYPE_DOUBLE_SLASH
                || type == CombatArtEffectPacket.TYPE_LUNGE_UP
                || type == CombatArtEffectPacket.TYPE_PARRY_WINDOW
                || type == CombatArtEffectPacket.TYPE_SHIELD_BASH;
    }

    // ============================== 起手爆闪（六个共用）==============================

    /**
     * 统一的起手爆闪：胸口高度的一记三层白色闪光，只在最初
     * {@value #FLASH_WINDOW} 的进度内出现。
     * <p>
     * <b>这是 v2.0 里最重要的一个新增。</b>前面几版的教训是：
     * 精心设计的形状语言，如果玩家<b>压根没注意到有东西出现过</b>，就等于不存在。
     * 一记足够亮的闪光是最便宜的保险——它不需要玩家盯着看，
     * 余光扫到亮度变化就够了，之后玩家自然会去看那个位置发生了什么。
     * </p>
     * <p>
     * <b>三层同心菱形</b>（大而淡 / 中 / 小而实）而不是单层，
     * 是为了得到「中心过曝、边缘辉散」的观感——单层菱形会读成一个几何贴片。
     * </p>
     * <p>
     * 用纯白不带色相，是为了不干扰各演出自己的主色；
     * 它衰减得非常快（{@link #FLASH_WINDOW} 内由 1 掉到 0），
     * 不会污染后续的配色识别。
     * </p>
     * <p>
     * <b>永不参与 LOD 削减</b>——只有 36 顶点，却是可见性的最后保险。
     * </p>
     *
     * @param cx     中心相对相机 X
     * @param cyFoot 脚底相对相机 Y
     * @param cz     中心相对相机 Z
     * @param radius 演出半径（决定闪光尺寸）
     * @param p      归一化进度
     */
    private static void drawImpactFlash(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz,
                                        float radius, float p) {
        if (p >= FLASH_WINDOW) {
            return;
        }
        // 由 1 快速衰减到 0；用平方让衰减前段更陡，闪光更「脆」
        float k = 1f - p / FLASH_WINDOW;
        float intensity = k * k;
        if (intensity <= 0.01f) {
            return;
        }
        float y = cyFoot + CHEST_HEIGHT;
        float base = radius * FLASH_SIZE_RATIO;

        // 外层：大而淡的辉散
        billboardDiamond(b, m, cx, y, cz, base * (0.7f + 0.8f * k),
                C_FLASH, FLASH_ALPHA * 0.3f * intensity);
        // 中层
        billboardDiamond(b, m, cx, y, cz, base * (0.4f + 0.5f * k),
                C_FLASH, FLASH_ALPHA * 0.6f * intensity);
        // 内核：小而实的过曝点
        billboardDiamond(b, m, cx, y, cz, base * (0.18f + 0.22f * k),
                C_FLASH, FLASH_ALPHA * intensity);
    }

    // ============================== 不屈壁障 ==============================

    /**
     * 不屈壁障：六片向外推开的竖直碎片 + 双层地面冲击环（起手爆闪由主循环统一绘制）。
     * <p>
     * 时间轴（总时长 600ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：统一起手爆闪（{@link #drawImpactFlash}）；</li>
     *     <li>p ∈ [0, 0.55]：六片碎片自身体向外推开（缓出，起手最快）；</li>
     *     <li>p ∈ [0, 0.7]：双层冲击环在地面扩散；</li>
     *     <li>p ∈ [0.5, 1]：整体渐隐。</li>
     * </ul>
     * <p>
     * <b>「向外推开」是核心。</b>本模组的战技全是横扫或前推，
     * 只有这一个是<b>以身体为中心向四面八方弹开</b>，
     * 一眼就能读出「这一下被我挡回去了」而不是「我打了一下」。
     * </p>
     * <p>
     * <b>配色是全模组唯一的无彩度</b>——钢白 / 钢灰 / 深钢，没有任何色相。
     * 不屈是意志而不是魔法，也正好与周围一片彩色特效拉开距离。
     * </p>
     * <p>
     * <b>v2.0：</b>碎片尺寸放大 55%、环带加粗一倍，碎片层的保留阈值也放宽了
     * （0.40 → 0.35，更晚才被砍掉）。
     * </p>
     */
    private static void drawIndomitable(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz,
                                        float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.5f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float chestY = cyFoot + CHEST_HEIGHT;

        // ===== 六片壁障碎片：自身体向外推开 =====
        if (VisualLod.keepLayer(detail, SHARD_KEEP_THRESHOLD)) {
            float push = easeOutCubic(clamp01(p / SHARD_PUSH_WINDOW));
            float dist = radius * (SHARD_START_RATIO + SHARD_PUSH_RATIO * push);
            // 推得越远越淡：碎片是「散开」而不是「停在外面」
            float shardAlpha = 0.9f * fade * (1f - 0.5f * push);

            int drawn = VisualLod.scale(SHARD_COUNT, detail);
            int step = Math.max(1, SHARD_COUNT / drawn);
            for (int i = 0; i < SHARD_COUNT; i += step) {
                // 角度基准用原始 SHARD_COUNT，保证保留碎片的方位与全细节时一致
                float a = baseAngle + TAU * i / SHARD_COUNT;
                float px = cx + Mth.cos(a) * dist;
                float pz = cz + Mth.sin(a) * dist;
                // 竖直短板，十字双面，从任意水平视角都看得见
                crossQuad(b, m, px, chestY - SHARD_HALF_HEIGHT, pz,
                        px, chestY + SHARD_HALF_HEIGHT, pz,
                        SHARD_HALF_WIDTH * 2.2f, C_STEEL_DEEP,
                        0.35f * shardAlpha, 0.12f * shardAlpha);
                crossQuad(b, m, px, chestY - SHARD_HALF_HEIGHT, pz,
                        px, chestY + SHARD_HALF_HEIGHT, pz,
                        SHARD_HALF_WIDTH, C_STEEL_GRAY,
                        shardAlpha, 0.4f * shardAlpha);
                // 高光内芯：让碎片有金属的锐度，而不是一块灰板
                crossQuad(b, m, px, chestY - SHARD_HALF_HEIGHT * 0.7f, pz,
                        px, chestY + SHARD_HALF_HEIGHT * 0.7f, pz,
                        SHARD_HALF_WIDTH * 0.35f, C_STEEL_WHITE,
                        shardAlpha, 0.5f * shardAlpha);
            }
        }

        // ===== 双层地面冲击环 =====
        float ringP = clamp01(p / 0.7f);
        if (ringP > 0f && ringP < 1f) {
            float rr = radius * easeOutCubic(ringP);
            float ringFade = (1f - ringP) * fade;
            int segments = VisualLod.scaleSegments(28, RING_SEGMENTS_MIN, detail);
            ring(b, m, cx, cyFoot + Y_OFFSET, cz, rr, segments, INDOMITABLE_RING_HALF,
                    C_STEEL_WHITE, 0.85f * ringFade);
            ring(b, m, cx, cyFoot + Y_OFFSET, cz, rr * 0.66f, segments,
                    INDOMITABLE_RING_HALF * 0.7f,
                    C_STEEL_DEEP, 0.5f * ringFade);
        }
    }

    // ============================== 狮子斩 ==============================

    /**
     * 狮子斩：三道平行斜切爪痕，自右上向左下划过目标。
     * <p>
     * 时间轴（总时长 650ms）：三道各自延迟 {@value #CLAW_STAGGER} 起手、
     * 在 {@value #CLAW_SWEEP_RATIO} 的进度内划完，其后整体渐隐并留下暗色残影。
     * </p>
     * <p>
     * <b>「三道平行」是狮子斩的定义</b>——爪子就是三根指头划下来的。
     * 因此这三道<b>完全不参与 LOD 削减</b>：减到两道就变成了二连斩，
     * 那是把一个附魔画成另一个。
     * </p>
     *
     * <h4>v2.0 针对「没看到」的三处改动</h4>
     * <ol>
     *     <li><b>平面前移</b>（{@link #CLAW_FORWARD_RATIO} 0.22 → 0.45）：
     *         爪痕原来贴在目标身体中轴附近，正面近战时被目标模型挡掉一半。
     *         现在明确落在目标<b>身前</b>；</li>
     *     <li><b>线宽加倍</b>（0.05 → 0.12）加上半径放大 60%——
     *         原来那个尺度在贴脸视角下细得像根头发；</li>
     *     <li><b>新增残影层</b>：爪痕划过后留下一道暗棕红的拖尾，
     *         在主爪痕淡出后仍存在一小段时间。这层让「刚才那儿有东西划过」
     *         多持续了两三帧，是补捉率提升最明显的一处。</li>
     * </ol>
     */
    private static void drawLionClaw(BufferBuilder b, Matrix4f m,
                                     float cx, float cyFoot, float cz,
                                     float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.4f, 1f, p);
        // 残影比主爪痕晚淡出，所以即使 fade 归零也可能还要画残影
        float ghostFade = 1f - smoothstep(0.6f, 1f, p);
        if (ghostFade <= 0f) {
            return;
        }

        // 朝向单位向量：baseAngle 已经是本项目极坐标口径，(cos, sin) 即攻击者正前方
        float fwdX = Mth.cos(baseAngle);
        float fwdZ = Mth.sin(baseAngle);
        // 平面基：u = 世界上方，w = 攻击者正右方
        float ux = 0f, uy = 1f, uz = 0f;
        float wx = -fwdZ, wy = 0f, wz = fwdX;

        // 中心朝攻击者一侧偏移，让爪痕落在目标身前而不是穿过身体
        float ccx = cx - fwdX * radius * CLAW_FORWARD_RATIO;
        float ccy = cyFoot + CHEST_HEIGHT;
        float ccz = cz - fwdZ * radius * CLAW_FORWARD_RATIO;

        // 斜切方向：右上 → 左下。垂直于它的方向用来铺开三道
        float su = radius * 0.78f, sv = radius * 0.6f;
        float eu = -radius * 0.68f, ev = -radius * 0.78f;
        // 三道之间的偏移方向（垂直于斜切方向）
        float offU = radius * CLAW_GAP * 0.7f;
        float offV = -radius * CLAW_GAP * 0.7f;

        boolean drawGhost = VisualLod.keepLayer(detail, AFTERIMAGE_KEEP_THRESHOLD);

        for (int i = 0; i < CLAW_COUNT; i++) {
            // 中间那道最长最亮，两侧略短——真实的爪痕不会三道一样长
            float k = i - 1;
            float lenMul = (i == 1) ? 1f : 0.86f;
            float alphaMul = (i == 1) ? 1f : 0.82f;

            float sweep = easeOutCubic(clamp01((p - i * CLAW_STAGGER) / CLAW_SWEEP_RATIO));
            if (sweep <= 0f) {
                continue;
            }

            float u1 = su * lenMul + offU * k;
            float v1 = sv * lenMul + offV * k;
            float u2 = eu * lenMul + offU * k;
            float v2 = ev * lenMul + offV * k;

            // ⭐ v2.0：残影层。最宽最暗，淡出最慢——「刚才这儿被划了一道」
            if (drawGhost) {
                planeStroke(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                        u1, v1, u2, v2, CLAW_HALF * 1.9f, CLAW_SEGMENTS, sweep,
                        C_CLAW_DEEP, C_CLAW_DEEP, 0.45f * alphaMul * ghostFade);
            }

            // 主体
            planeStroke(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    u1, v1, u2, v2, CLAW_HALF, CLAW_SEGMENTS, sweep,
                    C_CLAW_GOLD, C_CLAW_DEEP, 0.9f * alphaMul * fade);
            // 内芯：更细更亮的一层，让爪痕有「锋利」而不是「一道糊涂的橙线」
            planeStroke(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    u1, v1, u2, v2, CLAW_HALF * 0.38f, CLAW_SEGMENTS, sweep,
                    C_CLAW_WHITE, C_CLAW_GOLD, 1.0f * alphaMul * fade);
        }
    }

    // ============================== 二连斩 ==============================

    /**
     * 二连斩：两道交叉成 X 形的刀光，第二道延迟 {@value #SLASH_SECOND_DELAY} 追上。
     * <p>
     * <b>「两道 + 交叉成 X」是它与其余刀光的唯一区分依据</b>：
     * 居合是单道宽弧、水鸟是多道窄弧交叉、狮子斩是三道平行。
     * 因此两道<b>都不参与削减</b>——只画一道就成了居合的缩水版。
     * </p>
     * <p>
     * 第二道的延迟不能太小（看不出是两下）也不能太大（读成两次独立攻击）。
     * {@value #SLASH_SECOND_DELAY} 在 680ms 时长下折合约 95ms，
     * 正好是「唰唰」两声的间隔。
     * </p>
     * <p>
     * <b>v2.0：</b>线宽 0.055 → 0.13，平面同样前移（{@link #SLASH_FORWARD_RATIO}），
     * 半径由 1.6 放大到 2.5。交叉点现在有足够的展开空间，读得出是 X 而不是一个亮团。
     * </p>
     */
    private static void drawDoubleSlash(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz,
                                        float radius, float baseAngle, float p) {
        float fade = 1f - smoothstep(0.45f, 1f, p);
        if (fade <= 0f) {
            return;
        }

        float fwdX = Mth.cos(baseAngle);
        float fwdZ = Mth.sin(baseAngle);
        float ux = 0f, uy = 1f, uz = 0f;
        float wx = -fwdZ, wy = 0f, wz = fwdX;

        float ccx = cx - fwdX * radius * SLASH_FORWARD_RATIO;
        float ccy = cyFoot + CHEST_HEIGHT;
        float ccz = cz - fwdZ * radius * SLASH_FORWARD_RATIO;

        float d = radius * 0.8f;

        // 第一道：左上 → 右下
        float sweep1 = easeOutCubic(clamp01(p / SLASH_SWEEP_RATIO));
        if (sweep1 > 0f) {
            planeStroke(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    -d, d, d, -d, SLASH_HALF, SLASH_SEGMENTS, sweep1,
                    C_SLASH_STEEL, C_SLASH_SHADOW, 0.88f * fade);
            planeStroke(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    -d, d, d, -d, SLASH_HALF * 0.34f, SLASH_SEGMENTS, sweep1,
                    C_SLASH_EDGE, C_SLASH_STEEL, 1.0f * fade);
        }

        // 第二道：右上 → 左下，错相追上，与第一道交叉成 X
        float sweep2 = easeOutCubic(clamp01((p - SLASH_SECOND_DELAY) / SLASH_SWEEP_RATIO));
        if (sweep2 > 0f) {
            planeStroke(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    d, d, -d, -d, SLASH_HALF, SLASH_SEGMENTS, sweep2,
                    C_SLASH_STEEL, C_SLASH_SHADOW, 0.88f * fade);
            planeStroke(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    d, d, -d, -d, SLASH_HALF * 0.34f, SLASH_SEGMENTS, sweep2,
                    C_SLASH_EDGE, C_SLASH_STEEL, 1.0f * fade);
        }
    }

    // ============================== 箭步上砍 ==============================

    /**
     * 箭步上砍：地面急停尘环 + 自下而上的上挑弧 + 顶端击飞火花。
     * <p>
     * <b>运动方向向上，这是全部战技里独一份的。</b>其余八个演出全是水平的
     * （横扫、外推、前推、内收），所以哪怕只用余光扫一眼，
     * 「有个东西往上挑」也能立刻读出是这一个。
     * </p>
     * <p>
     * 时间轴（总时长 750ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：统一起手爆闪；</li>
     *     <li>p ∈ [0, 0.35]：上挑弧自下而上扫出（缓出，起手最快）；</li>
     *     <li>p ∈ [0, 0.55]：地面尘环向外扩散——冲刺被硬生生刹住的动量；</li>
     *     <li>p ≈ 0.35：弧到顶，顶端爆一颗火花，对应附魔在 5 tick 后的击飞；</li>
     *     <li>p ∈ [0.5, 1]：整体渐隐，尘土落定。</li>
     * </ul>
     * <p>
     * 上挑弧不是直线而是<b>略带弧度</b>的（起点偏右、终点偏左），
     * 直线读起来像「戳」，带弧才像「挑」。
     * </p>
     * <p>
     * <b>v2.0：</b>线宽加倍、分段数 10 → 12、半径 2.0 → 2.8，
     * 弧顶现在能到目标头顶以上，「把人挑起来」这层意思出得来了。
     * </p>
     */
    private static void drawLungeUp(BufferBuilder b, Matrix4f m,
                                    float cx, float cyFoot, float cz,
                                    float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.5f, 1f, p);
        if (fade <= 0f) {
            return;
        }

        float fwdX = Mth.cos(baseAngle);
        float fwdZ = Mth.sin(baseAngle);
        float ux = 0f, uy = 1f, uz = 0f;
        float wx = -fwdZ, wy = 0f, wz = fwdX;

        // ===== 地面急停尘环 =====
        float dustP = clamp01(p / LUNGE_DUST_RATIO);
        if (dustP > 0f && dustP < 1f) {
            float rr = radius * easeOutCubic(dustP);
            float dustFade = (1f - dustP) * fade;
            int segments = VisualLod.scaleSegments(28, RING_SEGMENTS_MIN, detail);
            ring(b, m, cx, cyFoot + Y_OFFSET, cz, rr, segments, LUNGE_DUST_HALF,
                    C_LUNGE_DUST, 0.6f * dustFade);
            ring(b, m, cx, cyFoot + Y_OFFSET, cz, rr * 1.18f, segments,
                    LUNGE_DUST_HALF * 0.7f,
                    C_LUNGE_DEEP, 0.34f * dustFade);
        }

        // ===== 上挑弧：自下而上，略带弧度 =====
        float sweep = easeOutCubic(clamp01(p / LUNGE_SWEEP_RATIO));
        if (sweep <= 0f) {
            return;
        }
        float ccx = cx;
        float ccy = cyFoot;
        float ccz = cz;

        int drawn = Math.max(1, Math.round(LUNGE_SEGMENTS * sweep));
        float prevU = arcU(0f, radius);
        float prevV = arcV(0f, radius);
        for (int i = 1; i <= drawn; i++) {
            float t = (float) i / LUNGE_SEGMENTS;
            float u = arcU(t, radius);
            float v = arcV(t, radius);
            // 宽度包络：中段最厚、两端收尖
            float hw = LUNGE_HALF * Mth.sin((float) Math.PI * t);
            // 越靠前端越亮（刀尖在那儿）
            float aPrev = fade * (float) Math.pow((i - 1) / (float) LUNGE_SEGMENTS, 1.2);
            float aCur = fade * (float) Math.pow(t, 1.2);
            planeLine(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    prevU, prevV, u, v, hw * 2.4f, C_LUNGE_DUST,
                    0.35f * aPrev, 0.35f * aCur);
            planeLine(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    prevU, prevV, u, v, hw, C_LUNGE_EDGE, aPrev, aCur);
            prevU = u;
            prevV = v;
        }

        // ===== 顶端击飞火花：弧到顶那一下 =====
        if (sweep > 0.82f) {
            float k = (sweep - 0.82f) / 0.18f;
            float tipU = arcU(1f, radius);
            float tipV = arcV(1f, radius);
            float tx = ccx + wx * tipU + ux * tipV;
            float ty = ccy + wy * tipU + uy * tipV;
            float tz = ccz + wz * tipU + uz * tipV;
            billboardDiamond(b, m, tx, ty, tz,
                    radius * 0.22f * (0.6f + 0.6f * k), C_LUNGE_DUST, 0.5f * k * fade);
            billboardDiamond(b, m, tx, ty, tz,
                    radius * 0.12f * (0.6f + 0.6f * k), C_LUNGE_EDGE, 0.95f * k * fade);
        }
    }

    /**
     * 上挑弧的平面横坐标（起点偏右、终点偏左，形成弧度）。
     * <p>直线读起来像「戳」，带弧才像「挑」——这一点弧度是必要的。</p>
     *
     * @param t      沿弧的归一化位置（0=起点，1=终点）
     * @param radius 尺度（格）
     * @return 平面内的 w 分量
     */
    private static float arcU(float t, float radius) {
        return radius * (0.3f - 0.55f * t);
    }

    /**
     * 上挑弧的平面纵坐标（自下而上）。
     *
     * @param t      沿弧的归一化位置
     * @param radius 尺度（格）
     * @return 平面内的 u 分量
     */
    private static float arcV(float t, float radius) {
        return radius * (-0.25f + 1.35f * t);
    }

    // ============================== 格挡窗口 ==============================

    /**
     * 格挡窗口：盾前弹开火星 + 一个随时间收缩的准星。
     * <p>
     * <b>这个演出与其余八个的性质不同</b>——它不是打击反馈，而是<b>状态提示</b>：
     * 告诉持有者「接下来 0.5 秒里打出去有 25%×等级 的额外伤害」。
     * 因此它必须<b>贯穿整个窗口</b>而不是闪一下就没，
     * 而且尺度要克制、不能挡住正要反击的目标。
     * </p>
     * <p>
     * <b>准星的收缩就是倒计时。</b>外环半径按 {@code (1 - p)} 线性收缩，
     * 收到零的那一刻正好是加成失效的那一刻——
     * 这要求客户端时长与附魔的 10 tick 严格对齐，见
     * {@code CombatArtEffectManager.PARRY_WINDOW_DURATION_MS}。
     * <b>本演出的时长是六个里唯一没有在 v1.3 上调的</b>，原因即在此。
     * </p>
     * <p>
     * 时间轴（总时长 500ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：统一起手爆闪；</li>
     *     <li>p ∈ [0, 0.32]：八颗火星自盾前向外飞散 + 一圈白色冲击环——「架住了」；</li>
     *     <li>p ∈ [0, 1]：准星（外环 + 十字）持续收缩；</li>
     *     <li>p ∈ [0.8, 1]：整体渐隐。</li>
     * </ul>
     */
    private static void drawParryWindow(BufferBuilder b, Matrix4f m,
                                        float cx, float cyFoot, float cz,
                                        float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.8f, 1f, p);
        if (fade <= 0f) {
            return;
        }

        float fwdX = Mth.cos(baseAngle);
        float fwdZ = Mth.sin(baseAngle);
        float ux = 0f, uy = 1f, uz = 0f;
        float wx = -fwdZ, wy = 0f, wz = fwdX;

        // 准星中心：盾前一点点、胸口高度
        float ccx = cx + fwdX * (float) PARRY_FORWARD;
        float ccy = cyFoot + CHEST_HEIGHT;
        float ccz = cz + fwdZ * (float) PARRY_FORWARD;

        // ===== 准星：外环 + 十字，半径随剩余时间收缩（这就是倒计时）=====
        float reticle = radius * PARRY_RETICLE_RATIO * (1f - p * 0.72f);
        if (reticle > 0.05f) {
            int segments = VisualLod.scaleSegments(24, PLANE_RING_SEGMENTS_MIN, detail);
            double half = PARRY_LINE_HALF;
            // 暗金外描边：让准星在亮背景下也不会糊掉
            planeRing(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    reticle - half * 2.0, reticle + half * 2.0, segments, 0f,
                    C_PARRY_DEEP, 0.45f * fade, 0.45f * fade);
            planeRing(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    reticle - half, reticle + half, segments, 0f,
                    C_PARRY_GOLD, 0.95f * fade, 0.95f * fade);
            // 四段十字：不连到圆心，留出中间的空当，读起来才是准星而不是靶子
            float inner = reticle * 0.35f;
            float outer = reticle * 0.8f;
            planeLine(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    inner, 0f, outer, 0f, half, C_PARRY_SPARK, 0.95f * fade, 0.45f * fade);
            planeLine(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    -inner, 0f, -outer, 0f, half, C_PARRY_SPARK, 0.95f * fade, 0.45f * fade);
            planeLine(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    0f, inner, 0f, outer, half, C_PARRY_SPARK, 0.95f * fade, 0.45f * fade);
            planeLine(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    0f, -inner, 0f, -outer, half, C_PARRY_SPARK, 0.95f * fade, 0.45f * fade);
        }

        // ===== 起手：白色冲击环 + 弹开的火星 =====
        if (p < PARRY_SPARK_WINDOW) {
            float k = p / PARRY_SPARK_WINDOW;
            float flash = 1f - k;

            int segments = VisualLod.scaleSegments(24, PLANE_RING_SEGMENTS_MIN, detail);
            float burst = radius * (0.4f + 0.95f * easeOutCubic(k));
            planeRing(b, m, ccx, ccy, ccz, ux, uy, uz, wx, wy, wz,
                    burst - 0.055, burst + 0.055, segments, 0f,
                    C_PARRY_SPARK, 0.9f * flash, 0.9f * flash);

            // 火星：均布角度，按步长抽取（截断会让火星只朝一侧飞）
            if (VisualLod.keepLayer(detail, SPARK_KEEP_THRESHOLD)) {
                int drawn = VisualLod.scale(PARRY_SPARK_COUNT, detail);
                int step = Math.max(1, PARRY_SPARK_COUNT / drawn);
                float dist = radius * (0.3f + 1.2f * easeOutCubic(k));
                for (int i = 0; i < PARRY_SPARK_COUNT; i += step) {
                    // 角度基准用原始数量，保证保留火星的方位与全细节时一致
                    float a = TAU * i / PARRY_SPARK_COUNT + baseAngle * 0.3f;
                    float su = Mth.cos(a) * dist;
                    float sv = Mth.sin(a) * dist;
                    float sx = ccx + wx * su + ux * sv;
                    float sy = ccy + wy * su + uy * sv;
                    float sz = ccz + wz * su + uz * sv;
                    billboardDiamond(b, m, sx, sy, sz,
                            PARRY_SPARK_SIZE * (0.5f + 0.8f * flash),
                            C_PARRY_DEEP, 0.9f * flash);
                    billboardDiamond(b, m, sx, sy, sz,
                            PARRY_SPARK_SIZE * 0.5f * (0.5f + 0.8f * flash),
                            C_PARRY_SPARK, 0.98f * flash);
                }
            }
        }
    }

    // ============================== 盾牌冲击 ==============================

    /**
     * 盾牌冲击：朝正前方推出的<b>竖直扇面冲击墙</b> + 地面落点扇形 + 胸口高度推力线。
     *
     * <h4>v2.0 的核心改动：从贴地扇形改为竖直墙</h4>
     * <p>
     * 实测反馈「好像没效果」。排查下来这是六个演出里问题最严重的一个，
     * 而根源是<b>把冲击波画在了地上</b>：
     * </p>
     * <ul>
     *     <li>举盾时玩家视线朝前平视，脚下三格远的一片水平面基本落在视野死角；</li>
     *     <li>触发场景恰恰是「正在挨打」，此时视角常因受击 / 击退而剧烈晃动；</li>
     *     <li>原时长仅 420ms，晃过去就没了。</li>
     * </ul>
     * <p>
     * v2.0 把主体改成一段<b>竖直的扇面墙</b>：以持有者为圆心、沿正面 130°、
     * 从脚踝（{@value #BASH_WALL_BOTTOM}）到头顶以上（{@value #BASH_WALL_TOP}）
     * 立起来的一片弧形墙，随进度向前推出。这个形态直接从视野正中横过去，
     * 无论视角怎么晃都躲不开。
     * </p>
     * <p>
     * 地面扇形<b>保留</b>但降为辅助层——它标示冲击落点，
     * 与竖直墙一起构成「一堵墙从脚下推出去」的立体感；
     * 推力线也从贴地抬到了胸口高度（{@value #BASH_THRUST_HEIGHT}）。
     * </p>
     * <p>
     * <b>扇形而非整圆</b>这一点没有变：附魔的击退只作用于正面的攻击者，
     * 画成整圈会让玩家以为身后的人也被推开了。
     * </p>
     * <p>
     * <b>⚠ 关于铁傀儡：</b>铁傀儡的击退抗性是 1.0，
     * {@code attacker.knockback()} 对它完全无效——所以拿铁傀儡测时
     * 「怪没被推开」是原版机制的正常结果，与本特效无关。
     * 特效本身仍会正常播放。
     * </p>
     * <p>
     * 时间轴（总时长 550ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：统一起手爆闪；</li>
     *     <li>p ∈ [0, 0.6]：竖直墙与地面扇形一起向前推出（缓出）；</li>
     *     <li>p ∈ [0, 0.45]：三条推力线向前射出并淡出；</li>
     *     <li>p ∈ [0.4, 1]：整体渐隐。</li>
     * </ul>
     */
    private static void drawShieldBash(BufferBuilder b, Matrix4f m,
                                       float cx, float cyFoot, float cz,
                                       float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.4f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float push = easeOutCubic(clamp01(p / BASH_PUSH_RATIO));
        int segments = VisualLod.scaleSegments(BASH_SEGMENTS, RING_SEGMENTS_MIN, detail);
        float startAngle = baseAngle - BASH_SPAN * 0.5f;

        double rOuter = radius * (0.25 + 0.85 * push);
        if (rOuter <= 0.05) {
            return;
        }

        // ===== ⭐ 竖直扇面冲击墙：v2.0 的主体，也是可见性的关键 =====
        // 双层：外层灰、内层白，让墙有厚度而不是一张纸
        float wallBottom = cyFoot + BASH_WALL_BOTTOM;
        float wallTop = cyFoot + BASH_WALL_TOP;
        wall(b, m, cx, cyFoot, cz, (float) (rOuter * 1.06), startAngle, BASH_SPAN,
                segments, wallBottom, wallTop, C_BASH_GRAY,
                0.42f * fade, 0.06f * fade);
        wall(b, m, cx, cyFoot, cz, (float) rOuter, startAngle, BASH_SPAN,
                segments, wallBottom, wallTop, C_BASH_WHITE,
                0.72f * fade, 0.1f * fade);

        // ===== 地面落点扇形：辅助层，与竖直墙一起构成立体感 =====
        float y = cyFoot + Y_OFFSET;
        double rInner = rOuter * 0.55;
        band(b, m, cx, cz, y, rInner, rOuter, startAngle, BASH_SPAN, segments,
                C_BASH_WHITE, 0.55f * fade, 0.08f * fade);
        band(b, m, cx, cz, y, rOuter, rOuter * 1.26, startAngle, BASH_SPAN, segments,
                C_BASH_GRAY, 0.3f * fade, 0f);

        // ===== 推力线：把「往前推」这件事说得更直白。v2.0 抬到胸口高度 =====
        if (VisualLod.keepLayer(detail, THRUST_KEEP_THRESHOLD)) {
            float thrustP = clamp01(p / 0.45f);
            float thrustFade = (1f - thrustP) * fade;
            if (thrustFade > 0.01f) {
                float ty = cyFoot + BASH_THRUST_HEIGHT;
                double near = radius * 0.3;
                double far = radius * (0.5 + 0.8 * easeOutCubic(thrustP));
                for (int i = 0; i < BASH_THRUST_COUNT; i++) {
                    // 三条平行分布在扇面中轴两侧
                    float a = baseAngle + (i - 1) * (BASH_SPAN * 0.28f);
                    float ca = Mth.cos(a);
                    float sa = Mth.sin(a);
                    line(b, m, cx + ca * (float) near, cz + sa * (float) near,
                            cx + ca * (float) far, cz + sa * (float) far, ty,
                            BASH_THRUST_HALF, C_BASH_DEEP, 0.8f * thrustFade, 0f);
                }
            }
        }
    }

    // ==================== 平面几何基元 ====================
    // 「平面」指垂直于持有者朝向的那个竖直平面，由 u（世界上方）与 w（持有者正右方）张成。
    // 平面内二维坐标 (pu 沿 w, pv 沿 u) 映射到世界：P = center + w·pu + up·pv

    /**
     * 在平面内绘制一道<b>带宽度包络与扫出进度</b>的笔画（爪痕 / 刀光）。
     * <p>
     * 与朴素的直线段相比多了三件事：
     * </p>
     * <ol>
     *     <li><b>扫出</b>：只画到 {@code sweep} 的位置，实现「划过去」而不是「凭空出现一整条」；</li>
     *     <li><b>宽度包络</b>：两端收尖、中段最厚（{@code sin(π·t)}），这是刀痕该有的形状；</li>
     *     <li><b>亮度梯度</b>：越靠前端越亮（{@code t^1.2}），读作「刀尖刚过去、尾巴在散」。</li>
     * </ol>
     * <p>
     * 这三样是狮子斩与二连斩共用的，所以提成一个方法而不是各写一遍。
     * </p>
     *
     * @param u1       起点平面横坐标
     * @param v1       起点平面纵坐标
     * @param u2       终点平面横坐标
     * @param v2       终点平面纵坐标
     * @param maxHalf  中段最大半宽（格）
     * @param segments 细分段数
     * @param sweep    已扫出的比例（0~1）
     * @param colHead  前段颜色（只读）
     * @param colTail  后段颜色（只读）
     * @param alpha    峰值不透明度
     */
    private static void planeStroke(BufferBuilder b, Matrix4f m,
                                    float cx, float cy, float cz,
                                    float ux, float uy, float uz,
                                    float wx, float wy, float wz,
                                    float u1, float v1, float u2, float v2,
                                    float maxHalf, int segments, float sweep,
                                    float[] colHead, float[] colTail, float alpha) {
        if (sweep <= 0f || alpha <= 0.004f) {
            return;
        }
        int drawn = Math.max(1, Math.round(segments * sweep));
        float prevU = u1;
        float prevV = v1;
        for (int i = 1; i <= drawn; i++) {
            // t 是沿「完整笔画」的位置，而不是沿已画出部分——
            // 这样宽度包络与颜色分布在扫出过程中保持稳定，不会随 sweep 伸缩
            float t = (float) i / segments;
            float u = u1 + (u2 - u1) * t;
            float v = v1 + (v2 - v1) * t;
            float hw = maxHalf * Mth.sin((float) Math.PI * t);
            float tPrev = (float) (i - 1) / segments;
            float aPrev = alpha * (float) Math.pow(tPrev, 1.2);
            float aCur = alpha * (float) Math.pow(t, 1.2);
            // 前半段用头色、后半段用尾色，中间自然过渡
            float[] col = (t < 0.5f) ? colHead : colTail;
            planeLine(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                    prevU, prevV, u, v, hw, col, aPrev, aCur);
            prevU = u;
            prevV = v;
        }
    }

    /**
     * 在平面内绘制一条带宽度的线段（用平面二维坐标表达端点）。
     *
     * @param px1 起点平面横坐标（沿 w）
     * @param py1 起点平面纵坐标（沿 u）
     * @param px2 终点平面横坐标
     * @param py2 终点平面纵坐标
     * @param hw  线半宽（格）
     */
    private static void planeLine(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
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
        double a1u = px1 + nx, a1w = py1 + ny;
        double a2u = px1 - nx, a2w = py1 - ny;
        double b1u = px2 + nx, b1w = py2 + ny;
        double b2u = px2 - nx, b2w = py2 - ny;

        float ax1 = cx + wx * (float) a1u + ux * (float) a1w;
        float ay1 = cy + wy * (float) a1u + uy * (float) a1w;
        float az1 = cz + wz * (float) a1u + uz * (float) a1w;
        float ax2 = cx + wx * (float) a2u + ux * (float) a2w;
        float ay2 = cy + wy * (float) a2u + uy * (float) a2w;
        float az2 = cz + wz * (float) a2u + uz * (float) a2w;
        float bx1 = cx + wx * (float) b1u + ux * (float) b1w;
        float by1 = cy + wy * (float) b1u + uy * (float) b1w;
        float bz1 = cz + wz * (float) b1u + uz * (float) b1w;
        float bx2 = cx + wx * (float) b2u + ux * (float) b2w;
        float by2 = cy + wy * (float) b2u + uy * (float) b2w;
        float bz2 = cz + wz * (float) b2u + uz * (float) b2w;

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, by1, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, ay2, az2).color(r, g, bl, a1).endVertex();
    }

    /**
     * 在平面内绘制一个圆环带（annulus），内外边缘可分别指定 alpha。
     *
     * @param rInner   内半径
     * @param rOuter   外半径
     * @param segments 分段数
     * @param rot      整环旋转角（弧度）
     */
    private static void planeRing(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
                                  double rInner, double rOuter, int segments, float rot,
                                  float[] col, float alphaInner, float alphaOuter) {
        if (rOuter <= rInner || segments < 3) {
            return;
        }
        if (alphaInner <= 0.004f && alphaOuter <= 0.004f) {
            return;
        }
        if (rInner < 0.0) {
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

            float ox0 = cx + (wx * prevCos + ux * prevSin) * ro;
            float oy0 = cy + (wy * prevCos + uy * prevSin) * ro;
            float oz0 = cz + (wz * prevCos + uz * prevSin) * ro;
            float ox1 = cx + (wx * ca + ux * sa) * ro;
            float oy1 = cy + (wy * ca + uy * sa) * ro;
            float oz1 = cz + (wz * ca + uz * sa) * ro;
            float ix0 = cx + (wx * prevCos + ux * prevSin) * ri;
            float iy0 = cy + (wy * prevCos + uy * prevSin) * ri;
            float iz0 = cz + (wz * prevCos + uz * prevSin) * ri;
            float ix1 = cx + (wx * ca + ux * sa) * ri;
            float iy1 = cy + (wy * ca + uy * sa) * ri;
            float iz1 = cz + (wz * ca + uz * sa) * ri;

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

    // ==================== 水平与竖直几何基元 ====================

    /**
     * <b>竖直扇面墙</b>：沿一段圆弧立起来的一片弧形墙（v2.0 新增）。
     * <p>
     * 盾牌冲击的主体。相比贴地的扇形，它<b>直接占据视野中央</b>，
     * 这是「让玩家看得见」最直接的手段——详见 {@link #drawShieldBash} 的说明。
     * </p>
     * <p>
     * 每一段是一个竖直四边形（下边在 {@code yBottom}、上边在 {@code yTop}），
     * 底部实、顶部渐隐，于是墙看起来是从地面涌起来的一道气浪，
     * 而不是一块悬在空中的板子。
     * </p>
     * <p>
     * <b>没有做双面</b>：这是个弧面，玩家总是从凹侧或凸侧之一看它，
     * 而顶点色渲染不做背面剔除，单层就够；做双面只会白白翻倍顶点数。
     * </p>
     *
     * @param cx          圆心 X（相对相机）
     * @param cyFoot      脚底 Y（仅用于文档语义，实际高度由 yBottom/yTop 给出）
     * @param cz          圆心 Z
     * @param radius      墙所在的半径（格）
     * @param startAngle  起始角（弧度）
     * @param span        跨度（弧度）
     * @param segments    分段数
     * @param yBottom     墙底部高度（相对相机）
     * @param yTop        墙顶部高度（相对相机）
     * @param alphaBottom 底部不透明度
     * @param alphaTop    顶部不透明度
     */
    private static void wall(BufferBuilder b, Matrix4f m,
                             float cx, float cyFoot, float cz, float radius,
                             float startAngle, float span, int segments,
                             float yBottom, float yTop,
                             float[] col, float alphaBottom, float alphaTop) {
        if (radius <= 1.0e-4f || segments < 2) {
            return;
        }
        if (alphaBottom <= 0.004f && alphaTop <= 0.004f) {
            return;
        }
        if (yTop <= yBottom) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        for (int i = 0; i < segments; i++) {
            float a0 = startAngle + span * i / segments;
            float a1 = startAngle + span * (i + 1) / segments;
            float x0 = cx + radius * Mth.cos(a0);
            float z0 = cz + radius * Mth.sin(a0);
            float x1 = cx + radius * Mth.cos(a1);
            float z1 = cz + radius * Mth.sin(a1);

            // 两端渐隐，避免墙在扇形边界处被硬生生切断
            float edge0 = Mth.sin((float) Math.PI * i / segments);
            float edge1 = Mth.sin((float) Math.PI * (i + 1) / segments);
            // 用 0.35 做下限：完全渐隐会让墙两端"缺角"，反而显得是渲染错误
            float e0 = 0.35f + 0.65f * edge0;
            float e1 = 0.35f + 0.65f * edge1;

            b.vertex(m, x0, yBottom, z0).color(r, g, bl, alphaBottom * e0).endVertex();
            b.vertex(m, x1, yBottom, z1).color(r, g, bl, alphaBottom * e1).endVertex();
            b.vertex(m, x1, yTop, z1).color(r, g, bl, alphaTop * e1).endVertex();

            b.vertex(m, x0, yBottom, z0).color(r, g, bl, alphaBottom * e0).endVertex();
            b.vertex(m, x1, yTop, z1).color(r, g, bl, alphaTop * e1).endVertex();
            b.vertex(m, x0, yTop, z0).color(r, g, bl, alphaTop * e0).endVertex();
        }
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

    /**
     * 水平扇形圆环带（annulus 的一段），内 / 外边缘可分别指定 alpha。
     * <p>盾牌冲击用它画地面落点，与竖直墙一起构成立体感。</p>
     *
     * @param startAngle 起始角（弧度）
     * @param span       跨度（弧度）
     */
    private static void band(BufferBuilder b, Matrix4f m, float cx, float cz, float cy,
                             double rInner, double rOuter, float startAngle, float span,
                             int segments, float[] col, float alphaInner, float alphaOuter) {
        if (alphaInner <= 0.004f && alphaOuter <= 0.004f) {
            return;
        }
        if (rOuter <= rInner) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        for (int i = 0; i < segments; i++) {
            double a0 = startAngle + span * i / segments;
            double a1 = startAngle + span * (i + 1) / segments;
            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

            float ox0 = (float) (cx + rOuter * cos0), oz0 = (float) (cz + rOuter * sin0);
            float ox1 = (float) (cx + rOuter * cos1), oz1 = (float) (cz + rOuter * sin1);
            float ix0 = (float) (cx + rInner * cos0), iz0 = (float) (cz + rInner * sin0);
            float ix1 = (float) (cx + rInner * cos1), iz1 = (float) (cz + rInner * sin1);

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ox1, cy, oz1).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alphaInner).endVertex();

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alphaInner).endVertex();
            b.vertex(m, ix0, cy, iz0).color(r, g, bl, alphaInner).endVertex();
        }
    }

    /**
     * 带宽度的水平线段（两端 alpha 可不同）。
     *
     * @param hw 线半宽（格）
     */
    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float z1, float x2, float z2, float y,
                             double hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        double nx = -dz / len * hw;
        double nz = dx / len * hw;
        float r = col[0], g = col[1], bl = col[2];
        float ax1 = (float) (x1 + nx), az1 = (float) (z1 + nz);
        float ax2 = (float) (x1 - nx), az2 = (float) (z1 - nz);
        float bx1 = (float) (x2 + nx), bz1 = (float) (z2 + nz);
        float bx2 = (float) (x2 - nx), bz2 = (float) (z2 - nz);

        b.vertex(m, ax1, y, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, y, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, y, az2).color(r, g, bl, a1).endVertex();
    }

    // ==================== 世界空间几何基元 ====================

    /**
     * 任意朝向的「十字双面」线段：沿世界 X、Z 轴各画一个四边形，
     * 使线段从任意水平视角皆可见、无需 billboard 计算。
     * <p>不屈壁障的竖直碎片用它——碎片绕身体一圈朝各个方向推开，
     * 做成 billboard 会全部正对相机、失去「四散」的立体感。</p>
     *
     * @param hw 线半宽（格）
     * @param a1 起点端 alpha
     * @param a2 终点端 alpha
     */
    private static void crossQuad(BufferBuilder b, Matrix4f m,
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
     * 面向相机的小菱形光点：中心最亮、四角渐隐。
     * <p>仅 12 顶点，不参与分段缩放；是否绘制由调用方按保留阈值决定。
     * 角点内联为标量，零分配。</p>
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

    // ==================== 数学辅助 ====================

    /** 缓出（cubic）。 */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    /** 平滑阶跃（Hermite）。 */
    private static float smoothstep(float e0, float e1, float x) {
        if (e1 <= e0) {
            return x < e0 ? 0f : 1f;
        }
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    /** 夹取到 0~1。 */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
