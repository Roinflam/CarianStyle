package pers.roinflam.carianstyle.client.gui;

import pers.roinflam.carianstyle.mc112.gui.Ui;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import java.util.Collections;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import pers.roinflam.carianstyle.mc112.gui.Font;
import pers.roinflam.carianstyle.mc112.gui.GuiGraphics;
import pers.roinflam.carianstyle.mc112.gui.Component;
import pers.roinflam.carianstyle.mc112.gui.FormattedCharSequence;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import pers.roinflam.carianstyle.codex.ItemFit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * 两个详情页共用的正文件：节头间距、点线行、带符号的条目、冲突小牌、物品格、放入物品后的结论卡片。
 * 两页的同一块只留这一份画法，免得改一边忘一边又长得不一样。
 */
@SideOnly(Side.CLIENT)
public final class DetailSections {

    public static final int SECTION_GAP = 10;
    public static final int LINE = 11;
    public static final int NOTE_STEP = 10;
    public static final int CHIP_H = 13;
    public static final int CHIP_GAP = 4;
    public static final int SLOT = 18;
    private static final int SLOT_GAP = 3;

    private static final int FOREIGN_CHIP = 0xFF8FB8E8;

    private static List<Item> stacksOf;
    private static List<ItemStack> stacks = Collections.emptyList();

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

    /** 各节上次画出来的高度，整节滚出视口时照这个高度跳过；条目、等级、宽度变了全部作废 */
    static final class SectionCache {
        private final int[] heights;
        private String key;

        SectionCache(int count) {
            heights = new int[count];
        }

        void reset() {
            key = null;
        }

        void begin(@Nonnull String key) {
            if (!key.equals(this.key)) {
                this.key = key;
                Arrays.fill(heights, -1);
            }
        }

        int draw(int index, int cursorY, int viewTop, int viewBottom, @Nonnull IntUnaryOperator section) {
            int h = heights[index];
            if (h >= 0 && (cursorY + h <= viewTop || cursorY >= viewBottom)) {
                return cursorY + h;
            }
            int end = section.applyAsInt(cursorY);
            heights[index] = end - cursorY;
            return end;
        }
    }

    public static int section(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component title,
                              int left, int y, int innerWidth) {
        return UiTheme.sectionHeader(g, font, title, left, y + SECTION_GAP, innerWidth);
    }

    /**
     * 标签 …… 值。值太长时在右边那一栏里折行、每行靠右；标签本身就占掉大半行时值整个挪到下一行。
     * 返回下一行的 y。
     */
    public static int kv(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component label,
                         @Nonnull String value, int left, int y, int innerWidth, int valueColor) {
        int labelW = font.width(label);
        int valueW = font.width(value);
        int avail = innerWidth - labelW - 14;
        if (valueW <= avail) {
            return UiTheme.keyValue(g, font, label, value, left, y, innerWidth, valueColor);
        }
        g.drawString(font, UiTheme.trim(font, label.getString(), innerWidth), left, y + 1, UiTheme.TEXT_DIM, false);
        List<FormattedCharSequence> lines;
        int lineY = y;
        if (avail < 40) {
            lines = font.splitSequences(Component.literal(value), Math.max(1, innerWidth));
            lineY += LINE;
        } else {
            lines = font.splitSequences(Component.literal(value), avail);
        }
        boolean first = true;
        for (FormattedCharSequence line : lines) {
            int w = font.width(line);
            int vx = left + innerWidth - w;
            g.drawString(font, line, vx, lineY + 1, valueColor, false);
            if (first && avail >= 40) {
                UiTheme.dots(g, left + labelW + 4, vx - 4, lineY + 7);
            }
            first = false;
            lineY += LINE;
        }
        return Math.max(lineY, y + LINE);
    }

    /** 一条带符号的条目，超宽折行；符号跟着字色走（警示金、压暗灰、其余银星） */
    public static int bullet(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                             int left, int y, int innerWidth, int color) {
        String mark = color == UiTheme.WARN || color == UiTheme.DANGER ? "bul_warn"
                : color == UiTheme.OFF || color == UiTheme.TEXT_DIM ? "bul_dim" : "bul";
        UiTheme.sprite(g, mark, left + 1, y + 3, 5, 5);
        for (FormattedCharSequence line : font.splitSequences(text, Math.max(1, innerWidth - 10))) {
            g.drawString(font, line, left + 10, y + 1, color, false);
            y += LINE;
        }
        return y;
    }

