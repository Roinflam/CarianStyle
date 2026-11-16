package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 条目页的卷首：月亮、名字和 id 牌、标签行、等级牌行，下沿一道银线压一层薄雾。
 * 卷首不铺底，透出后面的湖景；它跟着正文一起滚。本模组附魔和其他附魔两页共用。
 */
public final class DetailHead {

    public static final int NO_LEVEL = Integer.MIN_VALUE;

    private static final int ROW = 13;
    private static final int GAP_X = 3;
    /** 标签或等级牌一行放不下时折到下一行，行距同列距 */
    private static final int WRAP_GAP = 3;
    private static final int NAME_ID_GAP = 6;
    private static final int LEVEL_MIN_W = 15;

    public static final class Tag {
        final Component text;
        final int color;
        final int phase;

        public Tag(@Nonnull Component text, int color) {
            this(text, color, 0);
        }

        /** phase 1~4 时是稀有度标签，字前面带月相 */
        public Tag(@Nonnull Component text, int color, int phase) {
            this.text = text;
            this.color = color;
            this.phase = phase;
        }

        @Nonnull
        public static Tag rarity(@Nonnull Component text, int phase) {
            return new Tag(text, UiTheme.phaseColor(phase), phase);
        }
    }

    private boolean large;

    private String key;
    private Component name = Component.empty();
    private String id = "";
    private List<Tag> tags = Collections.emptyList();
    private int catColor = UiTheme.ACCENT;
    private int[] levels = new int[0];
    private boolean levelsVisible;
    private int current = 1;

    private long hiddenUntil = Long.MIN_VALUE / 4;
    private long riseStart = Long.MIN_VALUE / 4;
    private int riseDelay;
    private int riseDur = 500;
    private int riseSteps = 12;
    private int ringDelay = 200;
    private long breatheStart;
    private long pulseStart = Long.MIN_VALUE / 4;

    private int frameFrom;
    private int frameTo;
    private long frameStart;

    private int ptrFrom;
    private long ptrStart = Long.MIN_VALUE / 4;
    private boolean sparkPending;

    private int litLevel = NO_LEVEL;
    private long litStart;

    private final List<int[]> levelHits = new ArrayList<>();
    private int height;

    public void setLarge(boolean large) {
        this.large = large;
    }

    public boolean isLarge() {
        return large;
    }

    public int moonSize() {
        return large ? MoonDisc.LARGE : MoonDisc.SMALL;
    }

    /** 卷首左内边距，背景画的月心按 x + padLeft() + moonSize() / 2 对位 */
    public int padLeft() {
        return large ? 14 : 8;
    }

    /** 上一次 render 量出的卷首高度 */
    public int height() {
        return height;
    }

    /**
     * 换条目。key 和上一条不同才播换条目的动画（月亮重升、月相逐帧过渡、指针从公式重走）；
     * 同一条重设（重载数值、窗口改尺寸）只换内容。
     *
     * @param phase    稀有度月相 1~4，见 {@link UiTheme#phaseOf}
     * @param catColor 分类色，月晕和刻度环按它着色
     */
    public void setEntry(@Nonnull String key, @Nonnull Component name, @Nonnull String id,
                         @Nonnull List<Tag> tags, int phase, int catColor) {
        long t = UiTheme.now();
        int target = MoonDisc.frameOf(phase);
        if (this.key == null) {
            frameFrom = target;
        } else if (!this.key.equals(key)) {
            frameFrom = frame(t);
            frameStart = t + 260;
            rise(t, 0, 500, 12, 200);
            ptrFrom = 0;
            ptrStart = t + 320;
            sparkPending = false;
            litLevel = NO_LEVEL;
        } else if (target != frameTo) {
            frameFrom = target;
        }
        frameTo = target;
        this.key = key;
        this.name = name;
        this.id = id;
        this.tags = tags;
        this.catColor = catColor;
    }

    /** 清空（详情没有条目时），下一次 setEntry 不播过渡 */
    public void clear() {
        key = null;
    }

