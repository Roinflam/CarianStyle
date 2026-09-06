package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;

/**
 * 百科界面的配色与基础绘制工具。
 *
 * <h3>为什么自己画而不用原版控件</h3>
 * <p>
 * 原版的 {@code AbstractWidget} 体系（按钮九宫格贴图、列表 {@code ObjectSelectionList}）
 * 样式固定、留白很大，在一屏要塞下「分类 + 列表 + 详情 + 冲突跳转」的信息密度下不够用。
 * 而 Cloth Config 是为「一列开关」设计的，做不了双栏跳转。
 * </p>
 * <p>
 * 这里全部用 {@link GuiGraphics#fill} 拼矩形，没有任何贴图资源依赖，
 * 也就不会出现资源包覆盖导致界面变形的问题。
 * </p>
 *
 * <h3>颜色约定</h3>
 * <p>
 * 全部为 ARGB（{@code 0xAARRGGBB}）。基调取自模组的艾尔登法环主题：
 * 暗褐底 + 金色强调。所有颜色集中在本类，改主题只改这里。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class UiTheme {

    /** 整屏遮罩 */
    public static final int SCRIM = 0xE0090806;

    /** 面板底色 */
    public static final int PANEL = 0xFF17140F;

    /** 次级面板底色（列表行交替、详情分区） */
    public static final int PANEL_ALT = 0xFF1F1B14;

    /** 面板高亮底色（悬停） */
    public static final int PANEL_HOVER = 0xFF2B251A;

    /** 边框 */
    public static final int BORDER = 0xFF3A3226;

    /** 金色强调 */
    public static final int ACCENT = 0xFFD9B46A;

    /** 金色强调（半透明，用于选中底） */
    public static final int ACCENT_FILL = 0x55D9B46A;

    /** 主文本 */
    public static final int TEXT = 0xFFE8E2D4;

    /** 次要文本 */
    public static final int TEXT_DIM = 0xFF9C927E;

    /** 警示文本（数值被改动、关闭信息类特效等） */
    public static final int WARN = 0xFFE0A05A;

    /** 危险 / 冲突 */
    public static final int DANGER = 0xFFD97070;

    /** 启用状态 */
    public static final int ON = 0xFF8FD07A;

    /** 禁用状态 */
    public static final int OFF = 0xFF6A6255;

    private UiTheme() {
    }

    /**
     * 画一个带 1px 边框的面板。
     *
     * @param g      绘制上下文
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param fill   填充色
     */
    public static void panel(@Nonnull GuiGraphics g, int x, int y, int width, int height, int fill) {
        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, BORDER);
    }

    /**
     * 画一个 1px 边框（不填充）。
     *
     * @param g      绘制上下文
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param color  边框色
     */
    public static void border(@Nonnull GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y + 1, x + 1, y + height - 1, color);
        g.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * 画一条水平分隔线。
     *
     * @param g     绘制上下文
     * @param x     左
     * @param y     纵坐标
     * @param width 宽
     */
    public static void divider(@Nonnull GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, BORDER);
    }

    /**
     * 画一个左侧竖条强调的小标签。
     *
     * @param g     绘制上下文
     * @param font  字体
     * @param text  文字
     * @param x     左
     * @param y     上
     * @param color 竖条与文字颜色
     */
    public static void tag(@Nonnull GuiGraphics g, @Nonnull Font font,
                           @Nonnull Component text, int x, int y, int color) {
        g.fill(x, y, x + 2, y + font.lineHeight, color);
        g.drawString(font, text, x + 5, y, color, false);
    }

    /**
     * 画一个胶囊状小徽章，返回它占用的宽度。
     *
     * @param g       绘制上下文
     * @param font    字体
     * @param text    文字
     * @param x       左
     * @param y       上
     * @param color   文字与边框色
     * @param hovered 是否悬停（悬停时填充更亮）
     * @return 徽章总宽度（含右侧 3px 间距）
     */
    public static int badge(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                            int x, int y, int color, boolean hovered) {
        int textWidth = font.width(text);
        int width = textWidth + 8;
        int height = font.lineHeight + 4;
        g.fill(x, y, x + width, y + height, hovered ? PANEL_HOVER : PANEL_ALT);
        border(g, x, y, width, height, color);
        g.drawString(font, text, x + 4, y + 3, color, false);
        return width + 3;
    }

    /**
     * 画一个竖向滚动条。
     * <p>内容不超出可视区时不绘制。</p>
     *
     * @param g             绘制上下文
     * @param x             轨道左边缘
     * @param y             轨道上边缘
     * @param height        轨道高度
     * @param contentHeight 内容总高度
     * @param scroll        当前滚动偏移
     */
    public static void scrollbar(@Nonnull GuiGraphics g, int x, int y, int height,
                                 int contentHeight, int scroll) {
        if (contentHeight <= height) {
            return;
        }
        g.fill(x, y, x + 3, y + height, PANEL_ALT);

        int thumbHeight = Math.max(16, height * height / contentHeight);
        int maxScroll = contentHeight - height;
        int travel = height - thumbHeight;
        int thumbY = y + (maxScroll <= 0 ? 0 : (int) ((long) scroll * travel / maxScroll));

        g.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    /**
     * 绘制自动换行的多行文本，返回绘制后应继续的纵坐标。
     *
     * <h3>为什么要有这个</h3>
     * <p>
     * 详情面板里那些说明性短句原本都是直接 {@code drawString} 画的，
     * 短的时候看不出问题；一旦文字变长——比如提示里嵌了一条完整的语言键
     * {@code enchantment.lrtactical.backstab.desc}——就会一路画出面板右边界，
     * 后半句直接看不到，而且没有任何迹象表明它被截断了。
     * </p>
     * <p>
     * {@link #trimmed} 至少会给个省略号，但那是为「一行内必须放下」的场景准备的
     * （比如列表行）。说明文字应该换行而不是被砍掉。
     * </p>
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param text       文字
     * @param x          左
     * @param y          上
     * @param maxWidth   可用宽度
     * @param color      颜色
     * @return 绘制后应继续的纵坐标
     */
    public static int wrapped(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                              int x, int y, int maxWidth, int color) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, Math.max(1, maxWidth))) {
            g.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    /**
     * 绘制被宽度截断的文本，超出部分以省略号结尾。
     *
     * @param g        绘制上下文
     * @param font     字体
     * @param text     文字
     * @param x        左
     * @param y        上
     * @param maxWidth 最大宽度
     * @param color    颜色
     */
    public static void trimmed(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                               int x, int y, int maxWidth, int color) {
        String raw = text.getString();
        if (font.width(raw) <= maxWidth) {
            g.drawString(font, raw, x, y, color, false);
            return;
        }
        String clipped = font.plainSubstrByWidth(raw, maxWidth - font.width("...")) + "...";
        g.drawString(font, clipped, x, y, color, false);
    }

    /**
     * 画一个与本界面风格一致的按钮，并返回是否处于悬停状态。
     *
     * <h3>为什么不用原版 {@code Button}</h3>
     * <p>
     * 原版按钮是九宫格贴图，圆角、灰蓝渐变、按下时整体位移——它是为原版 GUI 的
     * 石头质感设计的。放进本界面的暗褐 + 金色配色里非常突兀，而且它的贴图会跟随资源包变化，
     * 换一个材质包按钮就变了样，旁边自绘的部分却不会跟着变，割裂感更明显。
     * </p>
     * <p>
     * 这里改成和面板、徽章同一套绘制原语（{@link #fill} + 1px 边框），
     * 整个界面只有一种视觉语言。
     * </p>
     *
     * @param g       绘制上下文
     * @param font    字体
     * @param label   按钮文字
     * @param x       左
     * @param y       上
     * @param width   宽
     * @param height  高
     * @param enabled 是否可用；不可用时文字与边框转为灰色且不响应悬停
     * @param mouseX  鼠标 X
     * @param mouseY  鼠标 Y
     * @return 是否悬停（不可用时恒为 false）
     */
    public static boolean button(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component label,
                                 int x, int y, int width, int height,
                                 boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && hit(mouseX, mouseY, x, y, width, height);

        int fill = !enabled ? PANEL : (hovered ? ACCENT_FILL : PANEL_ALT);
        int edge = !enabled ? BORDER : (hovered ? ACCENT : TEXT_DIM);
        int text = !enabled ? OFF : (hovered ? ACCENT : TEXT);

        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, edge);

        int textWidth = font.width(label);
        g.drawString(font, label, x + (width - textWidth) / 2,
                y + (height - font.lineHeight) / 2 + 1, text, false);
        return hovered;
    }

    /**
     * 画一个区块标题：左侧竖条 + 标题文字 + 右侧贯穿到底的细分隔线。
     * <p>
     * 比单独一行文字更容易在长详情页里扫到，也免去了在每个区块末尾再画一条分隔线——
     * 分隔线跟着标题走，区块的起止一眼可辨。
     * </p>
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param title      标题
     * @param x          左
     * @param y          上
     * @param innerWidth 可用宽度
     * @return 绘制后应继续的纵坐标
     */
    public static int sectionHeader(@Nonnull GuiGraphics g, @Nonnull Font font,
                                    @Nonnull Component title, int x, int y, int innerWidth) {
        g.fill(x, y, x + 2, y + font.lineHeight, tint);
        g.drawString(font, title, x + 6, y, tint, false);

        int lineLeft = x + 6 + font.width(title) + 6;
        int lineY = y + font.lineHeight / 2;
        if (lineLeft < x + innerWidth) {
            g.fill(lineLeft, lineY, x + innerWidth, lineY + 1, BORDER);
            // 区块分隔线的流光比顶栏那道更弱、相位错开，
            // 免得整屏的高光同步扫动像跑马灯
            sweep(g, lineLeft, lineY, x + innerWidth - lineLeft, time(), 0.35f);
        }
        return y + font.lineHeight + 5;
    }

    /**
     * 画一行「标签 —— 值」，标签左对齐、值右对齐。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param label      左侧标签
     * @param value      右侧值
     * @param x          左
     * @param y          上
     * @param innerWidth 可用宽度
     * @param valueColor 值的颜色
     * @return 绘制后应继续的纵坐标
     */
    public static int keyValue(@Nonnull GuiGraphics g, @Nonnull Font font,
                               @Nonnull Component label, @Nonnull String value,
                               int x, int y, int innerWidth, int valueColor) {
        g.drawString(font, label, x, y, TEXT_DIM, false);
        int valueWidth = font.width(value);
        g.drawString(font, value, x + innerWidth - valueWidth, y, valueColor, false);
        return y + font.lineHeight + 3;
    }

    /**
     * 画一个实心小圆点（用 3 段矩形近似，避免引入贴图）。
     *
     * @param g     绘制上下文
     * @param cx    圆心 X
     * @param cy    圆心 Y
     * @param color 颜色
     */
    public static void dot(@Nonnull GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
        g.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
    }

    /**
     * 把 1~10 转成罗马数字，与游戏内附魔等级的显示方式一致。
     *
     * @param level 等级
     * @return 罗马数字；超出 1~10 时返回原数字的十进制文本
     */
    @Nonnull
    public static String roman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return String.valueOf(level);
        }
    }

    // ==================== 动画与光效 ====================

    /** 流光的分段数，见 {@link #sweep} 的性能说明 */
    private static final int SWEEP_SEGMENTS = 24;

    /**
     * 当前界面的强调色调。
     *
     * <h3>为什么用一个可变的全局值</h3>
     * <p>
     * 分隔线流光、区块标题、面板角标这些装饰件散布在好几个类里，
     * 而它们应该<b>一起</b>随当前选中附魔的稀有度变色。
     * 逐个方法加一个颜色参数意味着从最外层一路往下传，
     * 中间那些根本不关心颜色的方法也得跟着改签名。
     * </p>
     * <p>
     * 装饰色是「本帧的绘制上下文」，不是某个方法的输入——每帧开头由
     * {@code CodexScreen} 设一次，之后所有装饰件自动同步。
     * </p>
     */
    private static int tint = ACCENT;

    /**
     * 本帧的「显现进度」0~1，供角标等装饰件做开场生长。
     *
     * <h3>为什么用它替掉原来那道光带</h3>
     * <p>
     * 原来开场时有一道金色光带自上而下扫过整屏。问题是它<b>与界面无关</b>——
     * 一道横线从世界背景上刷过去，既不是面板的一部分，也没说明任何事，
     * 看起来像渲染出了岔子。
     * </p>
     * <p>
     * 改成让面板四角的金饰从零「长」出来：动的是界面自己的构件，
     * 观感是这块面板正在被勾勒成形，而不是有个东西从旁边飞过。
     * </p>
     */
    private static float reveal = 1f;

    /**
     * 供各绘制方法共用的时间源（秒）。
     * <p>
     * 取模 600 秒是为了让浮点数不会随着游戏开着越久而精度越差——
     * 十分钟一个循环，所有动画都是周期性的，接缝处看不出来。
     * </p>
     *
     * @return 当前时间（秒）
     */
    /**
     * 设置本帧的装饰色调。
     *
     * @param color ARGB 颜色
     */
    public static void setTint(int color) {
        tint = color;
    }

    /**
     * 设置本帧的显现进度。
     *
     * @param value 0~1
     */
    public static void setReveal(float value) {
        reveal = Math.min(1f, Math.max(0f, value));
    }

    /**
     * @return 本帧的装饰色调
     */
    public static int tint() {
        return tint;
    }

    public static float time() {
        return (System.currentTimeMillis() % 600000L) / 1000f;
    }

    /**
     * 缓出三次曲线：开头快、结尾慢。
     * <p>
     * 界面出现用缓出而不是线性，是因为线性动画在末尾会「啪」地停住，
     * 看起来像卡了一下；缓出让它滑进最终位置，短到 0.2 秒也能感觉到差别。
     * </p>
     *
     * @param t 归一化进度 0~1
     * @return 缓动后的进度
     */
    public static float easeOutCubic(float t) {
        float clamped = Math.min(1f, Math.max(0f, t));
        float inv = 1f - clamped;
        return 1f - inv * inv * inv;
    }

    /**
     * 缓入三次曲线：开头慢、结尾快。
     * <p>
     * 与 {@link #easeOutCubic} 配对使用：出现用缓出（滑进来后稳稳停住），
     * 消失用缓入（先几乎不动，再加速离场）。
     * </p>
     * <p>
     * 反过来用是最常见的错误：消失若用缓出，画面会在按下的一瞬间猛地弹开一大截，
     * 然后慢吞吞地磨完剩下一点——观感就是「一顿」。
     * </p>
     *
     * @param t 归一化进度 0~1
     * @return 缓动后的进度
     */
    public static float easeInCubic(float t) {
        float clamped = Math.min(1f, Math.max(0f, t));
        return clamped * clamped * clamped;
    }

    /**
     * 把颜色的 alpha 乘上一个系数。
     *
     * @param argb  原色
     * @param scale 系数 0~1
     * @return 调整后的颜色
     */
    public static int withAlpha(int argb, float scale) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * Math.min(1f, Math.max(0f, scale)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 在一条水平线上画一段缓慢往复的流光。
     *
     * <h3>性能：为什么改成分段画</h3>
     * <p>
     * 原实现是<b>逐像素</b>的：光带半宽取 {@code width/8}，一条 1000px 宽的分隔线
     * 就要 251 次 {@code fill}。详情页有 7 个区块标题加顶栏分隔线，
     * 单这一项每帧约 2000 次绘制调用，60fps 下十二万次——这就是掉帧的来源。
     * </p>
     * <p>
     * 改成固定 {@value #SWEEP_SEGMENTS} 段，每段一个矩形、整段同一 alpha。
     * 段数够多，在暗底上的渐变看不出是台阶；调用次数从 251 降到 24。
     * </p>
     *
     * @param g     绘制上下文
     * @param x     左
     * @param y     纵坐标
     * @param width 宽
     * @param time  当前时间（秒）
     * @param phase 相位偏移，让不同分隔线的高光错开
     * @param color 流光颜色，可按稀有度着色
     */
    public static void sweep(@Nonnull GuiGraphics g, int x, int y, int width,
                             float time, float phase, int color) {
        if (width <= 0) {
            return;
        }
        // 用三角波而不是取模：取模会在回到起点时瞬移，三角波是来回扫
        float t = ((time / 12f) + phase) % 1f;
        float tri = t < 0.5f ? t * 2f : (1f - t) * 2f;

        int center = x + (int) (tri * width);
        int half = Math.max(16, width / 8);
        int step = Math.max(1, half * 2 / SWEEP_SEGMENTS);

        for (int i = 0; i < SWEEP_SEGMENTS; i++) {
            int sx = center - half + i * step;
            int ex = Math.min(sx + step, x + width);
            if (ex <= x || sx >= x + width) {
                continue;
            }
            // 段中心到光带中心的距离决定亮度
            float mid = (sx + ex) * 0.5f - center;
            float falloff = 1f - Math.min(1f, Math.abs(mid) / half);
            int alpha = (int) (110 * falloff * falloff);
            if (alpha <= 2) {
                continue;
            }
            g.fill(Math.max(x, sx), y, ex, y + 1, (alpha << 24) | (color & 0x00FFFFFF));
        }
    }

    /**
     * 用默认强调色画流光。
     *
     * @param g     绘制上下文
     * @param x     左
     * @param y     纵坐标
     * @param width 宽
     * @param time  当前时间（秒）
     * @param phase 相位偏移
     */
    public static void sweep(@Nonnull GuiGraphics g, int x, int y, int width,
                             float time, float phase) {
        sweep(g, x, y, width, time, phase, tint);
    }

    /**
     * 在给定区域内画缓缓上升的金色光尘。
     *
     * <h3>确定性而非随机</h3>
     * <p>
     * 每个光点的横坐标由下标经一次整数散列得出，纵坐标由时间线性推进后取模——
     * 全程没有 {@code Random}，因此不需要保存粒子状态，也不会因为帧率波动而抖动。
     * 界面重开时光点位置连续，不会「重新撒一把」。
     * </p>
     *
     * @param g      绘制上下文
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param time   当前时间（秒）
     * @param count  光点数量
     * @param alpha  整体透明度系数 0~1
     */
    public static void motes(@Nonnull GuiGraphics g, int x, int y, int width, int height,
                             float time, int count, float alpha) {
        motes(g, x, y, width, height, time, count, alpha, 1f);
    }

    /**
     * 画缓缓上升的光尘，可指定速度倍率。
     *
     * @param g      绘制上下文
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param time   当前时间（秒）
     * @param count  光点数量
     * @param alpha  整体透明度系数 0~1
     * @param speedScale 速度倍率，稀有度越高越快
     */
    public static void motes(@Nonnull GuiGraphics g, int x, int y, int width, int height,
                             float time, int count, float alpha, float speedScale) {
        if (width <= 0 || height <= 0 || alpha <= 0f) {
            return;
        }
        for (int i = 0; i < count; i++) {
            int hash = i * 1103515245 + 12345;
            int px = x + Math.floorMod(hash >> 8, Math.max(1, width));
            float speed = (6f + Math.floorMod(hash >> 3, 9)) * speedScale;
            float offset = Math.floorMod(hash >> 16, 1000) / 1000f;

            // 自下而上：进度 1 在底部、0 在顶部
            float progress = 1f - (((time * speed / height) + offset) % 1f);
            int py = y + (int) (progress * height);

            // 轻微横向漂移：完全垂直上升像雨滴倒放，加一点摆动才像悬浮的尘埃。
            // 用光点自身的散列值做相位，各飘各的，不会整片同步摆
            px += (int) (Math.sin(time * 0.6 + (hash >> 5)) * 3.0);
            if (px < x || px >= x + width) {
                continue;
            }

            // 两端淡出，避免光点在边缘凭空出现或消失
            float fade = Math.min(1f, Math.min(progress, 1f - progress) * 6f);
            int a = (int) (70 * fade * alpha);
            if (a <= 2) {
                continue;
            }
            int color = (a << 24) | (tint & 0x00FFFFFF);
            // 四分之一的光点画成 2x2，避免整片看起来像噪点。
            // 原来是额外补两个十字矩形，一颗光点要三次 fill；改成一次画大一点，
            // 视觉差别看不出来，调用次数少三分之二
            int size = (hash & 3) == 0 ? 2 : 1;
            g.fill(px, py, px + size, py + size, color);
        }
    }

    /**
     * 四角压暗的暗角效果。
     *
     * <h3>它解决什么</h3>
     * <p>
     * 遮罩是一层均匀的半透明黑，屏幕四周和中央一样亮，金色面板放上去后
     * 视线没有着落点。暗角把边缘再压暗一档，中央相对更亮，
     * 面板就自然「浮」出来了——这是最省力的聚焦手段，不需要任何贴图。
     * </p>
     * <p>
     * 用同心矩形逼近而不是真的算径向渐变：一层层往里画 12 圈，
     * 每圈 alpha 递减，在暗底上看不出是矩形。
     * </p>
     *
     * @param g      绘制上下文
     * @param width  屏幕宽
     * @param height 屏幕高
     * @param scale  整体强度系数 0~1
     */
    public static void vignette(@Nonnull GuiGraphics g, int width, int height, float scale) {
        if (scale <= 0f) {
            return;
        }
        // 6 圈足够，暗底上看不出台阶；12 圈只是把 fill 次数翻倍
        int rings = 6;
        int stepX = Math.max(1, width / (rings * 4));
        int stepY = Math.max(1, height / (rings * 4));
        for (int i = 0; i < rings; i++) {
            int inset = i;
            int alpha = (int) (26 * scale);
            if (alpha <= 0) {
                break;
            }
            int color = (alpha << 24);
            int left = inset * stepX;
            int top = inset * stepY;
            g.fill(0, top, left + stepX, height - top, color);
            g.fill(width - left - stepX, top, width, height - top, color);
            g.fill(0, top, width, top + stepY, color);
            g.fill(0, height - top - stepY, width, height, color);
        }
    }


    /**
     * 呼吸系数：在 {@code min} 与 1 之间以 {@code period} 秒为周期平滑往复。
     *
     * @param time   当前时间（秒）
     * @param period 周期（秒）
     * @param min    最低值
     * @return 系数
     */
    public static float breathe(float time, float period, float min) {
        float wave = (float) ((Math.sin(time / period * Math.PI * 2.0) + 1.0) * 0.5);
        return min + (1f - min) * wave;
    }

    /**
     * 在面板四角画金色转角装饰。
     * <p>
     * 黄金树的视觉语言里，金饰总是出现在边界与转角而不是铺满平面。
     * 四个 L 形短角比一整圈亮边框克制得多，却足以让面板从背景里「立」出来。
     * </p>
     *
     * @param g      绘制上下文
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param length 每条角边的长度
     * @param color  颜色
     */
    public static void corners(@Nonnull GuiGraphics g, int x, int y, int width, int height,
                               int length, int color) {
        // 开场时从零长到满长，四角像被逐渐勾勒出来
        length = Math.max(1, (int) (length * reveal));
        int right = x + width;
        int bottom = y + height;

        g.fill(x, y, x + length, y + 1, color);
        g.fill(x, y, x + 1, y + length, color);

        g.fill(right - length, y, right, y + 1, color);
        g.fill(right - 1, y, right, y + length, color);

        g.fill(x, bottom - 1, x + length, bottom, color);
        g.fill(x, bottom - length, x + 1, bottom, color);

        g.fill(right - length, bottom - 1, right, bottom, color);
        g.fill(right - 1, bottom - length, right, bottom, color);
    }

    /**
     * 画一条水平方向由浓到淡的渐变条。
     * <p>用于悬停行：纯色块会把整行「盖住」，渐变则像光扫过去，不抢文字。</p>
     *
     * @param g      绘制上下文
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @param color  起始颜色（右端淡出到全透明）
     */
    public static void gradientRow(@Nonnull GuiGraphics g, int x, int y, int width, int height,
                                   int color) {
        if (width <= 0) {
            return;
        }
        // 分 16 段近似，足够平滑又不至于画出上百个矩形
        int steps = 16;
        int step = Math.max(1, width / steps);
        for (int i = 0; i < steps; i++) {
            int sx = x + i * step;
            int ex = Math.min(x + width, sx + step);
            if (sx >= ex) {
                break;
            }
            float k = 1f - (i / (float) steps);
            g.fill(sx, y, ex, y + height, withAlpha(color, k * k));
        }
    }

    /**
     * 判断坐标是否落在矩形内。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     * @return 是否命中
     */
    public static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
