package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import pers.roinflam.carianstyle.visual.CarianStyleConditionDisplay;
import pers.roinflam.carianstyle.visual.CarianStyleStackDisplays;
import pers.roinflam.carianstyle.visual.StackDisplayRegistry;
import pers.roinflam.carianstyle.visual.StackHudManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 叠层 HUD 覆盖层（客户端）——常驻发光的玻璃卡片，满层时"燃烧"。
 * <p>
 * 屏幕左上角竖排显示所有叠层，每行一张卡片：左侧呼吸光条 + 名称 + “×层数” + 进度条。
 * 是否画进度条由<b>服务端下发的上限</b>决定（上限&gt;0 才画）。
 * <p>
 * <b>计数文本三态：</b>
 * <ul>
 *     <li>冷却倒计时项 → 显示剩余秒数（如 "5s"）；</li>
 *     <li>徽标行（血之共鸣 / 月之共鸣 / 隐身中，以 {@code Stacks(1,0,false)} 注册）→ 显示「已触发」，
 *         因其表达布尔激活态而非可累加层数（详见 {@link #isBadgeRow}）；</li>
 *     <li>其余叠层项（连击数等）→ 显示 "×层数"。</li>
 * </ul>
 * <p>
 * <b>常驻动效（不满层也一直在动，让整体"活"起来）：</b>
 * <ul>
 *     <li>卡片外侧多层强调色辉光晕，随正弦缓慢呼吸；</li>
 *     <li>每张卡周期性掠过一道高光"扫光"（玻璃质感，按行错相，不会同时闪）；</li>
 *     <li>进度条为竖向玻璃渐变 + 持续流动的高光带 + 领头亮条，绝不瞬跳；</li>
 *     <li>顶部高光棱边 + 底部暗角，卡片有厚度感；圆角描边。</li>
 * </ul>
 * <p>
 * <b>满层"燃烧"特效（仅满层附近激活，平时一行 if 直接跳过、零开销）：</b>
 * 进度条转熔岩竖向渐变 + 横向扫动热点；条上沿窜起闪烁火舌；填充区升起淡出火星；
 * 描边/光条/辉光转为闪烁余烬橙；层数数字转热色并随心跳呼吸放大。火焰强度 {@code heat}
 * 平滑升降，进出满层不突兀。
 * <p>
 * <b>消失误闪修复：</b>仅"上一帧仍在显示列表中"的行在层数增长时才触发白闪；短暂消失后重现
 * （切走武器再切回、服务端计数器仍在增长）的那一帧只静默同步层数，不误闪。
 * <p>
 * <b>淡出末段闪烁修复（原版字体特性）：</b>Minecraft 的 {@code Font.drawInternal} 含
 * {@code if ((color & 0xFC000000) == 0) color |= 0xFF000000;}——当文字 alpha 字节 &lt; 4
 * （透明度极低）时强制改为完全不透明。卡片淡出末段 alpha 趋近 0 那一两帧，白色名称/层数文字
 * 会被原版强制全亮、闪一下。修复：文字 alpha 低于 {@link #MIN_TEXT_ALPHA} 直接跳过绘制
 * （此时本就近乎不可见），从根上绕开该分支；而 {@code fill}/{@code fillGradient} 不经过字体
 * 渲染、无此问题，故背景/光条/进度条填充仍平滑淡出。
 * <p>
 * 所有外观参数集中在顶部常量。全部基于帧间隔 dt，与帧率无关。
 *
 * <h3>v2 性能：三类动态文本全部改为预建 {@link Component} 缓存</h3>
 * <p>
 * <b>问题：</b>本覆盖层每帧、每行都要产生三类文本，而它们此前<b>全是现场拼出来的</b>：
 * </p>
 * <pre>
 * "×" + a.lastCount              // 每行每帧一个新 String
 * seconds + "s"                  // 冷却项同上
 * translatableWithFallback(...).getString()   // 徽标：一个 Component + 一个 String
 * </pre>
 * <p>
 * 单帧的量不大（同屏叠层项通常几行），但这是<b>每帧都在跑的 UI 代码</b>，
 * 60fps × 数行 × 三类，一天下来是相当可观的垃圾量；而这些字符串的<b>取值集合极其有限</b>——
 * 层数就那几百种、秒数最多三百来种、徽标文案只有一种。
 * </p>
 * <p>
 * <b>做法：</b>全部改为<b>类加载时预建的 {@link Component} 数组</b>
 * （{@link #COUNT_LABELS} / {@link #SECOND_LABELS} / {@link #BADGE_LABEL}），
 * 渲染时按下标取用，零分配。相应地 {@link #drawScaledString} 的形参由
 * {@code String} 改为 {@link Component}——{@code Font.width(FormattedText)} 与
 * {@code GuiGraphics.drawString(Font, Component, ...)} 都有现成重载，改动很小。
 * </p>
 *
 * <h4>为什么徽标能安全地缓存成一个常量</h4>
 * <p>
 * 这是本次唯一需要想一下的地方：{@link #BADGE_LABEL} 是
 * {@code Component.translatableWithFallback(...)}，而<b>翻译组件是在渲染时才解析当前语言的</b>
 * ——它内部持有的是「翻译键 + 回退值」，不是某种语言的成品字符串。
 * 因此把实例缓存下来，玩家中途切换语言后<b>下一帧就会自动显示新语言</b>，不需要任何失效逻辑。
 * </p>
 * <p>
 * 相比之下，原实现每帧调 {@code .getString()} 反而是把它<b>立刻拍平成当前语言的 String</b>，
 * 既产生垃圾又没有任何好处。
 * </p>
 *
 * <h4>缓存容量与回退</h4>
 * <p>
 * 两个数组都设了上限，超出范围时<b>回退到动态创建</b>（{@link #countLabel} /
 * {@link #cooldownLabel}）：
 * </p>
 * <ul>
 *     <li>{@link #COUNT_LABELS} 覆盖 0~{@value #COUNT_LABEL_CACHE_SIZE}-1。
 *         常见叠层的上限都远低于此（尸山血海 50、居合 198、龙徽大盾 20、腐败翼剑 20）；
 *         唯一可能溢出的是<b>忍耐</b>——它显示的是「储存的伤害值」而非层数，
 *         上限为 {@code 最大生命 × 等级 × 0.4}，高血量时可达四位数。
 *         但忍耐的数值只在受击时变化、频率远低于每帧，走回退路径完全可接受；</li>
 *     <li>{@link #SECOND_LABELS} 覆盖 0~{@value #SECOND_LABEL_CACHE_SIZE}-1 秒。
 *         本模组最长冷却是回溯 / 巨剑方阵的 6000 tick = 300 秒，
 *         留到 600 秒是给将来加更长冷却的附魔留余量。</li>
 * </ul>
 *
 * <h4>刻意没做的一处</h4>
 * <p>
 * {@link #anims} 的 {@code entrySet().iterator()} 每帧也会分配一个迭代器对象。
 * <b>没有改</b>——要消掉它就得放弃「遍历中删除」的写法，改成先收集待删键再二次遍历，
 * 那反而要多分配一个集合。一个迭代器换一份可读性，这笔交易不划算。
 * </p>
 *
 * <h3>v3：行数溢出屏幕的三级兜底（自适应缩放 → 多列 → 折叠）</h3>
 *
 * <h4>这个问题不是「以后会有」，而是「一直都在」</h4>
 * <p>
 * 原实现把每一行硬排在 {@code ANCHOR_Y + index × ROW_STRIDE}，<b>没有任何上界检查</b>。
 * 按 {@value #ROW_STRIDE} 像素的行距算，1080p + GUI 缩放 3 的逻辑高度只有 360 像素，
 * 也就是说<b>超过 14 行就会直接画到屏幕外面去</b>，多出来的行连同它们承载的信息一起消失，
 * 而且是<b>无声消失</b>——玩家不会知道自己少看了几行。
 * </p>
 * <p>
 * 注册项当前已有三十余个，本次又新增八个。虽然「同时凑齐四十项」在实战中不太可能，
 * 但十四行这个门槛<b>是随手就能碰到的</b>：一身叠层护甲 + 一把多附魔武器就有七八行，
 * 再进一场群战（誓复仇、战士流血、冷却倒计时接连出现）立刻见顶。
 * </p>
 *
 * <h4>三级兜底，逐级才降质</h4>
 * <ol>
 *     <li><b>缩放</b>——先整体缩小，直到 {@value #MIN_SCALE}。缩放是<b>唯一不损失信息</b>的手段，
 *         所以排在第一位。下限取 {@value #MIN_SCALE} 是因为再小文字就开始糊了：
 *         GUI 缩放 3 时 0.62 相当于有效缩放 1.86，仍在原版 GUI 缩放 2 之上；</li>
 *     <li><b>多列</b>——缩到下限还放不下就往右开新列，最多 {@value #MAX_COLUMNS} 列。
 *         多列同样不损失信息，只是吃屏幕宽度，所以排第二；</li>
 *     <li><b>折叠</b>——列也开满了才丢行，并在最后一行显示 {@code +N} 告诉玩家
 *         「还有 N 项没显示」。<b>关键在于「明示」</b>：原实现是无声吞掉，
 *         而这里至少让玩家知道有东西被藏了。</li>
 * </ol>
 * <p>
 * 正常游戏里第三级基本走不到——{@value #MAX_COLUMNS} 列 × 缩放后每列十几行，
 * 容量在四五十项以上，已经超过注册总数。它存在的意义是<b>保证任何情况下都不会静默丢信息</b>。
 * </p>
 *
 * <h4>被折叠掉的一定是最不重要的</h4>
 * <p>
 * 折叠丢的是<b>末尾</b>若干行，而 {@code StackHudManager} v1.1 的排序保证末尾一定是
 * 「只显示数字的常驻状态」（穿着就一直亮着的减伤、档位之类），
 * 冷却倒计时与带进度条的叠层排在前面、永远不会被折叠。
 * <b>这两处改动是配套的，改一个必须看另一个。</b>
 * </p>
 *
 * <h4>为什么缩放要包住整个渲染而不是逐元素乘系数</h4>
 * <p>
 * 卡片由几十次 {@code fill} / {@code fillGradient} / {@code drawString} 拼出来，
 * 逐元素乘系数意味着每一处坐标都要改，且圆角描边、1px 高光棱边这类
 * <b>本来就依赖整数像素</b>的元素会在乘完之后错位。
 * </p>
 * <p>
 * 直接往 {@code PoseStack} 上压一个 {@code scale}，所有绘制自动跟着缩——
 * {@code GuiGraphics} 的 fill / fillGradient / drawString 在 1.20.1 都走
 * {@code pose.last().pose()}，全部受矩阵影响。代价是<b>逻辑坐标系被放大了</b>：
 * 缩放 {@code s} 之后可用的逻辑高度变成 {@code height / s}，
 * 布局计算必须用这个值而不是屏幕高度（见 {@link #computeLayout}）。
 * </p>
 * <p>
 * <b>副作用：</b>火星、扫光这些按像素定义的细节会一起缩小。这是缩放方案的固有代价，
 * 也正是把缩放下限卡在 {@value #MIN_SCALE} 的原因之一。
 * </p>
 *
 * <h4>换列 / 换行都是平滑的</h4>
 * <p>
 * {@link Anim} 新增了 {@code x} / {@code targetX} 两个字段，与原有的 {@code y} 一样走
 * 指数平滑。<b>没有让它瞬移</b>——行数变化时（某项消失导致后面的整体上移、
 * 或列容量变化导致整列迁移）如果直接跳位，视觉上就是一堆卡片突然乱窜。
 * 现在它们会斜着滑过去。
 * </p>
 * <p>
 * 注意 {@code x} 与既有的 {@code exitX}（出现 / 消失时的水平滑动）是<b>两回事</b>，
 * 相加后才是最终位置：前者是「我属于第几列」，后者是「我正在进场还是退场」。
 * 合并成一个字段会让「新行在第二列淡入」变成从屏幕左边滑到第二列的长距离飞行。
 * </p>
 *
 * @author FlameForge
 * @version 3
 */
@OnlyIn(Dist.CLIENT)
public final class StackHudOverlay implements IGuiOverlay {

    /** 单例 */
    public static final StackHudOverlay INSTANCE = new StackHudOverlay();

    /**
     * 名称翻译组件缓存：serialId -> 已构建的 {@link Component}。
     * <p><b>性能（视觉零变化）：</b>原先每帧每行都 {@code Component.translatable(...)} 新建一次组件
     * （每帧分配）。翻译组件在渲染时才解析当前语言，缓存其实例可跨帧复用、且语言切换仍正确，
     * 故按 serialId 缓存，仅每个附魔首帧分配一次。注册项数量有限（数十个），缓存有界、无需清理。
     * 仅客户端渲染线程访问，无并发问题。
     */
    private static final Map<Integer, Component> NAME_CACHE = new HashMap<>();

    // ===== 布局常量 =====
    private static final int ANCHOR_X = 6;
    private static final int ANCHOR_Y = 6;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_STRIDE = 25;
    private static final int BAR_WIDTH = 50;
    private static final int BAR_HEIGHT = 5;
    /** 出现/消失时的水平滑动距离（像素，负向为左） */
    private static final int SLIDE_PX = 18;

    // ===== v3 溢出布局常量 =====

    /**
     * 整体缩放下限。
     * <p>
     * 再小文字就开始糊了。GUI 缩放 3 时 {@value} 相当于有效缩放 1.86，
     * 仍高于原版 GUI 缩放 2 的观感；而火星、1px 高光棱边这类按像素定义的细节
     * 到这个比例已经接近可分辨极限。
     * </p>
     */
    private static final float MIN_SCALE = 0.62f;

    /**
     * 最多开几列。
     * <p>
     * 每列 {@value #COLUMN_WIDTH} 逻辑像素宽，三列约 312 像素。
     * 1080p + GUI 缩放 3 的逻辑宽度是 640，三列占掉不到一半，
     * 不会挡住准星区域或右侧的原版药水图标。再多就开始碍事了。
     * </p>
     */
    private static final int MAX_COLUMNS = 3;

    /**
     * 列宽（逻辑像素）。
     * <p>
     * 卡片实际宽度由文本长度决定、每行不同，因此列宽只能取一个固定值。
     * {@value} 是按最宽的情况估的：竖条与间距 10 + 计数文本约 22
     * + 间隔 6 + 进度条 {@value #BAR_WIDTH} + 右padding 7 ≈ 95，再留一点列间距。
     * </p>
     * <p>
     * <b>名称过长的行会略微越过列边界</b>，压在下一列卡片的辉光上。
     * 这是可接受的——多列本来就是「实在放不下」时才启用的降级路径，
     * 而为了严格对齐去每帧测量所有行的最大宽度并不划算。
     * </p>
     */
    private static final int COLUMN_WIDTH = 104;

    /** 折叠提示卡的高度（逻辑像素）。比正常行矮，因为它只有一行文字、没有进度条 */
    private static final int OVERFLOW_ROW_HEIGHT = 13;

    /** 折叠提示卡的宽度（逻辑像素） */
    private static final int OVERFLOW_ROW_WIDTH = 40;

    /** 折叠提示卡的配色：中性灰，刻意不用任何附魔的主题色 */
    private static final int COL_OVERFLOW = 0x9AA4B0;

    /**
     * 徽标计数文本的翻译键（带回退值，无需额外补语言键即可显示「已触发」）。
     * <p>如需自定义文案或多语言，可在语言文件中覆盖此键；缺省时由 {@link #BADGE_FALLBACK}
     * 兜底，不会显示原始键名。</p>
     */
    private static final String BADGE_KEY = "carianstyle.hud.triggered";
    /** 徽标计数文本的默认回退值。 */
    private static final String BADGE_FALLBACK = "已触发";

    /**
     * 徽标行的计数文本组件（<b>v2：全局缓存一份</b>）。
     * <p>
     * 翻译组件在<b>渲染时</b>才解析当前语言，因此缓存实例是安全的——
     * 玩家中途切换语言后下一帧自动显示新语言，无需任何失效逻辑
     * （详见类注释「为什么徽标能安全地缓存成一个常量」）。
     * </p>
     */
    private static final Component BADGE_LABEL =
            Component.translatableWithFallback(BADGE_KEY, BADGE_FALLBACK);

    /**
     * "×层数" 文本组件的缓存容量（覆盖 0 ~ 本值-1）。
     * <p>
     * 取 {@value} 的依据见类注释「缓存容量与回退」：常见叠层上限都远低于此，
     * 唯一可能溢出的忍耐（显示储存伤害值）变化频率极低，走回退路径无妨。
     * </p>
     */
    private static final int COUNT_LABEL_CACHE_SIZE = 256;

    /**
     * "N秒" 文本组件的缓存容量（覆盖 0 ~ 本值-1 秒）。
     * <p>本模组最长冷却 6000 tick = 300 秒，留到 {@value} 是给将来更长冷却的附魔留余量。</p>
     */
    private static final int SECOND_LABEL_CACHE_SIZE = 601;

    /**
     * "+N" 折叠提示文本的缓存容量（v3 新增）。
     * <p>被折叠的行数不可能超过注册总数，{@value} 绰绰有余；越界仍有动态回退。</p>
     */
    private static final int OVERFLOW_LABEL_CACHE_SIZE = 64;

    /**
     * 预建的 "×N" 文本组件表（v2 新增，索引即层数）。
     * <p>类加载时一次性建好，渲染时按下标取用、零分配。超出范围由 {@link #countLabel} 回退。</p>
     */
    private static final Component[] COUNT_LABELS = new Component[COUNT_LABEL_CACHE_SIZE];

    /**
     * 预建的 "Ns" 文本组件表（v2 新增，索引即剩余秒数）。
     * <p>类加载时一次性建好，渲染时按下标取用、零分配。超出范围由 {@link #cooldownLabel} 回退。</p>
     */
    private static final Component[] SECOND_LABELS = new Component[SECOND_LABEL_CACHE_SIZE];

    /**
     * 预建的 "+N" 折叠提示文本表（v3 新增，索引即被折叠的行数）。
     * <p>与前两张表同理：折叠提示每帧都要画，不该每帧拼字符串。</p>
     */
    private static final Component[] OVERFLOW_LABELS = new Component[OVERFLOW_LABEL_CACHE_SIZE];

    static {
        for (int i = 0; i < COUNT_LABEL_CACHE_SIZE; i++) {
            COUNT_LABELS[i] = Component.literal("×" + i);
        }
        for (int i = 0; i < SECOND_LABEL_CACHE_SIZE; i++) {
            SECOND_LABELS[i] = Component.literal(i + "s");
        }
        for (int i = 0; i < OVERFLOW_LABEL_CACHE_SIZE; i++) {
            OVERFLOW_LABELS[i] = Component.literal("+" + i);
        }
    }

    /**
     * 文字最小可绘制透明度（4/255）。
     * <p>Minecraft 的 {@code Font.drawInternal} 在 {@code (color & 0xFC000000) == 0}
     * （即 alpha 字节 &lt; 4）时执行 {@code color |= 0xFF000000} 强制完全不透明，
     * 导致淡出末段文字突然全亮闪一下。低于此阈值直接跳过文字绘制（此时文字本就近乎不可见，
     * 跳过无视觉损失），从根源规避该原版特性引起的闪烁。
     * <p>取 4/255 是因为：只要传入 alpha &ge; 4/255，{@code Math.round(alpha*255)} 必 &ge; 4，
     * 高 6 位不全为 0，便不会触发上述强制不透明分支。
     */
    private static final float MIN_TEXT_ALPHA = 4f / 255f;

    // ===== 动画速度（指数平滑系数，越大越快）=====
    private static final float SPEED_ALPHA = 11f;
    private static final float SPEED_BAR = 9f;
    private static final float SPEED_Y = 13f;
    private static final float SPEED_X = 14f;
    private static final float SPEED_FLASH = 6f;
    /**
     * 换列时的水平迁移速度（v3 新增）。
     * <p>比 {@link #SPEED_X}（进出场滑动）慢一些：换列是一段较长的横向位移，
     * 用同样的速度会显得太急、像是在弹射。</p>
     */
    private static final float SPEED_COLUMN = 9f;

    // ===== 基础配色 =====
    private static final int COL_TEXT = 0xFFFFFF;
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_BG_TOP = 0x0B0E14;
    private static final int COL_BG_BOT = 0x05070A;
    private static final int COL_BLACK = 0x000000;

    // ===== 火焰配色（0xRRGGBB）=====
    /** 火芯：亮黄白 */
    private static final int COL_EMBER_HOT = 0xFFF0A0;
    /** 火中：橙 */
    private static final int COL_EMBER_MID = 0xFF9A2B;
    /** 火外：深橙红 */
    private static final int COL_EMBER_DEEP = 0xE0451A;

    // ===== 辉光 / 扫光 / 流光参数 =====
    /** 辉光基础不透明度 */
    private static final float GLOW_BASE = 0.05f;
    /** 辉光呼吸幅度 */
    private static final float GLOW_PULSE = 0.05f;
    /** 扫光周期（秒，每张卡掠过一次的间隔）*/
    private static final float GLINT_PERIOD = 5.5f;
    /** 扫光占周期比例（→ 实际扫光时长 ≈ 周期 × 该值）*/
    private static final float GLINT_SWEEP = 0.16f;
    /** 扫光带半宽（像素）*/
    private static final int GLINT_RADIUS = 5;
    /** 扫光峰值不透明度 */
    private static final float GLINT_ALPHA = 0.22f;
    /** 进度条流光速度 */
    private static final float BAR_FLOW_SPEED = 0.5f;
    /** 进度条流光半宽（像素）*/
    private static final int BAR_FLOW_RADIUS = 3;

    // ===== 火焰参数 =====
    /** 每行火星上限（固定，懒分配，仅满层时使用）*/
    private static final int EMBER_COUNT = 8;
    /** 满层时 heat 上升速度 */
    private static final float HEAT_RISE_SPEED = 4.5f;
    /** 离开满层时 heat 下降速度（稍慢，制造余烬感）*/
    private static final float HEAT_FALL_SPEED = 2.2f;
    /** heat=1 时每秒生成火星数 */
    private static final float EMBER_SPAWN_RATE = 13f;
    /** 火星上升速度基准（像素/秒）*/
    private static final float EMBER_RISE = 16f;
    /** 火星寿命（秒）*/
    private static final float EMBER_LIFE = 0.8f;
    /** 火舌数量 */
    private static final int FLAME_TONGUES = 5;

    /** 上一帧时间戳（毫秒），用于计算 dt */
    private long lastMs = 0L;

    /** 逐附魔动画状态：serialId -> 状态 */
    private final Map<Integer, Anim> anims = new HashMap<>();

    private StackHudOverlay() {
    }

    /**
     * 单个附魔的动画状态。
     */
    private static final class Anim {
        float barFill;   // 当前进度条填充比例（平滑趋近目标）
        float alpha;     // 当前不透明度（淡入淡出）
        float x;         // 当前列 X（平滑趋近目标列位；v3 新增）
        float y;         // 当前 Y（平滑趋近目标行位）
        float exitX;     // 当前水平偏移（出现/消失滑动；0=就位，负=偏左）
        float flash;     // 闪烁强度（增层时置 1，衰减）
        float heat;      // 满层火焰强度 0~1（平滑升降）
        float targetRatio;
        float targetX;   // 目标列 X（v3 新增）
        float targetY;
        int lastCount;
        int lastMax;
        boolean cooldown;         // 本行是否为冷却倒计时项（影响数字格式与进度条方向）
        boolean present;          // 本帧是否仍在显示列表中
        boolean presentLastFrame; // 上一帧是否在显示列表中（抑制"消失再出现"误闪）
        boolean initialized;

        // —— 火星粒子（懒分配，仅满层时实例化；坐标相对进度条左上角）——
        float[] emberX;   // 相对填充区左端的 x（像素）
        float[] emberY;   // 高于进度条上沿的高度（像素，0=条顶，越大越高）
        float[] emberVy;  // 上升速度（像素/秒，正值）
        float[] emberLife;// 剩余寿命比例 1→0
        float[] emberSeed;// 每粒子相位种子（横向飘动差异）
        float emberSpawnAcc; // 生成累加器
        long rngState;       // 每行独立的无分配伪随机状态
    }

    /**
     * 一次布局解算的结果（v3 新增）。
     * <p>
     * 做成 record 而不是几个散落的局部变量，是因为解算逻辑本身有点绕
     * （缩放会反过来影响可用高度、可用高度又决定要不要多列），
     * 把它整块抽进 {@link #computeLayout} 之后 {@link #render} 那边就只剩「按结果排位」了。
     * </p>
     *
     * @param scale     整体缩放系数（1 = 不缩放）
     * @param rowsPerCol 缩放后每列能放下的行数（&ge;1）
     * @param visible   实际显示的行数（&le; 总行数）
     * @param hidden    被折叠掉的行数（0 表示没有折叠）
     */
    private record Layout(float scale, int rowsPerCol, int visible, int hidden) {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        List<StackHudManager.Entry> entries = StackHudManager.getEntries();

        // 计算帧间隔（秒），首帧为 0；夹取避免卡顿瞬间跳变
        long now = System.currentTimeMillis();
        float dt = (lastMs == 0L) ? 0f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        float time = now / 1000f;

        // 先把所有状态标记为“本帧未出现”
        for (Anim a : anims.values()) {
            a.present = false;
        }

        // ⭐ v3：先数一遍「元数据齐全、真的会画出来」的行数，再据此解算布局。
        // 必须先数：缩放系数取决于总行数，而行位又取决于缩放系数，
        // 一边遍历一边定位是算不出来的。列表最多几十项，多走一趟的成本可以忽略。
        int total = 0;
        for (StackHudManager.Entry entry : entries) {
            if (StackDisplayRegistry.getInfo(entry.serialId()) != null) {
                total++;
            }
        }
        Layout layout = computeLayout(total, height);

        // 同步目标值（entries 已由 StackHudManager 排好序，索引即显示次序）
        int index = 0;
        for (StackHudManager.Entry entry : entries) {
            StackDisplayRegistry.Info info = StackDisplayRegistry.getInfo(entry.serialId());
            if (info == null) {
                continue;
            }
            // 超出可见容量的行不标记 present，会自然淡出；它们的数量由 layout.hidden 汇总成 "+N"
            if (index >= layout.visible()) {
                index++;
                continue;
            }
            Anim a = anims.get(entry.serialId());
            if (a == null) {
                a = new Anim();
                a.exitX = -SLIDE_PX; // 新行自左侧滑入
                anims.put(entry.serialId(), a);
            }

            int count = entry.count();
            int max = entry.max();
            boolean cooldown = entry.cooldown();
            // 冷却项进度条为「充能」方向：填充 = 已恢复/总 = (max-count)/max（从空到满表示冷却恢复）；
            // 叠层项为「累积」方向：填充 = count/max。
            float targetRatio;
            if (cooldown) {
                targetRatio = max > 0 ? Math.min(1f, (float) (max - count) / max) : 0f;
            } else {
                targetRatio = max > 0 ? Math.min(1f, (float) count / max) : 0f;
            }
            // ⭐ v3：先分列再定行。列内行号 = index % rowsPerCol，列号 = index / rowsPerCol
            int column = index / layout.rowsPerCol();
            int rowInColumn = index % layout.rowsPerCol();
            float targetX = ANCHOR_X + column * COLUMN_WIDTH;
            float targetY = ANCHOR_Y + rowInColumn * ROW_STRIDE;

            if (!a.initialized) {
                a.initialized = true;
                a.x = targetX;
                a.y = targetY;
                a.barFill = targetRatio;
                a.alpha = 0f;
                a.lastCount = count;
            }
            // 仅当上一帧就在显示列表、且确属真实增长时才触发白闪；
            // 刚（重新）出现的行这一帧只静默同步层数，避免误闪。
            if (count > a.lastCount && a.presentLastFrame) {
                a.flash = 1f;
            }
            a.lastCount = count;
            a.lastMax = max;
            a.cooldown = cooldown;
            a.targetRatio = targetRatio;
            a.targetX = targetX;
            a.targetY = targetY;
            a.present = true;
            index++;
        }

        // ⭐ v3：整体缩放。压一层 scale 之后所有 fill / fillGradient / drawString 自动跟着缩，
        // 无需逐元素改坐标（详见类注释「为什么缩放要包住整个渲染」）。
        boolean scaled = layout.scale() < 0.999f;
        if (scaled) {
            graphics.pose().pushPose();
            graphics.pose().scale(layout.scale(), layout.scale(), 1f);
        }

        // 更新动画并渲染；消失的行（滑出+淡出后）移除
        Font font = mc.font;
        Iterator<Map.Entry<Integer, Anim>> it = anims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> e = it.next();
            int serialId = e.getKey();
            Anim a = e.getValue();

            float targetAlpha = a.present ? 1f : 0f;
            float targetExitX = a.present ? 0f : -SLIDE_PX;
            a.alpha = smooth(a.alpha, targetAlpha, SPEED_ALPHA, dt);
            a.exitX = smooth(a.exitX, targetExitX, SPEED_X, dt);
            a.barFill = smooth(a.barFill, a.targetRatio, SPEED_BAR, dt);
            a.x = smooth(a.x, a.targetX, SPEED_COLUMN, dt);
            a.y = smooth(a.y, a.targetY, SPEED_Y, dt);
            // 消失中的行（present=false，正在淡出）不应再出现白闪：直接清零残留闪光
            // （可能来自消失前最后一次层数增长或满层刷新），否则它会在淡出头几帧与卡片一起闪一下。
            a.flash = a.present ? smooth(a.flash, 0f, SPEED_FLASH, dt) : 0f;

            if (!a.present && a.alpha < 0.01f) {
                it.remove();
                continue;
            }

            StackDisplayRegistry.Info info = StackDisplayRegistry.getInfo(serialId);
            if (info != null) {
                renderRow(graphics, font, Math.round(a.x + a.exitX), Math.round(a.y),
                        a, info, serialId, dt, time);
            }

            // 记录本帧是否出现，供下一帧"误闪抑制"判断
            a.presentLastFrame = a.present;
        }

        // ⭐ v3：折叠提示。放在最后一列最后一行之下，明确告诉玩家还有几项没显示 ——
        // 无声吞掉才是最坏的结果。
        if (layout.hidden() > 0) {
            int column = Math.max(0, (layout.visible() - 1) / layout.rowsPerCol());
            int rowInColumn = layout.visible() - column * layout.rowsPerCol();
            renderOverflowHint(graphics, font,
                    ANCHOR_X + column * COLUMN_WIDTH,
                    ANCHOR_Y + rowInColumn * ROW_STRIDE,
                    layout.hidden(), time);
        }

        if (scaled) {
            graphics.pose().popPose();
        }
    }

    /**
     * 解算本帧的缩放系数、每列行数、可见行数与折叠行数（v3 新增）。
     *
     * <h4>三级兜底的顺序不能换</h4>
     * <p>
     * 缩放<b>不损失任何信息</b>，多列<b>也不损失信息、只吃宽度</b>，折叠<b>会丢行</b>。
     * 因此必须先缩到下限、再开列到上限、最后才折叠。反过来（先开列再缩放）
     * 会在只多出一两行时就把 HUD 铺到半个屏幕宽，那是过度反应。
     * </p>
     *
     * <h4>缩放之后可用高度反而变大了</h4>
     * <p>
     * 这是本方法唯一容易搞错的地方。{@code PoseStack.scale(s)} 缩小的是<b>绘制结果</b>，
     * 等价于把逻辑坐标系放大了 {@code 1/s} 倍——缩放 0.62 之后，
     * 一个 360 像素高的屏幕在逻辑坐标里有 {@code 360 / 0.62 ≈ 580} 像素可用。
     * 所以每列行数必须按 {@code height / scale} 算，按 {@code height} 算会白白浪费掉缩放的收益。
     * </p>
     *
     * @param total  本帧要显示的总行数（已排除元数据缺失的项）
     * @param height 屏幕逻辑高度（GUI 缩放后的值，由 Forge 传入）
     * @return 布局解算结果
     */
    private static Layout computeLayout(int total, int height) {
        // 上下各留一个 ANCHOR_Y 的边距，避免贴边
        int usableH = Math.max(ROW_STRIDE, height - ANCHOR_Y * 2);
        int rowsAtFullScale = Math.max(1, usableH / ROW_STRIDE);

        // 常态：一列就够，不缩放
        if (total <= rowsAtFullScale) {
            return new Layout(1f, rowsAtFullScale, total, 0);
        }

        // 第一级：缩放。刚好把 total 行塞进一列所需的系数，钳到下限
        float needed = (float) usableH / (total * ROW_STRIDE);
        float scale = Math.max(MIN_SCALE, Math.min(1f, needed));

        // 缩放后的逻辑可用高度（见方法注释：缩放会让逻辑坐标系变大）
        int logicalH = Math.max(ROW_STRIDE, Math.round(height / scale) - ANCHOR_Y * 2);
        int rowsPerCol = Math.max(1, logicalH / ROW_STRIDE);

        if (total <= rowsPerCol) {
            // 缩放就解决了，不用开列
            return new Layout(scale, rowsPerCol, total, 0);
        }

        // 第二级：多列（向上取整，钳到列数上限）
        int columns = Math.min(MAX_COLUMNS, (total + rowsPerCol - 1) / rowsPerCol);
        int capacity = rowsPerCol * columns;

        if (total <= capacity) {
            return new Layout(scale, rowsPerCol, total, 0);
        }

        // 第三级：折叠。留一行的位置给 "+N" 提示，其余全部显示
        int visible = Math.max(1, capacity - 1);
        return new Layout(scale, rowsPerCol, visible, total - visible);
    }

    /**
     * 渲染折叠提示卡「+N」（v3 新增）。
     * <p>
     * 刻意做得比正常行<b>矮一半、窄很多、颜色中性</b>：它不是一条信息，
     * 而是「这里还有信息没显示」的标记。做得和正常卡片一样显眼，
     * 反而会让人以为那是某个附魔的状态。
     * </p>
     * <p>
     * 不走 {@link Anim} 状态机——它没有淡入淡出的必要（出现与消失都跟随行数变化，
     * 而行数变化本身已经有卡片的滑动动画在表达），为它单开一份动画状态得不偿失。
     * </p>
     *
     * @param g      渲染上下文
     * @param font   字体
     * @param x      左上角 X
     * @param y      左上角 Y
     * @param hidden 被折叠掉的行数（&gt;0）
     * @param time   全局时间（秒，驱动呼吸）
     */
    private static void renderOverflowHint(GuiGraphics g, Font font,
                                           int x, int y, int hidden, float time) {
        int right = x + OVERFLOW_ROW_WIDTH;
        int bottom = y + OVERFLOW_ROW_HEIGHT;

        // 缓慢呼吸，让它区别于「静止的装饰」——它代表有东西被藏起来了，应该有一点存在感
        float breath = 0.55f + 0.20f * (0.5f + 0.5f * Mth.sin(time * 1.8f));

        g.fillGradient(x, y, right, bottom,
                argb(COL_BG_TOP, 0.45f), argb(COL_BG_BOT, 0.55f));
        drawRoundBorder(g, x, y, right, bottom, argb(COL_OVERFLOW, 0.30f * breath));

        Component label = overflowLabel(hidden);
        int textX = x + (OVERFLOW_ROW_WIDTH - font.width(label)) / 2;
        g.drawString(font, label, textX, y + 3, argb(COL_OVERFLOW, 0.85f * breath), true);
    }

    /**
     * 渲染单张卡片。
     *
     * @param g        渲染上下文
     * @param font     字体
     * @param x        卡片左上角 X（已含列位与水平滑动动画）
     * @param y        卡片左上角 Y（已含行位滑动动画）
     * @param a        动画状态
     * @param info     显示元数据
     * @param serialId 序列号（用于辉光/扫光/火焰相位去同步）
     * @param dt       帧间隔（秒，驱动火焰/火星）
     * @param time     全局时间（秒，驱动闪烁/扫动）
     */
    private void renderRow(GuiGraphics g, Font font, int x, int y, Anim a,
                           StackDisplayRegistry.Info info, int serialId, float dt, float time) {
        float alpha = a.alpha;
        int accent = info.color();
        int accentBright = brighten(accent, 0.35f);
        boolean hasBar = a.lastMax > 0;
        // 冷却项不参与「满层燃烧」：其 count 是剩余 tick，count>=max 只在冷却刚开始的瞬间成立、
        // 会误触发燃烧；且冷却结束（count 归 0）时该行直接消失、不会停在满条。故冷却项 atMax 恒 false。
        boolean atMax = hasBar && !a.cooldown && a.lastCount >= a.lastMax;

        // —— 火焰强度 heat 平滑（只有有进度条的行才会"燃烧"）——
        if (hasBar) {
            a.heat = smooth(a.heat, atMax ? 1f : 0f, atMax ? HEAT_RISE_SPEED : HEAT_FALL_SPEED, dt);
        }
        float heat = a.heat;

        // 名称翻译组件按 serialId 缓存，避免每帧每行重复 new（详见 NAME_CACHE）
        Component name = NAME_CACHE.get(serialId);
        if (name == null) {
            name = Component.translatable(info.nameKey());
            NAME_CACHE.put(serialId, name);
        }
        int nameWidth = font.width(name);
        // 计数文本三态：冷却项显示剩余秒数（如 "5s"）；徽标行（血之/月之共鸣、隐身中）显示「已触发」；
        // 其余叠层项显示 "×层数"。徽标行以 Stacks(1,0,false) 注册，表达布尔激活态而非可累加层数，
        // 故不显示 "×1"（详见 isBadgeRow）；连击数等其它非冷却项不受影响，仍为 "×层数"。
        //
        // ⭐ v2：三类文本全部取自预建的 Component 缓存，不再每帧拼字符串（详见类注释）
        Component countText;
        if (a.cooldown) {
            countText = cooldownLabel(a.lastCount);
        } else if (isBadgeRow(serialId)) {
            countText = BADGE_LABEL;
        } else {
            countText = countLabel(a.lastCount);
        }
        int countWidth = font.width(countText);

        int textX = x + 3 + 7; // 竖条(2~3) + 间距
        int line2Width = hasBar ? (countWidth + 6 + BAR_WIDTH) : countWidth;
        int contentRight = textX + Math.max(nameWidth, line2Width);
        int cardRight = contentRight + 7;
        int cardBottom = y + ROW_HEIGHT;

        // ===== 1. 外侧辉光晕（常驻呼吸；满层转余烬橙）=====
        float glowBreath = 0.5f + 0.5f * Mth.sin(time * 1.5f + serialId * 1.3f);
        int glowCol = heat > 0.02f ? lerpRgb(accent, COL_EMBER_MID, heat) : accent;
        float gA = (GLOW_BASE + GLOW_PULSE * glowBreath + 0.10f * heat) * alpha;
        drawHaloBorder(g, x, y, cardRight, cardBottom, 1, argb(glowCol, gA));
        drawHaloBorder(g, x, y, cardRight, cardBottom, 2, argb(glowCol, gA * 0.45f));

        // ===== 2. 卡片背景（竖向渐变；满层偏暖黑）=====
        int bgTop = heat > 0.02f ? lerpRgb(COL_BG_TOP, 0x1A0A04, heat * 0.7f) : lerpRgb(COL_BG_TOP, accent, 0.07f);
        g.fillGradient(x, y, cardRight, cardBottom, argb(bgTop, 0.52f * alpha), argb(COL_BG_BOT, 0.60f * alpha));

        // ===== 3. 底部暗角 + 顶部高光棱边（厚度感）=====
        g.fill(x + 1, cardBottom - 2, cardRight - 1, cardBottom - 1, argb(COL_BLACK, 0.28f * alpha));
        g.fill(x + 2, y + 1, cardRight - 2, y + 2, argb(brighten(accent, 0.55f), 0.16f * alpha));

        // ===== 4. 圆角描边（满层转热色闪烁）=====
        int borderColor;
        if (heat > 0.02f) {
            int hotBorder = lerpRgb(0xFF7A1E, COL_EMBER_HOT, flicker(time, serialId));
            borderColor = lerpRgb(accent, hotBorder, heat);
        } else {
            borderColor = accent;
        }
        drawRoundBorder(g, x, y, cardRight, cardBottom, argb(borderColor, (0.30f + 0.30f * heat) * alpha));

        // ===== 5. 玻璃扫光（周期掠过，按行错相）=====
        float glintCycle = frac((time + serialId * 0.61f) / GLINT_PERIOD);
        // 仅对仍在显示的行做扫光；淡出中的行不再扫光，避免消失瞬间又掠过一道高光。
        if (a.present && glintCycle < GLINT_SWEEP) {
            renderGlint(g, x, y + 1, cardRight, cardBottom - 1, glintCycle / GLINT_SWEEP, accent, alpha);
        }

        // ===== 6. 增层闪光覆盖（满层由白转橙，呈"爆燃"感）=====
        if (a.flash > 0.01f) {
            int flashCol = atMax ? lerpRgb(COL_WHITE, 0xFFB347, 0.55f) : COL_WHITE;
            g.fill(x, y, cardRight, cardBottom, argb(flashCol, 0.14f * a.flash * alpha));
        }

        // ===== 7. 左侧呼吸光条（竖向渐变；满层转余烬）=====
        float stripBreath = 0.75f + 0.25f * (0.5f + 0.5f * Mth.sin(time * 2f + serialId));
        int sTop = brighten(accent, 0.30f);
        int sBot = darken(accent, 0.15f);
        if (heat > 0.02f) {
            sTop = lerpRgb(sTop, COL_EMBER_HOT, heat * 0.85f);
            sBot = lerpRgb(sBot, COL_EMBER_DEEP, heat * 0.85f);
        }
        g.fillGradient(x + 1, y + 2, x + 3, cardBottom - 2,
                argb(sTop, stripBreath * (0.9f + 0.1f * a.flash) * alpha), argb(sBot, stripBreath * 0.8f * alpha));

        // ===== 8. 名称 =====
        // 规避 MC 字体「低 alpha 强制不透明」特性：alpha 低于阈值直接跳过（详见 MIN_TEXT_ALPHA），
        // 否则淡出末段（alpha 字节 1~3）文字会被原版强制全亮，造成消失瞬间闪一下。
        if (alpha >= MIN_TEXT_ALPHA) {
            g.drawString(font, name, textX, y + 3, argb(COL_TEXT, alpha), true);
        }

        // ===== 9. 层数（弹动 + 闪光 + 满层热色呼吸）=====
        int line2Y = y + 12;
        int numColor;
        if (atMax) {
            int hotNum = lerpRgb(0xFFD27A, COL_WHITE, 0.30f + 0.40f * flicker(time, serialId * 0.7f));
            numColor = lerpRgb(hotNum, COL_WHITE, a.flash);
        } else {
            numColor = lerpRgb(accentBright, COL_WHITE, a.flash);
        }
        float scale = 1f + 0.30f * a.flash + 0.06f * heat * (0.5f + 0.5f * Mth.sin(time * 6f));
        // 同样规避字体低 alpha 强制不透明特性（详见 MIN_TEXT_ALPHA）
        if (alpha >= MIN_TEXT_ALPHA) {
            drawScaledString(g, font, countText, textX, line2Y, argb(numColor, alpha), scale);
        }

        // ===== 10. 进度条 =====
        if (hasBar) {
            int barX = textX + countWidth + 6;
            int barY = line2Y + 1;
            int fillW = Math.round(BAR_WIDTH * a.barFill);

            // 轨道（内凹渐变 + 圆角描边）
            g.fillGradient(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT,
                    argb(0x05070A, 0.9f * alpha), argb(0x10141A, 0.9f * alpha));
            drawBorder(g, barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT,
                    argb(lerpRgb(accent, COL_EMBER_DEEP, heat), 0.30f * alpha));

            if (fillW > 0) {
                if (heat > 0.02f) {
                    // 熔岩竖向渐变 + 横向扫动热点
                    float fl = flicker(time, serialId * 1.3f);
                    int hotTop = lerpRgb(0xFFC23A, COL_EMBER_HOT, 0.4f + 0.6f * fl);
                    int hotBot = lerpRgb(COL_EMBER_DEEP, COL_EMBER_MID, fl);
                    g.fillGradient(barX, barY, barX + fillW, barY + BAR_HEIGHT,
                            argb(hotTop, 0.95f * alpha), argb(hotBot, 0.95f * alpha));
                    float sweep = frac(time * 0.8f + serialId * 0.3f);
                    int hot = barX + Math.round(sweep * fillW);
                    g.fill(Math.max(barX, hot - 1), barY, Math.min(barX + fillW, hot + 2), barY + BAR_HEIGHT,
                            argb(COL_EMBER_HOT, 0.50f * fl * alpha));
                } else {
                    // 玻璃竖向渐变（上亮下深）+ 顶部高光 + 持续流光带
                    g.fillGradient(barX, barY, barX + fillW, barY + BAR_HEIGHT,
                            argb(accentBright, 0.95f * alpha), argb(accent, 0.95f * alpha));
                    g.fill(barX, barY, barX + fillW, barY + 1, argb(COL_WHITE, 0.28f * alpha));
                    renderBarFlow(g, barX, barY, fillW, time, serialId, alpha);
                }
                // 领头亮条（两种情况共用）
                if (fillW >= 2) {
                    g.fill(barX + fillW - 2, barY, barX + fillW, barY + BAR_HEIGHT, argb(COL_WHITE, 0.70f * alpha));
                }
            }

            // —— 满层火焰：火舌 + 火星（仅 heat 足够时绘制，平时零开销）——
            if (heat > 0.02f) {
                renderFlameTongues(g, barX, barY, fillW, alpha, time, heat);
                stepEmbers(a, dt, fillW);
                renderEmbers(g, a, barX, barY, alpha);
            }
        }
    }

    // ==================== 常驻动效子系统 ====================

    /**
     * 一道掠过卡片的玻璃高光"扫光"（带柔和抛物线衰减的竖向亮带）。
     *
     * @param p        扫光进度 0~1（从卡片左外侧扫到右外侧）
     * @param accent   强调色（与白混合作为高光色）
     * @param alpha    整卡不透明度
     */
    private static void renderGlint(GuiGraphics g, int x0, int y0, int x1, int y1,
                                    float p, int accent, float alpha) {
        int w = x1 - x0;
        float center = x0 - GLINT_RADIUS + p * (w + 2 * GLINT_RADIUS);
        int lo = Math.max(x0, Math.round(center - GLINT_RADIUS));
        int hi = Math.min(x1, Math.round(center + GLINT_RADIUS + 1));
        int glintCol = lerpRgb(accent, COL_WHITE, 0.7f);
        for (int cx = lo; cx < hi; cx++) {
            float d = (cx + 0.5f - center) / GLINT_RADIUS;
            float inten = 1f - d * d;
            if (inten <= 0f) {
                continue;
            }
            g.fill(cx, y0, cx + 1, y1, argb(glintCol, inten * GLINT_ALPHA * alpha));
        }
    }

    /**
     * 进度条填充区内持续流动的高光带（循环，制造液态流光）。
     */
    private static void renderBarFlow(GuiGraphics g, int barX, int barY, int fillW,
                                      float time, int serialId, float alpha) {
        if (fillW < 2) {
            return;
        }
        float flow = frac(time * BAR_FLOW_SPEED + serialId * 0.27f);
        float center = barX + flow * fillW;
        int lo = Math.max(barX, Math.round(center - BAR_FLOW_RADIUS));
        int hi = Math.min(barX + fillW, Math.round(center + BAR_FLOW_RADIUS + 1));
        for (int cx = lo; cx < hi; cx++) {
            float d = (cx + 0.5f - center) / BAR_FLOW_RADIUS;
            float inten = 1f - d * d;
            if (inten > 0f) {
                g.fill(cx, barY, cx + 1, barY + BAR_HEIGHT, argb(COL_WHITE, inten * 0.35f * alpha));
            }
        }
    }

    // ==================== 火焰子系统 ====================

    /**
     * 沿进度条上沿绘制若干窜动的火舌（竖向渐变，下实上透）。
     */
    private static void renderFlameTongues(GuiGraphics g, int barX, int barY, int fillW,
                                           float alpha, float time, float heat) {
        if (fillW < 2) {
            return;
        }
        for (int i = 0; i < FLAME_TONGUES; i++) {
            float fh = flicker(time + i * 0.6f, i * 2.1f);
            int th = Math.round((2f + 6f * fh) * heat);
            if (th <= 0) {
                continue;
            }
            int tx = barX + Math.round((i + 0.5f) / FLAME_TONGUES * fillW);
            int top = lerpRgb(COL_EMBER_MID, COL_EMBER_HOT, fh);
            g.fillGradient(tx, barY - th, tx + 1, barY, argb(top, 0f), argb(top, 0.55f * heat * alpha));
        }
    }

    /**
     * 推进火星粒子并按 heat 补充生成（无分配）。
     */
    private static void stepEmbers(Anim a, float dt, int fillW) {
        ensureEmberArrays(a);
        for (int i = 0; i < EMBER_COUNT; i++) {
            if (a.emberLife[i] > 0f) {
                a.emberLife[i] -= dt / EMBER_LIFE;
                a.emberY[i] += a.emberVy[i] * dt;
                a.emberX[i] += Mth.sin((a.emberY[i] + a.emberSeed[i]) * 0.5f) * 4f * dt;
            }
        }
        a.emberSpawnAcc += EMBER_SPAWN_RATE * a.heat * dt;
        int guard = 0;
        while (a.emberSpawnAcc >= 1f && guard++ < EMBER_COUNT) {
            a.emberSpawnAcc -= 1f;
            spawnEmber(a, fillW);
        }
        if (a.emberSpawnAcc > 2f) {
            a.emberSpawnAcc = 2f; // 防止长帧后一次性爆量
        }
    }

    /**
     * 在填充区内生成一颗火星（找空位，找不到则跳过）。
     */
    private static void spawnEmber(Anim a, int fillW) {
        int slot = -1;
        for (int i = 0; i < EMBER_COUNT; i++) {
            if (a.emberLife[i] <= 0f) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return;
        }
        int span = Math.max(2, fillW);
        a.emberX[slot] = rngFloat(a) * span;
        a.emberY[slot] = 0f;
        a.emberVy[slot] = EMBER_RISE * (0.65f + 0.7f * rngFloat(a));
        a.emberLife[slot] = 1f;
        a.emberSeed[slot] = rngFloat(a) * 6.2832f;
    }

    /**
     * 渲染所有存活火星：随寿命由亮黄→橙→暗红，寿命平方淡出，新生略大。
     */
    private static void renderEmbers(GuiGraphics g, Anim a, int barX, int barY, float alpha) {
        if (a.emberLife == null) {
            return;
        }
        for (int i = 0; i < EMBER_COUNT; i++) {
            float life = a.emberLife[i];
            if (life <= 0f) {
                continue;
            }
            int col;
            if (life > 0.66f) {
                col = lerpRgb(COL_EMBER_MID, COL_EMBER_HOT, (life - 0.66f) / 0.34f);
            } else if (life > 0.33f) {
                col = lerpRgb(COL_EMBER_DEEP, COL_EMBER_MID, (life - 0.33f) / 0.33f);
            } else {
                col = COL_EMBER_DEEP;
            }
            float ea = life * life * alpha * a.heat;
            int size = life > 0.6f ? 2 : 1;
            int xi = barX + Math.round(a.emberX[i]);
            int yi = barY - Math.round(a.emberY[i]);
            g.fill(xi, yi, xi + size, yi + size, argb(col, ea));
            if (size == 2) {
                g.fill(xi, yi, xi + 1, yi + 1, argb(COL_EMBER_HOT, ea * 0.9f));
            }
        }
    }

    /**
     * 懒分配火星数组（仅某行首次燃烧时触发一次）。
     */
    private static void ensureEmberArrays(Anim a) {
        if (a.emberLife == null) {
            a.emberX = new float[EMBER_COUNT];
            a.emberY = new float[EMBER_COUNT];
            a.emberVy = new float[EMBER_COUNT];
            a.emberLife = new float[EMBER_COUNT];
            a.emberSeed = new float[EMBER_COUNT];
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 帧率无关的指数平滑：value 向 target 趋近。
     */
    private static float smooth(float value, float target, float speed, float dt) {
        if (dt <= 0f) {
            return value;
        }
        float t = 1f - (float) Math.exp(-dt * speed);
        return value + (target - value) * t;
    }

    /**
     * 类火焰的廉价伪随机闪烁（多频正弦叠加，结果恒在 0~1）。
     */
    private static float flicker(float t, float seed) {
        float v = Mth.sin(t * 11f + seed) * 0.5f
                + Mth.sin(t * 19f + seed * 1.7f) * 0.3f
                + Mth.sin(t * 37f + seed * 2.3f) * 0.2f;
        return 0.5f + 0.5f * v;
    }

    /**
     * 取小数部分（结果恒在 0 到 1 之间，不含 1）。
     */
    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /**
     * 每行独立的无分配伪随机（xorshift64），返回 0~1。
     */
    private static float rngFloat(Anim a) {
        long s = a.rngState == 0L ? 0x9E3779B97F4A7C15L : a.rngState;
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        a.rngState = s;
        return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    /**
     * 是否为「徽标行」（血之共鸣 / 月之共鸣 / 隐身中）。
     * <p>
     * 这几项都以 {@code Stacks(1, 0, false)} 注册，表达的是<b>布尔激活态</b>
     * 而非可累加的层数，故其计数文本显示「已触发」而非 "×1"。
     * 其余非冷却叠层项（连击数等）仍显示 "×层数"，不受影响。
     * </p>
     * <p>
     * serialId 直接引用各注册类的公开常量，避免魔数
     * （与 {@code AuraGroundRenderer} 引用光环序号常量的写法一致）。
     * <b>v3 新增隐身中</b>——它同样是布尔态，显示「×1」毫无意义。
     * </p>
     * <p>
     * <b>为什么用 if 串联而不是集合：</b>只有三项，串联的分支预测友好、零分配，
     * 而且这是每帧每行都会走的路径。等到十项以上再考虑换 {@code IntSet} 不迟。
     * </p>
     *
     * @param serialId 行序列号
     * @return 是徽标行返回 true
     */
    private static boolean isBadgeRow(int serialId) {
        return serialId == CarianStyleStackDisplays.BLOOD_RESONANCE
                || serialId == CarianStyleStackDisplays.MOON_RESONANCE
                || serialId == CarianStyleConditionDisplay.STEALTH_ACTIVE;
    }

    /**
     * 取 "×层数" 的文本组件（v2 新增）。
     * <p>
     * 优先命中 {@link #COUNT_LABELS} 预建表；超出缓存范围（或出现负数）时才动态创建。
     * 回退路径基本只会被<b>忍耐</b>触发——它显示的是储存伤害值而非层数，高血量时可达四位数，
     * 但其数值只在受击时变化、频率远低于每帧，动态创建完全可接受（详见类注释）。
     * </p>
     *
     * @param count 层数 / 数值
     * @return 文本组件
     */
    private static Component countLabel(int count) {
        if (count >= 0 && count < COUNT_LABEL_CACHE_SIZE) {
            return COUNT_LABELS[count];
        }
        return Component.literal("×" + count);
    }

    /**
     * 取剩余秒数的文本组件（v2 新增，取代原先返回 String 的 {@code formatCooldownSeconds}）。
     * <p>
     * 剩余冷却 tick 向上取整为秒，且至少显示 1s——剩余不足 1 秒但仍在冷却时显示 "1s"，
     * 直到归 0 那一刻该行从列表移除。例：90 tick → "5s"（90/20=4.5 向上取整为 5）。
     * </p>
     * <p>优先命中 {@link #SECOND_LABELS} 预建表；超出范围时才动态创建。</p>
     *
     * @param remainingTicks 剩余冷却 tick（&gt;0）
     * @return 形如 "5s" 的文本组件
     */
    private static Component cooldownLabel(int remainingTicks) {
        int seconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0));
        if (seconds < SECOND_LABEL_CACHE_SIZE) {
            return SECOND_LABELS[seconds];
        }
        return Component.literal(seconds + "s");
    }

    /**
     * 取折叠提示 "+N" 的文本组件（v3 新增）。
     *
     * @param hidden 被折叠掉的行数（&gt;0）
     * @return 形如 "+5" 的文本组件
     */
    private static Component overflowLabel(int hidden) {
        if (hidden >= 0 && hidden < OVERFLOW_LABEL_CACHE_SIZE) {
            return OVERFLOW_LABELS[hidden];
        }
        return Component.literal("+" + hidden);
    }

    /**
     * 用缩放绘制文本（围绕其中心缩放，用于层数弹动/呼吸）。
     * <p>
     * <b>v2：形参由 {@code String} 改为 {@link Component}</b>，配合三张预建缓存表消掉每帧字符串分配。
     * {@code Font.width(FormattedText)} 与 {@code GuiGraphics.drawString(Font, Component, ...)}
     * 都有现成重载，改动仅限签名。
     * </p>
     */
    private static void drawScaledString(GuiGraphics g, Font font, Component text,
                                         int x, int y, int color, float scale) {
        if (scale <= 1.001f) {
            g.drawString(font, text, x, y, color, true);
            return;
        }
        float w = font.width(text);
        float h = font.lineHeight;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, Math.round(-w / 2f), Math.round(-h / 2f), color, true);
        g.pose().popPose();
    }

    /**
     * 画 1px 直角边框（进度条等内部元素用）。
     */
    private static void drawBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /**
     * 画 1px 圆角边框（四角各缺 1px，伪圆角，卡片外框用）。
     */
    private static void drawRoundBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);     // 上
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);     // 下
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);     // 左
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);     // 右
    }

    /**
     * 画一圈外发光晕（在卡片外侧 inset 像素处铺一圈淡色，四角留缺呈圆角辉光）。
     *
     * @param inset 向外偏移的像素
     */
    private static void drawHaloBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int inset, int color) {
        int gx0 = x0 - inset, gy0 = y0 - inset, gx1 = x1 + inset, gy1 = y1 + inset;
        g.fill(gx0 + 1, gy0, gx1 - 1, gy0 + 1, color);   // 上
        g.fill(gx0 + 1, gy1 - 1, gx1 - 1, gy1, color);   // 下
        g.fill(gx0, gy0 + 1, gx0 + 1, gy1 - 1, color);   // 左
        g.fill(gx1 - 1, gy0 + 1, gx1, gy1 - 1, color);   // 右
    }

    /**
     * 把 0xRRGGBB + alpha(0~1) 打包为 ARGB。
     */
    private static int argb(int rgb, float alpha) {
        int a = Math.round(clamp01(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * 在两个 0xRRGGBB 之间线性插值。
     */
    private static int lerpRgb(int from, int to, float t) {
        t = clamp01(t);
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int gg = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (gg << 8) | b;
    }

    /** 向白混合（提亮）。 */
    private static int brighten(int rgb, float f) {
        return lerpRgb(rgb, COL_WHITE, f);
    }

    /** 向黑混合（压暗）。 */
    private static int darken(int rgb, float f) {
        return lerpRgb(rgb, COL_BLACK, f);
    }

    /** 夹取到 0~1。 */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
