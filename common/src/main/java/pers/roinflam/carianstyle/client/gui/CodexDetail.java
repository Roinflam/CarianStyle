package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.LevelDecay;
import pers.roinflam.carianstyle.client.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.client.codex.ModNames;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 百科右页：单个附魔的详情。上面是透明的卷首（{@link DetailHead}），下面从卷首下沿往下铺玻璃画正文，
 * 两者一起滚。
 * <p>
 * 内容高度不固定，滚动条要的总高取上一帧画完时累加出来的值；两帧之间内容不变，看不出差别，
 * 也省掉先量一遍再画一遍。冲突小牌和等级牌的点击区域都在画的时候记下，点击时直接查表，
 * 不会出现画的位置和点的位置对不上。
 * </p>
 */
public final class CodexDetail {

    private static final int SCROLLBAR = 7;
    /** 视口右边让出滚动条 7 像素 + 中间 1 像素缝 */
    private static final int SCROLL_COLUMN = SCROLLBAR + 1;
    private static final int WHEEL_STEP = 25;
    private static final int BULLET_INDENT = 10;

    private final DetailHead head = new DetailHead();

    private EnchantmentMeta meta;

    @Nullable
    private ItemFilter filter;

    private int scroll;
    private int contentHeight;
    private boolean large;

    private final List<HitBox> conflictHits = new ArrayList<>();

    /**
     * 描述看哪一级，跨附魔保持。首次打开落在最高级（多数人想知道练满了什么样），
     * 但看公式的人（平衡数值的服主、写攻略的）选了公式之后，翻十个附魔也不该点十次。
     * 收拢只在读取时做（{@link #clampViewLevel}），字段本身一直存玩家的原始选择：
     * 早先在 setMeta 里收拢，先看一个最高 III 级的附魔，之后再看最高 V 级的也只显示到 III。
     */
    private static int viewLevel = Integer.MAX_VALUE;

    private static int clampViewLevel(@Nonnull EnchantmentMeta meta) {
        // 只有一级时公式档没有意义，「持续[附魔等级]×20秒」不如直接写「持续20秒」
        if (meta.getMaxLevel() <= 1) {
            return 1;
        }
        if (viewLevel == DescriptionText.FORMULA) {
            return DescriptionText.FORMULA;
        }
        return Math.max(1, Math.min(viewLevel, meta.getMaxLevel()));
    }

    /** 描述排版缓存，键是 附魔 id + 等级 + 宽度：每帧重排要建上百个短命对象，排一次就够 */
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

    public void setMeta(@Nullable EnchantmentMeta meta) {
        this.meta = meta;
        this.scroll = 0;
        if (meta == null) {
            head.clear();
            return;
        }
        int phase = UiTheme.phaseOf(meta);
        List<DetailHead.Tag> tags = new ArrayList<>(4);
        tags.add(new DetailHead.Tag(meta.getTheme().getDisplayName(), meta.getTheme().getAccentColor()));
        tags.add(DetailHead.Tag.rarity(meta.getRarityName(), phase));
        if (meta.isTreasure()) {
            tags.add(new DetailHead.Tag(Component.translatable("carianstyle.codex.badge.treasure"), UiTheme.GOLD));
        }
        if (meta.isCurse()) {
            tags.add(new DetailHead.Tag(Component.translatable("carianstyle.codex.badge.curse"), UiTheme.DANGER));
        }
        head.setEntry(meta.getId(), meta.getDisplayName(), meta.getId(), tags, phase,
                meta.getTheme().getAccentColor());
        head.setLevels(meta.getMaxLevel(), true, meta.getMaxLevel() > 1);
        head.setLevel(clampViewLevel(meta), false);
    }

