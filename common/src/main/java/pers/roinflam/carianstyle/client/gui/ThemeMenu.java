package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

/**
 * 标题栏右侧的主题按钮和下拉列表：只管位置和命中，画法由各主题的界面自己来。
 */
public final class ThemeMenu {
    public static final int ARROW_W = 5;
    public static final int ARROW_GAP = 4;
    public static final int PAD = 2;

    private boolean open;
    private int x;
    private int y;
    private int w;
    private int h;
    private int listX;
    private int listY;
    private int listW;
    private int rowH;

    /** 按最长的主题名定宽，切换主题时按钮不跟着变宽 */
    public static int labelWidth(Font font) {
        int max = 0;
        for (CodexTheme theme : CodexTheme.values()) {
            max = Math.max(max, font.width(theme.displayName()));
        }
        return max;
    }

    /** 列表默认贴在按钮下面、右边对齐；下面放不下就翻到按钮上面 */
    public void place(int x, int y, int w, int h, int rowH, int listW, int screenW, int screenH) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.rowH = rowH;
        this.listW = Math.max(w, listW);
        int listH = listH();
        listX = Math.max(0, Math.min(x + w - this.listW, screenW - this.listW));
        listY = y + h + 1;
        if (listY + listH > screenH && y - 1 - listH >= 0) {
            listY = y - 1 - listH;
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean overButton(double mouseX, double mouseY) {
        return w > 0 && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public boolean overList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH();
    }

    @Nullable
    public CodexTheme rowAt(double mouseX, double mouseY) {
        if (!open || !overList(mouseX, mouseY)) {
            return null;
        }
        int i = (int) Math.floor((mouseY - listY - PAD) / rowH);
        CodexTheme[] all = CodexTheme.values();
        return i >= 0 && i < all.length ? all[i] : null;
    }

    public int rowY(int index) {
        return listY + PAD + index * rowH;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public int listX() {
        return listX;
    }

    public int listY() {
        return listY;
    }

    public int listW() {
        return listW;
    }

    public int listH() {
        return PAD * 2 + rowH * CodexTheme.values().length;
    }

    public int rowH() {
        return rowH;
    }

    /** 5×3 的小三角；列表开着时朝上 */
    public static void chevron(GuiGraphics g, int x, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? y + 2 - i : y + i;
            g.fill(x + i, row, x + ARROW_W - i, row + 1, color);
        }
    }
}
