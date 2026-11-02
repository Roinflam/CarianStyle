package pers.roinflam.carianstyle.client.gui;

import pers.roinflam.carianstyle.mc112.gui.Font;
import pers.roinflam.carianstyle.mc112.gui.GuiGraphics;
import pers.roinflam.carianstyle.mc112.gui.Component;
import pers.roinflam.carianstyle.mc112.gui.FormattedCharSequence;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.codex.ItemFit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
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
 * @author RoinFlam
 * @version 1.0
 */
@SideOnly(Side.CLIENT)
public final class DetailSections {

    /** 图标格子的边长（含间距） */
    private static final int ICON_STRIDE = 20;

    /** 图标本身的边长 */
    private static final int ICON_SIZE = 18;

    private DetailSections() {
    }

    // ==================== 1.12.2 版：附魔获取途径的判定 ====================
    //
    // 1.20.1 直接调 Enchantment 上的 isTreasureOnly / isDiscoverable / isTradeable / getMinCost。
    // 1.12.2 原版附魔类只有 isTreasureEnchantment 和 getMinEnchantability，另外两个是本模组
    // EnchantmentBase 自己加的普通方法，所以统一收在这里按「是不是本模组附魔」分开问。

    /**
     * 是否宝藏附魔（附魔台抽不到）。本模组附魔的 isTreasureOnly 已经桥接进 isTreasureEnchantment。
     *
     * @param enchantment 附魔
     * @return 是否宝藏
     */
    static boolean isTreasure(@Nonnull Enchantment enchantment) {
        return enchantment.isTreasureEnchantment();
    }

    /**
     * 能否被随机抽中（附魔台、战利品）。
     * <p>1.12.2 版：原版没有这个开关，别家附魔一律当作能抽中；本模组附魔问 {@link EnchantmentBase#isDiscoverable()}。</p>
     *
     * @param enchantment 附魔
     * @return 能否被随机抽中
     */
    static boolean isDiscoverable(@Nonnull Enchantment enchantment) {
        return !(enchantment instanceof EnchantmentBase) || ((EnchantmentBase) enchantment).isDiscoverable();
    }

    /**
     * 会不会出现在图书管理员的交易里。
     * <p>
     * 1.12.2 版：原版交易从附魔注册表里等概率抽，所有附魔都在池里；本模组在启动时把附魔书交易换成了
     * 过滤过的版本，候选池是「不是本模组附魔、或者 isTradeable 为 true」，这里照同一条规则回答。
     * </p>
     *
     * @param enchantment 附魔
     * @return 会不会出现在交易里
     */
    static boolean isTradeable(@Nonnull Enchantment enchantment) {
        return !(enchantment instanceof EnchantmentBase) || ((EnchantmentBase) enchantment).isTradeable();
    }

