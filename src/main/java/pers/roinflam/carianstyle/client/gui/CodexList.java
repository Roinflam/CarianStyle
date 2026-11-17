package pers.roinflam.carianstyle.client.gui;

import pers.roinflam.carianstyle.mc112.gui.Ui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import pers.roinflam.carianstyle.mc112.gui.Font;
import pers.roinflam.carianstyle.mc112.gui.GuiGraphics;
import pers.roinflam.carianstyle.mc112.gui.Component;
import pers.roinflam.carianstyle.mc112.gui.FormattedCharSequence;
import pers.roinflam.carianstyle.codex.CodexEntry;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 百科左页的目录：书脊分类头 + 25 高的条目行，右边一把刻度尺滚动条。
 * 标题行和条目行拍平在同一个列表里，各行的纵向位置在 setEntries 时算好。
 */
@SideOnly(Side.CLIENT)
public final class CodexList {

    private static final int ROW_HEIGHT = 25;
    private static final int HEADER_HEIGHT = 14;
    private static final int HEADER_GAP = 3;

    /** 视口右边让给滚动条：1 像素空隙 + 7 宽刻度尺 */
    private static final int SCROLLBAR_SPACE = 8;
    private static final int SCROLLBAR_WIDTH = 7;

    private static final int PAD_LEFT = 11;
    private static final int PAD_RIGHT = 22;
    private static final int NAME_Y = 3;
    private static final int SUB_Y = 14;
    /** 副行右边再让出的宽度，月相和行尾说明在这里 */
    private static final int SUB_RIGHT_EXTRA = 36;

    private static final int PHASE_RIGHT = 7;
    private static final int PHASE_Y = 8;
    private static final int MARK_Y = 9;

    private static final int SPINE_CONST_X = 13;
    private static final int SPINE_TEXT_X = SPINE_CONST_X + 21 + 6;
    private static final int SPINE_PAD_RIGHT = 13;

    private static final int EMPTY_Y = 24;
    private static final int EMPTY_PAD = 8;

    private static final int HOVER_FILL = 0x177FB4FF;
    private static final int PRESS_FILL = 0x0A7FB4FF;
    private static final int SELECT_LEFT = 0x577FB4FF;
    private static final int SELECT_RIGHT = 0x0F7FB4FF;
    private static final int SELECT_TOP_LINE = 0x8C9FC2FF;
    private static final int SELECT_BOTTOM_LINE = 0x4D9FC2FF;

    private static final long MARK_IN_MS = 200;
    private static final long SWEEP_MS = 450;
    private static final int SWEEP_WIDTH = 40;

    private final List<Row> rows = new ArrayList<>();

    @Nullable
    private String selectedId;
    /** 书签滑入和流光都从这一刻算起；换了选中项才重来，同一项重筛不重播 */
    private long flashStart = Long.MIN_VALUE / 2;

    private int scroll;
    private int contentHeight;

    @Nullable
    private Component emptyHint;

    private int x;
    private int y;
    private int width;
    private int height;

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    public void setEntries(@Nonnull List<? extends CodexEntry> metas) {
        rows.clear();

        String lastGroup = null;
        Row header = null;
        int offset = 0;
        for (CodexEntry meta : metas) {
            if (!meta.getGroupKey().equals(lastGroup)) {
                lastGroup = meta.getGroupKey();
                if (!rows.isEmpty()) {
                    offset += HEADER_GAP;
                }
                header = Row.header(meta, offset);
                rows.add(header);
                offset += HEADER_HEIGHT;
            }
            rows.add(Row.entry(meta, offset));
            offset += ROW_HEIGHT;
            header.count++;
        }
        contentHeight = offset;

        scroll = 0;
    }

    @Nullable
    public String getSelectedId() {
        return selectedId;
    }

    public void select(@Nullable String id) {
        setSelected(id);
        if (id == null) {
            return;
        }
        scrollTo(id);
    }

