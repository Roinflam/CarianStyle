package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.visual.CarianStyleConditionDisplay;
import pers.roinflam.carianstyle.config.ClientVisualConfig;
import pers.roinflam.carianstyle.visual.CarianStyleStackDisplays;
import pers.roinflam.carianstyle.visual.StackDisplayRegistry;
import pers.roinflam.carianstyle.visual.StackHudManager;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * PORT-STUB(V2)
 *
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
 * @author RoinFlam
 * @version 3
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class StackHudOverlay implements net.minecraft.client.gui.LayeredDraw.Layer {
    public static final StackHudOverlay INSTANCE = new StackHudOverlay();
    private static final Map<Integer, Component> NAME_CACHE = new HashMap<>();
    private static final int ANCHOR_X = 6;
    private static final int ANCHOR_Y = 6;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_STRIDE = 25;
    private static final int BAR_WIDTH = 50;
    private static final int BAR_HEIGHT = 5;
    private static final int SLIDE_PX = 18;
    private static final float MIN_SCALE = 0.62f;
    private static final int MAX_COLUMNS = 3;
    private static final int COLUMN_WIDTH = 104;
    private static final int OVERFLOW_ROW_HEIGHT = 13;
    private static final int OVERFLOW_ROW_WIDTH = 40;
    private static final int COL_OVERFLOW = 0x9AA4B0;
    private static final String BADGE_KEY = "carianstyle.hud.triggered";
    private static final String BADGE_FALLBACK = "已触发";
    private static final Component BADGE_LABEL = Component.translatableWithFallback(BADGE_KEY, BADGE_FALLBACK);
    private static final int COUNT_LABEL_CACHE_SIZE = 256;
    private static final int SECOND_LABEL_CACHE_SIZE = 601;
    private static final int OVERFLOW_LABEL_CACHE_SIZE = 64;
    private static final Component[] COUNT_LABELS = new Component[COUNT_LABEL_CACHE_SIZE];
    private static final Component[] SECOND_LABELS = new Component[SECOND_LABEL_CACHE_SIZE];
    private static final Component[] OVERFLOW_LABELS = new Component[OVERFLOW_LABEL_CACHE_SIZE];
    private static final float MIN_TEXT_ALPHA = 4f / 255f;
    private static final float SPEED_ALPHA = 11f;
    private static final float SPEED_BAR = 9f;
    private static final float SPEED_Y = 13f;
    private static final float SPEED_X = 14f;
    private static final float SPEED_FLASH = 6f;
    private static final float SPEED_COLUMN = 9f;
    private static final int COL_TEXT = 0xFFFFFF;
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_BG_TOP = 0x0B0E14;
    private static final int COL_BG_BOT = 0x05070A;
    private static final int COL_BLACK = 0x000000;
    private static final int COL_EMBER_HOT = 0xFFF0A0;
    private static final int COL_EMBER_MID = 0xFF9A2B;
    private static final int COL_EMBER_DEEP = 0xE0451A;
    private static final float GLOW_BASE = 0.05f;
    private static final float GLOW_PULSE = 0.05f;
    private static final float GLINT_PERIOD = 5.5f;
    private static final float GLINT_SWEEP = 0.16f;
    private static final int GLINT_RADIUS = 5;
    private static final float GLINT_ALPHA = 0.22f;
    private static final float BAR_FLOW_SPEED = 0.5f;
    private static final int BAR_FLOW_RADIUS = 3;
    private static final int EMBER_COUNT = 8;
    private static final float HEAT_RISE_SPEED = 4.5f;
    private static final float HEAT_FALL_SPEED = 2.2f;
    private static final float EMBER_SPAWN_RATE = 13f;
    private static final float EMBER_RISE = 16f;
    private static final float EMBER_LIFE = 0.8f;
    private static final int FLAME_TONGUES = 5;
    private StackHudOverlay() {
    }

    private static final class Anim {
        float barFill;
        float alpha;
        float x;
        float y;
        float exitX;
        float flash;
        float heat;
        float targetRatio;
        float targetX;
        float targetY;
        int lastCount;
        int lastMax;
        boolean cooldown;
        boolean present;
        boolean presentLastFrame;
        boolean initialized;
        float[] emberX;
        float[] emberY;
        float[] emberVy;
        float[] emberLife;
        float[] emberSeed;
        float emberSpawnAcc;
        long rngState;
    }

    private record Layout(float scale, int rowsPerCol, int visible, int hidden) {
    }
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
    }
}

