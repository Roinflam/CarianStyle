package pers.roinflam.carianstyle.client.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.mc112.gui.Font;
import pers.roinflam.carianstyle.mc112.gui.GuiGraphics;
import pers.roinflam.carianstyle.mc112.gui.Component;

import javax.annotation.Nonnull;

/**
 * 标题栏右侧的主题按钮和它弹出的列表：只管位置、开合和点击，画法留给各套界面自己。
 */
@SideOnly(Side.CLIENT)
public final class ThemeMenu {
    public static final int NONE = -2;
    public static final int CONSUMED = -1;
    /** 按钮里名字右边的小三角 */
    public static final int ARROW_W = 5;
    /** 列表里名字左边给「当前项」标记留的宽度 */
    public static final int MARK_W = 9;

    private final int[] button = new int[4];
    private final int[] panel = new int[4];
    private int padX;
    private int rowH;
    private int inset;
    private boolean open;

    /**
     * @param right   按钮右沿
     * @param height  按钮高
     * @param padX    按钮和列表左右的留白
     * @param rowH    列表每行高
     * @param inset   列表上下的留白
     */
    public void layout(@Nonnull Font font, int right, int y, int height, int padX, int rowH, int inset,
                       int screenW, int screenH) {
        this.padX = padX;
        this.rowH = rowH;
        this.inset = inset;
        int bw = padX + font.width(CodexTheme.current().title()) + 4 + ARROW_W + padX;
        set(button, right - bw, y, bw, height);

        int widest = 0;
        for (CodexTheme theme : CodexTheme.values()) {
            widest = Math.max(widest, font.width(theme.title()));
        }
        int pw = Math.max(bw, padX + MARK_W + widest + padX);
        int ph = inset * 2 + rowH * CodexTheme.values().length;
        int px = Math.max(2, Math.min(right, screenW - 2) - pw);
        int py = y + height + 2;
        if (py + ph > screenH - 2) {
            py = Math.max(2, y - 2 - ph);
        }
        set(panel, px, py, pw, ph);
    }

    public int[] button() {
        return button;
    }

    public int[] panel() {
        return panel;
    }

    public int padX() {
        return padX;
    }

    public int rowH() {
        return rowH;
    }

    public int rowY(int index) {
        return panel[1] + inset + index * rowH;
    }

    /** 名字的起点（标记在它左边 MARK_W 以内） */
    public int textX() {
        return panel[0] + padX + MARK_W;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public boolean overButton(double mx, double my) {
        return hit(button, mx, my);
    }

    public boolean overPanel(double mx, double my) {
        return open && hit(panel, mx, my);
    }

    public int rowAt(double mx, double my) {
        if (!open || mx < panel[0] || mx >= panel[0] + panel[2]) {
            return -1;
        }
        int dy = (int) Math.floor(my) - (panel[1] + inset);
        if (dy < 0) {
            return -1;
        }
        int index = dy / rowH;
        return index < CodexTheme.values().length ? index : -1;
    }

    /**
     * 列表开着时独占点击：点中一项返回它的序号，点别处只收起。
     * 列表没开时只认按钮，别处返回 {@link #NONE} 交给界面照常处理。
     */
    public int click(double mx, double my) {
        if (open) {
            int row = rowAt(mx, my);
            open = false;
            return row >= 0 ? row : CONSUMED;
        }
        if (overButton(mx, my)) {
            open = true;
            return CONSUMED;
        }
        return NONE;
    }

    @Nonnull
    public static Component name(int index) {
        return CodexTheme.values()[index].title();
    }

    /** 5×3 的小三角，x / y 是左上角 */
    public static void arrow(@Nonnull GuiGraphics g, int x, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? 2 - i : i;
            g.fill(x + i, y + row, x + ARROW_W - i, y + row + 1, color);
        }
    }

    /** 5×5 的菱形，x / y 是左上角 */
    public static void diamond(@Nonnull GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 3, y + 5, color);
        g.fill(x + 1, y + 1, x + 4, y + 4, color);
        g.fill(x, y + 2, x + 5, y + 3, color);
    }

    private static boolean hit(int[] r, double mx, double my) {
        return mx >= r[0] && my >= r[1] && mx < r[0] + r[2] && my < r[1] + r[3];
    }

    private static void set(int[] r, int x, int y, int w, int h) {
        r[0] = x;
        r[1] = y;
        r[2] = w;
        r[3] = h;
    }
}