    /**
     * @param maxLevel 最高等级
     * @param formula  有没有「公式」档（指针正上方那格）
     * @param visible  画不画等级牌；不画时指针仍指着当前级
     */
    public void setLevels(int maxLevel, boolean formula, boolean visible) {
        int count = Math.max(0, maxLevel) + (formula ? 1 : 0);
        if (levels.length != count || (count > 0 && levels[0] != (formula ? DescriptionText.FORMULA : 1))) {
            levels = new int[count];
            for (int i = 0; i < count; i++) {
                levels[i] = formula ? i : i + 1;
            }
        }
        levelsVisible = visible && count > 0;
    }

    /**
     * 当前等级（公式档传 {@link DescriptionText#FORMULA}）。animate 为 true 时是玩家点了等级牌：
     * 牌子闪一下、月晕脉冲、指针走到新级后迸几颗星点。
     */
    public void setLevel(int level, boolean animate) {
        long t = UiTheme.now();
        if (animate) {
            ptrFrom = pointer(t);
            ptrStart = t;
            sparkPending = true;
            litLevel = level;
            litStart = t;
            pulse();
        }
        current = level;
    }

    /**
     * 开场月出：大月在 handoff 时落进月盒，这之前月盒里不画月亮；大月升起时已经盈到本条目的月相，落位后不再变，
     * 环随后淡入，指针从公式走到当前级。
     */
    public void playIntro(long handoff) {
        hiddenUntil = handoff;
        rise(handoff, 0, 1, 1, 60);
        breatheStart = handoff + 400;
        frameFrom = frameTo;
        ptrFrom = 0;
        ptrStart = handoff + 160;
        sparkPending = false;
    }

    /** 开场被跳过或关掉：直接是播完的样子 */
    public void skipIntro() {
        hiddenUntil = Long.MIN_VALUE / 4;
        riseStart = Long.MIN_VALUE / 4;
        frameFrom = frameTo;
        ptrStart = Long.MIN_VALUE / 4;
        breatheStart = UiTheme.now();
        sparkPending = false;
    }

    /** 开场大月要盈到的月相帧 */
    public int frameAt(long t) {
        return frame(t);
    }

    public int catColor() {
        return catColor;
    }

    /** 切回这一页：月亮重升，指针重走 */
    public void playSelect() {
        long t = UiTheme.now();
        rise(t, 0, 500, 12, 200);
        ptrFrom = 0;
        ptrStart = t + 320;
        sparkPending = false;
    }

    /** 月晕闪一下（重载数值、切等级） */
    public void pulse() {
        long t = UiTheme.now();
        pulseStart = t;
        breatheStart = t + 360;
    }

    private void rise(long start, int delay, int dur, int steps, int ringDelay) {
        riseStart = start;
        riseDelay = delay;
        riseDur = dur;
        riseSteps = steps;
        this.ringDelay = ringDelay;
        breatheStart = start + delay + dur;
    }

    private int frame(long t) {
        int span = Math.abs(frameTo - frameFrom);
        int done = t < frameStart ? 0 : (int) Math.min(span, 1 + (t - frameStart) / 45L);
        return frameFrom + Integer.signum(frameTo - frameFrom) * done;
    }

    private int pointer(long t) {
        int to = stepOf(current);
        int span = Math.abs(to - ptrFrom);
        int done = t < ptrStart ? 0 : (int) Math.min(span, (t - ptrStart) / 26L);
        return ptrFrom + Integer.signum(to - ptrFrom) * done;
    }

    /**
     * 一级 4 步，环上 12 格。别的模组有最高十几级甚至上百级的附魔，照 4 步一级指针要绕好几圈、
     * 走几十秒，所以最高级超过 11 时按比例落在前 11 格上。
     */
    private int stepOf(int level) {
        int last = levels.length == 0 ? 0 : levels[levels.length - 1];
        if (last <= 11) {
            return level * 4;
        }
        return Math.round(Math.max(0, Math.min(level, last)) * 44f / last);
    }

    private float glow(long t) {
        long pe = t - pulseStart;
        if (pe >= 0 && pe < 360) {
            float p = UiTheme.steps(pe, 360, 6);
            return p < 0.4f ? 0.2f + 0.8f * p / 0.4f : 1f;
        }
        return t < breatheStart ? 1f : UiTheme.breathe(t - breatheStart, 4800, 8, 0.4f);
    }

