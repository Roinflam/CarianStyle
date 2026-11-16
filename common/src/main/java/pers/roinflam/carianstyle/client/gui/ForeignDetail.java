package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.client.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.client.codex.ForeignCodex;
import pers.roinflam.carianstyle.client.codex.ForeignMeta;
import pers.roinflam.carianstyle.client.codex.ModNames;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.codex.EnchantmentSlots;
import pers.roinflam.carianstyle.codex.ItemCategoryNamer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 外部附魔（原版和别的模组）的详情。版式和 {@link CodexDetail} 一样：透明卷首 + 卷首下沿往下铺玻璃的正文。
 * <p>
 * 没和 CodexDetail 合成一个类：本模组的详情有可调数值、宝藏归因、整类互斥折叠，外部附魔一概没有，
 * 合在一起到处是 isForeign 分支。共用的画法都在 {@link DetailHead}、{@link DetailSections} 里，这边只是调用。
 * </p>
 * <p>
 * 冲突小牌里外部附魔在本页跳，本模组附魔会切回「附魔百科」页（靠键里有没有冒号区分）。
 * 颜色和 CodexDetail 那边同一个意思：红牌留在本页，灰蓝牌会换页。
 * </p>
 */
public final class ForeignDetail {

    private static final int SCROLLBAR = 7;
    private static final int SCROLL_COLUMN = SCROLLBAR + 1;
    private static final int WHEEL_STEP = 25;
    private static final int BULLET_INDENT = 10;

    private final DetailHead head = new DetailHead();

    private ForeignMeta meta;

    @Nullable
    private ItemFilter filter;

    private int scroll;
    private int contentHeight;
    private boolean large;
    private boolean showLevels;

    private final List<HitBox> hits = new ArrayList<>();

    /** 描述看哪一级，跨附魔保持；MAX_VALUE 是跟着各自的最高级。和本模组那页各存各的 */
    private static int viewLevel = Integer.MAX_VALUE;

    private String layoutKey;
    private List<DescriptionText.Line> layoutCache = Collections.emptyList();
    private long descLitAt = Long.MIN_VALUE / 4;

    private int x;
    private int y;
    private int width;
    private int height;

