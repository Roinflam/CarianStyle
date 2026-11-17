package pers.roinflam.carianstyle.client.gui;

import pers.roinflam.carianstyle.mc112.gui.Ui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.mc112.gui.Font;
import pers.roinflam.carianstyle.mc112.gui.GuiGraphics;
import pers.roinflam.carianstyle.mc112.gui.Component;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 特效开关页：五组按行数分到左右两页，各页自己滚；每项是银框里嵌一粒辉石。
 */
@SideOnly(Side.CLIENT)
final class TogglePage {

    private static final int ROW = 25;
    private static final int HEAD = 20;
    private static final int GROUP_GAP = 3;
    // 卷尾纹饰：上空 12，纹饰高 7，下留 8
    private static final int TAIL = 12 + 7 + 8;
    private static final int CAT = 0xFF9FC2FF;
    private static final int GEM_MS = 55;

    private final Side left = new Side();
    private final Side right = new Side();
    private boolean large;
    private final Map<VisualEffectType, long[]> wax = new EnumMap<>(VisualEffectType.class);

    private static final class Side {
        int x;
        int y;
        int w;
        int h;
        int scroll;
        int content;
        final List<VisualEffectType.Category> groups = new ArrayList<>();
        final List<Object[]> hits = new ArrayList<>();
    }

    TogglePage() {
        split();
    }

    private void split() {
        VisualEffectType.Category[] cats = VisualEffectType.Category.values();
        int[] heights = new int[cats.length];
        int total = 0;
        for (int i = 0; i < cats.length; i++) {
            heights[i] = HEAD + ROW * count(cats[i]);
            total += heights[i];
        }
        int best = 1;
        int bestMax = Integer.MAX_VALUE;
        int acc = 0;
        for (int k = 1; k < cats.length; k++) {
            acc += heights[k - 1];
            int m = Math.max(acc, total - acc);
            if (m < bestMax) {
                bestMax = m;
                best = k;
            }
        }
        for (int i = 0; i < cats.length; i++) {
            (i < best ? left : right).groups.add(cats[i]);
        }
    }

    private static int count(@Nonnull VisualEffectType.Category category) {
        int n = 0;
        for (VisualEffectType type : VisualEffectType.values()) {
            if (type.getCategory() == category) {
                n++;
            }
        }
        return n;
    }

    void setBounds(int lx, int y, int lw, int rx, int rw, int h, boolean large) {
        this.large = large;
        left.x = lx;
        left.y = y;
        left.w = lw;
        left.h = h;
        right.x = rx;
        right.y = y;
        right.w = rw;
        right.h = h;
        clamp(left);
        clamp(right);
    }

    /** 两页里较高那页的内容高度（不含书页内边距） */
    int contentNeed() {
        return Math.max(height(left), height(right));
    }

    private int height(@Nonnull Side side) {
        int h = 0;
        for (int i = 0; i < side.groups.size(); i++) {
            h += (i > 0 ? GROUP_GAP : 0) + HEAD + ROW * count(side.groups.get(i));
        }
        return h + (large ? TAIL : 0);
    }

    void renderLeft(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
        render(g, font, left, mouseX, mouseY);
    }

    void renderRight(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
        render(g, font, right, mouseX, mouseY);
    }

    private void render(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Side side, int mouseX, int mouseY) {
        side.hits.clear();
        side.content = height(side);
        clamp(side);
        int vw = side.w - 8;
        boolean inside = UiTheme.hit(mouseX, mouseY, side.x, side.y, vw, side.h);
        int mx = inside ? mouseX : -1;
        int my = inside ? mouseY : -1;
        g.enableScissor(side.x, side.y, side.x + vw, side.y + side.h);
        int cy = side.y - side.scroll;
        for (int i = 0; i < side.groups.size(); i++) {
            if (i > 0) {
                cy += GROUP_GAP;
            }
            cy = renderGroup(g, font, side, side.groups.get(i), side.x, cy, vw, mx, my);
        }
        if (large) {
            int tx = side.x + Math.max(0, (vw - 97) / 2);
            int ty = cy + 12;
            g.drawManaged(() -> {
                for (int i = 0; i < 40; i++) {
                    int c = UiTheme.withAlpha(0xFF55679F, i / 39f);
                    g.fill(tx + i, ty + 3, tx + i + 1, ty + 4, c);
                    g.fill(tx + 96 - i, ty + 3, tx + 97 - i, ty + 4, c);
                }
            });
            UiTheme.sprite(g, "orn", tx + 45, ty, 7, 7);
        }
        g.disableScissor();
        UiTheme.scrollbar(g, side.x + side.w - 7, side.y, side.h, side.content, side.scroll,
                UiTheme.hit(mouseX, mouseY, side.x + side.w - 7, side.y, 7, side.h));
    }

