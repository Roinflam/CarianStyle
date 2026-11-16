package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * 卷首那轮月亮：月晕（按分类色着色、随月相变浓淡）、十帧月相、刻度环、点亮的刻度和指等级的亮星。
 * 小号 48 见方（月面直径 28），大号 80 见方（月面直径 52）。
 */
public final class MoonDisc {

    public static final int SMALL = 48;
    public static final int LARGE = 80;
    public static final int FRAMES = 10;
    /** 指针一圈 48 步，一级 4 步（刻度环 12 格） */
    public static final int STEPS = 48;

    /** 月晕透明度上限，按月相帧查：蛾眉最淡，满月最浓 */
    private static final float[] GLOW = {0.32f, 0.4f, 0.48f, 0.56f, 0.63f, 0.7f, 0.78f, 0.85f, 0.92f, 1f};

    private MoonDisc() {
    }

    /** 月相 1~4 停在第 0、3、6、9 帧 */
    public static int frameOf(int phase) {
        return (Math.max(1, Math.min(4, phase)) - 1) * 3;
    }

    public static float glowCap(int frame) {
        return GLOW[Math.max(0, Math.min(FRAMES - 1, frame))];
    }

    public static int glowTint(int catColor) {
        return UiTheme.mix(0xFFDFE9FF, catColor, 0.55f);
    }

    /** 月晕 + 月面；glow 是乘在月相上限上的系数（呼吸、脉冲） */
    public static void drawMoon(@Nonnull GuiGraphics g, int x, int y, int n, int frame, int catColor, float glow) {
        boolean large = n >= LARGE;
        int f = Math.max(0, Math.min(FRAMES - 1, frame));
        UiTheme.spriteTinted(g, large ? "glow_l" : "glow_s", x, y, n, n, 0, 0, n, n,
                glowTint(catColor), glowCap(f) * glow);
        UiTheme.sprite(g, large ? "moonph_l" : "moonph_s", x, y, n, n, f * n, 0, n, n, n * FRAMES, n);
    }

    /** 刻度环、走过的刻度、指针星；q 是指针所在的步数（0 在正上方，顺时针） */
    public static void drawRing(@Nonnull GuiGraphics g, int x, int y, int n, int catColor, int q, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        boolean large = n >= LARGE;
        UiTheme.spriteTinted(g, large ? "ring_l" : "ring_s", x, y, n, n, 0, 0, n, n, catColor, alpha);
        int step = Math.floorMod(q, STEPS);
        int lit = step / 4;
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, Math.min(1f, alpha));
        UiTheme.sprite(g, large ? "lit_l" : "lit_s", x, y, n, n, lit * n, 0, n, n, n * 12, n);
        double r = n / 2.0 - 2;
        double c = (n - 1) / 2.0;
        double a = step / (double) STEPS * Math.PI * 2 - Math.PI / 2;
        int px = (int) Math.round(c + r * Math.cos(a)) - 3;
        int py = (int) Math.round(c + r * Math.sin(a)) - 3;
        UiTheme.sprite(g, "ptr", x + px, y + py, 7, 7);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** 指针星正中的界面坐标（迸星点用） */
    public static int[] pointerCenter(int x, int y, int n, int q) {
        double r = n / 2.0 - 2;
        double c = (n - 1) / 2.0;
        double a = Math.floorMod(q, STEPS) / (double) STEPS * Math.PI * 2 - Math.PI / 2;
        return new int[]{x + (int) Math.round(c + r * Math.cos(a)), y + (int) Math.round(c + r * Math.sin(a))};
    }
}