    public void setFilter(@Nullable ItemFilter filter) {
        this.filter = filter;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setMeta(@Nullable ForeignMeta meta) {
        this.meta = meta;
        this.scroll = 0;
        if (meta == null) {
            head.clear();
            return;
        }
        int phase = UiTheme.phaseOf(meta);
        int groupColor = 0xFF000000 | meta.getGroupColor();
        Enchantment enchantment = meta.getEnchantment();
        List<DetailHead.Tag> tags = new ArrayList<>(4);
        tags.add(new DetailHead.Tag(meta.getGroupName(), groupColor));
        tags.add(DetailHead.Tag.rarity(meta.getRarityName(), phase));
        if (enchantment.isTreasureOnly()) {
            tags.add(new DetailHead.Tag(Component.translatable("carianstyle.codex.badge.treasure"), UiTheme.GOLD));
        }
        if (enchantment.isCurse()) {
            tags.add(new DetailHead.Tag(Component.translatable("carianstyle.codex.badge.curse"), UiTheme.DANGER));
        }
        head.setEntry(meta.getKey(), meta.getDisplayName(), meta.getKey(), tags, phase, groupColor);
        // 外部附魔没有「公式」档；描述里没有占位符时点等级什么都不变，不摆等级牌
        showLevels = meta.hasFormatArgs() && meta.getMaxLevel() > 1;
        head.setLevels(meta.getMaxLevel(), false, showLevels);
        head.setLevel(shownLevel(), false);
    }

    /** 没有等级牌时指针停在最高级，不跟着别的附魔上选的那级走 */
    private int shownLevel() {
        if (!showLevels) {
            return Math.max(1, meta.getMaxLevel());
        }
        return Math.max(1, Math.min(viewLevel, meta.getMaxLevel()));
    }

    public void render(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
        hits.clear();
        int vpW = width - SCROLL_COLUMN;
        g.fill(x + width - SCROLLBAR, y, x + width, y + height, 0xEB0A0F26);

        if (meta == null) {
            UiTheme.glass(g, x, y, vpW, height);
            Component hint = Component.translatable("carianstyle.codex.detail.empty");
            g.drawString(font, hint, x + (vpW - font.width(hint)) / 2, y + (height - 8) / 2, UiTheme.OFF, false);
            contentHeight = 0;
            return;
        }

        boolean inView = UiTheme.hit(mouseX, mouseY, x, y, vpW, height);
        int hx = inView ? mouseX : Integer.MIN_VALUE;
        int hy = inView ? mouseY : Integer.MIN_VALUE;
        int padL = large ? 14 : 10;
        int padR = large ? 14 : 8;
        int left = x + padL;
        int innerWidth = vpW - padL - padR;
        int top = y - scroll;

        g.enableScissor(x, y, x + vpW, y + height);

        head.setLevel(shownLevel(), false);
        int bodyTop = top + head.render(g, font, x, top, vpW, hx, hy, inView);

        int glassBottom = Math.max(top + contentHeight, y + height);
        UiTheme.glass(g, x, bodyTop, vpW, glassBottom - bodyTop);

        int cursorY = DetailSections.fitCard(g, font, filter, meta.getEnchantment(), left, bodyTop, innerWidth);
        cursorY = renderDescription(g, font, left, cursorY, innerWidth);
        cursorY = renderBasics(g, font, left, cursorY, innerWidth);
        cursorY = DetailSections.itemTypes(g, font, ItemCategoryNamer.describe(meta.getApplicableItems()),
                meta.getApplicableItems(), left, cursorY + DetailSections.SECTION_GAP, innerWidth);
        cursorY = renderAcquisition(g, font, left, cursorY, innerWidth);
        cursorY = renderEnchantingData(g, font, left, cursorY, innerWidth);
        cursorY = renderConflicts(g, font, left, cursorY, innerWidth, hx, hy);
        cursorY += large ? 12 : 10;

        g.disableScissor();

        contentHeight = cursorY - top;
        int maxScroll = Math.max(0, contentHeight - height);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        boolean barHot = UiTheme.hit(mouseX, mouseY, x + width - SCROLLBAR, y, SCROLLBAR, height);
        UiTheme.scrollbar(g, x + width - SCROLLBAR, y, height, contentHeight, scroll, barHot);
    }

    /**
     * 描述和本模组走同一套排版（；分条、：分层、括号压暗、数值高亮），不少模组的写法和本模组一样。
     * 作者没写描述时给两行灰字，留白会被当成百科没加载出来。
     */
    private int renderDescription(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        cursorY += 7;
        if (!meta.hasDescription()) {
            // 第二行嵌了一整条语言键，很容易超宽，两行都要折
            cursorY = DetailSections.note(g, font, Component.translatable("carianstyle.codex.foreign.no_description"),
                    left, cursorY + 1, innerWidth, UiTheme.OFF);
            return DetailSections.note(g, font, Component.translatable("carianstyle.codex.foreign.desc_hint",
                    meta.getPreferredDescKey()), left, cursorY + 3, innerWidth, UiTheme.OFF) + 2;
        }

        int level = shownLevel();
        String key = meta.getKey() + '#' + level + '#' + innerWidth;
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            // 外部附魔没有 [附魔等级] 这套占位符，一律按公式档排；%1$s 已经在 getDescription(level) 里填好
            layoutCache = DescriptionText.layout(meta.getDescription(level).getString(),
                    DescriptionText.FORMULA, font, innerWidth - BULLET_INDENT, BULLET_INDENT);
        }
        long lit = UiTheme.now() - descLitAt;
        float p = lit >= 0 && lit < 400 ? UiTheme.steps(lit, 400, 6) : 1f;

        for (DescriptionText.Line line : layoutCache) {
            if (line.isBlockStart()) {
                UiTheme.sprite(g, "bul_star", left + 1, cursorY + 2, 5, 5);
            }
            int tx = left + BULLET_INDENT + (line.getIndent() == 0 ? 0 : BULLET_INDENT);
            FormattedCharSequence seq = line.getText().getVisualOrderText();
            if (p < 1f) {
                seq = litLine(g, font, seq, tx, cursorY + 1, p);
            }
            g.drawString(font, seq, tx, cursorY + 1, UiTheme.TEXT, false);
            cursorY += DetailSections.LINE;
        }

        // 原版附魔的描述是本模组补的，写明出处，不冒充人家自带的
        if (meta.isDescriptionSupplied()) {
            cursorY = DetailSections.note(g, font, Component.translatable("carianstyle.codex.foreign.desc_by_us"),
                    left + BULLET_INDENT, cursorY + 3, innerWidth - BULLET_INDENT, UiTheme.OFF) + 2;
        }
        return cursorY;
    }