    /**
     * 画卷首，返回卷首高度。坐标是已经减过滚动量的；外面该有的视口 scissor 由调用方开，
     * 这里再套一层卷首自己的（月亮升起时裁在下沿）。
     *
     * @param width  卷首宽度（视口宽，不含滚动条那一列）
     * @param hover  鼠标在视口里，等级牌才响应悬停
     */
    public int render(@Nonnull GuiGraphics g, @Nonnull Font font, int x, int y, int width,
                      int mouseX, int mouseY, boolean hover) {
        levelHits.clear();
        long t = UiTheme.now();
        int n = moonSize();
        int pl = padLeft();
        int mr = large ? 12 : 8;
        int pr = large ? 14 : 8;
        int gap = large ? 8 : 4;
        int top = y + 4;
        int hmX = x + pl + n + mr;
        int hmW = Math.max(0, width - pl - n - mr - pr);

        int nameY = top + (large ? 8 : 0);
        int tagsY = nameY + ROW + gap;

        int[] tagW = new int[tags.size()];
        for (int i = 0; i < tagW.length; i++) {
            tagW[i] = Math.min(hmW, tagWidth(font, tags.get(i)));
        }
        int[][] tagPos = new int[tagW.length][];
        int tagsH = flow(tagW, hmX, tagsY, hmW, tagPos);

        int lvY = tagsY + tagsH + gap;
        int[] lvW = new int[levelsVisible ? levels.length : 0];
        for (int i = 0; i < lvW.length; i++) {
            lvW[i] = Math.min(hmW, Math.max(LEVEL_MIN_W, font.width(levelLabel(levels[i])) + 8));
        }
        int[][] lvPos = new int[lvW.length][];
        int lvH = Math.max(ROW, flow(lvW, hmX, lvY, hmW, lvPos));

        int bottom = Math.max(top + n, lvY + lvH) + 5;
        height = bottom - y;

        g.enableScissor(x, y, x + width, bottom);

        int mx = x + pl;
        int rise = Math.round((1f - UiTheme.steps(t - riseStart - riseDelay, riseDur, riseSteps)) * n * 0.62f);
        if (t >= hiddenUntil) {
            MoonDisc.drawMoon(g, mx, top + rise, n, frame(t), catColor, glow(t));
        }
        int q = pointer(t);
        MoonDisc.drawRing(g, mx, top, n, catColor, q, UiTheme.steps(t - riseStart - ringDelay, 300, 4));
        if (sparkPending && t >= ptrStart && q == stepOf(current)) {
            sparkPending = false;
            int[] c = MoonDisc.pointerCenter(mx, top, n, q);
            UiTheme.burst(c[0], c[1], 4);
        }

        // 薄雾和背景画岸线上那层用同一个偏移，横着对得上
        int off = CodexScene.mistOffset();
        for (int px = x + off - 128; px < x + width; px += 128) {
            UiTheme.sprite(g, "mist", px, bottom - 15, 128, 14);
        }
        g.fill(x, bottom - 1, x + width, bottom, UiTheme.EDGE);

        renderNameRow(g, font, hmX, nameY, hmW);

        for (int i = 0; i < tagW.length; i++) {
            renderTag(g, font, tags.get(i), tagPos[i][0], tagPos[i][1], tagW[i]);
        }

        boolean pressed = Minecraft.getInstance().mouseHandler.isLeftPressed();
        for (int i = 0; i < lvW.length; i++) {
            int lx = lvPos[i][0];
            int ly = lvPos[i][1];
            int w = lvW[i];
            int level = levels[i];
            boolean on = level == current;
            boolean hovered = hover && UiTheme.hit(mouseX, mouseY, lx, ly, w, ROW);
            int fill;
            int edge;
            int color;
            if (on) {
                fill = 0xFFE2EAFF;
                if (level == litLevel && t - litStart < 300) {
                    fill = UiTheme.mix(0xFF7FE3FF, 0xFFE2EAFF, UiTheme.steps(t - litStart, 300, 6));
                }
                edge = 0xFFFFFFFF;
                color = UiTheme.INK_DARK;
            } else {
                fill = hovered && pressed ? 0xFF070A1C : UiTheme.PANEL_ALT;
                edge = hovered ? UiTheme.SILVER : UiTheme.BORDER;
                color = hovered ? 0xFFFFFFFF : UiTheme.TEXT_DIM;
            }
            UiTheme.plate(g, lx, ly, w, ROW, fill, edge);
            String label = UiTheme.trim(font, levelLabel(level).getString(), w - 2);
            g.drawString(font, label, lx + (w - font.width(label)) / 2, ly + 3, color, false);
            if (on) {
                g.fill(lx + 2, ly + ROW + 2, lx + w - 2, ly + ROW + 3, UiTheme.ACCENT);
            }
            levelHits.add(new int[]{lx, ly, w, ROW, level});
        }

        g.disableScissor();
        return height;
    }

