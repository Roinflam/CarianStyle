package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 文字样式：颜色 + 五个格式开关
 * <p>
 * 不可变，每个 with 方法都返回新对象。各项为 null 表示"继承上一层"，和 1.20.1 的 Style 一样。
 * <p>
 * 颜色存 RGB：界面上的文字直接用这个颜色画（字体渲染器的颜色参数支持任意 RGB）；
 * 要写进物品名、Lore 这种只认 16 色 § 码的地方时，用 {@link #nearestLegacy} 换成最接近的原版颜色。
 */
public final class Style {

    public static final Style EMPTY = new Style(null, null, null, null, null, null);

    /**
     * 16 种原版颜色的 RGB，下标和 TextFormatting 的颜色序号一致（0 = 黑 ... 15 = 白）
     */
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static final TextFormatting[] LEGACY_COLORS = {
            TextFormatting.BLACK, TextFormatting.DARK_BLUE, TextFormatting.DARK_GREEN, TextFormatting.DARK_AQUA,
            TextFormatting.DARK_RED, TextFormatting.DARK_PURPLE, TextFormatting.GOLD, TextFormatting.GRAY,
            TextFormatting.DARK_GRAY, TextFormatting.BLUE, TextFormatting.GREEN, TextFormatting.AQUA,
            TextFormatting.RED, TextFormatting.LIGHT_PURPLE, TextFormatting.YELLOW, TextFormatting.WHITE
    };

    @Nullable
    private final Integer color;
    @Nullable
    private final Boolean bold;
    @Nullable
    private final Boolean italic;
    @Nullable
    private final Boolean underlined;
    @Nullable
    private final Boolean strikethrough;
    @Nullable
    private final Boolean obfuscated;

    private Style(@Nullable Integer color, @Nullable Boolean bold, @Nullable Boolean italic, @Nullable Boolean underlined,
                  @Nullable Boolean strikethrough, @Nullable Boolean obfuscated) {
        this.color = color == null ? null : color & 0xFFFFFF;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
    }

    /**
     * 颜色（RGB，不含透明度）
     *
     * @return RGB；null = 继承
     */
    @Nullable
    public Integer getColor() {
        return color;
    }

    public boolean isBold() {
        return Boolean.TRUE.equals(bold);
    }

    public boolean isItalic() {
        return Boolean.TRUE.equals(italic);
    }

    public boolean isUnderlined() {
        return Boolean.TRUE.equals(underlined);
    }

    public boolean isStrikethrough() {
        return Boolean.TRUE.equals(strikethrough);
    }

    public boolean isObfuscated() {
        return Boolean.TRUE.equals(obfuscated);
    }

    /**
     * 原始值（null = 没写，继承上一层）
     */
    @Nullable
    public Boolean bold() {
        return bold;
    }

    @Nullable
    public Boolean italic() {
        return italic;
    }

    @Nullable
    public Boolean underlined() {
        return underlined;
    }

    @Nullable
    public Boolean strikethrough() {
        return strikethrough;
    }

    @Nullable
    public Boolean obfuscated() {
        return obfuscated;
    }

    /**
     * 六项都没写
     *
     * @return true = 空样式（按内容比较，不是比对象）
     */
    public boolean isEmpty() {
        return color == null && bold == null && italic == null && underlined == null && strikethrough == null && obfuscated == null;
    }

    @Nonnull
    public Style withColor(@Nullable Integer rgb) {
        return new Style(rgb, bold, italic, underlined, strikethrough, obfuscated);
    }

    /**
     * 按格式码设颜色；传入的不是颜色（粗体之类）时等同 {@link #applyFormat}
     *
     * @param formatting 格式码
     * @return 新样式
     */
    @Nonnull
    public Style withColor(@Nullable TextFormatting formatting) {
        if (formatting == null) {
            return withColor((Integer) null);
        }
        if (!formatting.isColor()) {
            return applyFormat(formatting);
        }
        return withColor(rgb(formatting));
    }

    @Nonnull
    public Style withBold(@Nullable Boolean value) {
        return new Style(color, value, italic, underlined, strikethrough, obfuscated);
    }

    @Nonnull
    public Style withItalic(@Nullable Boolean value) {
        return new Style(color, bold, value, underlined, strikethrough, obfuscated);
    }

    @Nonnull
    public Style withUnderlined(@Nullable Boolean value) {
        return new Style(color, bold, italic, value, strikethrough, obfuscated);
    }

    @Nonnull
    public Style withStrikethrough(@Nullable Boolean value) {
        return new Style(color, bold, italic, underlined, value, obfuscated);
    }

    @Nonnull
    public Style withObfuscated(@Nullable Boolean value) {
        return new Style(color, bold, italic, underlined, strikethrough, value);
    }

    /**
     * 叠一个格式码：颜色码只换颜色、格式码只打开对应开关，RESET 清空（和 1.20.1 的 applyFormat 一样）
     *
     * @param formatting 格式码
     * @return 新样式
     */
    @Nonnull
    public Style applyFormat(@Nonnull TextFormatting formatting) {
        switch (formatting) {
            case OBFUSCATED:
                return withObfuscated(true);
            case BOLD:
                return withBold(true);
            case STRIKETHROUGH:
                return withStrikethrough(true);
            case UNDERLINE:
                return withUnderlined(true);
            case ITALIC:
                return withItalic(true);
            case RESET:
                return EMPTY;
            default:
                return formatting.isColor() ? withColor(rgb(formatting)) : this;
        }
    }

    /**
     * 按 § 码的规则叠一个格式码：颜色码同时关掉所有格式（字体渲染器就是这么处理的）
     *
     * @param formatting 格式码
     * @return 新样式
     */
    @Nonnull
    public Style applyLegacyFormat(@Nonnull TextFormatting formatting) {
        if (formatting.isColor()) {
            return new Style(rgb(formatting), false, false, false, false, false);
        }
        return applyFormat(formatting);
    }

    @Nonnull
    public Style applyFormats(@Nonnull TextFormatting... formattings) {
        Style style = this;
        for (TextFormatting formatting : formattings) {
            style = style.applyFormat(formatting);
        }
        return style;
    }

    /**
     * 以 parent 为底，本样式写了的项覆盖上去
     *
     * @param parent 上一层样式
     * @return 合并结果
     */
    @Nonnull
    public Style applyTo(@Nonnull Style parent) {
        if (this.isEmpty()) {
            return parent;
        }
        if (parent.isEmpty()) {
            return this;
        }
        return new Style(
                color != null ? color : parent.color,
                bold != null ? bold : parent.bold,
                italic != null ? italic : parent.italic,
                underlined != null ? underlined : parent.underlined,
                strikethrough != null ? strikethrough : parent.strikethrough,
                obfuscated != null ? obfuscated : parent.obfuscated);
    }

    /**
     * 格式开关压成位（见 {@link TextRun} 的常量）
     *
     * @return 位掩码
     */
    public int flags() {
        int flags = 0;
        if (isObfuscated()) {
            flags |= TextRun.OBFUSCATED;
        }
        if (isBold()) {
            flags |= TextRun.BOLD;
        }
        if (isStrikethrough()) {
            flags |= TextRun.STRIKETHROUGH;
        }
        if (isUnderlined()) {
            flags |= TextRun.UNDERLINE;
        }
        if (isItalic()) {
            flags |= TextRun.ITALIC;
        }
        return flags;
    }

    /**
     * 原版颜色码对应的 RGB
     *
     * @param formatting 格式码
     * @return RGB；不是颜色码时为 null（同 1.20.1 ChatFormatting#getColor）
     */
    @Nullable
    public static Integer rgb(@Nonnull TextFormatting formatting) {
        int index = formatting.getColorIndex();
        return formatting.isColor() && index >= 0 && index < 16 ? LEGACY_RGB[index] : null;
    }

    /**
     * 格式码的字符（{@code §c} 里的 c）
     *
     * @param formatting 格式码
     * @return 小写字符
     */
    public static char code(@Nonnull TextFormatting formatting) {
        return formatting.toString().charAt(1);
    }

    /**
     * 按字符取格式码（同 1.20.1 ChatFormatting#getByCode，大小写都认）
     *
     * @param code 字符
     * @return 格式码；不认识时为 null
     */
    @Nullable
    public static TextFormatting byCode(char code) {
        char lower = Character.toLowerCase(code);
        for (TextFormatting formatting : TextFormatting.values()) {
            if (code(formatting) == lower) {
                return formatting;
            }
        }
        return null;
    }

    /**
     * 离 RGB 最近的原版颜色（按 RGB 距离的平方）
     *
     * @param rgb RGB
     * @return 16 色之一
     */
    @Nonnull
    public static TextFormatting nearestLegacy(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            int c = LEGACY_RGB[i];
            long dr = r - ((c >> 16) & 0xFF);
            long dg = g - ((c >> 8) & 0xFF);
            long db = b - (c & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return LEGACY_COLORS[best];
    }

    /**
     * RGB 是不是正好等于某个原版颜色
     *
     * @param rgb RGB
     * @return 对应的颜色码；不是时为 null
     */
    @Nullable
    public static TextFormatting exactLegacy(int rgb) {
        int value = rgb & 0xFFFFFF;
        for (int i = 0; i < 16; i++) {
            if (LEGACY_RGB[i] == value) {
                return LEGACY_COLORS[i];
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Style)) {
            return false;
        }
        Style other = (Style) o;
        return Objects.equals(color, other.color) && Objects.equals(bold, other.bold) && Objects.equals(italic, other.italic)
                && Objects.equals(underlined, other.underlined) && Objects.equals(strikethrough, other.strikethrough)
                && Objects.equals(obfuscated, other.obfuscated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, bold, italic, underlined, strikethrough, obfuscated);
    }

    @Override
    public String toString() {
        return "Style{color=" + (color == null ? "null" : String.format("#%06X", color)) + ", bold=" + bold + ", italic=" + italic
                + ", underlined=" + underlined + ", strikethrough=" + strikethrough + ", obfuscated=" + obfuscated + '}';
    }
}