    /** 8 高的说明小字，折行按 10 一行；返回最后一行字的下沿 */
    public static int note(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                           int left, int y, int innerWidth, int color) {
        List<FormattedCharSequence> lines = font.splitSequences(text, Math.max(1, innerWidth));
        if (lines.isEmpty()) {
            return y;
        }
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), left, y + i * NOTE_STEP, color, false);
        }
        return y + (lines.size() - 1) * NOTE_STEP + 8;
    }

    public static int chipWidth(@Nonnull Font font, @Nonnull Component text, int maxWidth) {
        return Math.min(maxWidth, font.width(text) + 10);
    }

    /** 冲突小牌：本模组的红，外部附魔的灰蓝（点下去会换到「其他附魔」页） */
    public static void chip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                            int x, int y, int width, boolean hovered, boolean foreign) {
        boolean pressed = hovered && Ui.isLeftPressed();
        int c = foreign ? FOREIGN_CHIP : 0xFFFF5A6E;
        int fill = UiTheme.withAlpha(c, pressed ? 0.4f : hovered ? 0.24f : 0.08f);
        int edge = hovered ? (foreign ? FOREIGN_CHIP : UiTheme.DANGER)
                : foreign ? UiTheme.mix(0xFF0B1030, FOREIGN_CHIP, 0.55f) : 0xFF8F3F52;
        int color = hovered ? 0xFFFFFFFF : foreign ? 0xFFB4CDEE : 0xFFFF9AA6;
        UiTheme.plate(g, x, y, width, CHIP_H, fill, edge);
        g.drawString(font, UiTheme.trim(font, text.getString(), width - 10), x + 5, y + 3, color, false);
    }

    public static void slot(@Nonnull GuiGraphics g, @Nonnull ItemStack stack, int x, int y) {
        g.fill(x, y, x + SLOT, y + SLOT, 0xB3060A1A);
        UiTheme.border(g, x, y, SLOT, SLOT, UiTheme.DOTS);
        g.renderItem(stack, x + 1, y + 1);
    }

    /** 标题行右端的注册 id 牌；放不下（左边越过 minLeft）就不画 */
    public static void idChip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull String id,
                              int left, int y, int innerWidth, int minLeft) {
        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon + 1) : "";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        int chipW = font.width(namespace) + font.width(path) + 10;
        int chipX = left + innerWidth - chipW;
        if (chipX < minLeft) {
            return;
        }
        UiTheme.plate(g, chipX, y - 3, chipW, CHIP_H, UiTheme.PANEL, UiTheme.BORDER);
        int textX = chipX + 5;
        if (!namespace.isEmpty()) {
            g.drawString(font, namespace, textX, y, UiTheme.OFF, false);
            textX += font.width(namespace);
        }
        g.drawString(font, path, textX, y, UiTheme.TEXT_DIM, false);
    }

    /**
     * 「适用物品种类」整节（含节头，节头上的 10 由调用方空）。种类名在上、物品格在下：
     * 只看一把钻石剑，判断不出是只能附剑还是剑斧都行，种类名才是直接的回答。
     * 返回最后一行字的下沿。
     */
    public static int itemTypes(@Nonnull GuiGraphics g, @Nonnull Font font,
                                @Nonnull Component categoryName, @Nonnull List<Item> items,
                                int left, int cursorY, int innerWidth) {
        int y = UiTheme.sectionHeader(g, font, Component.translatable("carianstyle.codex.section.items"),
                left, cursorY, innerWidth);
        y = note(g, font, categoryName, left, y, innerWidth, UiTheme.TEXT);
        if (items.isEmpty()) {
            return note(g, font, Component.translatable("carianstyle.codex.items.none"),
                    left, y + 5, innerWidth, UiTheme.OFF);
        }
        if (stacksOf != items) {
            List<ItemStack> built = new ArrayList<>(items.size());
            for (Item item : items) {
                built.add(new ItemStack(item));
            }
            stacksOf = items;
            stacks = built;
        }
        int sx = left;
        int rowY = y + 5;
        for (ItemStack stack : stacks) {
            if (sx > left && sx + SLOT > left + innerWidth) {
                sx = left;
                rowY += SLOT + SLOT_GAP;
            }
            slot(g, stack, sx, rowY);
            sx += SLOT + SLOT_GAP;
        }
        return note(g, font, Component.translatable("carianstyle.codex.items.hint"),
                left, rowY + SLOT + 5, innerWidth, UiTheme.OFF);
    }

    /**
     * 放入物品后，详情最上面那张「这个附魔对这件东西怎么样」的卡片：玩家放物品进来就是要问这个，
     * 答案不能埋在描述和数值下面。边框跟结论色，不读字也能先看出大概。
     * 上空 7 再画；没启用筛选时什么都不画、原样返回。返回卡片下沿。
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
                // 附魔书被砂轮洗掉非诅咒附魔后变回普通书，普通书在铁砧上合不了任何附魔书，
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

        // 先排版再画：底色要在字之前画，而卡片多高要看正文折成几行
        int pad = 6;
        int textWidth = Math.max(1, innerWidth - pad * 2);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component paragraph : body) {
            lines.addAll(font.splitSequences(paragraph, textWidth));
        }

        // 窄到结论放不进名字右边时，结论单独起一行，宁可卡片高一行也不能和名字叠在一起
        int verdictWidth = font.width(verdict);
        int nameX = left + pad + 16 + 5;
        int verdictX = left + innerWidth - pad - verdictWidth;
        boolean verdictInline = verdictX >= nameX;
        int top = cursorY + 7;
        int headY = top + 4;
        int linesTop = headY + 16 + 2 + (verdictInline ? 0 : NOTE_STEP);
        int cardHeight = linesTop + lines.size() * NOTE_STEP + 5 - top;

        g.fill(left, top, left + innerWidth, top + cardHeight, UiTheme.withAlpha(color, 0.07f));
        UiTheme.border(g, left, top, innerWidth, cardHeight, verdictEdge(color));

        g.renderItem(stack, left + pad, headY);
        int textY = headY + 4;
        int nameRight;
        if (verdictInline) {
            g.drawString(font, verdict, verdictX, textY, color, false);
            nameRight = verdictX - 8;
        } else {
            g.drawString(font, verdict, left + pad, headY + 16 + 3, color, false);
            nameRight = left + innerWidth - pad;
        }
        // 名字连省略号都放不下就不画，图标已经说明了是哪件东西
        if (nameRight - nameX >= font.width("…") + 2) {
            UiTheme.trimmed(g, font, Component.literal(stack.getDisplayName()), nameX, textY, nameRight - nameX, UiTheme.MOON, true);
        }

        int lineY = linesTop;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, left + pad, lineY + 1, UiTheme.TEXT_DIM, false);
            lineY += NOTE_STEP;
        }
        return top + cardHeight;
    }

    /** 卡片边框：结论色压暗一档，和冲突小牌、页框用的是同一组暗色 */
    private static int verdictEdge(int color) {
        if (color == UiTheme.ON) {
            return 0xFF3F8F5C;
        }
        if (color == UiTheme.DANGER) {
            return 0xFF8F3F52;
        }
        if (color == UiTheme.WARN) {
            return 0xFF8F7238;
        }
        if (color == UiTheme.ACCENT) {
            return 0xFF3F5F8F;
        }
        if (color == UiTheme.TEXT_DIM || color == UiTheme.OFF) {
            return UiTheme.BORDER;
        }
        return UiTheme.mix(0xFF0B1030, color, 0.5f);
    }

    /** 「可以附上」时说走哪条路，按这一件现在实际能走的路说，和列表行的备注一致 */
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

    @Nonnull
    private static Component expensiveText(@Nonnull ItemFilter filter, @Nonnull ItemFit fit, int resultLevel) {
        if (filter.isStackMultiple()) {
            return Component.translatable("carianstyle.codex.fit.card.expensive.stack");
        }
        return Component.translatable("carianstyle.codex.fit.card.expensive.detail",
                filter.getBaseRepairCost(), UiTheme.roman(resultLevel), fit.getAnvilCost());
    }

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
            // 和原版 getFullname 一样：只有一级的附魔不写等级
            if (blocker.enchantmentLevel != 1 || other.getMaxLevel() != 1) {
                name.append(" ").append(UiTheme.roman(blocker.enchantmentLevel));
            }
            // 名字用正文亮色、诅咒用红，从灰色的说明里跳出来
            int nameColor = (other.isCurse() ? UiTheme.DANGER : UiTheme.TEXT) & 0xFFFFFF;
            names.append(name.withStyle(style -> style.withColor(nameColor)));
        }
        return names;
    }
}