    public void render(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
        conflictHits.clear();
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

        head.setLevel(clampViewLevel(meta), false);
        int bodyTop = top + head.render(g, font, x, top, vpW, hx, hy, inView);

        // 玻璃的渐变按整段正文算，不按视口，滚动时颜色跟着内容走
        int glassBottom = Math.max(top + contentHeight, y + height);
        UiTheme.glass(g, x, bodyTop, vpW, glassBottom - bodyTop);

        int cursorY = DetailSections.fitCard(g, font, filter, meta.getEnchantment(), left, bodyTop, innerWidth);
        cursorY = renderDescription(g, font, left, cursorY, innerWidth);
        cursorY = renderBasics(g, font, left, cursorY, innerWidth);
        cursorY = renderItems(g, font, left, cursorY, innerWidth);
        cursorY = renderAcquisition(g, font, left, cursorY, innerWidth);
        cursorY = renderEnchantingData(g, font, left, cursorY, innerWidth);
        cursorY = renderConflicts(g, font, left, cursorY, innerWidth, hx, hy);
        cursorY = renderValues(g, font, left, cursorY, innerWidth);
        cursorY += large ? 12 : 10;

        g.disableScissor();

        contentHeight = cursorY - top;
        // 内容变矮时（结论卡片消失或换成短的）把滚动收回来，不然底下空一截、游标画出框外
        int maxScroll = Math.max(0, contentHeight - height);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        boolean barHot = UiTheme.hit(mouseX, mouseY, x + width - SCROLLBAR, y, SCROLLBAR, height);
        UiTheme.scrollbar(g, x + width - SCROLLBAR, y, height, contentHeight, scroll, barHot);
    }

    /**
     * 描述：按标点结构逐行排好，断行由 DescriptionText 自己做（原版换行器对中文按宽度硬切，
     * 会把「180秒」劈成「18」「0秒」）。每条效果的首行前面一颗星。
     */
    private int renderDescription(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        int level = clampViewLevel(meta);
        String key = meta.getId() + '#' + level + '#' + innerWidth;
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            layoutCache = DescriptionText.layout(meta.getDescription().getString(), level,
                    font, innerWidth - BULLET_INDENT, BULLET_INDENT);
        }
        long lit = UiTheme.now() - descLitAt;
        float p = lit >= 0 && lit < 400 ? UiTheme.steps(lit, 400, 6) : 1f;

