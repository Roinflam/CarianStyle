package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 百科的背包选择器：弹出玩家背包的格子，点一件就把它放进「放入物品」槽。
 *
 * <h3>为什么不做成真正的物品栏</h3>
 * <p>
 * 真的能拖物品的界面得是容器界面，要服务端开一个菜单、要网络同步，
 * 百科还得从普通界面改成容器界面——为了「看一眼能附什么」动这么大不值，
 * 还平白多出刷物品、物品卡在界面里之类的风险。
 * 这里只读客户端背包、只拿副本，玩家的东西一件都不会动。
 * </p>
 *
 * <h3>排布</h3>
 * <p>
 * 第一行：头、胸、腿、脚四件盔甲，最右是副手；中间三行是背包；最下一行是快捷栏。
 * 与原版物品栏同一个上下顺序，玩家不用重新找东西在哪。当前手持的那一格底部有一道金线。
 * </p>
 *
 * <h3>压暗</h3>
 * <p>
 * 打开时对每一格算一次「还能附（或还能升级）几个附魔」，为 0 的格子压暗。
 * 一眼就能看出背包里哪些东西值得点开，不用挨个试。压暗的格子仍然可以点——
 * 玩家也许就是想确认「为什么这件什么都附不上」。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
final class InventoryPicker {

    /** {@link #mouseClicked} 的返回值：点在选择器里但没选中东西（或选择器没开） */
    static final int CONSUMED = -1;

    /** {@link #mouseClicked} 的返回值：点了「清除」 */
    static final int CLEAR = -2;

    /** {@link #mouseClicked} 的返回值：点在选择器外面，选择器已关闭 */
    static final int DISMISSED = -3;

    /** 格子边长 */
    private static final int CELL = 18;

    /** 面板内边距 */
    private static final int PAD = 6;

    /** 标题行高 */
    private static final int TITLE_HEIGHT = 14;

    /** 分区之间的空隙 */
    private static final int GAP = 4;

    /**
     * 整个选择器在深度上抬高的量。
     * <p>
     * 详情面板里也画着物品图标（「适用物品」那一排），物品模型画在 z≈150 附近，
     * 而普通的 {@code fill} 画在 z=0——不抬高的话，那些图标会从选择器的背景里透出来。
     * </p>
     */
    private static final float Z_LIFT = 200f;

    /** 背包格子总数：36 个主格 + 4 件盔甲 + 副手，下标与 {@link Inventory#getItem} 一致 */
    private static final int SLOT_COUNT = 41;

    /** 第一行的格子：盔甲头到脚（36~39 是脚到头，倒过来），-1 为空位，最右是副手 */
    private static final int[] TOP_ROW = {39, 38, 37, 36, -1, -1, -1, -1, 40};

    private boolean open;

    private int x;
    private int y;
    private int width;
    private int height;

    /** 「清除」按钮矩形（x, y, w, h）；宽为 0 表示没画 */
    private final int[] clearRect = new int[4];

    /** 打开时每一格的物品对象，用于发现格子里换了东西、需要重算 */
    private final ItemStack[] seen = new ItemStack[SLOT_COUNT];

    /** 每一格还能附的附魔数；-1 为空格 */
    private final int[] usable = new int[SLOT_COUNT];

    /**
     * @return 是否打开
     */
    boolean isOpen() {
        return open;
    }