    private int renderGroup(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Side side,
                            @Nonnull VisualEffectType.Category category, int x, int y, int w, int mx, int my) {
        spine(g, x, y, w, HEAD, CAT);

        boolean all = true;
        boolean none = true;
        for (VisualEffectType type : VisualEffectType.values()) {
            if (type.getCategory() == category) {
                boolean on = VisualToggle.isEnabled(type);
                all &= on;
                none &= !on;
            }
        }
        Component allOn = Component.translatable("carianstyle.codex.effects.all_on");
        Component allOff = Component.translatable("carianstyle.codex.effects.all_off");
        int offW = font.width(allOff) + 10;
        int onW = font.width(allOn) + 10;
        int offX = x + w - 13 - offW;
        int onX = offX - 3 - onW;
        mini(g, font, allOn, onX, y + 3, onW, all ? UiTheme.ON : 0, UiTheme.hit(mx, my, onX, y + 3, onW, 14));
        mini(g, font, allOff, offX, y + 3, offW, none ? UiTheme.DANGER : 0, UiTheme.hit(mx, my, offX, y + 3, offW, 14));
        side.hits.add(new Object[]{onX, y + 3, onW, 14, category, Boolean.TRUE});
        side.hits.add(new Object[]{offX, y + 3, offW, 14, category, Boolean.FALSE});

        // 组名放不下时先让出星座的位置，还放不下再截断
        String name = category.getDisplayName().getString();
        int nameX = x + 40;
        if (font.width(name) > onX - 6 - nameX) {
            nameX = x + 13;
        } else {
            UiTheme.constellation(g, UiTheme.CONST_FX + category.ordinal(), x + 13, y + 4, CAT);
        }
        UiTheme.text(g, font, UiTheme.trim(font, name, Math.max(0, onX - 6 - nameX)), nameX, y + 6, CAT);

        int cy = y + HEAD;
        for (VisualEffectType type : VisualEffectType.values()) {
            if (type.getCategory() != category) {
                continue;
            }
            renderRow(g, font, side, type, x, cy, w, mx, my);
            cy += ROW;
        }
        return cy;
    }

    static void spine(@Nonnull GuiGraphics g, int x, int y, int w, int h, int cat) {
        g.fill(x, y, x + w, y + h, UiTheme.mix(0xFF0B1030, cat, 0.22f));
        g.fill(x, y, x + w, y + 1, UiTheme.mix(0xFF0B1030, cat, 0.46f));
        g.fill(x, y + h - 1, x + w, y + h, 0xFF070A1C);
        int ridge = UiTheme.mix(0xFF0B1030, cat, 0.6f);
        g.fill(x + 3, y, x + 5, y + h, cat);
        g.fill(x + 7, y, x + 8, y + h, ridge);
        g.fill(x + w - 5, y, x + w - 3, y + h, cat);
        g.fill(x + w - 8, y, x + w - 7, y + h, ridge);
    }

    private static void mini(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component label,
                             int x, int y, int w, int current, boolean hovered) {
        int edge = current != 0 ? current : hovered ? UiTheme.SILVER : UiTheme.BORDER;
        int color = current != 0 ? current : hovered ? 0xFFFFFFFF : UiTheme.TEXT_DIM;
        boolean pressed = hovered && Ui.isLeftPressed();
        UiTheme.plate(g, x, y, w, 14, pressed ? 0xFF070A1C : 0xFF0D1330, edge);
        g.drawString(font, label, x + 5, y + 3, color, false);
    }

