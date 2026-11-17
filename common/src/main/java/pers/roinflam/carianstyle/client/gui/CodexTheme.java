package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 百科界面主题。选择存在客户端本地的 visual_toggle.json（键 codex_ui_theme），不走网络。
 * 和 {@code codex.CodexTheme}（附魔分类）无关。
 */
public enum CodexTheme {
    CARIAN("carian"),
    CLASSIC("classic");

    private final String id;

    CodexTheme(String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public String nameKey() {
        return "carianstyle.codex.ui_theme." + id;
    }

    @Nonnull
    public Component displayName() {
        return Component.translatable(nameKey());
    }

    @Nonnull
    public static CodexTheme byId(@Nullable String id) {
        for (CodexTheme theme : values()) {
            if (theme.id.equals(id)) {
                return theme;
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
    public Screen create(@Nonnull Resume resume) {
        if (this == CLASSIC) {
            return new pers.roinflam.carianstyle.client.gui.classic.CodexScreen(resume);
        }
        return new CodexScreen(resume);
    }

    /** 按键入口：按当前主题打开，标签页和选中项照各界面自己记住的 */
    public static void openCurrent() {
        Minecraft.getInstance().setScreen(current().create(Resume.FRESH));
    }

    /** 记下选择并换成这套界面，停在同一个标签页、同一个附魔上，不播开场 */
    public void switchTo(@Nonnull Resume resume) {
        select(this);
        Minecraft.getInstance().setScreen(create(resume));
    }

    /**
     * 换主题时带过去的状态。tab 是标签页名（codex / effects / others），null 表示照界面自己记住的。
     */
    public static final class Resume {
        public static final Resume FRESH = new Resume(null, null, null, false);

        @Nullable
        public final String tab;
        @Nullable
        public final String enchantmentId;
        @Nullable
        public final String foreignKey;
        public final boolean skipIntro;

        public Resume(@Nullable String tab, @Nullable String enchantmentId, @Nullable String foreignKey,
                      boolean skipIntro) {
            this.tab = tab;
            this.enchantmentId = enchantmentId;
            this.foreignKey = foreignKey;
            this.skipIntro = skipIntro;
        }
    }
}