    /** 名字靠左、id 牌靠右；挤不下时先截名字，名字剩不到三个字宽就把 id 牌让掉 */
    private void renderNameRow(@Nonnull GuiGraphics g, @Nonnull Font font, int x, int y, int w) {
        String raw = name.getString();
        int nameW = font.width(raw);
        int idW = id.isEmpty() ? 0 : font.width(id) + 10;
        boolean showId = idW > 0 && idW <= w;
        if (showId && nameW + NAME_ID_GAP + idW > w && w - NAME_ID_GAP - idW < Math.min(nameW, font.width("一二三") + 6)) {
            showId = false;
        }
        int nameMax = showId ? w - NAME_ID_GAP - idW : w;
        UiTheme.text(g, font, UiTheme.trim(font, raw, nameMax), x, y + 3, 0xFFFFFFFF);
        if (!showId) {
            return;
        }
        int ix = x + w - idW;
        UiTheme.plate(g, ix, y, idW, ROW, UiTheme.PANEL, UiTheme.BORDER);
        // 命名空间那半截对读的人没什么用，压暗，冒号后面才是要抄的
        int colon = id.indexOf(':');
        int tx = ix + 5;
        if (colon >= 0) {
            String ns = id.substring(0, colon + 1);
            g.drawString(font, ns, tx, y + 3, UiTheme.OFF, false);
            tx += font.width(ns);
        }
        g.drawString(font, id.substring(colon + 1), tx, y + 3, UiTheme.TEXT_DIM, false);
    }

    private static int tagWidth(@Nonnull Font font, @Nonnull Tag tag) {
        int w = font.width(tag.text) + 10;
        return tag.phase > 0 ? w + 11 : w;
    }

    private static void renderTag(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Tag tag, int x, int y, int w) {
        UiTheme.plate(g, x, y, w, ROW, UiTheme.mix(0xFF0B1030, tag.color, 0.14f), tag.color);
        int tx = x + 5;
        if (tag.phase > 0) {
            UiTheme.phase(g, tag.phase, x + 3, y + 2, 1f, 0f);
            tx = x + 16;
        }
        g.drawString(font, UiTheme.trim(font, tag.text.getString(), x + w - 5 - tx), tx, y + 3, tag.color, false);
    }

    @Nonnull
    private static Component levelLabel(int level) {
        return DescriptionText.chipLabel(level);
    }

    /** 从左往右排，放不下折行；返回总高度，out 里是各块左上角 */
    private static int flow(int[] widths, int x0, int y0, int maxW, int[][] out) {
        if (widths.length == 0) {
            return 0;
        }
        int cx = x0;
        int cy = y0;
        for (int i = 0; i < widths.length; i++) {
            if (cx > x0 && cx + widths[i] > x0 + maxW) {
                cx = x0;
                cy += ROW + WRAP_GAP;
            }
            out[i] = new int[]{cx, cy};
            cx += widths[i] + GAP_X;
        }
        return cy - y0 + ROW;
    }

    /** 点中等级牌返回那一级，否则 {@link #NO_LEVEL}；用的是上一帧画的位置 */
    public int mouseClicked(double mouseX, double mouseY) {
        for (int[] hit : levelHits) {
            if (UiTheme.hit(mouseX, mouseY, hit[0], hit[1], hit[2], hit[3])) {
                return hit[4];
            }
        }
        return NO_LEVEL;
    }
}