        cursorY += 7;
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
        return cursorY;
    }

    /** 切等级时数值那几个字从白色退回本色，底下垫一层青光跟着淡掉 */
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
        cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.max_level"),
                UiTheme.roman(meta.getMaxLevel()) + " (" + meta.getMaxLevel() + ")",
                left, cursorY, innerWidth, UiTheme.MOON);

        StringBuilder slotText = new StringBuilder();
        List<EquipmentSlot> slots = meta.getSlots();
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) {
                slotText.append("  ");
            }
            slotText.append(Component.translatable("carianstyle.codex.slot." + slots.get(i).getName()).getString());
        }
        if (slotText.length() == 0) {
            slotText.append("-");
        }
        cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.slots"),
                slotText.toString(), left, cursorY, innerWidth, UiTheme.MOON);

        // 内部实现分类：列表不按它分组了，但它决定附魔走哪条触发路径，读代码、配怪物触发开关的人用得着
        return DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.category"),
                meta.getGroup().getDisplayName().getString(), left, cursorY, innerWidth, UiTheme.MOON);
    }

    private int renderItems(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        // 种类名取注解的 type / customType；没登记翻译就显示原始键，至少和注解里写的对得上
        String key = "carianstyle.codex.category." + meta.getCategoryKey();
        Component category = I18n.exists(key) ? meta.getCategoryName() : Component.literal(meta.getCategoryKey());
        return DetailSections.itemTypes(g, font, category, meta.getApplicableItems(),
                left, cursorY + DetailSections.SECTION_GAP, innerWidth);
    }

    /**
     * 获取方式：全部现读配置。宝藏与否、能不能找村民换、是不是被禁用，都能被服务器配置改掉，
     * 同一个附魔在两台服务器上的获取途径可能完全不同，写死一句「可从附魔台获得」就会出错。
     */
    private int renderAcquisition(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.acquisition"),
                left, cursorY, innerWidth);

        Enchantment enchantment = meta.getEnchantment();
        if (enchantment instanceof EnchantmentBase base && base.isDisabled()) {
            return DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.disabled"),
                    left, cursorY, innerWidth, UiTheme.DANGER);
        }

        if (enchantment.isTreasureOnly()) {
            // 原因接在同一条后面：单开一条会被读成并列的另一种获取途径
            Component reason = reasonForTreasure();
            Component line = reason == null
                    ? Component.translatable("carianstyle.codex.acquire.treasure")
                    : Component.translatable("carianstyle.codex.acquire.treasure").copy().append(" ").append(reason);
            cursorY = DetailSections.bullet(g, font, line, left, cursorY, innerWidth, UiTheme.WARN);
        } else {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.table"),
                    left, cursorY, innerWidth, UiTheme.ON);
        }

        cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.loot"),
                left, cursorY, innerWidth, UiTheme.TEXT);

        if (enchantment.isTradeable() && ConfigLoader.allowVillagerBookTrade) {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.villager"),
                    left, cursorY, innerWidth, UiTheme.TEXT);
        } else {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.acquire.no_villager"),
                    left, cursorY, innerWidth, UiTheme.OFF);
        }
        return cursorY;
    }

    @Nullable
    private Component reasonForTreasure() {
        switch (meta.getRarity()) {
            case VERY_RARE:
                if (ConfigLoader.isTreasureVeryRaryEnchantment) {
                    return Component.translatable("carianstyle.codex.acquire.treasure_by_config",
                            Component.translatable("carianstyle.codex.rarity.very_rare"));
                }
                break;
            case RARE:
                if (ConfigLoader.isTreasureRaryEnchantment) {
                    return Component.translatable("carianstyle.codex.acquire.treasure_by_config",
                            Component.translatable("carianstyle.codex.rarity.rare"));
                }
                break;
            case UNCOMMON:
                if (ConfigLoader.isTreasureUncommonEnchantment) {
                    return Component.translatable("carianstyle.codex.acquire.treasure_by_config",
                            Component.translatable("carianstyle.codex.rarity.uncommon"));
                }
                break;
            default:
                break;
        }
        // 配置没把这一档变成宝藏，那就是附魔自己声明的
        return Component.translatable("carianstyle.codex.acquire.treasure_by_design");
    }

    /**
     * 附魔台数据。权重直接读附魔实例：EnchantmentBase 覆写了 getRarity()，
     * useVanillaRarityWeight 关掉时返回最高权重档，读到的就是附魔台实际抽取用的值；
     * getMinCost 里也已经乘过 enchantingDifficulty。界面不再复刻一遍配置判断，免得以后改两处。
     */
    private int renderEnchantingData(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.enchanting"),
                left, cursorY, innerWidth);

        Enchantment enchantment = meta.getEnchantment();
        cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.weight"),
                String.valueOf(enchantment.getRarity().getWeight()), left, cursorY, innerWidth,
                ConfigLoader.useVanillaRarityWeight ? UiTheme.MOON : UiTheme.WARN);
        cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.weight_mode"),
                Component.translatable(ConfigLoader.useVanillaRarityWeight
                        ? "carianstyle.codex.weight.by_rarity"
                        : "carianstyle.codex.weight.flat").getString(),
                left, cursorY, innerWidth, UiTheme.MOON);

        int minCost = enchantment.getMinCost(1);
        int maxCost = enchantment.getMinCost(meta.getMaxLevel());
        cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.cost"),
                minCost + " ~ " + maxCost, left, cursorY, innerWidth, UiTheme.MOON);

        if (ConfigLoader.enchantingDifficulty != 1.0D) {
            cursorY = DetailSections.kv(g, font, Component.translatable("carianstyle.codex.field.difficulty"),
                    "x" + trimDouble(ConfigLoader.enchantingDifficulty), left, cursorY, innerWidth, UiTheme.WARN);
        }

        if (ConfigLoader.levelLimit) {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.field.level_limit_on"),
                    left, cursorY, innerWidth, UiTheme.WARN);
        }

        if (LevelDecay.isEnabled()) {
            int threshold = LevelDecay.threshold(enchantment);
            StringBuilder samples = new StringBuilder();
            // 取满级的 2 / 5 / 10 / 20 倍，上限不同的附魔都能看到同一段曲线
            for (int multiple : new int[]{2, 5, 10, 20}) {
                int sample = threshold * multiple;
                if (samples.length() > 0) {
                    samples.append(", ");
                }
                samples.append(sample).append(" → ").append(LevelDecay.apply(enchantment, sample));
            }
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.field.level_decay_on",
                    threshold, samples.toString()), left, cursorY, innerWidth, UiTheme.WARN);
        }
        return cursorY;
    }

    private int renderConflicts(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY,
                                int innerWidth, int mouseX, int mouseY) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.conflicts"),
                left, cursorY, innerWidth);

        List<String> conflicts = meta.getConflictIds();
        List<Enchantment> foreign = meta.getForeignConflicts();
        if (conflicts.isEmpty() && foreign.isEmpty()) {
            return DetailSections.note(g, font, Component.translatable("carianstyle.codex.conflicts.none"),
                    left, cursorY, innerWidth, UiTheme.OFF);
        }

        // 整类互斥折成一句：战技之间两两互斥，逐个列出来是二十几块牌子，把真正特殊的那几条冲突淹掉了。
        // 下面的牌子只留跨分类的冲突，那才是要记住的
        Set<String> sameGroup = new HashSet<>();
        if (meta.conflictsWithWholeGroup()) {
            cursorY = DetailSections.bullet(g, font, Component.translatable("carianstyle.codex.conflicts.whole_group",
                            meta.getGroup().getDisplayName(), meta.getGroupMemberCount() - 1),
                    left, cursorY, innerWidth, UiTheme.DANGER);
            for (String id : conflicts) {
                EnchantmentMeta other = EnchantmentCodex.get(id);
                if (other != null && other.getGroup() == meta.getGroup()) {
                    sameGroup.add(id);
                }
            }
            cursorY += 3;
        }

        int[] pos = {left, cursorY};
        boolean drewAny = false;
        for (String conflictId : conflicts) {
            if (sameGroup.contains(conflictId)) {
                continue;
            }
            EnchantmentMeta other = EnchantmentCodex.get(conflictId);
            if (other != null) {
                placeChip(g, font, other.getDisplayName(), conflictId, false, pos, left, innerWidth, mouseX, mouseY);
                drewAny = true;
            }
        }

        // 原版和别的模组的附魔收在「其他附魔」页，同样能点；按所属模组排（原版在前），
        // 不排就是注册表遍历顺序，对玩家毫无意义
        List<Enchantment> sortedForeign = new ArrayList<>(foreign);
        sortedForeign.sort((a, b) -> {
            ResourceLocation ka = BuiltInRegistries.ENCHANTMENT.getKey(a);
            ResourceLocation kb = BuiltInRegistries.ENCHANTMENT.getKey(b);
            if (ka == null || kb == null) {
                return 0;
            }
            int byMod = ModNames.compare(ka.getNamespace(), kb.getNamespace());
            if (byMod != 0) {
                return byMod;
            }
            return Component.translatable(a.getDescriptionId()).getString()
                    .compareTo(Component.translatable(b.getDescriptionId()).getString());
        });
        for (Enchantment other : sortedForeign) {
            ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(other);
            if (id != null) {
                placeChip(g, font, Component.translatable(other.getDescriptionId()), id.toString(), true,
                        pos, left, innerWidth, mouseX, mouseY);
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
                           boolean foreign, int[] pos, int left, int innerWidth, int mouseX, int mouseY) {
        int w = DetailSections.chipWidth(font, label, innerWidth);
        if (pos[0] > left && pos[0] + w > left + innerWidth) {
            pos[0] = left;
            pos[1] += DetailSections.CHIP_H + DetailSections.CHIP_GAP;
        }
        boolean hovered = UiTheme.hit(mouseX, mouseY, pos[0], pos[1], w, DetailSections.CHIP_H);
        DetailSections.chip(g, font, label, pos[0], pos[1], w, hovered, foreign);
        conflictHits.add(new HitBox(pos[0], pos[1], w, DetailSections.CHIP_H, target));
        pos[0] += w + DetailSections.CHIP_GAP;
    }

    /** 可调数值：每项两行，上行名字和当前生效值，下行默认值和允许范围；被改过的整项标成金色 */
    private int renderValues(@Nonnull GuiGraphics g, @Nonnull Font font, int left, int cursorY, int innerWidth) {
        cursorY = DetailSections.section(g, font, Component.translatable("carianstyle.codex.section.values"),
                left, cursorY, innerWidth);

        List<EnchantmentValues.Handle> handles = EnchantmentValues.handlesOf(meta.getId());
        if (handles.isEmpty()) {
            return DetailSections.note(g, font, Component.translatable("carianstyle.codex.values.none"),
                    left, cursorY, innerWidth, UiTheme.OFF);
        }

        // 表头写明右边那列是当前生效值，不是默认值也不是范围
        Component current = Component.translatable("carianstyle.codex.values.header_current");
        int currentW = font.width(current);
        UiTheme.trimmed(g, font, Component.translatable("carianstyle.codex.values.header_name"),
                left, cursorY, innerWidth - currentW - 8, UiTheme.TEXT_DIM);
        g.drawString(font, current, left + innerWidth - currentW, cursorY, UiTheme.TEXT_DIM, false);
        UiTheme.divider(g, left, cursorY + 10, innerWidth);
        cursorY += 14;

        boolean anyOverridden = false;
        for (EnchantmentValues.Handle handle : handles) {
            boolean overridden = handle.isOverridden();
            anyOverridden |= overridden;
            String value = format(handle, handle.get());
            int valueW = font.width(value);
            int valueColor = overridden ? UiTheme.WARN : UiTheme.MOON;
            UiTheme.trimmed(g, font, valueName(handle), left, cursorY + 3, innerWidth - valueW - 8, UiTheme.TEXT);
            g.drawString(font, value, left + innerWidth - valueW, cursorY + 3, valueColor, false);
            // 默认值和范围；改过的一起标出来，好看出改了多少
            String detail = Component.translatable("carianstyle.codex.values.detail",
                    format(handle, handle.getDefaultValue()),
                    format(handle, handle.getMin()) + " ~ " + format(handle, handle.getMax())).getString();
            UiTheme.trimmed(g, font, Component.literal(detail), left + 8, cursorY + 13, innerWidth - 8,
                    overridden ? UiTheme.WARN : UiTheme.OFF);
            cursorY += 24;
        }

        if (anyOverridden) {
            cursorY += 4;
            for (FormattedCharSequence line : font.split(
                    Component.translatable("carianstyle.codex.values.desc_warning"), Math.max(1, innerWidth))) {
                g.drawString(font, line, left, cursorY + 1, UiTheme.WARN, false);
                cursorY += DetailSections.LINE;
            }
        }
        return cursorY;
    }

    /** 数值名查 carianstyle.value.<附魔id>.<键>；没登记翻译就显示原始键，和配置文件里要改的那行对得上 */
    @Nonnull
    private Component valueName(@Nonnull EnchantmentValues.Handle handle) {
        String key = "carianstyle.value." + handle.getEnchantmentId() + "." + handle.getKey();
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(handle.getKey());
    }

    @Nonnull
    private static String format(@Nonnull EnchantmentValues.Handle handle, double value) {
        return handle.isIntegral() ? String.valueOf(Math.round(value)) : trimDouble(value);
    }

    @Nonnull
    private static String trimDouble(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** 点等级牌切描述等级（返回 null）；点冲突小牌返回要跳去的 id */
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
        for (HitBox box : conflictHits) {
            if (UiTheme.hit(mouseX, mouseY, box.x, box.y, box.width, box.height)) {
                return box.targetId;
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
        private final String targetId;

        HitBox(int x, int y, int width, int height, String targetId) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.targetId = targetId;
        }
    }

    /** 大窗口：月亮 80、卷首和正文内边距 14 */
    public void setLarge(boolean large) {
        this.large = large;
        head.setLarge(large);
    }

    public void playIntro(long handoff, long waneAt) {
        head.playIntro(handoff, waneAt);
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