    /** 切等级时数值那几个字从白退回本色，底下一层青光跟着淡掉；和本模组详情同一个效果 */
    @Nonnull
    private static FormattedCharSequence litLine(@Nonnull GuiGraphics g, @Nonnull Font font,
                                                 @Nonnull FormattedCharSequence seq, int x, int y, float p) {
        int bg = UiTheme.withAlpha(0xFF7FE3FF, 0.35f * (1f - p));
        int[] cursor = {x};
        seq.accept((index, style, cp) -> {
            int w = font.width(FormattedCharSequence.codepoint(cp, style));
            if (DescriptionText.isValueColor(style.getColor())) {
                g.fill(cursor[0], y, cursor[0] + w, y + 8, bg);
            }
            cursor[0] += w;
            return true;
        });
        return sink -> seq.accept((index, style, cp) -> {
            TextColor c = style.getColor();
            if (DescriptionText.isValueColor(c)) {
                Style lit = style.withColor(TextColor.fromRgb(UiTheme.mix(0xFFFFFFFF, 0xFF000000 | c.getValue(), p) & 0xFFFFFF));
                return sink.accept(index, lit, cp);
            }
            return sink.accept(index, style, cp);
        });
    }

    private int renderBasics(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.basics"),
                left, cursorY, innerWidth);