    private void setSelected(@Nullable String id) {
        if (id != null && !id.equals(selectedId)) {
            flashStart = UiTheme.now();
        }
        selectedId = id;
    }

    private void scrollTo(@Nonnull String id) {
        for (Row row : rows) {
            if (row.meta != null && row.meta.getKey().equals(id)) {
                if (row.top < scroll) {
                    scroll = row.top;
                } else if (row.top + ROW_HEIGHT > scroll + height) {
                    scroll = row.top + ROW_HEIGHT - height;
                }
                clampScroll();
                return;
            }
        }
    }

    private int viewWidth() {
        return width - SCROLLBAR_SPACE;
    }

    public void render(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
        int vw = viewWidth();
        boolean mouseInside = UiTheme.hit(mouseX, mouseY, x, y, vw, height);
        boolean pressed = mouseInside && Ui.isLeftPressed();
        long now = UiTheme.now();

        g.enableScissor(x, y, x + vw, y + height);
        for (Row row : rows) {
            int top = y - scroll + row.top;
            if (top >= y + height) {
                break;
            }
            if (top + row.height() <= y) {
                continue;
            }
            if (row.meta == null) {
                renderHeader(g, font, row, top, vw);
            } else {
                boolean hovered = mouseInside && UiTheme.hit(mouseX, mouseY, x, top, vw, ROW_HEIGHT);
                renderEntry(g, font, row.meta, top, vw, hovered, hovered && pressed, now);
            }
        }
        g.disableScissor();

        // 搜不到、或放入物品后一条都附不上时，空页面上要说清楚是哪种情况
        if (rows.isEmpty() && emptyHint != null) {
            int maxW = Math.max(1, vw - EMPTY_PAD * 2);
            int lineY = y + EMPTY_Y;
            for (FormattedCharSequence line : font.splitSequences(emptyHint, maxW)) {
                int lx = x + (vw - font.width(line)) / 2;
                g.drawString(font, line, lx, lineY, UiTheme.OFF, false);
                lineY += font.lineHeight;
            }
        }

        int sbx = x + width - SCROLLBAR_WIDTH;
        boolean hot = UiTheme.hit(mouseX, mouseY, sbx, y, SCROLLBAR_WIDTH, height);
        UiTheme.scrollbar(g, sbx, y, height, contentHeight, scroll, hot);
    }

    public void setEmptyHint(@Nullable Component hint) {
        this.emptyHint = hint;
    }

    /** 分类头：横放的书脊，两头各两道棱，左边这一类的小星座，右边本类条数 */
    private void renderHeader(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Row row, int top, int vw) {
        CodexEntry first = row.group;
        int cat = 0xFF000000 | first.getGroupColor();
        TogglePage.spine(g, x, top, vw, HEADER_HEIGHT, cat);
        UiTheme.constellation(g, constellationOf(first), x + SPINE_CONST_X, top + 1, cat);

        int textY = top + (HEADER_HEIGHT - 8) / 2;
        int nameRight = x + vw - SPINE_PAD_RIGHT;
        // 放入物品后的四组，组名的翻译里已经带了条数，右边就不再重复
        if (ItemFilter.constellationOf(first) < 0) {
            String count = String.valueOf(row.count);
            int cw = font.width(count);
            g.drawString(font, count, nameRight - cw, textY, UiTheme.mix(0xFF0B1030, cat, 0.62f), false);
            nameRight -= cw + 6;
        }
        int maxW = nameRight - (x + SPINE_TEXT_X);
        UiTheme.text(g, font, keepCount(font, first.getGroupName().getString(), maxW), x + SPINE_TEXT_X, textY, cat);
    }

