package pers.roinflam.carianstyle.mc112.gui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 控件的悬停提示（同 1.20.1 Tooltip：按 170 像素自动换行，结果缓存）
 */
public final class Tooltip {

    private static final int MAX_WIDTH = 170;

    @Nonnull
    private final Component message;
    @Nullable
    private List<Component> cachedLines;
    @Nullable
    private String cachedLanguage;

    private Tooltip(@Nonnull Component message) {
        this.message = message;
    }

    /**
     * @param message 提示文字
     * @return 提示
     */
    @Nonnull
    public static Tooltip create(@Nonnull Component message) {
        return new Tooltip(message);
    }

    /**
     * 1.20.1 的第二个参数是旁白文字，1.12.2 没有旁白读控件，忽略
     */
    @Nonnull
    public static Tooltip create(@Nonnull Component message, @Nullable Component narration) {
        return new Tooltip(message);
    }

    @Nonnull
    public Component message() {
        return message;
    }

    /**
     * 换好行的提示文字（同 1.20.1 toCharSequence）
     *
     * @return 每行一个组件
     */
    @Nonnull
    public List<Component> lines() {
        String language = Ui.mc().gameSettings.language;
        if (cachedLines == null || !String.valueOf(language).equals(cachedLanguage)) {
            cachedLines = Font.get().split(message, MAX_WIDTH);
            cachedLanguage = String.valueOf(language);
        }
        return cachedLines;
    }
}
