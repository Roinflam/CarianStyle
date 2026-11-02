package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 文字颜色（RGB，不含透明度），对应 1.20.1 的 {@code net.minecraft.network.chat.TextColor}
 * <p>
 * 1.12.2 的界面兼容层里 {@link Style} 直接存 RGB 整数，这个类只是让 1.20.1 里
 * {@code TextColor.fromRgb(0xD9B46A)} 这种写法能原样搬过来：
 * 用 {@link Style#withColor(TextColor)} 设进样式，效果和 {@link Style#withColor(Integer)} 一样。
 * </p>
 * 不可变，按数值比较相等。
 */
public final class TextColor {

    /**
     * RGB（高 8 位恒为 0）
     */
    private final int value;

    private TextColor(int value) {
        this.value = value & 0xFFFFFF;
    }

    /**
     * 由 RGB 建颜色（同 1.20.1 TextColor.fromRgb）
     *
     * @param rgb RGB；传 ARGB 时透明度被丢掉
     * @return 颜色
     */
    @Nonnull
    public static TextColor fromRgb(int rgb) {
        return new TextColor(rgb);
    }

    /**
     * 由原版格式码建颜色（同 1.20.1 TextColor.fromLegacyFormat）
     *
     * @param formatting 格式码
     * @return 颜色；传入的不是颜色码（粗体之类）时返回 null
     */
    @Nullable
    public static TextColor fromLegacyFormat(@Nonnull TextFormatting formatting) {
        Integer rgb = Style.rgb(formatting);
        return rgb == null ? null : new TextColor(rgb);
    }

    /**
     * 解析 {@code #RRGGBB} 或原版颜色名（同 1.20.1 TextColor.parseColor）
     *
     * @param text 文字
     * @return 颜色；解析不了时返回 null
     */
    @Nullable
    public static TextColor parseColor(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.charAt(0) == '#') {
            try {
                return new TextColor(Integer.parseInt(text.substring(1), 16));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        TextFormatting formatting = TextFormatting.getValueByName(text);
        return formatting == null ? null : fromLegacyFormat(formatting);
    }

    /**
     * @return RGB
     */
    public int getValue() {
        return value;
    }

    /**
     * @return {@code #RRGGBB} 形式（同 1.20.1 serialize 对非原版颜色的写法）
     */
    @Nonnull
    public String serialize() {
        return String.format(Locale.ROOT, "#%06X", value);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TextColor && ((TextColor) o).value == value);
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
