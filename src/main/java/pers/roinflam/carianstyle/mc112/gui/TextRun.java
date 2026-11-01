package pers.roinflam.carianstyle.mc112.gui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 一段同样式的文字（展开后的最小单位）
 * <p>
 * text 里不含格式码；出现的 § 一律按普通字符画出来。
 * rgb 为 null 表示用调用方传进来的颜色画（相当于 1.20.1 里样式没写颜色）。
 */
public final class TextRun {

    public static final int OBFUSCATED = 1;
    public static final int BOLD = 2;
    public static final int STRIKETHROUGH = 4;
    public static final int UNDERLINE = 8;
    public static final int ITALIC = 16;

    @Nonnull
    public final String text;
    @Nullable
    public final Integer rgb;
    public final int flags;

    public TextRun(@Nonnull String text, @Nullable Integer rgb, int flags) {
        this.text = text;
        this.rgb = rgb == null ? null : rgb & 0xFFFFFF;
        this.flags = flags;
    }

    public boolean bold() {
        return (flags & BOLD) != 0;
    }

    /**
     * 同样式的另一段文字
     *
     * @param text 文字
     * @return 新的一段
     */
    @Nonnull
    public TextRun withText(@Nonnull String text) {
        return new TextRun(text, rgb, flags);
    }

    /**
     * 和另一段样式是否相同（相同就能拼成一段）
     *
     * @param other 另一段
     * @return true = 相同
     */
    public boolean sameStyle(@Nonnull TextRun other) {
        return flags == other.flags && Objects.equals(rgb, other.rgb);
    }

    /**
     * 转回样式对象
     *
     * @return 样式
     */
    @Nonnull
    public Style style() {
        Style style = Style.EMPTY.withColor(rgb);
        if ((flags & OBFUSCATED) != 0) {
            style = style.withObfuscated(true);
        }
        if ((flags & BOLD) != 0) {
            style = style.withBold(true);
        }
        if ((flags & STRIKETHROUGH) != 0) {
            style = style.withStrikethrough(true);
        }
        if ((flags & UNDERLINE) != 0) {
            style = style.withUnderlined(true);
        }
        if ((flags & ITALIC) != 0) {
            style = style.withItalic(true);
        }
        return style;
    }

    @Override
    public String toString() {
        return "TextRun{" + text + ", rgb=" + (rgb == null ? "null" : String.format("#%06X", rgb)) + ", flags=" + flags + '}';
    }
}