    /** 截组名时保住末尾括号里的条数，只截前面的字 */
    @Nonnull
    private static String keepCount(@Nonnull Font font, @Nonnull String name, int maxW) {
        if (font.width(name) <= maxW) {
            return name;
        }
        int cut = Math.max(name.lastIndexOf('('), name.lastIndexOf('（'));
        if (cut <= 0) {
            return UiTheme.trim(font, name, maxW);
        }
        String head = name.substring(0, cut);
        String tail = name.substring(cut);
        String sep = head.endsWith(" ") ? " " : "";
        String cutHead = UiTheme.trim(font, head.replaceAll("\\s+$", ""), maxW - font.width(sep + tail));
        return cutHead.isEmpty() ? UiTheme.trim(font, tail, maxW) : cutHead + sep + tail;
    }

    private static int constellationOf(@Nonnull CodexEntry entry) {
        int fit = ItemFilter.constellationOf(entry);
        if (fit >= 0) {
            return fit;
        }
        if (entry instanceof EnchantmentMeta) {
            return UiTheme.constIndex(((EnchantmentMeta) entry).getTheme());
        }
        return UiTheme.CONST_MOD;
    }

    private void renderEntry(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull CodexEntry meta,
                             int top, int vw, boolean hovered, boolean pressed, long now) {
        boolean selected = meta.getKey().equals(selectedId);
        int right = x + vw;

        if (selected) {
            hGradient(g, x, top, right, top + ROW_HEIGHT, SELECT_LEFT, SELECT_RIGHT);
            g.fill(x, top, right, top + 1, SELECT_TOP_LINE);
            g.fill(x, top + ROW_HEIGHT - 1, right, top + ROW_HEIGHT, SELECT_BOTTOM_LINE);
        } else if (hovered) {
            g.fill(x, top, right, top + ROW_HEIGHT, pressed ? PRESS_FILL : HOVER_FILL);
        }

        // 书签：选中时从页边滑进来，悬停时只探出半截（另一半被视口左边裁掉）
        if (selected) {
            long e = now - flashStart;
            int dx = e < MARK_IN_MS ? -Math.round(6 * (1f - UiTheme.steps(e, MARK_IN_MS, 5))) : 0;
            UiTheme.sprite(g, "rowmark", x + dx, top + MARK_Y, 6, 7);
        } else if (hovered) {
            UiTheme.spriteAlpha(g, "rowmark", x - 3, top + MARK_Y, 6, 7, 0.55f);
        }

        int nameColor = selected || hovered ? 0xFFFFFFFF : meta.isRowDimmed() ? UiTheme.TEXT_DIM : UiTheme.TEXT;
        int textX = x + PAD_LEFT;
        UiTheme.trimmed(g, font, meta.getDisplayName(), textX, top + NAME_Y,
                vw - PAD_LEFT - PAD_RIGHT, nameColor, true);

        // 行尾：放入物品后是这件东西能走哪条路，平时是冲突数；右边对齐到同一条竖线上
        int subY = top + SUB_Y;
        int plainColor = selected ? UiTheme.TEXT_DIM : UiTheme.OFF;
        String note = null;
        int noteColor = plainColor;
        Component rowNote = meta.getRowNote();
        if (rowNote != null) {
            note = rowNote.getString();
            noteColor = meta.getRowNoteColor();
        } else if (meta.getConflictCount() > 0) {
            note = Component.translatable("carianstyle.codex.list.conflicts", meta.getConflictCount()).getString();
        }
        int subRight = right - PAD_RIGHT - SUB_RIGHT_EXTRA;
        if (note != null) {
            note = UiTheme.trim(font, note, (vw - PAD_LEFT - PAD_RIGHT) / 2);
            int nx = right - PAD_RIGHT - font.width(note);
            g.drawString(font, note, nx, subY, noteColor, false);
            subRight = Math.min(subRight, nx - 4);
        }

        CodexEntry base = ItemFilter.unwrap(meta);
        int phase = UiTheme.phaseOf(base);
        String rarity = meta.getRarityName().getString();
        String level = Component.translatable("carianstyle.codex.list.max_level",
                UiTheme.roman(meta.getMaxLevel())).getString();
        int avail = subRight - textX;
        int space = font.width(" ");
        if (font.width(rarity) + space + font.width(level) > avail) {
            // 挤不下先截稀有度：行尾的月相已经标了稀有度，最高等级只有这里写
            rarity = UiTheme.trim(font, rarity, avail - space - Math.min(font.width(level), avail));
            level = UiTheme.trim(font, level, avail - (rarity.isEmpty() ? 0 : font.width(rarity) + space));
        }
        g.drawString(font, rarity, textX, subY, UiTheme.phaseColor(phase), false);
        int levelX = rarity.isEmpty() ? textX : textX + font.width(rarity) + space;
        if (!level.isEmpty()) {
            g.drawString(font, level, levelX, subY, plainColor, false);
        }

        // 满月档外圈的月晕：平时淡淡一层，选中后慢慢呼吸
        float halo = phase == 4 ? (selected ? UiTheme.breathe(now, 3600f, 6, 0.4f) : 0.55f) : 0f;
        UiTheme.phase(g, phase, right - PHASE_RIGHT - 9, top + PHASE_Y, selected || hovered ? 1f : 0.82f, halo);

        if (selected) {
            long e = now - flashStart;
            if (e >= 0 && e < SWEEP_MS) {
                int sx = x - SWEEP_WIDTH + Math.round((vw + SWEEP_WIDTH) * UiTheme.steps(e, SWEEP_MS, 18));
                int half = SWEEP_WIDTH / 2;
                g.enableScissor(x, top, right, top + ROW_HEIGHT);
                hGradient(g, sx, top, sx + half, top + ROW_HEIGHT, 0x00FFFFFF, 0x4DFFFFFF);
                hGradient(g, sx + half, top, sx + SWEEP_WIDTH, top + ROW_HEIGHT, 0x4DFFFFFF, 0x00FFFFFF);
                g.disableScissor();
            }
        }
    }