        // 「可交易」「可被发现」回答的是怎么拿到，放在获取方式里，不在这里重复
        String levels = meta.getMinLevel() == meta.getMaxLevel()
                ? UiTheme.roman(meta.getMaxLevel())
                : UiTheme.roman(meta.getMinLevel()) + " ~ " + UiTheme.roman(meta.getMaxLevel());
        cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.max_level"),
                levels + " (" + meta.getMaxLevel() + ")", left, cursorY, innerWidth, UiTheme.MOON);

        // 槽位靠反射读，读不到就整行不画，纯展示信息
        List<EquipmentSlot> slots = EnchantmentSlots.of(meta.getEnchantment());
        if (!slots.isEmpty()) {
            StringBuilder slotText = new StringBuilder();
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) {
                    slotText.append("  ");
                }
                slotText.append(Component.translatable("carianstyle.codex.slot." + slots.get(i).getName()).getString());
            }
            cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.slots"),
                    slotText.toString(), left, cursorY, innerWidth, UiTheme.MOON);
        }
        return cursorY;
    }

    /**
     * 外部附魔没有本模组那套配置开关，获取途径只看原版附魔台和村民交易实际读的四个标志：
     * isDiscoverable、isTreasureOnly、isTradeable、isCurse。
     */
    private int renderAcquisition(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.acquisition"),
                left, cursorY, innerWidth);

        Enchantment enchantment = meta.getEnchantment();
        if (!enchantment.isDiscoverable()) {
            // 排最前：抽不到的话下面的途径都无从谈起
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.not_discoverable"),
                    left, cursorY, innerWidth, UiTheme.WARN);
        } else if (enchantment.isTreasureOnly()) {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.treasure"),
                    left, cursorY, innerWidth, UiTheme.WARN);
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.loot"),
                    left, cursorY, innerWidth, UiTheme.TEXT);
        } else {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.table"),
                    left, cursorY, innerWidth, UiTheme.ON);
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.loot"),
                    left, cursorY, innerWidth, UiTheme.TEXT);
        }

        boolean tradeable = enchantment.isTradeable();
        cursorY = DetailSections.bullet(g, font, Component.translatable(tradeable
                        ? "carianstyle.codex.acquire.villager"
                        : "carianstyle.codex.acquire.no_villager"),
                left, cursorY, innerWidth, tradeable ? UiTheme.TEXT : UiTheme.OFF);

        if (enchantment.isCurse()) {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.curse"),
                    left, cursorY, innerWidth, UiTheme.DANGER);
        }
        return cursorY;
    }

    private int renderEnchantingData(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.enchanting"),
                left, cursorY, innerWidth);
        cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.weight"),
                String.valueOf(meta.getWeight()), left, cursorY, innerWidth, UiTheme.MOON);

        Enchantment enchantment = meta.getEnchantment();
        int minCost = enchantment.getMinCost(enchantment.getMinLevel());
        int maxCost = enchantment.getMinCost(enchantment.getMaxLevel());
        return DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.cost"),
                minCost == maxCost ? String.valueOf(minCost) : minCost + " ~ " + maxCost,
                left, cursorY, innerWidth, UiTheme.MOON);
    }

    private int renderConflicts(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY,
                                int innerWidth, int mouseX, int mouseY) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.conflicts"),
                left, cursorY, innerWidth);

        List<String> foreign = meta.getForeignConflicts();
        List<String> carian = meta.getCarianConflicts();
        if (foreign.isEmpty() && carian.isEmpty()) {
            return DetailSections.note(g, font, Component.translatable("carianstyle.codex.conflicts.none"),
                    left, cursorY, innerWidth, UiTheme.OFF);
        }

        // 和「附魔百科」页同一条规则：原版最前，其余按模组显示名，同模组内按附魔名
        List<String> sortedForeign = new ArrayList<>(foreign);
        sortedForeign.sort((a, b) -> {
            ForeignMeta ma = ForeignCodex.get(a);
            ForeignMeta mb = ForeignCodex.get(b);
            if (ma == null || mb == null) {
                return 0;
            }
            int byMod = ModNames.compare(ma.getRegistryName().getNamespace(), mb.getRegistryName().getNamespace());
            if (byMod != 0) {
                return byMod;
            }
            return ma.getDisplayName().getString().compareTo(mb.getDisplayName().getString());
        });
        List<String> sortedCarian = new ArrayList<>(carian);
        sortedCarian.sort((a, b) -> {
            EnchantmentMeta ma = EnchantmentCodex.get(a);
            EnchantmentMeta mb = EnchantmentCodex.get(b);
            if (ma == null || mb == null) {
                return 0;
            }
            return ma.getDisplayName().getString().compareTo(mb.getDisplayName().getString());
        });

        int[] pos = {left, cursorY};
        boolean drewAny = false;
        for (String other : sortedForeign) {
            ForeignMeta target = ForeignCodex.get(other);
            if (target != null) {
                placeChip(g, font, target.getDisplayName(), other, false, pos, left, innerWidth, mouseX, mouseY);
                drewAny = true;
            }
        }
        for (String id : sortedCarian) {
            EnchantmentMeta target = EnchantmentCodex.get(id);
            if (target != null) {
                placeChip(g, font, target.getDisplayName(), id, true, pos, left, innerWidth, mouseX, mouseY);
                drewAny = true;
            }
        }

        if (!drewAny) {
            return cursorY;
        }
        return DetailSections.note(g, font, Component.translatable("carianstyle.codex.conflicts.hint"),
                left, pos[1] + DetailSections.CHIP_H + 5, innerWidth, UiTheme.OFF);
    }

    private void placeChip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component label, @Nonnull String target,
                           boolean otherPage, int[] pos, int left, int innerWidth, int mouseX, int mouseY) {
        int w = DetailSections.chipWidth(font, label, innerWidth);
        if (pos[0] > left && pos[0] + w > left + innerWidth) {
            pos[0] = left;
            pos[1] += DetailSections.CHIP_H + DetailSections.CHIP_GAP;
        }
        boolean hovered = UiTheme.hit(mouseX, mouseY, pos[0], pos[1], w, DetailSections.CHIP_H);
        DetailSections.chip(g, font, label, pos[0], pos[1], w, hovered, otherPage);
        hits.add(new HitBox(pos[0], pos[1], w, DetailSections.CHIP_H, target));
        pos[0] += w + DetailSections.CHIP_GAP;
    }

    /** 点等级牌切描述等级（返回 null）；点冲突小牌返回要跳去的键 */
    @Nullable
    public String mouseClicked(double mouseX, double mouseY) {
        if (meta == null || !UiTheme.hit(mouseX, mouseY, x, y, width - SCROLL_COLUMN, height)) {
            return null;
        }
        int level = head.mouseClicked(mouseX, mouseY);
        if (level != DetailHead.NO_LEVEL) {
            viewLevel = level;
            head.setLevel(level, true);
            descLitAt = UiTheme.now();
            return null;
        }
        for (HitBox box : hits) {
            if (UiTheme.hit(mouseX, mouseY, box.x, box.y, box.width, box.height)) {
                return box.targetKey;
            }
        }
        return null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        scroll -= (int) Math.round(delta * WHEEL_STEP);
        int max = Math.max(0, contentHeight - height);
        scroll = Math.max(0, Math.min(scroll, max));
        return true;
    }

    private static final class HitBox {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String targetKey;

        HitBox(int x, int y, int width, int height, String targetKey) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.targetKey = targetKey;
        }
    }

    /** 大窗口：月亮 80、卷首和正文内边距 14 */
    public void setLarge(boolean large) {
        this.large = large;
        head.setLarge(large);
    }

    public void playIntro(long handoff) {
        head.playIntro(handoff);
    }

    public void skipIntro() {
        head.skipIntro();
    }

    public int introFrame(long t) {
        return head.frameAt(t);
    }

    public int moonColor() {
        return head.catColor();
    }

    public void playSelect() {
        head.playSelect();
    }

    public void pulse() {
        head.pulse();
    }
}
