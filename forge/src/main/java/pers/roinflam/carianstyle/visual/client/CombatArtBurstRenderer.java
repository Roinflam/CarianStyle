package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
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
 * 五个「数值型附魔」打击反馈的独立渲染器（纯客户端自绘）。
 * <p>
 * 负责 {@link CombatArtEffectPacket} 的类型 10 / 12 / 14 / 17 / 18：
 * 血刃、挥石魔法、黄金律法、对空射击、硬箭。
 * </p>
 *
 * <h3>⚠ 本文件最重要的一条规则：自身特效一律贴地</h3>
 * <p>
 * v1.0 的血刃与黄金律法都<b>挡视野</b>，这不是调参能救的，是设计错了：
 * </p>
 * <ul>
 *     <li>血刃在自己胸口立了一根 1.85 格高的血柱，还朝正前方伸出一把刃；</li>
 *     <li>黄金律法在身前 0.55 格处立了一块约 2 格高的矩形碑。</li>
 *  </ul>
 * <p>
 * <b>第一人称的相机就在胸口高度、朝正前方。</b>任何锚定在自己身上、
 * 又处在胸口高度或前方锥形区里的几何体，必然糊在脸上——半秒的演出足以让人打不中下一下。
 * </p>
 * <p>
 * 因此本版立下硬规则：
 * </p>
 * <ul>
 *     <li><b>锚在自己身上的演出（血刃、黄金律法）</b>——全部图元压在
 *         {@value #SELF_MAX_HEIGHT} 格以下，主体画在<b>地面平面</b>上。
 *         地面是唯一既能放大图形、又完全不进视野的地方；</li>
 *     <li><b>锚在目标身上的演出（挥石、对空、硬箭）</b>——可以有高度，
 *         因为目标离相机有距离，且玩家本来就在看它。</li>
 * </ul>
 * <p>
 * 贴地不等于不显眼：一圈鲜红的血环在脚下炸开，余光一定会捕捉到——
 * 而且它不会挡住你正要打的那个人。
 * </p>
 *
 * <h3>可见性调整</h3>
 * <ul>
 *     <li><b>血刃</b>——原配色是 {@code 0x6E0A14} 这种极暗的红，在泥土 / 石头 / 夜晚背景下
 *         基本看不见。改用鲜血红 {@code 0xE81830} + 溅射高光 {@code 0xFF7A78}，
 *         并把主体换成地面放射状血花，面积大得多；</li>
 *     <li><b>对空射击</b>——原来只在目标躯干处画一圈半径 3 的环，
 *         而目标至少在 5 格以外的高空，投影到屏幕上很小。现在半径提到
 *         {@value #SKY_BURST_RADIUS_RATIO} 倍、加了八道径向尖刺、
 *         补了一道<b>自爆点垂下的光柱</b>——那道光柱才是「刚才那一箭打在哪」最有效的指示；</li>
 *     <li><b>硬箭</b>——原来用四层同心平面环堆出「锥形」，实际渲染出来是一团糊的同心圆。
 *         改成三个读法明确的元素：钉入式十字冲击、<b>一道沿箭道后退并张开的单环</b>、
 *         向后散开的火星。运动方向清楚，形状也不再互相干扰。</li>
 * </ul>
 *
 * <h3>类型编号有空档是刻意的</h3>
 * <p>
 * 11（复仇誓言）、13（战士）、15（碎星）、16（献斗剑）四个类型已移除。
 * <b>刻意不把 17 / 18 往前挪</b>——重编号会让服务端与客户端在版本不一致时
 * 把一种特效当成另一种播，而留几个永不发送的空号成本为零。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 起手爆闪（3 层 × 12）              36（五个共用）
 * 血刃（飞出新月 + 自伤血溅 + 血滴）~450
 * 挥石魔法（法阵 + 石头 + 碎渣石屑） ~420
 * 黄金律法（底盘 + 双环 + 辐条 + 法环）~620
 * 对空射击（贯穿箭 + 双环 + 尖刺 + 光柱） ~760
 * 硬箭（十字 + 后退环 + 火星）       ~520
 * </pre>
 * <p>
 * 全部接入 {@link VisualLod}，含 {@link VisualLod#countInstance()}——
 * 拥挤度是全局共享的，少登记一个渲染器就会让 {@code crowdFactor} 被系统性高估、
 * 已接入的重量级渲染器削减不足。
 * </p>
 * <p>
 * 削减遵循既有两条原则：角度均布的元素（溅射线、尖刺、火星）按<b>步长抽取</b>而非截断，
 * 否则会只朝一侧；形状本身承载辨识度的元素（黄金律法的双环与法环小环、硬箭的十字）<b>完全不削</b>。
 * 起手爆闪永不削减——只有 36 顶点，却是「能不能被看见」的最后保险。
 * </p>
 *
 * <h3>v2.1：整体尺寸下调</h3>
 * <p>
 * 五个演出的半径基数在 {@code CarianStyleCombatArtEffects} 里统一收了约三成，
 * 本文件同步削减了那些会<b>与半径相乘、进一步放大体积</b>的系数：
 * 贯穿箭起始高度、尖刺长度、光柱长度、后退环行程、碎片飞散距离、碑文长宽比。
 * </p>
 * <p>
 * <b>刻意不动的是「粗细」类参数</b>——血环带宽、光柱半宽之外的线宽都保持原值。
 * 图形变小之后线再变细就真的看不见了：
 * <b>尺寸决定占多大地方，粗细决定看不看得见</b>，要收的是前者。
 * </p>
 *
 * @author FlameForge
 *
 * <h3>v2.2：黄金律法改为原著的环形符印；硬箭尺寸随伤害缩放</h3>
 * <ul>
 *     <li><b>黄金律法</b>——矩形碑文改为同心双环 + 十二辐条 + 偏心「法环」小环。
 *         原来选矩形纯粹是「全模组唯一形状」的形式考虑，
 *         而原著里黄金律法的视觉语言从来是圆的（详见 {@code drawGoldenLaw} 的注释）；</li>
 *     <li><b>硬箭</b>——本渲染器不变，缩放由服务端在
 *         {@code CarianStyleCombatArtEffects.hardArrow} 里算好后写进包的 radius 字段。
 *         打得越重演出越大，轻轻擦一下就只剩三分之一；</li>
 *     <li><b>血刃</b>——改为原作《血刃》朝前射出的<b>细长新月</b>
 *         （此前误按《鲜血斩击》的扩散波来做，而那是 {@code blood_slash} 的语汇）；</li>
 *     <li><b>挥石魔法</b>——改为「正经法阵 + 一块朴素石头」的反差演出。
 *         它画的是一个<b>梗</b>而不是一个法术，详见 {@code drawWaveStone} 的注释。</li>
 * </ul>
 *
 * <h3>v2.2 的性能改动</h3>
 * <p>本批改动<b>降低</b>了平均开销，不是提高：</p>
 * <ul>
 *     <li>{@link #ring} 与 {@link #arcBand} 改用<b>递推复用三角值</b>——
 *         相邻两段共用端点，每段的 {@code cos/sin} 调用由 4 次降到 2 次。
 *         {@code ring()} 是本文件调用最密的基元，单个演出里能被调七八次、每次 24~36 段，
 *         这一处的省量比任何一个演出的顶点削减都大；</li>
 *     <li>{@link #arcBand} 里每段一次的 {@code Math.pow(u, 1.5)} 换成
 *         {@code u × Math.sqrt(u)}——完全等价，但 {@code pow} 是本文件原先最贵的浮点调用；</li>
 *     <li>挥石魔法由「18 段横扫弧 + 6 碎片」改为「3 岩块 + 6 碎屑」，
 *         顶点数由约 480 降到约 390。</li>
 * </ul>
 *
 * @version 2.2
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatArtBurstRenderer {

    /** 距离裁剪（格） */
    private static final double CULL = 64.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** 离地高度偏移，避免地面图形与地形 z-fighting */
    private static final float Y_OFFSET = 0.02f;

    /**
     * 自身锚定演出的高度硬上限（格）。
     * <p>
     * <b>这是本文件的核心约束。</b>第一人称相机在约 1.62 格高、朝正前方，
     * 任何超过这个高度又贴在自己身上的东西都会进入视野。
     * {@value} 格大致是「低头才看得见」的高度，既能承载可观的体量，
     * 又绝不干扰瞄准。
     * </p>
     */
    private static final float SELF_MAX_HEIGHT = 0.65f;

    /**
     * 名义胸口高度（格，自脚底算起）。
     * <p><b>只用于目标锚定的演出</b>——自身锚定的一律不得使用（见 {@link #SELF_MAX_HEIGHT}）。</p>
     */
    private static final float CHEST_HEIGHT = 1.0f;

    /** 细节系数计算时的最小视觉半径（格），避免贴脸时被误判为远 */
    private static final double MIN_VISUAL_RADIUS = 2.0;

    // ==================== 起手爆闪 ====================

    /** 爆闪持续的进度比例：只在最初这一小段出现 */
    private static final float FLASH_WINDOW = 0.18f;
    /** 爆闪最大尺寸系数（× 半径） */
    private static final float FLASH_SIZE_RATIO = 0.6f;
    /** 爆闪不透明度 */
    private static final float FLASH_ALPHA = 0.95f;
    /**
     * 自身锚定演出的爆闪高度（格）。
     * <p>贴着地面闪，而不是在胸口闪——后者同样是在脸上开了一盏灯。</p>
     */
    private static final float FLASH_SELF_HEIGHT = 0.12f;

    // ==================== 配色（0xRRGGBB） ====================

    /** 通用爆闪白 */
    private static final int FLASH_WHITE = 0xFFFFFF;

    // ===== 血刃：鲜血红 + 溅射高光 + 深底。v2.0 整体提亮，原配色在多数地面上看不见 =====
    private static final int BLOOD_MAIN = 0xE81830;
    private static final int BLOOD_SPLASH = 0xFF7A78;
    private static final int BLOOD_DEEP = 0x8A0C18;

    // ===== 挥石魔法：石灰 + 岩暗 + 残魔紫 =====
    private static final int STONE_PALE = 0xB8B0A0;
    private static final int STONE_DARK = 0x7A6E5E;
    private static final int STONE_MANA = 0x9A7ADC;

    // ===== 黄金律法：黄金 + 律法白 + 碑暗 =====
    private static final int LAW_GOLD = 0xFFD86A;
    private static final int LAW_WHITE = 0xFFFBEA;
    private static final int LAW_DARK = 0xA8781C;

    // ===== 对空射击：天白 + 天蓝 + 深天 =====
    private static final int SKY_WHITE = 0xE0F4FF;
    private static final int SKY_BLUE = 0x5AB4F0;
    private static final int SKY_DEEP = 0x2A6A9A;

    // ===== 硬箭：铁灰 + 暗金 + 铁暗 =====
    private static final int HARD_IRON = 0xD8D8D0;
    private static final int HARD_GOLD = 0xF0BC48;
    private static final int HARD_DARK = 0x5A564E;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定：类加载时解包一次、此后永久复用的常量颜色数组。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，会永久污染该配色。
    private static final float[] C_FLASH = VisualColor.constant(FLASH_WHITE);
    private static final float[] C_BLOOD_MAIN = VisualColor.constant(BLOOD_MAIN);
    private static final float[] C_BLOOD_SPLASH = VisualColor.constant(BLOOD_SPLASH);
    private static final float[] C_BLOOD_DEEP = VisualColor.constant(BLOOD_DEEP);
    private static final float[] C_STONE_PALE = VisualColor.constant(STONE_PALE);
    private static final float[] C_STONE_DARK = VisualColor.constant(STONE_DARK);
    private static final float[] C_STONE_MANA = VisualColor.constant(STONE_MANA);
    private static final float[] C_LAW_GOLD = VisualColor.constant(LAW_GOLD);
    private static final float[] C_LAW_WHITE = VisualColor.constant(LAW_WHITE);
    private static final float[] C_LAW_DARK = VisualColor.constant(LAW_DARK);
    private static final float[] C_SKY_WHITE = VisualColor.constant(SKY_WHITE);
    private static final float[] C_SKY_BLUE = VisualColor.constant(SKY_BLUE);
    private static final float[] C_SKY_DEEP = VisualColor.constant(SKY_DEEP);
    private static final float[] C_HARD_IRON = VisualColor.constant(HARD_IRON);
    private static final float[] C_HARD_GOLD = VisualColor.constant(HARD_GOLD);
    private static final float[] C_HARD_DARK = VisualColor.constant(HARD_DARK);

    // ==================== LOD 下限与保留阈值 ====================

    /** 环 / 扇形带的最少分段数 */
    private static final int RING_SEGMENTS_MIN = 12;
    /** 平面环的最少分段数 */
    private static final int PLANE_RING_SEGMENTS_MIN = 10;
    /** 血滴 / 碎片 / 火星这类装饰层的保留阈值 */
    private static final float DEBRIS_KEEP_THRESHOLD = 0.4f;
    /** 极细装饰线（刻纹、光尘）的保留阈值 */
    private static final float DETAIL_LINE_KEEP_THRESHOLD = 0.45f;

    // ==================== 血刃（自身，全部贴地）====================

    /**
     * 血刃新月的角度跨度（弧度）：约 92°。
     * <p>原作《血刃》射出的是一道<b>细长的新月</b>，跨度过大就变成了《鲜血斩击》
     * 那种环绕自身的爆发——那是另一个附魔（{@code blood_slash}）的东西。</p>
     */
    private static final float BLOOD_CRESCENT_SPAN = 1.6f;
    /**
     * 新月自身的曲率半径系数（× 半径）。
     * <p><b>这个值在整段飞行里恒定不变</b>，弧只平移、不涨大——
     * 这正是「飞出去的弹体」与「从脚下扩散的冲击波」的分水岭。</p>
     */
    private static final float BLOOD_CRESCENT_CURVE = 0.72f;
    /** 新月圆心向前平移的距离系数（× 半径） */
    private static final float BLOOD_CRESCENT_TRAVEL = 1.85f;
    /** 新月所在高度（格）。必须远低于 {@link #SELF_MAX_HEIGHT} */
    private static final float BLOOD_CRESCENT_HEIGHT = 0.3f;
    /** 新月的半厚（格） */
    private static final float BLOOD_CRESCENT_THICK = 0.2f;
    /** 新月飞完全程所占的进度比例 */
    private static final float BLOOD_CRESCENT_RATIO = 0.62f;
    /** 新月弧的分段数 */
    private static final int BLOOD_CRESCENT_SEGMENTS = 18;

    /** 自伤血环扩散所占的进度比例 */
    private static final float BLOOD_RING_RATIO = 0.32f;
    /** 自伤血环带半宽（格）。贴地图形太细会被地表纹理吃掉 */
    private static final float BLOOD_RING_HALF = 0.2f;
    /** 自伤溅射线条数 */
    private static final int BLOOD_SPLAT_COUNT = 7;
    /** 溅射线半宽（格） */
    private static final float BLOOD_SPLAT_HALF = 0.1f;
    /** 自伤血泉高度（格）。只是「割了自己一下」的一点提示，做高就挡视野了 */
    private static final float BLOOD_FOUNT_HEIGHT = 0.3f;
    /** 血泉半宽（格） */
    private static final float BLOOD_FOUNT_HALF = 0.14f;
    /** 随斩波前散的血滴数量 */
    private static final int BLOOD_DROP_COUNT = 9;
    /** 血滴最高抛物线高度（格） */
    private static final float BLOOD_DROP_APEX = 0.45f;

    // ==================== 挥石魔法（目标）====================

    /** 辉石法阵的半径系数（× 半径） */
    private static final float STONE_SIGIL_RATIO = 0.5f;
    /** 法阵环带半宽（格） */
    private static final float STONE_SIGIL_RING_HALF = 0.045f;
    /** 法阵辐条数。做得像模像样，才衬得出后面那块石头有多朴素 */
    private static final int STONE_SIGIL_SPOKES = 6;
    /** 法阵辐条线半宽（格） */
    private static final float STONE_SIGIL_SPOKE_HALF = 0.03f;
    /** 法阵浮现所占的进度比例 */
    private static final float STONE_SIGIL_RATIO_IN = 0.12f;

    /**
     * 石头的尺寸（格）。
     * <p><b>刻意做得很大很笨</b>——它是整个演出的笑点所在，
     * 小了就成了一颗普通的法术弹丸，反差就没了。</p>
     */
    private static final float STONE_ROCK_SIZE = 0.42f;
    /** 石头挥过的角度跨度（弧度）：约 137° */
    private static final float STONE_SWING_SPAN = 2.4f;
    /** 石头挥击轨迹的半径系数（× 半径） */
    private static final float STONE_SWING_RADIUS = 0.85f;
    /** 石头挥击的高度（格，自目标脚底）。腰部高度，钝器该在的位置 */
    private static final float STONE_SWING_HEIGHT = 0.7f;
    /** 石头挥到命中点所占的进度比例 */
    private static final float STONE_SWING_RATIO = 0.3f;

    /** 钝击环扩散所占的进度比例 */
    private static final float STONE_IMPACT_RATIO = 0.4f;
    /** 钝击环带半宽（格）。做粗——钝器的冲击不该是一条细线 */
    private static final float STONE_IMPACT_HALF = 0.19f;
    /** 法阵溃散后的紫色魔力碎片数量 */
    private static final int STONE_SHARD_COUNT = 8;
    /** 石屑数量 */
    private static final int STONE_CHUNK_COUNT = 6;
    /** 石屑基准尺寸（格） */
    private static final float STONE_CHUNK_SIZE = 0.1f;
    /** 石屑飞散距离系数（× 半径） */
    private static final float STONE_CHUNK_SPREAD = 0.9f;

    // ==================== 黄金律法（自身，全部贴地）====================

    /** 符印外环半径系数（× 半径） */
    private static final float LAW_OUTER_RATIO = 1.0f;
    /** 符印内环半径系数（× 半径） */
    private static final float LAW_INNER_RATIO = 0.72f;
    /** 双环的环带半宽（格） */
    private static final float LAW_RING_HALF = 0.06f;
    /**
     * 内外环之间的辐条数。
     * <p>{@value} 是刻意选的：它同时是十二分圆，读起来像星盘 / 律法钟面，
     * 而不是随手画的放射线。八道太稀显得空，十六道在小尺寸下会糊成一片。</p>
     */
    private static final int LAW_SPOKE_COUNT = 12;
    /** 辐条线半宽（格） */
    private static final float LAW_SPOKE_HALF = 0.038f;
    /**
     * 「法环」小环的偏心距系数（× 半径，沿持有者正前方）。
     * <p>艾尔登法环的标志性构成就是<b>大环内套一个偏心的小环</b>——
     * 不是同心，正是那一点偏心让它成为「法环」而不是普通的圆。</p>
     */
    private static final float LAW_ELDEN_OFFSET_RATIO = 0.26f;
    /** 「法环」小环的半径系数（× 半径） */
    private static final float LAW_ELDEN_RATIO = 0.34f;
    /** 「法环」小环的环带半宽（格） */
    private static final float LAW_ELDEN_HALF = 0.05f;
    /** 中心底盘（半透明填充）的半径系数（× 半径），让符印在杂色地面上也立得住 */
    private static final float LAW_DISC_RATIO = 0.66f;
    /** 符印展开所占的进度比例 */
    private static final float LAW_OPEN_RATIO = 0.22f;
    /** 上升光尘数量 */
    private static final int LAW_MOTE_COUNT = 8;

    // ==================== 对空射击（目标，可以有高度）====================

    /**
     * 贯穿箭起始高度（格，自目标脚底往上）。
     * <p>v2.1 由 9.0 收到 {@value}——这根线是整个演出里最占体积的部分，
     * 却只是个引子，真正承载信息的是爆环和光柱。</p>
     */
    private static final float SKY_ARROW_TOP = 6.5f;
    /** 贯穿箭半宽（格） */
    private static final float SKY_ARROW_HALF = 0.12f;
    /** 贯穿箭下贯所占的进度比例 */
    private static final float SKY_DIVE_RATIO = 0.28f;
    /** 空爆环所在高度（格，自目标脚底）：目标躯干处，<b>不落地</b> */
    private static final float SKY_BURST_HEIGHT = 1.15f;
    /** 空爆环扩散所占的进度比例 */
    private static final float SKY_BURST_RATIO = 0.55f;
    /** 空爆环最终半径系数（× 半径）。v2.0 放大，原来在高空看着太小 */
    private static final float SKY_BURST_RADIUS_RATIO = 1.35f;
    /** 空爆环带半宽（格） */
    private static final float SKY_BURST_HALF = 0.18f;
    /** 径向尖刺条数 */
    private static final int SKY_SPIKE_COUNT = 8;
    /** 尖刺长度系数（× 半径）。v2.1 由 1.7 收到 {@value} */
    private static final float SKY_SPIKE_LENGTH = 1.35f;
    /** 尖刺半宽（格） */
    private static final float SKY_SPIKE_HALF = 0.1f;
    /**
     * 自爆点垂下的光柱长度（格）。「刚才那一箭打在哪」最有效的指示。
     * <p>v2.1 由 7.0 收到 {@value}。<b>收得比其它项保守</b>——
     * 它是竖直长线，在远距离下的可见性贡献远高于占的体积。</p>
     */
    private static final float SKY_COLUMN_DROP = 5.0f;
    /** 光柱半宽（格）。v2.1 由 0.30 收到 {@value} */
    private static final float SKY_COLUMN_HALF = 0.22f;

    // ==================== 硬箭（目标）====================

    /** 钉入式十字冲击的臂长系数（× 半径） */
    private static final float HARD_CROSS_LENGTH = 1.05f;
    /** 十字臂根部半宽（格），向尖端收细 */
    private static final float HARD_CROSS_HALF = 0.14f;
    /** 十字张开所占的进度比例 */
    private static final float HARD_CROSS_RATIO = 0.22f;
    /** 后退冲击环最终半径系数（× 半径） */
    private static final float HARD_RING_RADIUS_RATIO = 1.15f;
    /** 后退冲击环沿箭道退出的距离系数（× 半径）。v2.1 由 1.8 收到 {@value} */
    private static final float HARD_RING_TRAVEL = 1.4f;
    /** 后退冲击环带半宽（格） */
    private static final float HARD_RING_HALF = 0.14f;
    /** 后退冲击环走完全程所占的进度比例 */
    private static final float HARD_RING_RATIO = 0.65f;
    /** 火星数量 */
    private static final int HARD_SPARK_COUNT = 10;

    private CombatArtBurstRenderer() {
    }

    /**
     * 渲染回调：只处理本渲染器负责的五个类型。
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
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
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

            // 细节系数按「到演出视觉边界的距离」取，而非到中心
            double visualRadius = Math.max(fx.radius, MIN_VISUAL_RADIUS);
            double edge = Math.max(0.0, Math.sqrt(distSqr) - visualRadius);
            float detail = VisualLod.detail(edge * edge);
            VisualLod.countInstance();

            float rx = (float) dx;
            float ryFoot = (float) dy;
            float rz = (float) dz;

            // ⭐ 起手爆闪的高度按「自身 / 目标」分流：
            // 自身演出贴地闪，否则等于在第一人称视野里开了一盏灯
            boolean selfAnchored = isSelfAnchored(fx.type);
            float flashY = selfAnchored ? FLASH_SELF_HEIGHT : CHEST_HEIGHT;
            drawImpactFlash(builder, matrix, rx, ryFoot + flashY, rz, fx.radius, p);

            switch (fx.type) {
                case CombatArtEffectPacket.TYPE_BLOOD_BLADE ->
                        drawBloodBlade(builder, matrix, rx, ryFoot, rz, fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_WAVE_STONE ->
                        drawWaveStone(builder, matrix, rx, ryFoot, rz, fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_GOLDEN_LAW ->
                        drawGoldenLaw(builder, matrix, rx, ryFoot, rz, fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_SKY_SHOT ->
                        drawSkyShot(builder, matrix, rx, ryFoot, rz, fx.radius, fx.baseAngle, p, detail);
                case CombatArtEffectPacket.TYPE_HARD_ARROW ->
                        drawHardArrow(builder, matrix, rx, ryFoot, rz, fx.radius, fx.baseAngle, p, detail);
                default -> {
                    // isHandled 已经筛过，走不到这里；留着只是让 switch 完备
                }
            }
        }
    }

    /**
     * 本渲染器是否负责该类型。
     * <p>集中判断而不是散在 switch 里，是为了让主循环能在做距离裁剪<b>之前</b>就跳过
     * 不属于自己的特效——同屏可能同时存在前两批共十个战技的实例。</p>
     *
     * @param type 特效类型
     * @return 由本渲染器绘制返回 true
     */
    private static boolean isHandled(int type) {
        return type == CombatArtEffectPacket.TYPE_BLOOD_BLADE
                || type == CombatArtEffectPacket.TYPE_WAVE_STONE
                || type == CombatArtEffectPacket.TYPE_GOLDEN_LAW
                || type == CombatArtEffectPacket.TYPE_SKY_SHOT
                || type == CombatArtEffectPacket.TYPE_HARD_ARROW;
    }

    /**
     * 该类型是否锚定在<b>持有者自己</b>身上。
     * <p>
     * 这个判断决定爆闪画在哪个高度，也是各 draw 方法遵守
     * {@link #SELF_MAX_HEIGHT} 约束的依据。自身锚定的演出<b>永远不能在胸口高度画东西</b>。
     * </p>
     *
     * @param type 特效类型
     * @return 锚在自己身上返回 true
     */
    private static boolean isSelfAnchored(int type) {
        return type == CombatArtEffectPacket.TYPE_BLOOD_BLADE
                || type == CombatArtEffectPacket.TYPE_GOLDEN_LAW;
    }

    // ============================== 起手爆闪 ==============================

    /**
     * 统一的起手爆闪：一记三层白色闪光，只在最初 {@value #FLASH_WINDOW} 的进度内出现。
     * <p>
     * <b>这是可见性的最后保险。</b>精心设计的形状语言，如果玩家<b>压根没注意到有东西出现过</b>，
     * 就等于不存在。一记足够亮的闪光最便宜——它不需要玩家盯着看，
     * 余光扫到亮度变化就够了，之后玩家自然会去看那个位置发生了什么。
     * </p>
     * <p>
     * <b>三层同心菱形</b>（大而淡 / 中 / 小而实）而不是单层，
     * 是为了得到「中心过曝、边缘辉散」的观感——单层菱形会读成一个几何贴片。
     * </p>
     * <p><b>永不参与 LOD 削减</b>——只有 36 顶点。</p>
     *
     * @param cx     中心相对相机 X
     * @param cy     <b>闪光所在高度</b>（已含自身 / 目标的高度分流）
     * @param cz     中心相对相机 Z
     * @param radius 演出半径（决定闪光尺寸）
     * @param p      归一化进度
     */
    private static void drawImpactFlash(BufferBuilder b, Matrix4f m,
                                        float cx, float cy, float cz,
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
        float base = radius * FLASH_SIZE_RATIO;

        billboardDiamond(b, m, cx, cy, cz, base * (0.7f + 0.8f * k),
                C_FLASH, FLASH_ALPHA * 0.3f * intensity);
        billboardDiamond(b, m, cx, cy, cz, base * (0.4f + 0.5f * k),
                C_FLASH, FLASH_ALPHA * 0.6f * intensity);
        billboardDiamond(b, m, cx, cy, cz, base * (0.18f + 0.22f * k),
                C_FLASH, FLASH_ALPHA * intensity);
    }

    // ============================== 血刃（自身，全部贴地）==============================

    /**
     * 血刃：自伤血溅 + <b>一道朝正前方射出的细长血色新月</b> + 随之前散的血滴。
     * <p>
     * 时间轴（总时长 700ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：贴地爆闪；</li>
     *     <li>p ∈ [0, 0.30]：脚下自伤血环与七道溅射线炸开，同时涌起一小段血泉；</li>
     *     <li>p ∈ [0.06, 0.68]：血色新月自身前射出，圆心前移 {@value #BLOOD_CRESCENT_TRAVEL} 倍半径；</li>
     *     <li>p ∈ [0.15, 1]：血滴跟着新月向前抛散；</li>
     *     <li>p ∈ [0.5, 1]：整体渐隐。</li>
     * </ul>
     *
     * <h4>v2.3：认准原型是《血刃》，不是《鲜血斩击》</h4>
     * <p>
     * 这个演出前后返工两次，两次都错：
     * </p>
     * <ul>
     *     <li>第一版画成「脚下一圈血花」——只画了自伤，
     *         攻击那一半完全没画，玩家看到的是「我流血了」而不是「我甩出了什么」；</li>
     *     <li>第二版补上了弧，但<b>照着《鲜血斩击》做的</b>——
     *         以自身为圆心、半径逐渐变大的一道扩散波。</li>
     * </ul>
     * <p>
     * 第二版的问题在于<b>认错了原型</b>。《艾尔登法环》里这是两个不同的战技：
     * </p>
     * <ul>
     *     <li><b>《鲜血斩击》</b>——割破自身，在身周甩出一道<b>大范围的爆发</b>，
     *         是近身技；</li>
     *     <li><b>《血刃》</b>——割破自身，朝正前方<b>射出一道细长的新月</b>，
     *         是远程技。</li>
     * </ul>
     * <p>
     * 而本模组里这<b>两个战技各有各的附魔</b>：{@code blood_slash}（鲜血斩击）
     * 与 {@code blood_blade}（血刃）。把血刃画成扩散波，
     * 不但认错了原型，还占掉了鲜血斩击将来该用的那套语汇。
     * </p>
     * <p>
     * 现在改对了，关键在一处几何差别：<b>新月的曲率半径恒定不变，
     * 向前平移的是弧的圆心。</b>弧只飞、不涨大——这正是
     * 「射出去的弹体」与「从脚下扩散的冲击波」的分水岭，
     * 也是两个战技在观感上最直接的区别。
     * </p>
     * <p>
     * <b>它仍然贴着地面（{@value #BLOOD_CRESCENT_HEIGHT} 格）</b>——
     * 原作那道新月是齐腰高的，但那个高度在第一人称里正好糊住准星，
     * 这里必须为可玩性让步。压低之后读作「血刃贴地掠出去」，语义并没有损失。
     * </p>
     * <p>
     * 自伤那部分保留但缩小了：它是代价的提示，不该抢走新月的主体地位。
     * </p>
     * <p><b>削减：</b>斩波分段数缩放；溅射线按步长抽取；血滴按种子截断。</p>
     */
    private static void drawBloodBlade(BufferBuilder b, Matrix4f m,
                                       float cx, float cyFoot, float cz,
                                       float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.5f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float groundY = cyFoot + Y_OFFSET;

        // ===== 血刃新月：本演出的主体，一枚朝前飞出去的弹体 =====
        float waveP = clamp01((p - 0.06f) / BLOOD_CRESCENT_RATIO);
        if (waveP > 0f) {
            // ⭐ 关键：曲率半径恒定，向前平移的是弧的【圆心】而不是半径。
            // 若改成「圆心固定在玩家、半径逐渐变大」，画出来就是一圈从脚下扩散的
            // 冲击波 —— 那是《鲜血斩击》的语汇，不是《血刃》的
            float travel = radius * BLOOD_CRESCENT_TRAVEL * easeOutCubic(waveP);
            float curve = radius * BLOOD_CRESCENT_CURVE;
            float fwdX = Mth.cos(baseAngle);
            float fwdZ = Mth.sin(baseAngle);
            float ccx = cx + fwdX * travel;
            float ccz = cz + fwdZ * travel;

            // 飞得越远越淡、越薄 —— 血刃在空气里散掉的观感
            float wf = (1f - waveP) * fade;
            float thick = BLOOD_CRESCENT_THICK * (1f - waveP * 0.4f);
            int segments = VisualLod.scaleSegments(BLOOD_CRESCENT_SEGMENTS,
                    RING_SEGMENTS_MIN, detail);
            // 弧张在圆心【前方】，故弧背朝前、两翼向后掠 —— 这才是新月飞行的姿态
            float start = baseAngle - BLOOD_CRESCENT_SPAN * 0.5f;
            float waveY = cyFoot + BLOOD_CRESCENT_HEIGHT;

            arcBand(b, m, ccx, waveY, ccz, curve - thick * 1.9f, curve + thick * 1.9f,
                    start, BLOOD_CRESCENT_SPAN, 1f, segments, C_BLOOD_DEEP, 0.45f * wf);
            arcBand(b, m, ccx, waveY, ccz, curve - thick, curve + thick,
                    start, BLOOD_CRESCENT_SPAN, 1f, segments, C_BLOOD_MAIN, 0.95f * wf);
            arcBand(b, m, ccx, waveY, ccz, curve - thick * 0.32f, curve + thick * 0.32f,
                    start, BLOOD_CRESCENT_SPAN, 1f, segments, C_BLOOD_SPLASH, 0.75f * wf);
        }

        // ===== 自伤血环：代价的提示，刻意压得比斩波小 =====
        float ringP = clamp01(p / BLOOD_RING_RATIO);
        if (ringP > 0f) {
            float rr = radius * 0.62f * easeOutCubic(ringP);
            float rf = (1f - ringP * 0.7f) * fade;
            int segments = VisualLod.scaleSegments(24, RING_SEGMENTS_MIN, detail);
            ring(b, m, cx, groundY, cz, rr, segments, BLOOD_RING_HALF,
                    C_BLOOD_MAIN, 0.9f * rf);
            ring(b, m, cx, groundY, cz, rr, segments, BLOOD_RING_HALF * 0.4f,
                    C_BLOOD_SPLASH, 0.75f * rf);
        }

        // ===== 自伤溅射线 =====
        float splatP = clamp01(p / 0.3f);
        if (splatP > 0f) {
            int drawn = VisualLod.scale(BLOOD_SPLAT_COUNT, detail);
            int step = Math.max(1, BLOOD_SPLAT_COUNT / drawn);
            float grow = easeOutCubic(splatP);
            float splatFade = (1f - smoothstep(0.45f, 0.95f, p)) * fade;
            for (int i = 0; i < BLOOD_SPLAT_COUNT; i += step) {
                // 角度基准用原始条数，保证保留下来的溅射线方位与全细节时一致
                float ang = baseAngle + TAU * i / BLOOD_SPLAT_COUNT;
                long sd = seedFor((int) (baseAngle * 1000f), i + 20);
                float lenRand = 0.55f + 0.7f * rngFloat(sd);

                float inner = radius * 0.14f;
                float outer = radius * 0.78f * lenRand * grow;
                if (outer <= inner) {
                    continue;
                }
                float ca = Mth.cos(ang);
                float sa = Mth.sin(ang);
                line(b, m, cx + ca * inner, cz + sa * inner,
                        cx + ca * outer, cz + sa * outer, groundY,
                        BLOOD_SPLAT_HALF, C_BLOOD_MAIN, 0.9f * splatFade, 0f);
            }
        }

        // ===== 自伤血泉：唯一有高度的部分，且远低于视线 =====
        float fountP = clamp01(p / 0.26f);
        if (fountP > 0f) {
            float h = BLOOD_FOUNT_HEIGHT * easeOutCubic(fountP);
            float ff = (1f - smoothstep(0.3f, 0.75f, p)) * fade;
            crossQuad(b, m, cx, cyFoot, cz, cx, cyFoot + h, cz,
                    BLOOD_FOUNT_HALF * 1.9f, C_BLOOD_DEEP, 0.5f * ff, 0.05f * ff);
            crossQuad(b, m, cx, cyFoot, cz, cx, cyFoot + h, cz,
                    BLOOD_FOUNT_HALF, C_BLOOD_MAIN, 0.95f * ff, 0.2f * ff);
        }

        // ===== 血滴：跟着斩波向前抛散，最高不超过 SELF_MAX_HEIGHT =====
        if (VisualLod.keepLayer(detail, DEBRIS_KEEP_THRESHOLD)) {
            float dropP = clamp01((p - 0.15f) / 0.85f);
            if (dropP > 0f) {
                int count = VisualLod.scale(BLOOD_DROP_COUNT, detail);
                for (int i = 0; i < count; i++) {
                    long sd = seedFor((int) (baseAngle * 1000f), i + 40);
                    // 在斩波的角度跨度内散布，而不是绕一整圈 —— 血滴是跟着弧飞的
                    float spread = (rngFloat(sd) - 0.5f) * BLOOD_CRESCENT_SPAN;
                    sd = rngNext(sd);
                    float speed = 0.55f + 0.85f * rngFloat(sd);
                    sd = rngNext(sd);
                    float sizeRand = 0.7f + 0.7f * rngFloat(sd);

                    float t = clamp01(dropP - i * 0.025f);
                    if (t <= 0f) {
                        continue;
                    }
                    float ang = baseAngle + spread;
                    float horiz = radius * (BLOOD_CRESCENT_TRAVEL + BLOOD_CRESCENT_CURVE)
                            * 0.75f * speed * easeOutCubic(t);
                    // 抛物线：峰值刻意压到 BLOOD_DROP_APEX，绝不进入视线高度
                    float vert = BLOOD_DROP_APEX * 4f * t * (1f - t);
                    float px = cx + Mth.cos(ang) * horiz;
                    float pz = cz + Mth.sin(ang) * horiz;
                    float py = cyFoot + Y_OFFSET + vert;

                    float a = (1f - t) * 0.9f * fade;
                    if (a <= 0.01f) {
                        continue;
                    }
                    billboardDiamond(b, m, px, py, pz,
                            radius * 0.055f * sizeRand, C_BLOOD_MAIN, a);
                }
            }
        }
    }

    // ============================== 挥石魔法（目标）==============================

    /**
     * 挥石魔法：辉石法阵正儿八经地亮起 → <b>一块朴素的大石头抡过去</b> → 法阵碎成紫渣。
     * <p>
     * 时间轴（总时长 580ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.12]：目标身前浮现一个像模像样的辉石法阵（环 + 六道辐条）；</li>
     *     <li>p ∈ [0.05, 0.35]：一块大石头沿约 137° 的弧抡进来；</li>
     *     <li>p = 0.35 前后：命中。法阵<b>碎成紫色渣子四散</b>，灰色钝击环炸开；</li>
     *     <li>p ∈ [0.35, 1]：石屑落下；</li>
     *     <li>p ∈ [0.45, 1]：整体渐隐。</li>
     * </ul>
     *
     * <h4>这个演出画的是一个梗，不是一个法术</h4>
     * <p>
     * 本演出前后返工两次，两次都错在<b>把它当正经东西做</b>：
     * </p>
     * <ul>
     *     <li>第一版画成横扫刀光——读起来是近战挥击，可它挂在「魔法」名下；</li>
     *     <li>第二版改成原作《岩石弹》的三块土石隆起——庄严、有重力涟漪，
     *         结果是个<b>正经的重力系法术</b>。</li>
     * </ul>
     * <p>
     * 而这个附魔的笑点恰恰在于它<b>一点也不正经</b>：
     * 法师魔力聚了半天，掏出来的是块石头，而且比法术好使。
     * 名字本身就是这个反差——「挥石」是抡石头，「魔法」是硬贴上去的。
     * 把它做得越庄严，离原意就越远。
     * </p>
     *
     * <h4>所以视觉的全部重心是「反差」</h4>
     * <ul>
     *     <li><b>法阵要正经。</b>紫色辉石环加六道辐条，起手一板一眼，
     *         看着就是要放个大的；</li>
     *     <li><b>石头绝对不能发光。</b>没有光晕、没有拖尾、没有魔力包裹——
     *         就是一块灰扑扑的方石头。<b>这是整个演出唯一不能妥协的一条</b>：
     *         只要给它加一点光效，它就变成「石属性法术弹」了，梗当场消失；</li>
     *     <li><b>法阵在石头命中的瞬间碎掉。</b>紫色碎渣被挤得四散——
     *         那是「魔力白准备了」的视觉表达，也是整个笑话的落点。</li>
     * </ul>
     * <p>
     * 尺寸上石头刻意做得又大又笨（{@value #STONE_ROCK_SIZE} 格，
     * 比其它演出的粒子大一个量级），小了就成了普通弹丸。
     * </p>
     * <p><b>削减：</b>法阵与钝击环分段数缩放；辐条按步长抽取；碎渣与石屑按种子截断。
     * <b>石头本身不削</b>——它就是这个演出。</p>
     */
    private static void drawWaveStone(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz,
                                      float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.45f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float swingY = cyFoot + STONE_SWING_HEIGHT;
        // 命中时刻：石头抡到位的那一瞬
        boolean impacted = p >= STONE_SWING_RATIO;

        // ===== 辉石法阵：起手一板一眼，看着就是要放个大的 =====
        if (!impacted) {
            float in = easeOutCubic(clamp01(p / STONE_SIGIL_RATIO_IN));
            float sr = radius * STONE_SIGIL_RATIO * in;
            if (sr > 0.06f) {
                int seg = VisualLod.scaleSegments(24, RING_SEGMENTS_MIN, detail);
                ring(b, m, cx, swingY, cz, sr, seg, STONE_SIGIL_RING_HALF,
                        C_STONE_MANA, 0.9f * fade);
                ring(b, m, cx, swingY, cz, sr * 0.6f, seg, STONE_SIGIL_RING_HALF * 0.7f,
                        C_STONE_MANA, 0.7f * fade);

                int drawn = VisualLod.scale(STONE_SIGIL_SPOKES, detail);
                int step = Math.max(1, STONE_SIGIL_SPOKES / drawn);
                for (int i = 0; i < STONE_SIGIL_SPOKES; i += step) {
                    // 角度基准用原始条数，保证保留下来的辐条方位与全细节时一致
                    float ang = baseAngle + TAU * i / STONE_SIGIL_SPOKES;
                    float ca = Mth.cos(ang);
                    float sa = Mth.sin(ang);
                    line(b, m, cx + ca * sr * 0.6f, cz + sa * sr * 0.6f,
                            cx + ca * sr, cz + sa * sr, swingY,
                            STONE_SIGIL_SPOKE_HALF, C_STONE_MANA, 0.85f * fade, 0.85f * fade);
                }
            }
        }

        // ===== 石头：抡进来，一路不发光 =====
        {
            float swing = easeOutCubic(clamp01(p / STONE_SWING_RATIO));
            // 命中后石头略微回弹一点点再消失，读作「砸实了」
            float settle = impacted
                    ? clamp01((p - STONE_SWING_RATIO) / 0.18f)
                    : 0f;
            float ang = baseAngle + STONE_SWING_SPAN * (1f - swing) - STONE_SWING_SPAN * 0.5f;
            float dist = radius * STONE_SWING_RADIUS * (1f - swing * 0.72f)
                    + radius * 0.3f * settle;
            float px = cx + Mth.cos(ang) * dist;
            float pz = cz + Mth.sin(ang) * dist;
            float py = swingY + 0.18f * (1f - swing) + 0.12f * settle;

            float a = fade * (1f - settle * 0.85f);
            if (a > 0.01f) {
                long sd = seedFor((int) (baseAngle * 1000f), 7);
                float spin = rngFloat(sd) * TAU;
                // ⚠ 只有两层朴素的灰，没有任何光晕或魔力包裹。
                // 给它加一点光效，它就变成「石属性法术弹」了，梗当场消失
                billboardSquare(b, m, px, py, pz, STONE_ROCK_SIZE,
                        spin + swing * 2.6f, C_STONE_DARK, a);
                billboardSquare(b, m, px, py, pz, STONE_ROCK_SIZE * 0.66f,
                        spin + swing * 2.6f, C_STONE_PALE, a);
            }
        }

        if (!impacted) {
            return;
        }

        // ===== 以下都是命中之后才发生的 =====
        float post = clamp01((p - STONE_SWING_RATIO) / (1f - STONE_SWING_RATIO));

        // 钝击环：粗、灰、没有任何魔法感
        float impactP = clamp01((p - STONE_SWING_RATIO) / STONE_IMPACT_RATIO);
        if (impactP > 0f && impactP < 1f) {
            float rr = radius * easeOutCubic(impactP);
            float rf = (1f - impactP) * fade;
            int seg = VisualLod.scaleSegments(26, RING_SEGMENTS_MIN, detail);
            ring(b, m, cx, swingY, cz, rr, seg, STONE_IMPACT_HALF, C_STONE_DARK, 0.6f * rf);
            ring(b, m, cx, swingY, cz, rr, seg, STONE_IMPACT_HALF * 0.5f, C_STONE_PALE, 0.9f * rf);
        }

        // 法阵碎渣：紫色的魔力被石头挤得四散 —— 整个笑话的落点
        if (VisualLod.keepLayer(detail, DEBRIS_KEEP_THRESHOLD)) {
            int count = VisualLod.scale(STONE_SHARD_COUNT, detail);
            for (int i = 0; i < count; i++) {
                long sd = seedFor((int) (baseAngle * 1000f), i + 190);
                float ang = rngFloat(sd) * TAU;
                sd = rngNext(sd);
                float speed = 0.6f + 0.8f * rngFloat(sd);
                sd = rngNext(sd);
                float vert = (rngFloat(sd) - 0.35f) * 0.9f;

                float t = clamp01(post * 1.6f - i * 0.03f);
                if (t <= 0f) {
                    continue;
                }
                float dist = radius * 0.9f * speed * easeOutCubic(t);
                float a = (1f - t) * 0.9f * fade;
                if (a <= 0.01f) {
                    continue;
                }
                billboardDiamond(b, m,
                        cx + Mth.cos(ang) * dist, swingY + vert * t,
                        cz + Mth.sin(ang) * dist,
                        radius * 0.06f, C_STONE_MANA, a);
            }
        }

        // 石屑
        if (VisualLod.keepLayer(detail, DEBRIS_KEEP_THRESHOLD)) {
            int count = VisualLod.scale(STONE_CHUNK_COUNT, detail);
            for (int i = 0; i < count; i++) {
                long sd = seedFor((int) (baseAngle * 1000f), i + 150);
                float ang = rngFloat(sd) * TAU;
                sd = rngNext(sd);
                float speed = 0.55f + 0.85f * rngFloat(sd);
                sd = rngNext(sd);
                float sizeRand = 0.65f + 0.7f * rngFloat(sd);
                sd = rngNext(sd);
                float spin = rngFloat(sd) * TAU;

                float t = clamp01(post - i * 0.05f);
                if (t <= 0f) {
                    continue;
                }
                float dist = radius * STONE_CHUNK_SPREAD * speed * easeOutCubic(t);
                float px = cx + Mth.cos(ang) * dist;
                float pz = cz + Mth.sin(ang) * dist;
                float py = swingY + 0.55f * t - 1.7f * t * t;
                if (py < cyFoot + Y_OFFSET) {
                    py = cyFoot + Y_OFFSET;
                }
                float a = (1f - t) * 0.9f * fade;
                if (a <= 0.01f) {
                    continue;
                }
                billboardSquare(b, m, px, py, pz, STONE_CHUNK_SIZE * sizeRand,
                        spin + t * 5.5f, C_STONE_PALE, a);
            }
        }
    }

    // ============================== 黄金律法（自身，全部贴地）==============================

    /**
     * 黄金律法：脚下浮现黄金律法符印——同心双环 + 十二道辐条 + 偏心的「法环」小环
     * + 半透明底盘 + 低矮升腾光尘（<b>全部贴地</b>）。
     * <p>
     * 时间轴（总时长 650ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：贴地爆闪；</li>
     *     <li>p ∈ [0, 0.22]：底盘与同心双环自中心展开；</li>
     *     <li>p ∈ [0.12, 0.50]：十二道辐条自内环向外环生长；</li>
     *     <li>p ∈ [0.18, 0.55]：偏心的「法环」小环浮现；</li>
     *     <li>p ∈ [0.1, 0.8]：低矮金色光尘上升（高度不超过 {@value #SELF_MAX_HEIGHT}）；</li>
     *     <li>p ∈ [0.45, 1]：整体渐隐。</li>
     * </ul>
     *
     * <h4>v2.2：矩形碑文改为环形符印</h4>
     * <p>
     * 前一版画的是一块矩形石板。<b>那个选择是为了区分而区分——</b>当时的理由是
     * 「矩形在全模组独一份」，纯粹从形状不重复出发，却完全没顾及原著。
     * </p>
     * <p>
     * 而《艾尔登法环》里黄金律法的视觉语言<b>从来就是圆的</b>：
     * 拉达冈的光之环、律法系祷告的术式圈、乃至艾尔登法环本体，
     * 全部是同心金环配辐条的星盘式符印，没有一处是方的。
     * 一块方石板放在黄金律法名下，玩家第一眼就会觉得不对。
     * </p>
     * <p>
     * 现在的构成完全取自原著：
     * </p>
     * <ul>
     *     <li><b>同心双环</b>——律法系术式圈最基本的骨架；</li>
     *     <li><b>十二道辐条</b>——十二分圆读作星盘 / 律法钟面，
     *         这是「秩序」「律条」的视觉惯例；</li>
     *     <li><b>偏心的小环</b>——大环内套一个<b>不同心</b>的小环，
     *         这正是艾尔登法环本体的标志性构成。就是那一点偏心让它成为「法环」，
     *         同心的话就只是两个普通的圆。</li>
     * </ul>
     *
     * <h4>那怎么和其它金色演出区分</h4>
     * <p>
     * 放弃矩形之后确实失去了「唯一形状」这层保险，改由<b>结构</b>来区分：
     * </p>
     * <ul>
     *     <li><b>祈祷一击</b>是竖直光柱 + 单圈向外扩散的金环——它在<b>动</b>，是一记打击；</li>
     *     <li><b>黄金树祝福</b>是根须与落叶，有机形态，没有正圆；</li>
     *     <li><b>神圣净化</b>是三维十字，立体的；</li>
     *     <li><b>黄金律法</b>是<b>静止的、结构化的符印</b>——环不扩散，辐条不旋转，
     *         浮现出来就定在那里。这个「不动」本身就是律法的语义，
     *         也是它与上面三者最直接的区别。</li>
     * </ul>
     * <p>
     * <b>⚠ 仍然全部贴地。</b>免疫恰恰发生在被围殴的时候，那个时机挡视野尤其致命。
     * </p>
     * <p><b>削减：</b>环分段数缩放；辐条按步长抽取（均布角度，截断会只剩一侧）；
     * 光尘整层可跳过。双环与法环小环不削——它们是全部辨识度所在。</p>
     */
    private static void drawGoldenLaw(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz,
                                      float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.45f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float groundY = cyFoot + Y_OFFSET;
        float fx = Mth.cos(baseAngle);
        float fz = Mth.sin(baseAngle);

        float open = easeOutCubic(clamp01(p / LAW_OPEN_RATIO));
        float outerR = radius * LAW_OUTER_RATIO * open;
        float innerR = radius * LAW_INNER_RATIO * open;
        if (outerR <= 0.08f) {
            return;
        }
        int segments = VisualLod.scaleSegments(36, RING_SEGMENTS_MIN, detail);

        // ===== 半透明底盘：halfWidth 等于半径即得到实心圆盘 =====
        // 贴地图形最大的敌人是杂色地表，底盘把符印和地面分开
        float discR = radius * LAW_DISC_RATIO * open;
        ring(b, m, cx, groundY, cz, discR, segments, discR, C_LAW_DARK, 0.3f * fade);

        // ===== 同心双环：律法术式圈的骨架 =====
        ring(b, m, cx, groundY, cz, outerR, segments, LAW_RING_HALF, C_LAW_GOLD, 0.95f * fade);
        ring(b, m, cx, groundY, cz, innerR, segments, LAW_RING_HALF * 0.8f, C_LAW_GOLD, 0.9f * fade);

        // ===== 十二道辐条：十二分圆读作星盘 / 律法钟面 =====
        float spokeP = clamp01((p - 0.12f) / 0.38f);
        if (spokeP > 0f) {
            int drawn = VisualLod.scale(LAW_SPOKE_COUNT, detail);
            int step = Math.max(1, LAW_SPOKE_COUNT / drawn);
            float grow = easeOutCubic(spokeP);
            for (int i = 0; i < LAW_SPOKE_COUNT; i += step) {
                // 角度基准用原始条数，保证保留下来的辐条方位与全细节时一致
                float ang = baseAngle + TAU * i / LAW_SPOKE_COUNT;
                float ca = Mth.cos(ang);
                float sa = Mth.sin(ang);
                float from = innerR;
                float to = innerR + (outerR - innerR) * grow;
                if (to <= from) {
                    continue;
                }
                line(b, m, cx + ca * from, cz + sa * from,
                        cx + ca * to, cz + sa * to, groundY,
                        LAW_SPOKE_HALF, C_LAW_WHITE, 0.9f * fade, 0.9f * fade);
            }
        }

        // ===== 「法环」小环：大环内套一个偏心的小环 =====
        // 就是这一点偏心让它成为艾尔登法环，同心的话只是两个普通的圆
        float eldenP = clamp01((p - 0.18f) / 0.37f);
        if (eldenP > 0f) {
            float e = easeOutCubic(eldenP);
            float off = radius * LAW_ELDEN_OFFSET_RATIO;
            float er = radius * LAW_ELDEN_RATIO * e;
            if (er > 0.05f) {
                int eSeg = VisualLod.scaleSegments(24, RING_SEGMENTS_MIN, detail);
                ring(b, m, cx + fx * off, groundY, cz + fz * off, er, eSeg,
                        LAW_ELDEN_HALF, C_LAW_WHITE, 0.95f * fade);
            }
            // 中心一点，作为符印的视觉锚
            billboardDiamond(b, m, cx, groundY + 0.04f, cz,
                    radius * 0.1f * e, C_LAW_WHITE, 0.85f * fade);
        }

        // ===== 低矮升腾光尘：给贴地图形一点体积感，但绝不进入视线 =====
        if (VisualLod.keepLayer(detail, DEBRIS_KEEP_THRESHOLD)) {
            float moteP = clamp01((p - 0.1f) / 0.7f);
            if (moteP > 0f) {
                int count = VisualLod.scale(LAW_MOTE_COUNT, detail);
                for (int i = 0; i < count; i++) {
                    long sd = seedFor((int) (baseAngle * 1000f), i + 90);
                    float ang = rngFloat(sd) * TAU;
                    sd = rngNext(sd);
                    float distRand = 0.35f + 0.6f * rngFloat(sd);
                    sd = rngNext(sd);
                    float speedRand = 0.6f + 0.7f * rngFloat(sd);

                    float t = clamp01(moteP - i * 0.05f);
                    if (t <= 0f) {
                        continue;
                    }
                    float dist = radius * 0.75f * distRand;
                    float py = cyFoot + SELF_MAX_HEIGHT * speedRand * t;
                    float a = (1f - t) * 0.85f * fade;
                    if (a <= 0.01f) {
                        continue;
                    }
                    billboardDiamond(b, m,
                            cx + Mth.cos(ang) * dist, py, cz + Mth.sin(ang) * dist,
                            radius * 0.05f, C_LAW_WHITE, a);
                }
            }
        }
    }

    // ============================== 对空射击（目标）==============================

    /**
     * 对空射击：自更高处竖直贯下的箭光 + <b>目标高度处</b>的大爆环 + 八道径向尖刺
     * + <b>自爆点垂下的光柱</b> + 地面落点环。
     * <p>
     * 时间轴（总时长 800ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：起手爆闪；</li>
     *     <li>p ∈ [0, 0.28]：箭光自 {@value #SKY_ARROW_TOP} 格高处竖直贯下至目标躯干；</li>
     *     <li>p ∈ [0.20, 0.75]：大爆环在目标躯干高度向外扩散（半径达 {@value #SKY_BURST_RADIUS_RATIO} 倍）；</li>
     *     <li>p ∈ [0.22, 0.62]：八道径向尖刺自爆点甩出；</li>
     *     <li>p ∈ [0.25, 1]：光柱自爆点垂下 {@value #SKY_COLUMN_DROP} 格；</li>
     *     <li>p ∈ [0.28, 0.85]：地面落点环扩散；</li>
     *     <li>p ∈ [0.55, 1]：整体渐隐。</li>
     * </ul>
     *
     * <h4>v2.0 为什么整体放大并加了光柱</h4>
     * <p>
     * 旧版「不明显」的根因是<b>距离</b>：这个附魔要求目标高出射手至少 5 格，
     * 实战里往往是十几格外的空中目标。半径 3 格的环投影到那个距离上只有指甲盖大，
     * 而射手的注意力还在瞄准上，根本注意不到。
     * </p>
     * <p>
     * 三处补救：环半径提到 {@value #SKY_BURST_RADIUS_RATIO} 倍并加粗；
     * 加八道径向尖刺让轮廓在小尺寸下依然有「炸开」的读法；
     * 最关键的是<b>那道从爆点垂下的光柱</b>——它把一个高空的点变成一条竖直的长线，
     * 而竖直长线是远距离下最容易被余光捕捉的形状。
     * </p>
     * <p>
     * <b>爆环画在目标所在高度而非地面</b>，这是它与其余全部演出的关键区别——
     * 对空射击的语义正是「在空中把它打下来」，环若落到地面就完全不成立了。
     * 脚下那圈落点环则相反，它<b>刻意贴地</b>，两圈一高一低共同表达高度落差。
     * </p>
     */
    private static void drawSkyShot(BufferBuilder b, Matrix4f m,
                                    float cx, float cyFoot, float cz,
                                    float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.55f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float burstY = cyFoot + SKY_BURST_HEIGHT;

        // ===== 竖直贯穿箭：自高处扎下 =====
        float dive = easeOutCubic(clamp01(p / SKY_DIVE_RATIO));
        if (dive > 0f) {
            float headY = cyFoot + SKY_ARROW_TOP + (burstY - cyFoot - SKY_ARROW_TOP) * dive;
            float tailY = headY + 2.6f * (1f - dive * 0.5f);
            crossQuad(b, m, cx, tailY, cz, cx, headY, cz,
                    SKY_ARROW_HALF * 2.8f, C_SKY_DEEP, 0.06f * fade, 0.35f * fade);
            crossQuad(b, m, cx, tailY, cz, cx, headY, cz,
                    SKY_ARROW_HALF, C_SKY_BLUE, 0.18f * fade, 0.9f * fade);
            crossQuad(b, m, cx, tailY, cz, cx, headY, cz,
                    SKY_ARROW_HALF * 0.4f, C_SKY_WHITE, 0.25f * fade, fade);
            billboardDiamond(b, m, cx, headY, cz, radius * 0.2f, C_SKY_WHITE, 0.95f * fade);
        }

        // ===== 空爆环：⭐ 画在目标躯干高度，不落地 =====
        float burstP = clamp01((p - 0.2f) / SKY_BURST_RATIO);
        if (burstP > 0f && burstP < 1f) {
            float rr = radius * SKY_BURST_RADIUS_RATIO * easeOutCubic(burstP);
            float bf = (1f - burstP) * fade;
            int segments = VisualLod.scaleSegments(32, RING_SEGMENTS_MIN, detail);
            ring(b, m, cx, burstY, cz, rr, segments, SKY_BURST_HALF, C_SKY_WHITE, 0.95f * bf);
            ring(b, m, cx, burstY, cz, rr * 1.28f, segments, SKY_BURST_HALF * 0.75f,
                    C_SKY_BLUE, 0.6f * bf);
        }

        // ===== 八道径向尖刺：让轮廓在远距离小尺寸下依然读作「炸开」 =====
        float spikeP = clamp01((p - 0.22f) / 0.4f);
        if (spikeP > 0f) {
            int drawn = VisualLod.scale(SKY_SPIKE_COUNT, detail);
            int step = Math.max(1, SKY_SPIKE_COUNT / drawn);
            float grow = easeOutCubic(spikeP);
            float sf = (1f - smoothstep(0.5f, 0.9f, p)) * fade;
            for (int i = 0; i < SKY_SPIKE_COUNT; i += step) {
                // 角度基准用原始条数，保证保留下来的尖刺方位与全细节时一致
                float ang = baseAngle + TAU * i / SKY_SPIKE_COUNT;
                float ca = Mth.cos(ang);
                float sa = Mth.sin(ang);
                float inner = radius * 0.2f;
                float outer = radius * SKY_SPIKE_LENGTH * grow;
                line(b, m, cx + ca * inner, cz + sa * inner,
                        cx + ca * outer, cz + sa * outer, burstY,
                        SKY_SPIKE_HALF, C_SKY_WHITE, 0.9f * sf, 0f);
            }
        }

        // ===== 自爆点垂下的光柱：远距离下最有效的位置指示 =====
        float colP = clamp01((p - 0.25f) / 0.75f);
        if (colP > 0f) {
            float drop = SKY_COLUMN_DROP * easeOutCubic(colP);
            float cf = (1f - colP) * 0.7f * fade;
            crossQuad(b, m, cx, burstY, cz, cx, burstY - drop, cz,
                    SKY_COLUMN_HALF, C_SKY_BLUE, 0.55f * cf, 0f);
            crossQuad(b, m, cx, burstY, cz, cx, burstY - drop, cz,
                    SKY_COLUMN_HALF * 0.4f, C_SKY_WHITE, 0.7f * cf, 0f);
        }

        // ===== 地面落点环：与空爆环形成高度落差 =====
        float dustP = clamp01((p - 0.28f) / 0.57f);
        if (dustP > 0f && dustP < 1f) {
            float rr = radius * 1.2f * easeOutCubic(dustP);
            int segments = VisualLod.scaleSegments(24, RING_SEGMENTS_MIN, detail);
            ring(b, m, cx, cyFoot + Y_OFFSET, cz, rr, segments, 0.13f,
                    C_SKY_DEEP, 0.45f * (1f - dustP) * fade);
        }
    }

    // ============================== 硬箭（目标）==============================

    /**
     * 硬箭：钉入式十字冲击 + 一道沿箭道后退并张开的冲击环 + 向后散开的火星。
     * <p>
     * 时间轴（总时长 600ms）：
     * </p>
     * <ul>
     *     <li>p ∈ [0, 0.18]：起手爆闪；</li>
     *     <li>p ∈ [0, 0.22]：四臂十字自命中点炸开（在垂直于箭道的平面内）；</li>
     *     <li>p ∈ [0.08, 0.73]：冲击环沿箭道朝射手方向后退，边退边张开；</li>
     *     <li>p ∈ [0.05, 1]：火星向后锥形散开；</li>
     *     <li>p ∈ [0.4, 1]：整体渐隐。</li>
     * </ul>
     *
     * <h4>v2.0 为什么推翻旧的「锥形」</h4>
     * <p>
     * 旧版用四层<b>同心平面环</b>沿箭道排布来模拟一个锥。问题在于：四个圆环同时出现在
     * 同一个视线方向上，投影到屏幕后彼此重叠成一团同心圆，
     * <b>既读不出锥、也读不出运动方向</b>，只剩一团糊。
     * </p>
     * <p>
     * 现在拆成三个读法互不干扰的元素：
     * </p>
     * <ul>
     *     <li><b>十字冲击</b>——四条从中心向外收细的臂，静态、锐利，
     *         表达「钉进去了」。十字是硬边形状，小尺寸下也不会糊；</li>
     *     <li><b>单个后退环</b>——只有一个环，但它<b>一边沿箭道后退一边扩大</b>。
     *         单个运动物体的方向感远强于四个静止物体的排列；</li>
     *     <li><b>火星</b>——向后锥形散开，补足「冲击往回炸」的质感。</li>
     * </ul>
     * <p>
     * 顺带去掉了旧版的地面龟裂：那东西假设目标站在地上，
     * 而硬箭经常射中空中或水面的目标，龟裂会凭空浮在半空。
     * </p>
     */
    private static void drawHardArrow(BufferBuilder b, Matrix4f m,
                                      float cx, float cyFoot, float cz,
                                      float radius, float baseAngle, float p, float detail) {
        float fade = 1f - smoothstep(0.4f, 1f, p);
        if (fade <= 0f) {
            return;
        }
        float fwdX = Mth.cos(baseAngle);
        float fwdZ = Mth.sin(baseAngle);
        // 平面基：u = 世界上方，w = 垂直于箭道的水平方向
        float ux = 0f, uy = 1f, uz = 0f;
        float wx = -fwdZ, wy = 0f, wz = fwdX;
        float chestY = cyFoot + CHEST_HEIGHT;

        // ===== 钉入式十字冲击：锐利、静态，小尺寸下也不糊 =====
        float cross = easeOutCubic(clamp01(p / HARD_CROSS_RATIO));
        if (cross > 0f) {
            float len = radius * HARD_CROSS_LENGTH * cross;
            float crossFade = (1f - smoothstep(0.3f, 0.85f, p)) * fade;
            // 四臂：上下左右（平面二维坐标）
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, 0f, len,
                    HARD_CROSS_HALF, C_HARD_IRON, 0.95f * crossFade);
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, 0f, -len,
                    HARD_CROSS_HALF, C_HARD_IRON, 0.95f * crossFade);
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, len, 0f,
                    HARD_CROSS_HALF, C_HARD_IRON, 0.95f * crossFade);
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, -len, 0f,
                    HARD_CROSS_HALF, C_HARD_IRON, 0.95f * crossFade);
            // 暗金核心：短而实，作为「箭钉在这里」的锚点
            float coreLen = len * 0.42f;
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, 0f, coreLen,
                    HARD_CROSS_HALF * 0.55f, C_HARD_GOLD, crossFade);
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, 0f, -coreLen,
                    HARD_CROSS_HALF * 0.55f, C_HARD_GOLD, crossFade);
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, coreLen, 0f,
                    HARD_CROSS_HALF * 0.55f, C_HARD_GOLD, crossFade);
            planeTaper(b, m, cx, chestY, cz, ux, uy, uz, wx, wy, wz, 0f, 0f, -coreLen, 0f,
                    HARD_CROSS_HALF * 0.55f, C_HARD_GOLD, crossFade);
        }

        // ===== 后退冲击环：一边沿箭道退向射手、一边张开 =====
        float ringP = clamp01((p - 0.08f) / HARD_RING_RATIO);
        if (ringP > 0f && ringP < 1f) {
            float e = easeOutCubic(ringP);
            float back = radius * HARD_RING_TRAVEL * e;
            float rr = radius * HARD_RING_RADIUS_RATIO * e;
            if (rr > 0.06f) {
                float rf = (1f - ringP) * fade;
                int segs = VisualLod.scaleSegments(24, PLANE_RING_SEGMENTS_MIN, detail);
                float ringX = cx - fwdX * back;
                float ringZ = cz - fwdZ * back;
                planeRing(b, m, ringX, chestY, ringZ, ux, uy, uz, wx, wy, wz,
                        rr - HARD_RING_HALF, rr + HARD_RING_HALF, segs, 0f,
                        C_HARD_IRON, 0.9f * rf, 0.9f * rf);
                planeRing(b, m, ringX, chestY, ringZ, ux, uy, uz, wx, wy, wz,
                        rr - HARD_RING_HALF * 0.35, rr + HARD_RING_HALF * 0.35, segs, 0f,
                        C_HARD_GOLD, 0.75f * rf, 0.75f * rf);
            }
        }

        // ===== 火星：向后锥形散开 =====
        if (VisualLod.keepLayer(detail, DEBRIS_KEEP_THRESHOLD)) {
            float sparkP = clamp01((p - 0.05f) / 0.95f);
            if (sparkP > 0f) {
                int count = VisualLod.scale(HARD_SPARK_COUNT, detail);
                for (int i = 0; i < count; i++) {
                    long s = seedFor((int) (baseAngle * 1000f), i + 130);
                    float spreadAng = (rngFloat(s) - 0.5f) * 1.4f;
                    s = rngNext(s);
                    float speed = 0.55f + 0.9f * rngFloat(s);
                    s = rngNext(s);
                    float vertOff = (rngFloat(s) - 0.45f) * 1.2f;
                    s = rngNext(s);
                    float sizeRand = 0.6f + 0.8f * rngFloat(s);

                    float t = clamp01(sparkP - i * 0.03f);
                    if (t <= 0f) {
                        continue;
                    }
                    // 沿箭道反方向（朝射手）飞
                    float ang = baseAngle + (float) Math.PI + spreadAng;
                    float dist = radius * 1.5f * speed * easeOutCubic(t);
                    float px = cx + Mth.cos(ang) * dist;
                    float pz = cz + Mth.sin(ang) * dist;
                    float py = chestY + vertOff * t * 0.8f;

                    float a = (1f - t) * 0.95f * fade;
                    if (a <= 0.01f) {
                        continue;
                    }
                    billboardDiamond(b, m, px, py, pz,
                            radius * 0.05f * sizeRand, C_HARD_GOLD, a);
                    billboardDiamond(b, m, px, py, pz,
                            radius * 0.022f * sizeRand, C_HARD_IRON, a);
                }
            }
        }
    }

    // ==================== 平面几何基元 ====================
    // 「平面」指垂直于朝向的那个竖直平面，由 u（世界上方）与 w（正右方）张成。
    // 平面内二维坐标 (pu 沿 w, pv 沿 u) 映射到世界：P = center + w·pu + up·pv

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
        planeTaperInner(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                px1, py1, px2, py2, hw, hw, col, a1, a2);
    }

    /**
     * 在平面内绘制一条<b>根粗尖细</b>的线段（尖端收为零宽、零 alpha）。
     * <p>硬箭的十字冲击用它——收细的尖端让形状读作「锐利的钉入」而非「一根棍」。</p>
     *
     * @param hwRoot 根部半宽（格）
     */
    private static void planeTaper(BufferBuilder b, Matrix4f m,
                                   float cx, float cy, float cz,
                                   float ux, float uy, float uz,
                                   float wx, float wy, float wz,
                                   double px1, double py1, double px2, double py2,
                                   double hwRoot, float[] col, float alpha) {
        planeTaperInner(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                px1, py1, px2, py2, hwRoot, hwRoot * 0.08, col, alpha, 0f);
    }

    /**
     * 平面线段的通用实现：两端可分别指定半宽与 alpha。
     */
    private static void planeTaperInner(BufferBuilder b, Matrix4f m,
                                        float cx, float cy, float cz,
                                        float ux, float uy, float uz,
                                        float wx, float wy, float wz,
                                        double px1, double py1, double px2, double py2,
                                        double hw1, double hw2, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        double ddx = px2 - px1;
        double ddy = py2 - py1;
        double len = Math.sqrt(ddx * ddx + ddy * ddy);
        if (len < 1.0e-6) {
            return;
        }
        double n1x = -ddy / len * hw1;
        double n1y = ddx / len * hw1;
        double n2x = -ddy / len * hw2;
        double n2y = ddx / len * hw2;

        float r = col[0], g = col[1], bl = col[2];

        float ax1 = cx + wx * (float) (px1 + n1x) + ux * (float) (py1 + n1y);
        float ay1 = cy + wy * (float) (px1 + n1x) + uy * (float) (py1 + n1y);
        float az1 = cz + wz * (float) (px1 + n1x) + uz * (float) (py1 + n1y);
        float ax2 = cx + wx * (float) (px1 - n1x) + ux * (float) (py1 - n1y);
        float ay2 = cy + wy * (float) (px1 - n1x) + uy * (float) (py1 - n1y);
        float az2 = cz + wz * (float) (px1 - n1x) + uz * (float) (py1 - n1y);
        float bx1 = cx + wx * (float) (px2 + n2x) + ux * (float) (py2 + n2y);
        float by1 = cy + wy * (float) (px2 + n2x) + uy * (float) (py2 + n2y);
        float bz1 = cz + wz * (float) (px2 + n2x) + uz * (float) (py2 + n2y);
        float bx2 = cx + wx * (float) (px2 - n2x) + ux * (float) (py2 - n2y);
        float by2 = cy + wy * (float) (px2 - n2x) + uy * (float) (py2 - n2y);
        float bz2 = cz + wz * (float) (px2 - n2x) + uz * (float) (py2 - n2y);

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
        if (rOuter <= rInner || segments < 3 || rInner < 0.0) {
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

    // ==================== 水平几何基元 ====================

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
        // ⭐ 递推复用三角值：相邻两段共用一个端点，本段末端就是下一段起点。
        // 原实现每段算 4 次 Math.cos/sin，现在每段只算 2 次 Mth.cos/sin。
        // ring() 是本文件调用最密的基元（单个演出里能被调七八次、每次 24~36 段），
        // 这一处的省量比任何一个演出的顶点削减都大
        float prevCos = 1f;
        float prevSin = 0f;
        for (int i = 0; i < segments; i++) {
            float a1 = TAU * (i + 1) / segments;
            float cos1 = Mth.cos(a1);
            float sin1 = Mth.sin(a1);

            float ox0 = cx + rOuter * prevCos;
            float oz0 = cz + rOuter * prevSin;
            float ox1 = cx + rOuter * cos1;
            float oz1 = cz + rOuter * sin1;
            float ix0 = cx + rInner * prevCos;
            float iz0 = cz + rInner * prevSin;
            float ix1 = cx + rInner * cos1;
            float iz1 = cz + rInner * sin1;

            prevCos = cos1;
            prevSin = sin1;

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ox1, cy, oz1).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();
            b.vertex(m, ix0, cy, iz0).color(r, g, bl, alpha).endVertex();
        }
    }

    /**
     * 水平扇形弧带（带扫出进度与沿弧长的亮度梯度）。
     *
     * @param rInner     内半径
     * @param rOuter     外半径
     * @param startAngle 起始角（弧度）
     * @param span       总跨度（弧度）
     * @param sweep      已扫过的比例（0~1）
     * @param segments   整段跨度对应的细分数
     */
    private static void arcBand(BufferBuilder b, Matrix4f m,
                                float cx, float cy, float cz,
                                float rInner, float rOuter,
                                float startAngle, float span, float sweep,
                                int segments, float[] col, float alpha) {
        if (sweep <= 0f || alpha <= 0.004f || rOuter <= rInner) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        int drawn = Math.max(1, Math.round(segments * sweep));
        float segAngle = span * sweep / drawn;
        // ⭐ 同 ring()：递推复用三角值，每段省下一半 Mth.cos/sin
        float cos0 = Mth.cos(startAngle);
        float sin0 = Mth.sin(startAngle);
        float invDrawn = 1f / drawn;
        for (int i = 0; i < drawn; i++) {
            float a1 = startAngle + segAngle * (i + 1);
            float u0 = i * invDrawn;
            float u1 = (i + 1) * invDrawn;
            // ⭐ u^1.5 = u × √u。原来每段调一次 Math.pow —— 那是本文件最贵的一个浮点调用，
            // 而它算的只是个亮度梯度。改成乘一次开方后完全等价，开销降一个量级
            float alpha0 = alpha * u0 * (float) Math.sqrt(u0);
            float alpha1 = alpha * u1 * (float) Math.sqrt(u1);

            float cos1 = Mth.cos(a1), sin1 = Mth.sin(a1);

            float ox0 = cx + rOuter * cos0, oz0 = cz + rOuter * sin0;
            float ox1 = cx + rOuter * cos1, oz1 = cz + rOuter * sin1;
            float ix0 = cx + rInner * cos0, iz0 = cz + rInner * sin0;
            float ix1 = cx + rInner * cos1, iz1 = cz + rInner * sin1;

            cos0 = cos1;
            sin0 = sin1;

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha0).endVertex();
            b.vertex(m, ox1, cy, oz1).color(r, g, bl, alpha1).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha1).endVertex();

            b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha0).endVertex();
            b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha1).endVertex();
            b.vertex(m, ix0, cy, iz0).color(r, g, bl, alpha0).endVertex();
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
     * <p>仅 12 顶点，不参与分段缩放；角点内联为标量，零分配。</p>
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

    /**
     * 面向相机的<b>实心方块</b>（可绕视线旋转），四角不渐隐——保住棱角。
     * <p>
     * <b>这是挥石魔法的岩石碎片专用基元，也是全模组唯一的方块状粒子。</b>
     * 刻意不做边缘渐隐：渐隐会让方块糊成圆形柔光，那正是要避免的。
     * </p>
     *
     * @param size 半边长（格）
     * @param rot  绕视线方向的旋转角（弧度）
     */
    private static void billboardSquare(BufferBuilder b, Matrix4f m,
                                        float cx, float cy, float cz, float size, float rot,
                                        float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rgX = VisualBatch.rightX();
        float rgY = VisualBatch.rightY();
        float rgZ = VisualBatch.rightZ();
        float upX = VisualBatch.upX();
        float upY = VisualBatch.upY();
        float upZ = VisualBatch.upZ();

        float cosR = Mth.cos(rot) * size;
        float sinR = Mth.sin(rot) * size;
        // 四角在 billboard 平面内的坐标（已含旋转）
        float aU = cosR - sinR, aV = sinR + cosR;
        float bU = cosR + sinR, bV = sinR - cosR;

        float x1 = cx + rgX * aU + upX * aV, y1 = cy + rgY * aU + upY * aV, z1 = cz + rgZ * aU + upZ * aV;
        float x2 = cx + rgX * bU + upX * bV, y2 = cy + rgY * bU + upY * bV, z2 = cz + rgZ * bU + upZ * bV;
        float x3 = cx - rgX * aU - upX * aV, y3 = cy - rgY * aU - upY * aV, z3 = cz - rgZ * aU - upZ * aV;
        float x4 = cx - rgX * bU - upX * bV, y4 = cy - rgY * bU - upY * bV, z4 = cz - rgZ * bU - upZ * bV;

        b.vertex(m, x1, y1, z1).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x2, y2, z2).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x3, y3, z3).color(r, g, bl, alpha).endVertex();

        b.vertex(m, x1, y1, z1).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x3, y3, z3).color(r, g, bl, alpha).endVertex();
        b.vertex(m, x4, y4, z4).color(r, g, bl, alpha).endVertex();
    }

    // ==================== 无分配伪随机（xorshift64） ====================

    private static long seedFor(int base, int index) {
        long s = (base * 0x9E3779B97F4A7C15L) ^ ((index + 1L) * 0x85EBCA6BL);
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