    /**
     * 打开选择器。
     *
     * @param anchorX      锚点左（「放入物品」槽的左边缘）
     * @param anchorY      锚点上（槽的下边缘再往下留点空）
     * @param screenWidth  屏幕宽
     * @param screenHeight 屏幕高
     * @param inventory    玩家背包
     */
    void open(int anchorX, int anchorY, int screenWidth, int screenHeight, @Nonnull Inventory inventory) {
        width = PAD * 2 + CELL * 9;
        height = PAD + TITLE_HEIGHT + CELL + GAP + CELL * 3 + GAP + CELL + PAD;
        // 右边放不下就往左挪，下边放不下就往上挪；小窗口里宁可盖住一点顶栏也不能出屏
        x = Math.max(4, Math.min(anchorX, screenWidth - width - 4));
        y = Math.max(4, Math.min(anchorY, screenHeight - height - 4));

        Arrays.fill(seen, null);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            refreshSlot(inventory, slot);
        }
        open = true;
    }

    /**
     * 关闭选择器。
     */
    void close() {
        open = false;
        // 不留着物品引用：选择器关了，这些副本没有任何用处
        Arrays.fill(seen, null);
    }

    /**
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 鼠标是否在选择器范围内
     */
    boolean contains(double mouseX, double mouseY) {
        return open && UiTheme.hit(mouseX, mouseY, x, y, width, height);
    }

    /**
     * 重算一格的可附数量。
     *
     * @param inventory 背包
     * @param slot      格子
     */
    private void refreshSlot(@Nonnull Inventory inventory, int slot) {
        ItemStack stack = slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
        seen[slot] = stack;
        usable[slot] = stack.isEmpty() ? -1 : ItemFilter.countUsable(stack);
    }

    /**
     * 绘制选择器。
     *
     * @param g           绘制上下文
     * @param font        字体
     * @param inventory   玩家背包
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     * @param currentSlot 当前放入物品的来源格子；-1 表示没有
     */
    void render(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Inventory inventory,
                int mouseX, int mouseY, int currentSlot) {
        if (!open) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0f, 0f, Z_LIFT);

        // 投影：选择器浮在内容之上，给一圈暗边让它和底下的列表分开
        g.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x66000000);
        UiTheme.panel(g, x, y, width, height, UiTheme.PANEL);
        UiTheme.corners(g, x, y, width, height, 6, UiTheme.ACCENT);

        g.drawString(font, Component.translatable("carianstyle.codex.filter.picker_title"),
                x + PAD, y + PAD, UiTheme.ACCENT, false);

        clearRect[2] = 0;
        if (currentSlot >= 0) {
            Component clear = Component.translatable("carianstyle.codex.filter.clear");
            int w = font.width(clear) + 8;
            setClearRect(x + width - PAD - w, y + PAD - 3, w, 13);
            UiTheme.button(g, font, clear, clearRect[0], clearRect[1], clearRect[2], clearRect[3],
                    true, mouseX, mouseY);
        }

        int hoveredSlot = -1;
        int rowY = y + PAD + TITLE_HEIGHT;
        hoveredSlot = renderRow(g, font, inventory, TOP_ROW, rowY, mouseX, mouseY, currentSlot, hoveredSlot);
        rowY += CELL + GAP;
        int[] row = new int[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                row[c] = 9 + r * 9 + c;
            }
            hoveredSlot = renderRow(g, font, inventory, row, rowY, mouseX, mouseY, currentSlot, hoveredSlot);
            rowY += CELL;
        }
        rowY += GAP;
        for (int c = 0; c < 9; c++) {
            row[c] = c;
        }
        hoveredSlot = renderRow(g, font, inventory, row, rowY, mouseX, mouseY, currentSlot, hoveredSlot);

        // 提示框最后画、并且仍在抬高后的深度里：它要压住选择器自己的物品图标和压暗层
        if (hoveredSlot >= 0) {
            renderTooltip(g, font, inventory.getItem(hoveredSlot), usable[hoveredSlot], mouseX, mouseY);
        }

        g.pose().popPose();
    }

    private void setClearRect(int rx, int ry, int rw, int rh) {
        clearRect[0] = rx;
        clearRect[1] = ry;
        clearRect[2] = rw;
        clearRect[3] = rh;
    }

    /**
     * 画一行格子。
     *
     * @return 悬停中的格子；这一行没有悬停时原样返回传入值
     */
    private int renderRow(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Inventory inventory,
                          @Nonnull int[] slots, int rowY, int mouseX, int mouseY,
                          int currentSlot, int hoveredSlot) {
        for (int c = 0; c < slots.length; c++) {
            int slot = slots[c];
            if (slot < 0) {
                continue;
            }
            int cellX = x + PAD + c * CELL;
            ItemStack stack = slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
            if (stack != seen[slot]) {
                // 选择器开着的时候背包变了（捡到东西、被打掉耐久）：只重算这一格
                refreshSlot(inventory, slot);
            }

            boolean hovered = UiTheme.hit(mouseX, mouseY, cellX, rowY, CELL, CELL);
            boolean current = slot == currentSlot;
            g.fill(cellX, rowY, cellX + CELL, rowY + CELL,
                    current ? UiTheme.ACCENT_FILL : (hovered && !stack.isEmpty() ? UiTheme.PANEL_HOVER : UiTheme.PANEL_ALT));
            UiTheme.border(g, cellX, rowY, CELL, CELL,
                    current ? UiTheme.ACCENT : (hovered && !stack.isEmpty() ? UiTheme.TEXT_DIM : UiTheme.BORDER));
            if (slot == inventory.selected) {
                // 手持的那一格：底边一道金线，最常见的用法就是「看看我手上这把」
                g.fill(cellX + 2, rowY + CELL - 2, cellX + CELL - 2, rowY + CELL - 1, UiTheme.ACCENT);
            }

            if (stack.isEmpty()) {
                continue;
            }
            g.renderItem(stack, cellX + 1, rowY + 1);
            g.renderItemDecorations(font, stack, cellX + 1, rowY + 1);

            if (usable[slot] == 0 && !current) {
                // 压暗层要盖住物品和数量字：两者分别画在 +150 与 +200 的深度上
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 250f);
                g.fill(cellX + 1, rowY + 1, cellX + CELL - 1, rowY + CELL - 1, 0xA0100D0A);
                g.pose().popPose();
            }
            if (hovered) {
                hoveredSlot = slot;
            }
        }
        return hoveredSlot;
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
        g.renderTooltip(font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
    }

    /**
     * 处理点击。
     *
     * @param mouseX    鼠标 X
     * @param mouseY    鼠标 Y
     * @param inventory 玩家背包
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
            return slot;
        }
        return CONSUMED;
    }

    /**
     * 反查鼠标下的格子。与 {@link #render} 用同一套排布常量。
     *
     * @return 格子下标；不在任何格子上时为 -1
     */
    private int slotAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - (x + PAD)) / CELL);
        if (col < 0 || col >= 9) {
            return -1;
        }
        int top = y + PAD + TITLE_HEIGHT;
        if (mouseY >= top && mouseY < top + CELL) {
            return TOP_ROW[col];
        }
        int mainTop = top + CELL + GAP;
        if (mouseY >= mainTop && mouseY < mainTop + CELL * 3) {
            int r = (int) Math.floor((mouseY - mainTop) / CELL);
            return 9 + r * 9 + col;
        }
        int hotbarTop = mainTop + CELL * 3 + GAP;
        if (mouseY >= hotbarTop && mouseY < hotbarTop + CELL) {
            return col;
        }
        return -1;
    }
}
