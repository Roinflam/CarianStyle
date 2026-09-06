package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 两个详情面板共用的区块绘制。
 *
 * <h3>为什么要抽出来</h3>
 * <p>
 * {@link CodexDetail}（本模组附魔）与 {@link ForeignDetail}（原版及其它模组附魔）
 * 各自实现了「适用物品种类」这一块。两份代码<b>几乎</b>一样——同样的标题、同样的图标网格、
 * 同样的脚注——但因为是分两次写的，缩进、间距、颜色、种类名画不画，都出现过不一致。
 * </p>
 * <p>
 * 每修一次都是「把 B 改成和 A 一样」，而下次给其中一边加东西时又会再分叉。
 * 问题不在于某一次改漏了，在于同一块界面存在两份实现。
 * </p>
 * <p>
 * 抽到这里之后，两边传入各自的数据、共用同一段绘制代码，
 * <b>它们不可能再长得不一样</b>。两边信息量的差异（本模组读注解、外部靠实测反推）
 * 收敛成了「传进来的 {@code categoryName} 不同」，仅此而已。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class DetailSections {

    /** 图标格子的边长（含间距） */
    private static final int ICON_STRIDE = 20;

    /** 图标本身的边长 */
    private static final int ICON_SIZE = 18;

    private DetailSections() {
    }

    /**
     * 在标题行右端画注册 ID 的芯片。
     *
     * <h3>为什么做成芯片而不是一行小字</h3>
     * <p>
     * ID 最早是单独一行、字号与标题相同，看着像副标题；改成右端小字之后仍有点尴尬——
     * 它和左边的附魔名在同一行同一种排版里，读起来像「名字的一部分」。
     * </p>
     * <p>
     * 但它其实是<b>另一种东西</b>：给配置文件和命令用的机器名。
     * 套一个深色底的胶囊之后，它在视觉上就从「文案」变成了「一段可以抄走的代码」，
     * 不需要任何文字说明。
     * </p>
     * <p>
     * 命名空间画得比路径更暗：{@code minecraft:} 那半截对读者几乎没有信息量，
     * 真正要看的是冒号后面。
     * </p>
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param id         完整注册名；不含冒号时整体按路径处理
     * @param left       内容左边界
     * @param y          纵坐标
     * @param innerWidth 可用宽度
     * @param minLeft    芯片左边缘不得越过该坐标（避免压住标题）
     */
    public static void idChip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull String id,
                              int left, int y, int innerWidth, int minLeft) {
        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon + 1) : "";
        String path = colon >= 0 ? id.substring(colon + 1) : id;

        int textWidth = font.width(namespace) + font.width(path);
        int chipW = textWidth + 10;
        int chipH = font.lineHeight + 3;
        int chipX = left + innerWidth - chipW;
        if (chipX < minLeft) {
            // 放不下就不画：标题才是主角，挤成两行反而更乱
            return;
        }

        g.fill(chipX, y - 2, chipX + chipW, y - 2 + chipH, UiTheme.PANEL_ALT);
        UiTheme.border(g, chipX, y - 2, chipW, chipH, UiTheme.BORDER);

        int textX = chipX + 5;
        if (!namespace.isEmpty()) {
            g.drawString(font, namespace, textX, y, UiTheme.OFF, false);
            textX += font.width(namespace);
        }
        g.drawString(font, path, textX, y, UiTheme.TEXT_DIM, false);
    }

    /**
     * 绘制「适用物品种类」区块。
     *
     * @param g            绘制上下文
     * @param font         字体
     * @param categoryName 种类名；本模组取自注解，外部附魔由实测结果反推
     * @param items        代表性物品
     * @param left         内容左边界
     * @param cursorY      当前纵坐标
     * @param innerWidth   可用宽度
     * @return 绘制后的纵坐标
     */
    public static int itemTypes(@Nonnull GuiGraphics g, @Nonnull Font font,
                                @Nonnull Component categoryName, @Nonnull List<Item> items,
                                int left, int cursorY, int innerWidth) {
        cursorY = UiTheme.sectionHeader(g, font,
                Component.translatable("carianstyle.codex.section.items"),
                left, cursorY, innerWidth);

        // 种类名在上、图标在下。只画图标的话，玩家看到一把钻石剑也判断不出
        // 「是只能附剑，还是剑斧都行」——种类名才是这个问题的直接回答
        cursorY = UiTheme.wrapped(g, font, categoryName, left, cursorY, innerWidth, UiTheme.TEXT) + 5;

        if (items.isEmpty()) {
            return UiTheme.wrapped(g, font, Component.translatable("carianstyle.codex.items.none"),
                    left, cursorY, innerWidth, UiTheme.TEXT_DIM) + 8;
        }

        int iconX = left;
        int rowTop = cursorY;
        for (Item item : items) {
            if (iconX + ICON_SIZE > left + innerWidth) {
                iconX = left;
                rowTop += ICON_STRIDE;
            }
            g.renderItem(new ItemStack(item), iconX, rowTop);
            iconX += ICON_STRIDE;
        }
        cursorY = rowTop + ICON_STRIDE + 3;

        cursorY = UiTheme.wrapped(g, font, Component.translatable("carianstyle.codex.items.hint"),
                left, cursorY, innerWidth, UiTheme.OFF);
        return cursorY + 8;
    }
}