    private void renderRow(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Side side,
                           @Nonnull VisualEffectType type, int x, int y, int w, int mx, int my) {
        boolean on = VisualToggle.isEnabled(type);
        boolean hovered = UiTheme.hit(mx, my, x, y, w, ROW);
        if (hovered) {
            g.fill(x, y, x + w, y + ROW, 0x177FB4FF);
        }
        int frame = on ? 3 : hovered ? 1 : 0;
        long[] anim = wax.get(type);
        if (anim != null) {
            long e = UiTheme.now() - anim[0];
            if (e >= 0) {
                int k = (int) (e / GEM_MS);
                if (k > 3) {
                    wax.remove(type);
                    // 切走过的页回来时别补迸；滚出书页的行也不迸，星点不受裁剪会画到页外
                    boolean late = e > 4 * GEM_MS + 150;
                    boolean visible = y + 12 >= side.y && y + 12 < side.y + side.h;
                    if (anim[1] == 1L && !late && visible) {
                        UiTheme.burst(x + 16, y + 12, 5);
                    }
                } else {
                    frame = anim[1] == 1L ? k : 3 - k;
                }
            } else {
                frame = anim[1] == 1L ? 0 : 3;
            }
        }
        UiTheme.sprite(g, "gem_" + frame, x + 10, y + 6, 13, 13);

        int textX = x + 31;
        int textW = w - 31 - 6;
        String name = type.getDisplayName().getString();
        int descW = textW;
        if (type.isInformational() && !on) {
            // 警告和名字同一行放得下就放名字右边；放不下挪到描述那行右端，描述让位，挤到不足 30 就不画描述
            String warn = Component.translatable("carianstyle.codex.effects.info_warning").getString();
            int warnW = font.width(warn);
            if (font.width(name) + 6 + warnW <= textW) {
                g.drawString(font, warn, x + w - 6 - warnW, y + 3, UiTheme.WARN, false);
            } else {
                warn = UiTheme.trim(font, warn, textW);
                warnW = font.width(warn);
                g.drawString(font, warn, x + w - 6 - warnW, y + 14, UiTheme.WARN, false);
                descW = textW - warnW - 6;
                if (descW < 30) {
                    descW = 0;
                }
            }
        }
        name = UiTheme.trim(font, name, textW);
        if (on) {
            UiTheme.text(g, font, name, textX, y + 3, UiTheme.MOON);
        } else {
            g.drawString(font, name, textX, y + 3, UiTheme.OFF, false);
        }
        if (descW > 0) {
            UiTheme.trimmed(g, font, type.getDescription(), textX, y + 14, descW, on ? UiTheme.TEXT_DIM : 0xFF48527A);
        }
        side.hits.add(new Object[]{x, y, w, ROW, type, null});
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        return click(left, mouseX, mouseY) || click(right, mouseX, mouseY);
    }

    private boolean click(@Nonnull Side side, double mouseX, double mouseY) {
        if (!UiTheme.hit(mouseX, mouseY, side.x, side.y, side.w - 8, side.h)) {
            return false;
        }
        long t = UiTheme.now();
        for (Object[] hit : side.hits) {
            if (!UiTheme.hit(mouseX, mouseY, (Integer) hit[0], (Integer) hit[1], (Integer) hit[2], (Integer) hit[3])) {
                continue;
            }
            Object target = hit[4];
            if (target instanceof VisualEffectType) {
                VisualEffectType type = (VisualEffectType) target;
                boolean on = !VisualToggle.isEnabled(type);
                VisualToggle.set(type, on);
                wax.put(type, new long[]{t, on ? 1L : 0L});
            } else if (target instanceof VisualEffectType.Category) {
                VisualEffectType.Category category = (VisualEffectType.Category) target;
                boolean on = Boolean.TRUE.equals(hit[5]);
                // 全开 / 全关：变了的那几项依次错开 35 毫秒嵌入
                int n = 0;
                for (VisualEffectType type : VisualEffectType.values()) {
                    if (type.getCategory() == category && VisualToggle.isEnabled(type) != on) {
                        wax.put(type, new long[]{t + n * 35L, on ? 1L : 0L});
                        n++;
                    }
                }
                VisualToggle.setCategory(category, on);
            }
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Side side = UiTheme.hit(mouseX, mouseY, left.x, left.y, left.w, left.h) ? left
                : UiTheme.hit(mouseX, mouseY, right.x, right.y, right.w, right.h) ? right : null;
        if (side == null) {
            return false;
        }
        side.scroll -= (int) (delta * ROW);
        clamp(side);
        return true;
    }

    private void clamp(@Nonnull Side side) {
        int max = Math.max(0, side.content - side.h);
        side.scroll = Math.max(0, Math.min(max, side.scroll));
    }
}
