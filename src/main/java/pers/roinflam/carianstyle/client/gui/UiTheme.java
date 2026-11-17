package pers.roinflam.carianstyle.client.gui;

import pers.roinflam.carianstyle.mc112.gui.Ui;
import pers.roinflam.carianstyle.mc112.gui.TextRun;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.mc112.gui.RenderSystem;
import net.minecraft.client.Minecraft;
import pers.roinflam.carianstyle.mc112.gui.Font;
import pers.roinflam.carianstyle.mc112.gui.GuiGraphics;
import pers.roinflam.carianstyle.mc112.gui.Component;
import pers.roinflam.carianstyle.mc112.gui.TextColor;
import net.minecraft.util.ResourceLocation;
import pers.roinflam.carianstyle.mc112.gui.FormattedCharSequence;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.codex.CodexEntry;
import pers.roinflam.carianstyle.codex.CodexTheme;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 百科界面的配色和基础画法：夜色深蓝底、银边玻璃页，装饰件都是 textures/gui/codex 下的像素贴图。
 * 坐标一律是界面像素，动效全部按 {@link #now()} 的真实毫秒算，不跟帧数走。
 */
@SideOnly(Side.CLIENT)
public final class UiTheme {

    public static final int TEXT = 0xFFD3DCF5;
    public static final int MOON = 0xFFEEF3FF;
    public static final int TEXT_DIM = 0xFF8E9AC0;
    public static final int OFF = 0xFF5E6A94;
    public static final int SILVER = 0xFFC9D6F2;
    public static final int EDGE = 0xFF9FB0DE;
    public static final int EDGE_HI = 0xFFDFE8FF;
    public static final int LINE = 0xFF2B3869;
    public static final int DOTS = 0xFF33437E;
    public static final int BUFF = 0xFF7FE3FF;
    public static final int DEBUFF = 0xFFFF8F9A;
    public static final int EMPH = 0xFFF2E2A6;
    public static final int GOLD = 0xFFE6CF8A;
    public static final int ON = 0xFF8FE6A0;
    public static final int DANGER = 0xFFFF7F8C;
    public static final int WARN = 0xFFF4C264;
    /** 辉石蓝：选中、聚焦、指针星 */
    public static final int ACCENT = 0xFF7FB4FF;
    public static final int SHADOW = 0xFF05081A;
    public static final int INK_DARK = 0xFF101842;
    public static final int NAVY = 0xFF0B1030;

    public static final int PANEL = 0xE0060A1A;
    public static final int PANEL_ALT = 0xEB080D22;
    public static final int PANEL_HOVER = 0xFF18214C;
    public static final int BORDER = 0xFF4A5C96;
    public static final int ACCENT_FILL = 0x577FB4FF;
    public static final int GLASS_TOP = 0xE60F1736;
    public static final int GLASS_BOTTOM = 0xF0080C1E;

    /** 月相 1~4（蛾眉、上弦、盈凸、满月）的颜色，下标 0 不用 */
    private static final int[] PHASE_COLOR = {0xFFA9B4CF, 0xFFA9B4CF, 0xFFB79CFF, 0xFFF08FC8, 0xFFFFB561};

    /** const.png 里 25 个分类之后的格子 */
    public static final int CONST_MOD = 25;
    public static final int CONST_FIT_OK = 26;
    public static final int CONST_FIT_EXPENSIVE = 27;
    public static final int CONST_FIT_OWNED = 28;
    public static final int CONST_FIT_BLOCKED = 29;
    public static final int CONST_FX = 30;
    private static final int CONST_CELLS = 35;

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private UiTheme() {
    }

    public static long now() {
        return Ui.millis();
    }

    /** CSS 的 steps(n)：线性进度按 n 档取整，到头是 1 */
    public static float steps(long elapsed, float duration, int n) {
        if (elapsed <= 0) {
            return 0f;
        }
        float p = Math.min(1f, elapsed / duration);
        return p >= 1f ? 1f : (float) Math.floor(p * n) / n;
    }

    /** 来回呼吸：从 1 降到 lo 再回来，单程 periodMs，分 n 档 */
    public static float breathe(long time, float periodMs, int n, float lo) {
        float t = time / periodMs;
        int cycle = (int) Math.floor(t);
        float f = t - cycle;
        if ((cycle & 1) == 1) {
            f = 1f - f;
        }
        f = (float) Math.floor(f * n) / n;
        return 1f - (1f - lo) * f;
    }

    public static float easeOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public static int withAlpha(int argb, float scale) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, scale)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bb = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (a & 0xFF000000) | (r << 16) | (gg << 8) | bb;
    }

    /** 稀有度对应的月相：本模组三档对蛾眉 / 上弦 / 满月，原版四档一档一张 */
    public static int phaseOf(@Nonnull CodexEntry entry) {
        // 放入物品后列表里的条目包了一层，要按原条目认档
        entry = ItemFilter.unwrap(entry);
        int order = Math.max(0, entry.getRarityOrder());
        if (entry instanceof EnchantmentMeta) {
            return order >= 2 ? 4 : order + 1;
        }
        return Math.min(4, order + 1);
    }

    public static int phaseColor(int phase) {
        return PHASE_COLOR[Math.max(1, Math.min(4, phase))];
    }

    public static int constIndex(@Nonnull CodexTheme theme) {
        return theme.ordinal();
    }

    @Nonnull
    public static ResourceLocation tex(@Nonnull String name) {
        return TEXTURES.computeIfAbsent(name,
                n -> new ResourceLocation(Reference.MOD_ID, "textures/gui/codex/" + n + ".png"));
    }

    public static void sprite(@Nonnull GuiGraphics g, @Nonnull String name, int x, int y, int w, int h) {
        sprite(g, name, x, y, w, h, 0, 0, w, h, w, h);
    }

    /** 从贴图 (u, v) 取 uw×vh 画成 w×h；uw 取负数就左右翻转 */
    public static void sprite(@Nonnull GuiGraphics g, @Nonnull String name, int x, int y, int w, int h,
                              float u, float v, int uw, int vh, int texW, int texH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex(name), x, y, w, h, u, v, uw, vh, texW, texH);
    }

    public static void spriteAlpha(@Nonnull GuiGraphics g, @Nonnull String name, int x, int y, int w, int h,
                                   float alpha) {
        if (alpha <= 0f) {
            return;
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.min(1f, alpha));
        sprite(g, name, x, y, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void spriteAlpha(@Nonnull GuiGraphics g, @Nonnull String name, int x, int y, int w, int h,
                                   float u, float v, int uw, int vh, int texW, int texH, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.min(1f, alpha));
        sprite(g, name, x, y, w, h, u, v, uw, vh, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** 白图按颜色着色画（月晕、刻度环、星座） */
    public static void spriteTinted(@Nonnull GuiGraphics g, @Nonnull String name, int x, int y, int w, int h,
                                    float u, float v, int texW, int texH, int rgb, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f, Math.min(1f, alpha));
        sprite(g, name, x, y, w, h, u, v, w, h, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** 21×11 的小星座：上行着色，下行叠白（星点提亮、主星中心纯白） */
    public static void constellation(@Nonnull GuiGraphics g, int index, int x, int y, int color) {
        int u = Math.max(0, Math.min(CONST_CELLS - 1, index)) * 21;
        spriteTinted(g, "const", x, y, 21, 11, u, 0, CONST_CELLS * 21, 22, color, 1f);
        sprite(g, "const", x, y, 21, 11, u, 11, 21, 11, CONST_CELLS * 21, 22);
    }

    /** 9×9 稀有度月相；满月另画一圈 15×15 淡月晕，haloAlpha 为 0 就不画 */
    public static void phase(@Nonnull GuiGraphics g, int phase, int x, int y, float alpha, float haloAlpha) {
        int p = Math.max(1, Math.min(4, phase));
        if (p == 4 && haloAlpha > 0f) {
            spriteAlpha(g, "phase_halo", x - 3, y - 3, 15, 15, haloAlpha);
        }
        spriteAlpha(g, "phase_" + p, x, y, 9, 9, alpha);
    }

    public static void border(@Nonnull GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + height - 1, x + width, y + height, color);
        g.fill(x, y + 1, x + 1, y + height - 1, color);
        g.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void panel(@Nonnull GuiGraphics g, int x, int y, int width, int height, int fill) {
        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, BORDER);
    }

    public static void glass(@Nonnull GuiGraphics g, int x, int y, int width, int height) {
        glass(g, x, y, width, height, 1f);
    }

    public static void glass(@Nonnull GuiGraphics g, int x, int y, int width, int height, float thickness) {
        if (width > 0 && height > 0) {
            g.fillGradient(x, y, x + width, y + height, withAlpha(GLASS_TOP, thickness), withAlpha(GLASS_BOTTOM, thickness));
        }
    }

    /** 书页：外圈一道暗边，里面一道银边；glass 为 true 时铺玻璃底。四角另由 {@link #pageCorners} 压在内容上面 */
    public static void page(@Nonnull GuiGraphics g, int x, int y, int width, int height, boolean glass) {
        page(g, x, y, width, height, glass ? 1f : 0f);
    }

    public static void page(@Nonnull GuiGraphics g, int x, int y, int width, int height, float glass) {
        if (glass > 0f) {
            glass(g, x, y, width, height, glass);
        }
        border(g, x, y, width, height, 0xD905081A);
        border(g, x + 1, y + 1, width - 2, height - 2, EDGE);
    }

    public static void pageCorners(@Nonnull GuiGraphics g, int x, int y, int width, int height) {
        int r = x + width - 8;
        int b = y + height - 8;
        sprite(g, "pcorner", x, y, 8, 8, 0, 0, 8, 8, 8, 8);
        sprite(g, "pcorner", r, y, 8, 8, 8, 0, -8, 8, 8, 8);
        sprite(g, "pcorner", x, b, 8, 8, 0, 8, 8, -8, 8, 8);
        sprite(g, "pcorner", r, b, 8, 8, 8, 8, -8, -8, 8, 8);
    }

    /** 1 像素内描边的小牌（id 牌、标签、等级牌、冲突牌都是它） */
    public static void plate(@Nonnull GuiGraphics g, int x, int y, int width, int height, int fill, int edge) {
        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, edge);
    }

    /** 分类 / 宝藏这类彩色标签，13 高，返回宽度 */
    public static int tag(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                          int x, int y, int color) {
        int w = font.width(text) + 10;
        plate(g, x, y, w, 13, mix(0xFF0B1030, color, 0.14f), color);
        g.drawString(font, text.getString(), x + 5, y + 3, color, false);
        return w;
    }

    /** 冲突小牌（红），返回宽度（不含间距） */
    public static int chip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                           int x, int y, boolean hovered) {
        int w = font.width(text) + 10;
        plate(g, x, y, w, 13, hovered ? 0x3DFF5A6E : 0x14FF5A6E, hovered ? DANGER : 0xFF8F3F52);
        g.drawString(font, text.getString(), x + 5, y + 3, hovered ? 0xFFFFFFFF : 0xFFFF9AA6, false);
        return w;
    }

    public static int badge(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                            int x, int y, int color, boolean hovered) {
        int w = font.width(text) + 10;
        plate(g, x, y, w, 13, hovered ? mix(0xFF0B1030, color, 0.28f) : mix(0xFF0B1030, color, 0.14f),
                hovered ? 0xFFFFFFFF : color);
        g.drawString(font, text.getString(), x + 5, y + 3, hovered ? 0xFFFFFFFF : color, false);
        return w + 3;
    }

    public static void divider(@Nonnull GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, LINE);
    }

    /**
     * 银边按钮，高 14：上下各一道亮边和暗边，按下时字往下沉 1 像素。
     * 宽度由调用方给（一般是字宽 + 14）。返回是否悬停。
     */
    public static boolean button(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component label,
                                 int x, int y, int width, int height,
                                 boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && hit(mouseX, mouseY, x, y, width, height);
        boolean pressed = hovered && Ui.isLeftPressed();
        int rim = pressed ? 0xFF8FA2D6 : hovered ? EDGE_HI : 0xFF7F93C8;
        int hi = pressed ? 0xFF0B1030 : hovered ? 0xFF5367BD : 0xFF33468C;
        int bd = pressed ? 0xFF0F1638 : hovered ? 0xFF27367C : 0xFF1C2759;
        int lo = hovered ? 0xFF1A2458 : 0xFF121A40;
        if (!enabled) {
            rim = BORDER;
        }
        g.fill(x, y, x + width, y + height, bd);
        g.fill(x, y, x + width, y + 1, rim);
        g.fill(x, y + 1, x + width, y + 2, hi);
        g.fill(x, y + height - 2, x + width, y + height - 1, lo);
        g.fill(x, y + height - 1, x + width, y + height, rim);
        g.fill(x, y, x + 1, y + height, rim);
        g.fill(x + width - 1, y, x + width, y + height, rim);
        int color = !enabled ? OFF : hovered ? 0xFFFFFFFF : SILVER;
        int tx = x + (width - font.width(label)) / 2;
        text(g, font, label, tx, y + 3 + (pressed ? 1 : 0), color);
        return hovered;
    }

    public static int buttonWidth(@Nonnull Font font, @Nonnull Component label) {
        return font.width(label) + 14;
    }

    /** 节头：分节星 + 银字 + 一道线 + 收尾小菱，高 10，返回下一行的 y（已含下面 3 像素） */
    public static int sectionHeader(@Nonnull GuiGraphics g, @Nonnull Font font,
                                    @Nonnull Component title, int x, int y, int innerWidth) {
        sprite(g, "orn", x, y + 1, 7, 7);
        int tx = x + 12;
        int endX = x + innerWidth - 5;
        int titleW = font.width(title);
        if (titleW <= endX - 5 - tx) {
            text(g, font, title, tx, y + 1, SILVER);
        } else {
            String cut = trim(font, title.getString(), endX - 5 - tx);
            text(g, font, cut, tx, y + 1, SILVER);
            titleW = font.width(cut);
        }
        int lineLeft = tx + titleW + 5;
        if (lineLeft < endX - 5) {
            g.fill(lineLeft, y + 4, endX - 5, y + 5, LINE);
        }
        sprite(g, "orn_end", endX, y + 2, 5, 5);
        return y + 13;
    }

    /** 标签 …… 值，值靠右；行高 11 */
    public static int keyValue(@Nonnull GuiGraphics g, @Nonnull Font font,
                               @Nonnull Component label, @Nonnull String value,
                               int x, int y, int innerWidth, int valueColor) {
        int labelW = font.width(label);
        int valueW = font.width(value);
        g.drawString(font, label.getString(), x, y + 1, TEXT_DIM, false);
        int vx = x + innerWidth - valueW;
        g.drawString(font, value, vx, y + 1, valueColor, false);
        dots(g, x + labelW + 4, vx - 4, y + 7);
        return y + 11;
    }

    public static void dots(@Nonnull GuiGraphics g, int x0, int x1, int y) {
        if (x0 >= x1) {
            return;
        }
        // fill 不在 drawManaged 里时一次一提交，一行点线合成一批
        g.drawManaged(() -> {
            for (int x = x0; x < x1; x += 3) {
                g.fill(x, y, x + 1, y + 1, DOTS);
            }
        });
    }

    /** 刻度尺滚动条，宽 7：每 20 一长刻、每 4 一短刻、中轴一道，游标框里穿一颗月珠。内容放得下时什么都不画 */
    public static void scrollbar(@Nonnull GuiGraphics g, int x, int y, int height,
                                 int contentHeight, int scroll, boolean hot) {
        if (contentHeight <= height || height <= 0) {
            return;
        }
        int th = thumbHeight(height, contentHeight);
        int max = contentHeight - height;
        int ty = y + (max <= 0 ? 0 : Math.round((float) (height - th) * Math.min(scroll, max) / max));
        // 刻度和游标框合成一批提交；月珠是贴图，得等这批画完再盖上去
        g.drawManaged(() -> {
            g.fill(x + 3, y, x + 4, y + height, 0xFF232F5C);
            for (int i = 0; i < height; i += 4) {
                g.fill(x + 2, y + i, x + 5, y + i + 1, 0xFF3D4F8A);
            }
            for (int i = 0; i < height; i += 20) {
                g.fill(x + 1, y + i, x + 6, y + i + 1, 0xFF6C7FB8);
            }
            g.fill(x + 1, ty, x + 6, ty + th, hot ? 0xF528387A : 0xF5101839);
            border(g, x + 1, ty, 5, th, hot ? 0xFFFFFFFF : EDGE);
        });
        sprite(g, "bead", x + 1, ty + th / 2 - 2, 5, 5);
    }

    public static void scrollbar(@Nonnull GuiGraphics g, int x, int y, int height,
                                 int contentHeight, int scroll) {
        scrollbar(g, x, y, height, contentHeight, scroll, false);
    }

    /** 游标高度取奇数，月珠才落在正中 */
    public static int thumbHeight(int height, int contentHeight) {
        int h = Math.max(13, Math.round((float) height * height / Math.max(1, contentHeight)));
        h = (h & 1) == 0 ? h + 1 : h;
        return Math.max(1, Math.min(height, h));
    }

    /** 带投影的字：投影是右下 1 像素的深蓝，不走原版那种按字色压暗的投影 */
    public static void text(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                            int x, int y, int color) {
        text(g, font, FormattedCharSequence.of(text), x, y, color);
    }

    public static void text(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull String text,
                            int x, int y, int color) {
        g.drawString(font, text, x + 1, y + 1, SHADOW, false);
        g.drawString(font, text, x, y, color, false);
    }

    public static void text(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull FormattedCharSequence text,
                            int x, int y, int color) {
        g.drawString(font, recolor(text, SHADOW), x + 1, y + 1, SHADOW, false);
        g.drawString(font, text, x, y, color, false);
    }

    @Nonnull
    public static FormattedCharSequence recolor(@Nonnull FormattedCharSequence seq, int color) {
        List<TextRun> out = new ArrayList<>(seq.runs().size());
        for (TextRun run : seq.runs()) {
            out.add(new TextRun(run.text, color & 0xFFFFFF, run.flags));
        }
        return FormattedCharSequence.of(out);
    }

    /** 自动换行，返回下一行的 y；每行占 lineHeight + 1 */
    public static int wrapped(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                              int x, int y, int maxWidth, int color) {
        return wrapped(g, font, text, x, y, maxWidth, color, font.lineHeight + 1, false);
    }

    public static int wrapped(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                              int x, int y, int maxWidth, int color, int lineStep, boolean shadow) {
        for (FormattedCharSequence line : font.splitSequences(text, Math.max(1, maxWidth))) {
            if (shadow) {
                text(g, font, line, x, y, color);
            } else {
                g.drawString(font, line, x, y, color, false);
            }
            y += lineStep;
        }
        return y;
    }

    public static void trimmed(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                               int x, int y, int maxWidth, int color) {
        trimmed(g, font, text, x, y, maxWidth, color, false);
    }

    public static void trimmed(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                               int x, int y, int maxWidth, int color, boolean shadow) {
        String s = trim(font, text.getString(), maxWidth);
        if (shadow) {
            text(g, font, s, x, y, color);
        } else {
            g.drawString(font, s, x, y, color, false);
        }
    }

    @Nonnull
    public static String trim(@Nonnull Font font, @Nonnull String raw, int maxWidth) {
        if (font.width(raw) <= maxWidth) {
            return raw;
        }
        if (maxWidth <= font.width("…")) {
            return "";
        }
        return font.plainSubstrByWidth(raw, maxWidth - font.width("…")) + "…";
    }

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

    public static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static final int[][] DIRS = {{1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    private static final List<long[]> SPARKS = new ArrayList<>();

    /** 从 (cx, cy) 往八个方向迸出 n 颗 3×3 星点，每 45 毫秒走一步，六步灭 */
    public static void burst(int cx, int cy, int n) {
        long t = now();
        for (int i = 0; i < n && SPARKS.size() < 64; i++) {
            SPARKS.add(new long[]{t, cx - 1, cy - 1, (i * 3 + 1) % 8, 2 + (i % 3)});
        }
    }

    public static void renderSparks(@Nonnull GuiGraphics g) {
        if (SPARKS.isEmpty()) {
            return;
        }
        long t = now();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        Iterator<long[]> it = SPARKS.iterator();
        while (it.hasNext()) {
            long[] s = it.next();
            int step = (int) ((t - s[0]) / 45L);
            if (step > 5) {
                it.remove();
                continue;
            }
            int[] d = DIRS[(int) s[3]];
            int dist = 4 + step * (int) s[4];
            spriteAlpha(g, "spark", (int) s[1] + d[0] * dist, (int) s[2] + d[1] * dist, 3, 3, step > 3 ? 0.4f : 1f);
        }
        g.pose().popPose();
    }
}
