package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.network.chat.Component;
import pers.roinflam.carianstyle.codex.CodexEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 百科左栏：可滚动的附魔列表，按分组插入标题行。
 *
 * <h3>行结构</h3>
 * <p>
 * 列表被拍平成 {@link Row} 序列，标题行与条目行混在同一个数组里，
 * 这样滚动、命中测试、绘制都只需要一次线性遍历，不用维护「第 N 组的第 M 项」这种嵌套索引。
 * </p>
 *
 * <h3>性能</h3>
 * <p>
 * {@link #render} 只遍历<b>可视区内</b>的行（先按滚动量算出起止下标），
 * 113 个附魔时每帧实际绘制约 20 行。筛选结果在
 * {@link #setEntries} 时算好并缓存，不在渲染路径里做字符串匹配。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class CodexList {

    /** 条目行高 */
    private static final int ROW_HEIGHT = 22;

    /** 分组标题行高 */
    private static final int HEADER_HEIGHT = 16;

    /** 拍平后的行序列 */
    private final List<Row> rows = new ArrayList<>();

    /** 当前选中的附魔 id，null 表示未选中 */
    private String selectedId;

    /** 滚动偏移（像素） */
    private int scroll;

    /** 内容总高度，随 {@link #setEntries} 重算 */
    private int contentHeight;

    /** 列表为空时的提示，见 {@link #setEmptyHint} */
    @Nullable
    private Component emptyHint;

    // 布局区域，由 {@link CodexScreen} 在 init 时写入
    private int x;
    private int y;
    private int width;
    private int height;

    /**
     * 设置布局区域。
     *
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    /**
     * 用筛选结果重建列表。
     *
     * @param metas 已筛选并排好序的附魔元数据
     */
    public void setEntries(@Nonnull List<? extends CodexEntry> metas) {
        rows.clear();

        String lastGroup = null;
        for (CodexEntry meta : metas) {
            if (!meta.getGroupKey().equals(lastGroup)) {
                lastGroup = meta.getGroupKey();
                rows.add(Row.header(meta));
            }
            rows.add(Row.entry(meta));
        }

        contentHeight = 0;
        for (Row row : rows) {
            contentHeight += row.height();
        }

        scroll = 0;
    }

    /**
     * @return 当前选中的附魔 id，可能为 null
     */
    @Nullable
    public String getSelectedId() {
        return selectedId;
    }

    /**
     * 设置选中项，并把它滚动到可视区内。
     *
     * @param id 附魔 id，可为 null（取消选中）
     */
    public void select(@Nullable String id) {
        this.selectedId = id;
        if (id == null) {
            return;
        }
        scrollTo(id);
    }

    /**
     * 把指定条目滚动进可视区（已在可视区内时不动）。
     *
     * @param id 附魔 id
     */
    private void scrollTo(@Nonnull String id) {
        int offset = 0;
        for (Row row : rows) {
            if (row.meta != null && row.meta.getKey().equals(id)) {
                if (offset < scroll) {
                    scroll = offset;
                } else if (offset + ROW_HEIGHT > scroll + height) {
                    scroll = offset + ROW_HEIGHT - height;
                }
                clampScroll();
                return;
            }
            offset += row.height();
        }
    }

    /**
     * 绘制列表。
     *
     * @param g      绘制上下文
     * @param font   字体
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    public void render(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
        UiTheme.panel(g, x, y, width, height, UiTheme.PANEL);
        UiTheme.corners(g, x, y, width, height, 8, UiTheme.ACCENT);

        boolean mouseInside = UiTheme.hit(mouseX, mouseY, x, y, width, height);

        // 裁剪到面板内部，避免行绘制溢出到边框外
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        int rowY = y - scroll;
        for (Row row : rows) {
            int rowHeight = row.height();

            // 只绘制与可视区相交的行
            if (rowY + rowHeight >= y && rowY <= y + height) {
                if (row.meta == null) {
                    renderHeader(g, font, row, rowY);
                } else {
                    boolean hovered = mouseInside
                            && UiTheme.hit(mouseX, mouseY, x + 1, rowY, width - 5, rowHeight);
                    renderEntry(g, font, row, rowY, hovered);
                }
            }

            rowY += rowHeight;
            if (rowY > y + height) {
                break;
            }
        }

        g.disableScissor();

        // 空列表给一句话。以前搜不到东西时就是一整块空面板，
        // 分不清是没结果、还没加载完，还是界面坏了；放入物品筛选之后空列表更常见，必须说清楚
        if (rows.isEmpty() && emptyHint != null) {
            UiTheme.wrapped(g, font, emptyHint, x + 10, y + 12, width - 20, UiTheme.TEXT_DIM);
        }

        UiTheme.scrollbar(g, x + width - 4, y + 1, height - 2, contentHeight, scroll);
    }

    /**
     * 设置列表为空时显示的提示。
     *
     * @param hint 提示；null 表示空列表时什么都不画
     */
    public void setEmptyHint(@Nullable Component hint) {
        this.emptyHint = hint;
    }

    /**
     * 绘制分组标题行。
     *
     * @param g    绘制上下文
     * @param font 字体
     * @param row  行
     * @param rowY 行的屏幕纵坐标
     */
    private void renderHeader(@Nonnull GuiGraphics g, @Nonnull Font font,
                              @Nonnull Row row, int rowY) {
        int color = row.group.getGroupColor();
        g.fill(x + 1, rowY, x + width - 4, rowY + HEADER_HEIGHT, UiTheme.PANEL_ALT);
        g.fill(x + 1, rowY, x + 3, rowY + HEADER_HEIGHT, color);
        g.drawString(font, row.group.getGroupName(), x + 8, rowY + 4, color, false);
    }

    /**
     * 绘制附魔条目行。
     *
     * @param g       绘制上下文
     * @param font    字体
     * @param row     行
     * @param rowY    行的屏幕纵坐标
     * @param hovered 是否悬停
     */
    private void renderEntry(@Nonnull GuiGraphics g, @Nonnull Font font,
                             @Nonnull Row row, int rowY, boolean hovered) {
        CodexEntry meta = row.meta;
        boolean selected = meta.getKey().equals(selectedId);

        if (selected) {
            g.fill(x + 1, rowY, x + width - 4, rowY + ROW_HEIGHT, UiTheme.ACCENT_FILL);
            // 选中行的竖条以 2.4 秒为周期轻微呼吸：在一屏 20 行里，
            // 这是唯一还在动的元素，视线离开又回来时能立刻找回选中项
            g.fill(x + 1, rowY, x + 3, rowY + ROW_HEIGHT,
                    UiTheme.withAlpha(UiTheme.ACCENT, UiTheme.breathe(UiTheme.time(), 2.4f, 0.55f)));
        } else if (hovered) {
            // 渐变而非纯色：像一道光从左扫过这一行，不会把文字压暗
            UiTheme.gradientRow(g, x + 1, rowY, width - 5, ROW_HEIGHT, UiTheme.PANEL_HOVER);
        }

        int nameColor = selected ? UiTheme.ACCENT : (meta.isRowDimmed() ? UiTheme.TEXT_DIM : UiTheme.TEXT);
        UiTheme.trimmed(g, font, meta.getDisplayName(), x + 9, rowY + 3, width - 30, nameColor);

        // 副行分成左右两栏。
        //
        // 原来是「稀有 · 最高 III 级 · 冲突 2」一整串挤在左边，三段信息同色同字号，
        // 分隔点两侧各留两个空格，结果既没重点也不整齐——每一行的长度还都不一样，
        // 竖着扫下来是锯齿状的。
        //
        // 现在：稀有度用它自己的颜色（和右侧圆点同色，两处互相印证），
        // 等级紧跟其后；冲突数右对齐，于是所有行的冲突数在同一条竖线上，
        // 有没有冲突、多少个，扫一眼就看得出。
        int subY = rowY + 13;
        String rarity = meta.getRarityName().getString();
        g.drawString(font, rarity, x + 9, subY, meta.getRarityColor(), false);

        String level = Component.translatable("carianstyle.codex.list.max_level",
                UiTheme.roman(meta.getMaxLevel())).getString();
        g.drawString(font, level, x + 9 + font.width(rarity) + 6, subY, UiTheme.OFF, false);

        // 条目自带说明时（放入物品筛选）占用冲突数的位置：
        // 那时候要回答的是「对这件东西怎么样」，全局冲突数是另一个问题
        Component note = meta.getRowNote();
        int conflicts = meta.getConflictCount();
        if (note != null) {
            String text = note.getString();
            g.drawString(font, text, x + width - 20 - font.width(text), subY, meta.getRowNoteColor(), false);
        } else if (conflicts > 0) {
            String text = Component.translatable(
                    "carianstyle.codex.list.conflicts", conflicts).getString();
            g.drawString(font, text, x + width - 20 - font.width(text), subY, UiTheme.OFF, false);
        }

        // 右侧稀有度圆点：颜色即稀有度，不占宽度，也不需要玩家解读缩写
        UiTheme.dot(g, x + width - 11, rowY + ROW_HEIGHT / 2, meta.getRarityColor());
    }



    /**
     * 处理点击。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 被点中的附魔 id；未命中条目时返回 null
     */
    @Nullable
    public String mouseClicked(double mouseX, double mouseY) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return null;
        }

        int rowY = y - scroll;
        for (Row row : rows) {
            int rowHeight = row.height();
            if (row.meta != null && UiTheme.hit(mouseX, mouseY, x + 1, rowY, width - 5, rowHeight)) {
                selectedId = row.meta.getKey();
                return selectedId;
            }
            rowY += rowHeight;
        }
        return null;
    }

    /**
     * 处理滚轮。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param delta  滚轮增量（向上为正）
     * @return 是否消费了本次事件
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        scroll -= (int) (delta * ROW_HEIGHT);
        clampScroll();
        return true;
    }

    /**
     * 把滚动量收拢到合法范围。
     */
    private void clampScroll() {
        int max = Math.max(0, contentHeight - height + 2);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    /**
     * @return 当前列表中的条目数量（不含分组标题）
     */
    public int entryCount() {
        int count = 0;
        for (Row row : rows) {
            if (row.meta != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 一行：要么是分组标题（{@code meta == null}），要么是附魔条目。
     */
    private static final class Row {

        /** 附魔条目；为 null 表示这是分组标题行 */
        private final CodexEntry meta;

        /**
         * 分组标题行所代表的分组，取自该组的第一个条目。
         * <p>直接存条目而不是「分组对象」，是因为两个标签页的分组来源不同
         * （本模组用题材主题、外部附魔用所属模组），但它们都能通过
         * {@link CodexEntry} 回答「组名是什么、什么颜色」。</p>
         */
        private final CodexEntry group;

        private Row(CodexEntry meta, CodexEntry group) {
            this.meta = meta;
            this.group = group;
        }

        /**
         * 构造分组标题行。
         *
         * @param first 该组的第一个条目
         * @return 行
         */
        static Row header(CodexEntry first) {
            return new Row(null, first);
        }

        /**
         * 构造附魔条目行。
         *
         * @param meta 条目
         * @return 行
         */
        static Row entry(CodexEntry meta) {
            return new Row(meta, null);
        }

        /**
         * @return 本行高度
         */
        int height() {
            return meta == null ? HEADER_HEIGHT : ROW_HEIGHT;
        }
    }
}
