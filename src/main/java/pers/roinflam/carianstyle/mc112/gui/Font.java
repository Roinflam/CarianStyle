package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 字体：包一层 1.12.2 的 FontRenderer，方法名和语义照 1.20.1 的 Font
 * <p>
 * <ul>
 *   <li>{@link #width(String)}、{@link #split(String, int)}：解析 § 码（同 1.20.1 传 String 的版本）；</li>
 *   <li>{@link #widthRaw}、{@link #plainSubstrByWidth}：不解析，§ 当普通字符（同 1.20.1 的 plain 系列）；</li>
 *   <li>宽度按实际画出来的样子算：颜色码会清掉粗体（原版 getStringWidth 在这点上和渲染不一致，这里以渲染为准）。</li>
 * </ul>
 * 画字统一走 {@link GuiGraphics}，不要直接调 FontRenderer，否则会和缓冲里还没画出去的色块顺序错乱。
 */
public final class Font implements TextLayout.CharWidth {

    @Nullable
    private static Font instance;

    /**
     * 行高（同 1.20.1 Font#lineHeight）
     */
    public final int lineHeight;

    @Nonnull
    private final FontRenderer renderer;

    /**
     * 字体渲染器的 glyphWidth 数组（§ 的字形宽度要从这里读：getCharWidth 对 § 一律返回 -1）
     */
    @Nullable
    private final byte[] glyphWidths;

    private Font(@Nonnull FontRenderer renderer) {
        this.renderer = renderer;
        this.lineHeight = renderer.FONT_HEIGHT;
        byte[] widths = null;
        try {
            Field field = ReflectionHelper.findField(FontRenderer.class, "glyphWidth", "field_78287_e");
            widths = (byte[]) field.get(renderer);
        } catch (Exception ignored) {
            // 读不到就按普通字符的宽度估
        }
        this.glyphWidths = widths;
    }

    /**
     * 游戏字体
     *
     * @return 字体
     */
    @Nonnull
    public static Font get() {
        FontRenderer renderer = Minecraft.getMinecraft().fontRenderer;
        Font font = instance;
        if (font == null || font.renderer != renderer) {
            font = new Font(renderer);
            instance = font;
        }
        return font;
    }

    /**
     * 底层字体渲染器（只在要调原版接口时用，例如 GuiUtils 画物品提示框）
     *
     * @return 字体渲染器
     */
    @Nonnull
    public FontRenderer renderer() {
        return renderer;
    }

    @Override
    public int width(char c, boolean bold) {
        int w;
        if (c == '\u00A7') {
            w = sectionWidth();
        } else {
            w = renderer.getCharWidth(c);
            if (w < 0) {
                w = 0;
            }
        }
        return bold && w > 0 ? w + 1 : w;
    }

    /**
     * 当普通字符画出来的 § 有多宽（同 FontRenderer 对 unicode 字形的算法）
     */
    private int sectionWidth() {
        byte[] widths = glyphWidths;
        if (widths == null) {
            return renderer.getCharWidth('?');
        }
        int packed = widths[0xA7] & 255;
        if (packed == 0) {
            return 0;
        }
        int start = packed >>> 4;
        int end = (packed & 15) + 1;
        return (end - start) / 2 + 1;
    }

    /**
     * 带 § 码的文字宽度
     *
     * @param text 文字（§ 码按格式解析）
     * @return 宽度
     */
    public int width(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        List<TextRun> runs = new ArrayList<>();
        Component.collectFormatted(text, Style.EMPTY, runs);
        return TextLayout.width(runs, this);
    }

    /**
     * 组件宽度
     *
     * @param text 组件
     * @return 宽度
     */
    public int width(@Nonnull Component text) {
        return TextLayout.width(text.runs(), this);
    }

    /**
     * 文字段宽度
     *
     * @param runs 文字段
     * @return 宽度
     */
    public int width(@Nonnull List<TextRun> runs) {
        return TextLayout.width(runs, this);
    }

    /**
     * 不解析格式码的宽度（§ 当普通字符）
     *
     * @param text 文字
     * @return 宽度
     */
    public int widthRaw(@Nullable String text) {
        return text == null ? 0 : TextLayout.width(text, false, this);
    }

    /**
     * 从头截出不超过 maxWidth 的部分（不解析格式码，同 1.20.1 plainSubstrByWidth）
     *
     * @param text     文字
     * @param maxWidth 最大宽度
     * @return 截出来的前缀
     */
    @Nonnull
    public String plainSubstrByWidth(@Nonnull String text, int maxWidth) {
        return text.substring(0, TextLayout.headIndex(text, maxWidth, this));
    }

    /**
     * 截出不超过 maxWidth 的部分
     *
     * @param text     文字
     * @param maxWidth 最大宽度
     * @param tail     true = 从尾部取
     * @return 截出来的部分
     */
    @Nonnull
    public String plainSubstrByWidth(@Nonnull String text, int maxWidth, boolean tail) {
        return tail ? text.substring(TextLayout.tailIndex(text, maxWidth, this)) : plainSubstrByWidth(text, maxWidth);
    }

    /**
     * 按样式量宽度，从头截出不超过 maxWidth 的部分（同 1.20.1 substrByWidth）
     *
     * @param text     组件
     * @param maxWidth 最大宽度
     * @return 截出来的部分
     */
    @Nonnull
    public Component substrByWidth(@Nonnull Component text, int maxWidth) {
        return Component.ofRuns(TextLayout.head(text.runs(), maxWidth, this));
    }

    /**
     * 自动换行（同 1.20.1 Font#split）
     *
     * @param text     组件
     * @param maxWidth 每行最大宽度
     * @return 每行一个组件；空文字得到空列表
     */
    @Nonnull
    public List<Component> split(@Nonnull Component text, int maxWidth) {
        return toLines(TextLayout.split(text.runs(), maxWidth, this));
    }

    /**
     * 自动换行（文字里的 § 码按格式解析）
     *
     * @param text     文字
     * @param maxWidth 每行最大宽度
     * @return 每行一个组件
     */
    @Nonnull
    public List<Component> split(@Nonnull String text, int maxWidth) {
        List<TextRun> runs = new ArrayList<>();
        Component.collectFormatted(text, Style.EMPTY, runs);
        return toLines(TextLayout.split(runs, maxWidth, this));
    }

    /**
     * 一行已排版文字的宽度（同 1.20.1 Font#width(FormattedCharSequence)）
     *
     * @param text 一行
     * @return 宽度
     */
    public int width(@Nonnull FormattedCharSequence text) {
        return TextLayout.width(text.runs(), this);
    }

    /**
     * 自动换行，每行一个 {@link FormattedCharSequence}（同 1.20.1 Font#split 的返回类型）
     * <p>
     * 1.12.2 版：兼容层的 {@link #split(Component, int)} 早已返回 {@code List<Component>}，
     * Java 不能只靠返回值类型区分重载，所以换了个名字。断行规则和 {@link #split(Component, int)} 完全相同。
     * </p>
     *
     * @param text     组件
     * @param maxWidth 每行最大宽度
     * @return 每行一个；空文字得到空列表
     */
    @Nonnull
    public List<FormattedCharSequence> splitSequences(@Nonnull Component text, int maxWidth) {
        List<List<TextRun>> lines = TextLayout.split(text.runs(), maxWidth, this);
        List<FormattedCharSequence> out = new ArrayList<>(lines.size());
        for (List<TextRun> line : lines) {
            out.add(FormattedCharSequence.of(line));
        }
        return out;
    }

    @Nonnull
    private static List<Component> toLines(@Nonnull List<List<TextRun>> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (List<TextRun> line : lines) {
            out.add(line.isEmpty() ? Component.empty() : Component.ofRuns(line));
        }
        return out;
    }

    /**
     * 画一串文字段（只给 GuiGraphics 用；调用前缓冲已经刷新、混合已经打开）
     * <p>
     * 每段单独调一次字体渲染器：颜色走参数（任意 RGB），格式走前缀码；
     * 文字里的 § 切开分别画，让每个 § 都落在一次调用的末尾，渲染器就会把它当普通字符画。
     *
     * @param runs   文字段
     * @param x      左
     * @param y      上
     * @param color  默认颜色（ARGB）
     * @param shadow 是否带阴影
     * @return 画完后的 x（不含阴影的 1 像素）
     */
    float draw(@Nonnull List<TextRun> runs, float x, float y, int color, boolean shadow) {
        StringBuilder builder = new StringBuilder();
        for (TextRun run : runs) {
            int runColor = run.rgb == null ? color : (color & 0xFF000000) | run.rgb;
            String text = run.text;
            int start = 0;
            while (start < text.length()) {
                int section = text.indexOf('\u00A7', start);
                int end = section < 0 ? text.length() : section + 1;
                builder.setLength(0);
                Component.appendFlagCodes(builder, run.flags);
                builder.append(text, start, end);
                int endX = renderer.drawString(builder.toString(), x, y, runColor, shadow);
                x = shadow ? endX - 1 : endX;
                start = end;
            }
        }
        return x;
    }
}