    private static void hGradient(@Nonnull GuiGraphics g, int x0, int y0, int x1, int y1, int left, int right) {
        g.fillGradientHorizontal(x0, y0, x1, y1, left, right);
    }

    @Nullable
    public String mouseClicked(double mouseX, double mouseY) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return null;
        }

        int sbx = x + width - SCROLLBAR_WIDTH;
        if (mouseX >= sbx) {
            int max = contentHeight - height;
            if (max > 0) {
                int th = UiTheme.thumbHeight(height, contentHeight);
                double t = (mouseY - y) - th / 2.0;
                scroll = (int) Math.round(t / Math.max(1, height - th) * max);
                clampScroll();
            }
            return null;
        }
        if (mouseX >= x + viewWidth()) {
            return null;
        }

        for (Row row : rows) {
            int top = y - scroll + row.top;
            if (row.meta != null && UiTheme.hit(mouseX, mouseY, x, top, viewWidth(), ROW_HEIGHT)) {
                setSelected(row.meta.getKey());
                return selectedId;
            }
        }
        return null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        scroll -= (int) (delta * ROW_HEIGHT);
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int max = Math.max(0, contentHeight - height);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    public int entryCount() {
        int count = 0;
        for (Row row : rows) {
            if (row.meta != null) {
                count++;
            }
        }
        return count;
    }

    /** 一行：meta 为 null 是分类头，group 存该组第一条（两页的分组来源不同，但都能从条目上问到组名和颜色） */
    private static final class Row {

        @Nullable
        private final CodexEntry meta;
        @Nullable
        private final CodexEntry group;
        private final int top;
        private int count;

        private Row(@Nullable CodexEntry meta, @Nullable CodexEntry group, int top) {
            this.meta = meta;
            this.group = group;
            this.top = top;
        }

        static Row header(@Nonnull CodexEntry first, int top) {
            return new Row(null, first, top);
        }

        static Row entry(@Nonnull CodexEntry meta, int top) {
            return new Row(meta, null, top);
        }

        int height() {
            return meta == null ? HEADER_HEIGHT : ROW_HEIGHT;
        }
    }
}
