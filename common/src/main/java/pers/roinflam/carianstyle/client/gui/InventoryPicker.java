package pers.roinflam.carianstyle.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.platform.Services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 百科的背包选择器：从「放入物品」槽下面弹出一块书页，列出玩家背包，点一件就放进槽里。
 *
 * <p>
 * 不做成真正的物品栏：能拖物品的界面得是容器界面，要服务端开菜单、要网络同步，
 * 为了「看一眼能附什么」不值，还平白多出刷物品的风险。这里只读客户端背包、只拿副本。
 * </p>
 *
 * <p>
 * 排布和原版物品栏同一个上下顺序：第一行头、胸、腿、脚四件盔甲，最右是副手；中间三行背包；最下一行快捷栏。
 * 手持那一格底边换成金线。打开时对每一格算一次「还能附（或还能升级）几个附魔」，为 0 的压暗——
 * 压暗的仍然能点，玩家也许就是想确认「为什么这件什么都附不上」。
 * </p>
 */
final class InventoryPicker {

    /** {@link #mouseClicked} 的返回值：点在选择器里但没选中东西（或选择器没开） */
    static final int CONSUMED = -1;

    /** {@link #mouseClicked} 的返回值：点了「清除」 */
    static final int CLEAR = -2;

    /** {@link #mouseClicked} 的返回值：点在选择器外面，选择器已关闭 */
    static final int DISMISSED = -3;

    private static final int CELL = 18;
    private static final int COLS = 9;
    /** 左右内边距 8：页框 2 像素 + 四角花饰 8 像素宽，格子从花饰里侧开始，两边不压 */
    private static final int PAD = 8;
    private static final int TITLE_Y = 6;
    private static final int TITLE_H = 14;
    private static final int GRID_Y = TITLE_Y + TITLE_H + 5;
    private static final int GAP = 4;
    private static final int WIDTH = PAD * 2 + CELL * COLS;
    private static final int HEIGHT = GRID_Y + CELL + GAP + CELL * 3 + GAP + CELL + PAD;

    private static final int OPEN_MS = 180;
    private static final int OPEN_STEPS = 4;
    private static final int OPEN_DROP = 4;
    private static final int HOVER_MS = 150;

    private static final int CELL_FILL = 0xB3060A1A;
    private static final int CELL_HOVER_FLASH = 0xFF34488E;
    private static final int CURRENT_TOP = 0x577FB4FF;
    private static final int CURRENT_BOTTOM = 0x0F7FB4FF;
    private static final int DIM = 0xA8060A1A;

    /**
     * 整个选择器在深度上抬高的量。
     * <p>
     * 详情面板里也画着物品图标（「适用物品」那一排），物品模型画在 z≈150 附近，
     * 而普通的 {@code fill} 画在 z=0——不抬高的话，那些图标会从选择器的底里透出来。
     * </p>
     */
    private static final float Z_LIFT = 200f;

    /** 背包格子总数：36 个主格 + 4 件盔甲 + 副手，下标与 {@link Inventory#getItem} 一致 */
    private static final int SLOT_COUNT = 41;

    /** 第一行的格子：盔甲头到脚（36~39 是脚到头，倒过来），-1 为空位，最右是副手 */
    private static final int[] TOP_ROW = {39, 38, 37, 36, -1, -1, -1, -1, 40};

    private static final Component TITLE = Component.translatable("carianstyle.codex.filter.picker_title");
    private static final Component CLEAR_LABEL = Component.translatable("carianstyle.codex.filter.clear");

    private boolean open;
    private long openedAt;

    private int x;
    private int y;

    private int hoverSlot = -1;
    private long hoverSince;

    /** 「清除」按钮矩形（x, y, w, h）；宽为 0 表示没画 */
    private final int[] clearRect = new int[4];

    /** 打开时每一格的物品对象，用于发现格子里换了东西、需要重算 */
    private final ItemStack[] seen = new ItemStack[SLOT_COUNT];

    /** 每一格还能附的附魔数；-1 为空格 */
    private final int[] usable = new int[SLOT_COUNT];

    boolean isOpen() {
        return open;
    }

