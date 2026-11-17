package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 百科的界面主题。加一套主题 = 加一个枚举项 + 一套界面类，并在 {@link #create} 里接上。
 * <p>标签页序号三套通用：0 附魔百科、1 特效开关、2 其他附魔；传 -1 / null 表示沿用该界面自己记住的。</p>
 */
public enum CodexTheme {
    CARIAN("carian"),
    CLASSIC("classic");

    public static final int TAB_CODEX = 0;
    public static final int TAB_EFFECTS = 1;
    public static final int TAB_OTHERS = 2;

    private final String id;

    CodexTheme(String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public String langKey() {
        return "carianstyle.codex.ui_theme." + id;
    }

    @Nonnull
    public Component title() {
        return Component.translatable(langKey());
    }

    @Nonnull
    public static CodexTheme byId(@Nullable String id) {
        if (id != null) {
            for (CodexTheme theme : values()) {
                if (theme.id.equals(id)) {
                    return theme;
                }
            }
        }
        return CARIAN;
    }

    @Nonnull
    public static CodexTheme current() {
        return byId(VisualToggle.codexTheme());
    }

    public static void select(@Nonnull CodexTheme theme) {
        VisualToggle.setCodexTheme(theme.id);
    }

    @Nonnull
    public Screen create(boolean skipIntro, int tab, @Nullable String enchantId, @Nullable String foreignKey) {
        switch (this) {
            case CLASSIC:
                pers.roinflam.carianstyle.client.gui.classic.CodexScreen.restore(tab, enchantId, foreignKey);
                return new pers.roinflam.carianstyle.client.gui.classic.CodexScreen(skipIntro);
            case CARIAN:
            default:
                CodexScreen.restore(tab, enchantId, foreignKey);
                return new CodexScreen(skipIntro);
        }
    }

    public void open(boolean skipIntro, int tab, @Nullable String enchantId, @Nullable String foreignKey) {
        Minecraft.getInstance().setScreen(create(skipIntro, tab, enchantId, foreignKey));
    }

    /** 按键入口：按保存的主题打开，停在上次的标签页 */
    public static void openCurrent() {
        current().open(false, -1, null, null);
    }

    /** 从一套界面切到另一套：记住选择，带着当前标签页和选中项直接换，不播开场 */
    public static void switchTo(@Nonnull CodexTheme target, int tab,
                                @Nullable String enchantId, @Nullable String foreignKey) {
        if (target == current()) {
            return;
        }
        select(target);
        target.open(true, tab, enchantId, foreignKey);
    }
}