    /**
     * 附魔台上出这一级所需的最低附魔能力（1.20.1 的 getMinCost）。
     *
     * @param enchantment 附魔
     * @param level       等级
     * @return 最低附魔能力
     */
    static int minCost(@Nonnull Enchantment enchantment, int level) {
        return enchantment.getMinEnchantability(level);
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

    /**
     * 放入物品后，在详情最上方画「这个附魔对这件东西怎么样」的结论卡片。
     *
     * <h3>为什么放在最上面</h3>
     * <p>
     * 玩家放物品进来就是为了问这一个问题，答案不该埋在描述和数值下面。
     * 卡片边框用结论的颜色（绿 / 金 / 红 / 灰），不读字也能先看出个大概。
     * </p>
     * <p>
     * 两个详情面板共用这一份，理由同 {@link #itemTypes}。
     * </p>
     *
     * @param g           绘制上下文
     * @param font        字体
     * @param filter      放入物品筛选；未启用时不画
     * @param enchantment 当前附魔
     * @param left        内容左边界
     * @param cursorY     当前纵坐标
     * @param innerWidth  可用宽度
     * @return 绘制后的纵坐标
     */
    public static int fitCard(@Nonnull GuiGraphics g, @Nonnull Font font, @Nullable ItemFilter filter,
                              @Nonnull Enchantment enchantment, int left, int cursorY, int innerWidth) {
        if (filter == null || !filter.isActive()) {
            return cursorY;
        }
        ItemStack stack = filter.getStack();
        ItemFit fit = filter.fitOf(enchantment);
        if (stack == null || fit == null) {
            return cursorY;
        }

        Component verdict;
        int color;
        List<Component> body = new ArrayList<>(2);
        switch (fit.getStatus()) {
            case FITS:
                if (fit.isActionable()) {
                    verdict = Component.translatable("carianstyle.codex.fit.card.fits");
                    color = UiTheme.ON;
                    body.add(fitsText(fit));
                } else {
                    // 这类物品收它，但这一件附过魔（进不了附魔台）、铁砧又嫌贵：哪条路都走不通
                    verdict = Component.translatable("carianstyle.codex.fit.card.expensive");
                    color = UiTheme.WARN;
                    body.add(expensiveText(filter, fit, 1));
                    if (fit.isViaTable()) {
                        body.add(Component.translatable("carianstyle.codex.fit.card.expensive.table_hint"));
                    }
                }
                break;
            case OWNED:
                verdict = Component.translatable("carianstyle.codex.fit.card.owned",
                        UiTheme.roman(fit.getOwnedLevel()));
                color = fit.isActionable() ? UiTheme.ACCENT : UiTheme.TEXT_DIM;
                int next = Math.min(fit.getOwnedLevel() + 1, fit.getMaxLevel());
                if (!fit.getBlockers().isEmpty()) {
                    // 物品上带着和它互斥的附魔（命令、旧版本留下的）：铁砧升级同样出不了结果
                    body.add(Component.translatable("carianstyle.codex.fit.card.owned.blocked",
                            blockerNames(fit)));
                } else if (!fit.isViaAnvil()) {
                    body.add(Component.translatable("carianstyle.codex.fit.card.owned.stuck",
                            UiTheme.roman(fit.getMaxLevel())));
                } else if (fit.isAnvilTooExpensive()) {
                    body.add(expensiveText(filter, fit, next));
                } else {
                    body.add(Component.translatable("carianstyle.codex.fit.card.owned.upgrade",
                            UiTheme.roman(fit.getOwnedLevel()), UiTheme.roman(next),
                            UiTheme.roman(fit.getMaxLevel())));
                }
                break;
            case MAXED:
                verdict = Component.translatable("carianstyle.codex.fit.card.maxed",
                        UiTheme.roman(fit.getOwnedLevel()));
                color = UiTheme.TEXT_DIM;
                body.add(Component.translatable("carianstyle.codex.fit.card.maxed.detail"));
                break;
            case BLOCKED:
                verdict = Component.translatable("carianstyle.codex.fit.card.blocked");
                color = UiTheme.DANGER;
                body.add(Component.translatable("carianstyle.codex.fit.card.blocked.detail",
                        blockerNames(fit)));
                // 附魔书被砂轮洗掉非诅咒附魔后会变回普通书，而普通书在铁砧上合不了任何附魔书——
                // 对附魔书说「洗掉再附」是把人往死路上引
                body.add(Component.translatable(stack.getItem() == Items.ENCHANTED_BOOK
                        ? "carianstyle.codex.fit.card.blocked.tip_book"
                        : "carianstyle.codex.fit.card.blocked.tip"));
                break;
            case NONE:
            default:
                verdict = Component.translatable("carianstyle.codex.fit.card.none");
                color = UiTheme.OFF;
                body.add(Component.translatable(fit.isViaTable()
                        ? "carianstyle.codex.fit.card.none.enchanted"
                        : "carianstyle.codex.fit.card.none.detail"));
                break;
        }

        // 先排版再画：背景得在文字之前画，而背景多高要看正文折成几行
        int pad = 6;
        int textWidth = innerWidth - pad * 2;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component paragraph : body) {
            lines.addAll(font.splitSequences(paragraph, Math.max(1, textWidth)));
        }

        // 标题行：图标 + 物品名 + 靠右的结论。面板太窄、结论放不进图标右边时，
        // 结论单独起一行放在图标下面——宁可卡片高一行，也不能让结论钻到图标底下或和名字叠在一起
        int verdictWidth = font.width(verdict);
        int nameX = left + pad + ICON_SIZE + 4;
        int verdictX = left + innerWidth - pad - verdictWidth;
        boolean verdictInline = verdictX >= nameX;
        int headHeight = verdictInline ? ICON_SIZE : ICON_SIZE + font.lineHeight + 1;
        int cardHeight = pad + headHeight + 3 + lines.size() * (font.lineHeight + 1) + pad - 1;

        g.fill(left, cursorY, left + innerWidth, cursorY + cardHeight, UiTheme.PANEL_ALT);
        UiTheme.border(g, left, cursorY, innerWidth, cardHeight, UiTheme.withAlpha(color, 0.7f));
        g.fill(left, cursorY, left + 2, cursorY + cardHeight, color);

        int headY = cursorY + pad;
        g.renderItem(stack, left + pad, headY - 1);

        int textY = headY + (ICON_SIZE - font.lineHeight) / 2;
        int nameRight;
        if (verdictInline) {
            g.drawString(font, verdict, verdictX, textY, color, false);
            nameRight = verdictX - 8;
        } else {
            g.drawString(font, verdict, left + pad, headY + ICON_SIZE + 1, color, false);
            nameRight = left + innerWidth - pad;
        }
        // 名字放不下「...」就干脆不画：图标已经说明了是哪件东西
        if (nameRight - nameX >= font.width("...") + 2) {
            UiTheme.trimmed(g, font, Component.literal(stack.getDisplayName()), nameX, textY, nameRight - nameX, UiTheme.TEXT);
        }

        int lineY = headY + headHeight + 3;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, left + pad, lineY, UiTheme.TEXT_DIM, false);
            lineY += font.lineHeight + 1;
        }
        return cursorY + cardHeight + 10;
    }

    /**
     * 「可以附上」时说走哪条路。按这一件现在实际能走的路说，与列表行备注的判断一致。
     *
     * @param fit 判定结果（FITS 且能实际附上）
     * @return 说明
     */
    @Nonnull
    private static Component fitsText(@Nonnull ItemFit fit) {
        boolean table = fit.isViaTable() && !fit.isStackEnchanted();
        boolean anvil = fit.isViaAnvil() && !fit.isAnvilTooExpensive();
        if (table && anvil) {
            return Component.translatable("carianstyle.codex.fit.card.fits.both");
        }
        if (table) {
            // 铁砧本来收它、只是这一件惩罚太高，和「物品不收附魔书」是两回事，分开说
            return fit.isViaAnvil()
                    ? Component.translatable("carianstyle.codex.fit.card.fits.table_expensive",
                    fit.getAnvilCost())
                    : Component.translatable("carianstyle.codex.fit.card.fits.table");
        }
        return Component.translatable(fit.isViaTable()
                ? "carianstyle.codex.fit.card.fits.both_enchanted"
                : "carianstyle.codex.fit.card.fits.anvil");
    }

    /**
     * 「铁砧过于昂贵」的说明。
     *
     * @param filter      筛选（取累计惩罚、是否整组）
     * @param fit         判定结果
     * @param resultLevel 合完之后的等级
     * @return 说明
     */
    @Nonnull
    private static Component expensiveText(@Nonnull ItemFilter filter, @Nonnull ItemFit fit, int resultLevel) {
        if (filter.isStackMultiple()) {
            return Component.translatable("carianstyle.codex.fit.card.expensive.stack");
        }
        return Component.translatable("carianstyle.codex.fit.card.expensive.detail",
                filter.getBaseRepairCost(), UiTheme.roman(resultLevel), fit.getAnvilCost());
    }

    /**
     * 把挡路的附魔拼成一串「锋利 V、亡灵杀手 III」。
     *
     * @param fit 判定结果
     * @return 拼好的名字
     */
    @Nonnull
    private static Component blockerNames(@Nonnull ItemFit fit) {
        Component names = Component.empty();
        Component separator = Component.translatable("carianstyle.codex.fit.card.separator");
        boolean first = true;
        for (EnchantmentData blocker : fit.getBlockers()) {
            if (!first) {
                names.append(separator);
            }
            first = false;
            Enchantment other = blocker.enchantment;
            Component name = Component.translatable(other.getName());
            // 与原版 getFullname 一致：只有一级的附魔不写等级
            if (blocker.enchantmentLevel != 1 || other.getMaxLevel() != 1) {
                name.append(" ").append(UiTheme.roman(blocker.enchantmentLevel));
            }
            // 名字用正文亮色、诅咒用红色，从灰色的说明文字里跳出来
            int nameColor = (other.isCurse() ? UiTheme.DANGER : UiTheme.TEXT) & 0xFFFFFF;
            names.append(name.withStyle(style -> style.withColor(nameColor)));
        }
        return names;
    }
}