    /**
     * 打开选择器。第一列格子对齐在锚点正下方（和「放入物品」槽同一列），靠窗口边时往里挪。
     *
     * @param anchorX 锚点左（「放入物品」槽的左边缘）
     * @param anchorY 锚点上（槽的下边缘再往下留点空）
     */
    void open(int anchorX, int anchorY, int screenWidth, int screenHeight, @Nonnull Inventory inventory) {
        // 小窗口里宁可盖住一点顶栏也不能出屏
        x = Math.max(4, Math.min(anchorX - PAD, screenWidth - WIDTH - 4));
        y = Math.max(4, Math.min(anchorY, screenHeight - HEIGHT - 4));

        Arrays.fill(seen, null);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            refreshSlot(inventory, slot);
        }
        open = true;
        openedAt = UiTheme.now();
        hoverSlot = -1;
    }

    void close() {
        open = false;
        // 选择器关了，这些副本没有任何用处
        Arrays.fill(seen, null);
    }

    boolean contains(double mouseX, double mouseY) {
        return open && UiTheme.hit(mouseX, mouseY, x, y, WIDTH, HEIGHT);
    }

    private void refreshSlot(@Nonnull Inventory inventory, int slot) {
        ItemStack stack = slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
        seen[slot] = stack;
        usable[slot] = stack.isEmpty() ? -1 : ItemFilter.countUsable(stack);
    }

    /**
     * @param currentSlot 当前放入物品的来源格子；-1 表示没有
     */
    void render(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Inventory inventory,
                int mouseX, int mouseY, int currentSlot) {
        if (!open) {
            return;
        }
        long t = UiTheme.now();
        float p = UiTheme.steps(t - openedAt, OPEN_MS, OPEN_STEPS);
        float alpha = 0.2f + 0.8f * p;
        int dy = -Math.round(OPEN_DROP * (1f - p));

        // 点击和悬停一律按落定后的位置算，弹出那 0.18 秒里只是画的位置在动
        g.pose().pushPose();
        g.pose().translate(0f, (float) dy, Z_LIFT);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        // 右下 2 像素的落影，和字的投影同一个方向；只画露在外面的 L 形，免得从玻璃底里透出来
        int shadow = 0x99050818;
        g.fill(x + WIDTH, y + 2, x + WIDTH + 2, y + HEIGHT + 2, shadow);
        g.fill(x + 2, y + HEIGHT, x + WIDTH, y + HEIGHT + 2, shadow);
        // 玻璃本身是 .9 的透明度，底下垫一层，免得书页上的字隐约透上来
        g.fill(x, y, x + WIDTH, y + HEIGHT, 0xCC060A1A);
        UiTheme.page(g, x, y, WIDTH, HEIGHT, true);

        int gx = x + PAD;
        int gridRight = gx + CELL * COLS;
        int ty = y + TITLE_Y;
        int titleRight = gridRight;
        clearRect[2] = 0;
        if (currentSlot >= 0) {
            int bw = UiTheme.buttonWidth(font, CLEAR_LABEL);
            setClearRect(gridRight - bw, ty, bw, TITLE_H);
            UiTheme.button(g, font, CLEAR_LABEL, clearRect[0], clearRect[1], clearRect[2], clearRect[3],
                    true, mouseX, mouseY);
            titleRight = clearRect[0] - 5;
        }
        renderTitle(g, font, gx, ty, titleRight);

        int hovered = -1;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int cx = cellX(slot);
            int cy = cellY(slot);
            ItemStack stack = slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
            if (stack != seen[slot]) {
                // 开着的时候背包变了（捡到东西、被打掉耐久）：只重算这一格
                refreshSlot(inventory, slot);
            }
            boolean over = !stack.isEmpty() && UiTheme.hit(mouseX, mouseY, cx, cy, CELL, CELL);
            if (over) {
                hovered = slot;
                if (slot != hoverSlot) {
                    hoverSlot = slot;
                    hoverSince = t;
                }
            }
            renderCell(g, cx, cy, over, slot == currentSlot, slot == inventory.selected, t);
        }
        if (hovered < 0) {
            hoverSlot = -1;
        }
        UiTheme.pageCorners(g, x, y, WIDTH, HEIGHT);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 物品模型不吃透明度，前两档先不画，免得一出来就是实心的图标浮在半透明的底上
        if (p >= 0.5f) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                ItemStack stack = seen[slot];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                int cx = cellX(slot);
                int cy = cellY(slot);
                g.renderItem(stack, cx + 1, cy + 1);
                g.renderItemDecorations(font, stack, cx + 1, cy + 1);
                if (usable[slot] == 0 && slot != currentSlot) {
                    // 压暗层要盖住物品和数量字：两者分别画在 +150 与 +200 的深度上
                    g.pose().pushPose();
                    g.pose().translate(0f, 0f, 250f);
                    g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, DIM);
                    g.pose().popPose();
                }
            }
        }
        g.pose().popPose();

        // 提示框不跟着弹出的位移走，但仍要抬高：它得压住选择器自己的物品图标和压暗层
        if (hovered >= 0) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, Z_LIFT);
            renderTooltip(g, font, inventory.getItem(hovered), usable[hovered], mouseX, mouseY);
            g.pose().popPose();
        }
    }

    /** 和正文节头同一个画法：分节星 + 银字 + 一道线 + 收尾小菱；挤的时候先省线和小菱，再截字 */
    private static void renderTitle(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int top, int right) {
        int textY = top + 3;
        UiTheme.sprite(g, "orn", left, textY, 7, 7);
        int tx = left + 12;
        String title = UiTheme.trim(font, TITLE.getString(), right - tx);
        UiTheme.text(g, font, title, tx, textY, UiTheme.SILVER);
        int endX = right - 5;
        int lineLeft = tx + font.width(title) + 5;
        if (lineLeft + 5 > endX) {
            return;
        }
        if (lineLeft < endX - 5) {
            g.fill(lineLeft, top + 6, endX - 5, top + 7, UiTheme.LINE);
        }
        UiTheme.sprite(g, "orn_end", endX, top + 4, 5, 5);
    }

    private void renderCell(@Nonnull GuiGraphics g, int cx, int cy, boolean hovered, boolean current,
                            boolean held, long t) {
        int fill = CELL_FILL;
        if (hovered) {
            // 刚移上来的 0.15 秒底色亮一档再落回悬停色
            float flash = 1f - UiTheme.steps(t - hoverSince, HOVER_MS, 3);
            fill = UiTheme.mix(UiTheme.PANEL_HOVER, CELL_HOVER_FLASH, flash);
        }
        g.fill(cx, cy, cx + CELL, cy + CELL, fill);
        if (current) {
            g.fillGradient(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, CURRENT_TOP, CURRENT_BOTTOM);
        }
        int edge = current ? (hovered ? UiTheme.EDGE_HI : UiTheme.ACCENT)
                : hovered ? UiTheme.SILVER : UiTheme.DOTS;
        UiTheme.border(g, cx, cy, CELL, CELL, edge);
        if (held) {
            // 手持那一格：底边换成金线，最常见的用法就是「看看我手上这把」；画在描边上，不和图标抢像素
            g.fill(cx + 2, cy + CELL - 1, cx + CELL - 2, cy + CELL, UiTheme.GOLD);
        }
    }

    private void setClearRect(int rx, int ry, int rw, int rh) {
        clearRect[0] = rx;
        clearRect[1] = ry;
        clearRect[2] = rw;
        clearRect[3] = rh;
    }

    private int column(int slot) {
        if (slot >= 36) {
            return slot == 40 ? COLS - 1 : 39 - slot;
        }
        return slot % COLS;
    }

    private int cellX(int slot) {
        return x + PAD + column(slot) * CELL;
    }

    private int cellY(int slot) {
        int top = y + GRID_Y;
        if (slot >= 36) {
            return top;
        }
        int mainTop = top + CELL + GAP;
        if (slot >= COLS) {
            return mainTop + (slot / COLS - 1) * CELL;
        }
        return mainTop + CELL * 3 + GAP;
    }

    /**
     * 物品原本的提示框，末尾加一行「还能附几个」。
     * <p>
     * 用带 {@code ItemStack} 的那个重载：Forge 的提示框事件、以及整合包里改提示框样式的模组
     * 都认这个，画出来和玩家在物品栏里看到的一样。
     * </p>
     */
    private static void renderTooltip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull ItemStack stack,
                                      int usable, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        Component extra = usable > 0
                ? Component.translatable("carianstyle.codex.filter.tooltip_count", usable)
                .withStyle(style -> style.withColor(UiTheme.ON & 0xFFFFFF))
                : Component.translatable("carianstyle.codex.filter.tooltip_none")
                .withStyle(style -> style.withColor(UiTheme.TEXT_DIM & 0xFFFFFF));
        lines.add(extra);
        Services.client().renderItemTooltip(g, font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
    }

    /**
     * @return 选中的格子下标（≥0），或 {@link #CLEAR} / {@link #CONSUMED} / {@link #DISMISSED}
     */
    int mouseClicked(double mouseX, double mouseY, @Nonnull Inventory inventory) {
        if (!open) {
            return CONSUMED;
        }
        if (!contains(mouseX, mouseY)) {
            close();
            return DISMISSED;
        }
        if (clearRect[2] > 0 && UiTheme.hit(mouseX, mouseY, clearRect[0], clearRect[1], clearRect[2], clearRect[3])) {
            return CLEAR;
        }
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0 && slot < inventory.getContainerSize() && !inventory.getItem(slot).isEmpty()) {
            // 选择器随即关掉，星点画在书页上那一格原来的位置
            UiTheme.burst(cellX(slot) + CELL / 2, cellY(slot) + CELL / 2, 5);
            return slot;
        }
        return CONSUMED;
    }

    private int slotAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - (x + PAD)) / CELL);
        if (col < 0 || col >= COLS) {
            return -1;
        }
        int top = y + GRID_Y;
        if (mouseY >= top && mouseY < top + CELL) {
            return TOP_ROW[col];
        }
        int mainTop = top + CELL + GAP;
        if (mouseY >= mainTop && mouseY < mainTop + CELL * 3) {
            int r = (int) Math.floor((mouseY - mainTop) / CELL);
            return COLS + r * COLS + col;
        }
        int hotbarTop = mainTop + CELL * 3 + GAP;
        if (mouseY >= hotbarTop && mouseY < hotbarTop + CELL) {
            return col;
        }
        return -1;
    }
}
