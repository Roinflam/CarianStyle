package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.codex.EnchantmentSlots;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.codex.ForeignCodex;
import pers.roinflam.carianstyle.codex.ItemCategoryNamer;
import pers.roinflam.carianstyle.codex.ModNames;
import pers.roinflam.carianstyle.codex.ForeignMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 外部附魔（原版与其它模组）的详情面板。
 *
 * <h3>为什么单独写一个而不是复用 {@link CodexDetail}</h3>
 * <p>
 * 两者能展示的信息差得太远：本模组的详情有题材主题、可调数值、宝藏归因、
 * 整类互斥折叠；外部附魔这些一概没有，硬塞进同一个面板就会到处是
 * {@code if (isForeign)}，两种形态互相牵制，改一个要担心另一个。
 * </p>
 * <p>
 * 共用的部分已经抽到 {@link UiTheme}（区块标题、信息行、徽章）和
 * {@link DescriptionText}（描述排版）里，重复的只是几十行调用，值得。
 * </p>
 *
 * <h3>跳转</h3>
 * <p>
 * 冲突列表里既有外部附魔也有本模组附魔，两者都可点击：
 * 前者在本标签页内跳转，后者会切回「附魔百科」标签页。
 * 界面靠键里有没有冒号来区分，见 {@code CodexEntry.getKey()}。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class ForeignDetail {

    /** 内容左右内边距 */
    private static final int PADDING = 10;

    /** 当前展示的附魔 */
    private ForeignMeta meta;

    /** 滚动偏移 */
    private int scroll;

    /** 上一帧算出的内容总高度 */
    private int contentHeight;

    /** 本帧记录的可点击徽章 */
    private final List<HitBox> hits = new ArrayList<>();

    /**
     * 描述查看等级，跨附魔保持。
     * <p>{@link Integer#MAX_VALUE} 表示「跟随各自的最高级」，与本模组那边同一套语义。</p>
     */
    private static int viewLevel = Integer.MAX_VALUE;

    /** 本帧记录的等级选择条命中区域，元素为 {x, y, w, h, level} */
    private final List<int[]> levelHits = new ArrayList<>();

    /** 描述排版缓存的键 */
    private String layoutKey;

    /** 描述排版缓存 */
    private List<DescriptionText.Line> layoutCache = Collections.emptyList();

    // 布局区域
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
    }

    /**
     * 切换展示的附魔。
     *
     * @param meta 元数据，可为 null（空态）
     */
    public void setMeta(@Nullable ForeignMeta meta) {
        this.meta = meta;
        this.scroll = 0;
    }

    /**
     * 绘制详情。
     *
     * @param g      绘制上下文
     * @param font   字体
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    public void render(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
        UiTheme.panel(g, x, y, width, height, UiTheme.PANEL);
        UiTheme.corners(g, x, y, width, height, 8, UiTheme.tint());
        hits.clear();
        levelHits.clear();

        if (meta == null) {
            Component hint = Component.translatable("carianstyle.codex.detail.empty");
            int textWidth = font.width(hint);
            g.drawString(font, hint, x + (width - textWidth) / 2, y + height / 2 - 4,
                    UiTheme.TEXT_DIM, false);
            contentHeight = 0;
            return;
        }

        int innerWidth = width - PADDING * 2 - 6;
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        int cursorY = y + PADDING - scroll;
        cursorY = renderTitle(g, font, cursorY, innerWidth);
        cursorY = renderDescription(g, font, cursorY, innerWidth);
        cursorY = renderBasics(g, font, cursorY, innerWidth);
        cursorY = renderItems(g, font, cursorY, innerWidth);
        cursorY = renderAcquisition(g, font, cursorY, innerWidth);
        cursorY = renderEnchantingData(g, font, cursorY, innerWidth);
        cursorY = renderConflicts(g, font, cursorY, innerWidth, mouseX, mouseY);

        g.disableScissor();

        contentHeight = cursorY - (y + PADDING - scroll) + PADDING;
        UiTheme.scrollbar(g, x + width - 4, y + 1, height - 2, contentHeight, scroll);
    }

    /**
     * 标题区：名称 + 注册名 + 分组与稀有度徽章。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderTitle(@Nonnull GuiGraphics g, @Nonnull Font font, int cursorY, int innerWidth) {
        int left = x + PADDING;

        g.drawString(font, meta.getDisplayName(), left, cursorY, UiTheme.tint(), false);
        DetailSections.idChip(g, font, meta.getKey(), left, cursorY, innerWidth,
                left + font.width(meta.getDisplayName()) + 16);
        cursorY += font.lineHeight + 8;

        int badgeX = left;
        badgeX += UiTheme.badge(g, font, meta.getGroupName(), badgeX, cursorY,
                meta.getGroupColor(), false);
        badgeX += UiTheme.badge(g, font, meta.getRarityName(), badgeX, cursorY,
                meta.getRarityColor(), false);

        Enchantment enchantment = meta.getEnchantment();
        if (enchantment.isTreasureOnly()) {
            badgeX += UiTheme.badge(g, font, Component.translatable("carianstyle.codex.badge.treasure"),
                    badgeX, cursorY, UiTheme.WARN, false);
        }
        if (enchantment.isCurse()) {
            UiTheme.badge(g, font, Component.translatable("carianstyle.codex.badge.curse"),
                    badgeX, cursorY, UiTheme.DANGER, false);
        }
        cursorY += font.lineHeight + 12;

        UiTheme.divider(g, left, cursorY, innerWidth);
        return cursorY + 9;
    }

    /**
     * 描述区。
     * <p>
     * 与本模组的描述走同一套排版（{@code ；} 分条、{@code ：} 分层、括号压暗、数值高亮），
     * 因为不少模组的描述写法和本模组一样。作者没写描述时显示一行灰字说明——
     * 留白会让人以为是百科没加载出来。
     * </p>
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderDescription(@Nonnull GuiGraphics g, @Nonnull Font font,
                                  int cursorY, int innerWidth) {
        int left = x + PADDING;
        int bulletIndent = 9;

        if (!meta.hasDescription()) {
            // 两行都要换行：提示里嵌了一条完整的语言键，很容易超出面板宽度。
            // 原来直接 drawString，后半句被切在面板外，而且看不出是被截断了
            cursorY = UiTheme.wrapped(g, font,
                    Component.translatable("carianstyle.codex.foreign.no_description"),
                    left, cursorY, innerWidth, UiTheme.OFF);
            cursorY += 2;
            cursorY = UiTheme.wrapped(g, font,
                    Component.translatable("carianstyle.codex.foreign.desc_hint",
                            meta.getPreferredDescKey()),
                    left, cursorY, innerWidth, UiTheme.OFF);
            return cursorY + 8;
        }

        int level = clampViewLevel();

        // 只有描述里真的带占位符时才显示等级条。
        // 没有可变量的描述摆一排等级按钮，点了什么都不变，纯属噪音
        if (meta.hasFormatArgs() && meta.getMaxLevel() > 1) {
            int chipX = left;
            int chipH = font.lineHeight + 4;
            for (int lv = 1; lv <= meta.getMaxLevel(); lv++) {
                Component label = Component.literal(UiTheme.roman(lv));
                int chipW = font.width(label) + 8;
                if (chipX + chipW > left + innerWidth) {
                    chipX = left;
                    cursorY += chipH + 3;
                }
                boolean active = lv == level;
                g.fill(chipX, cursorY, chipX + chipW, cursorY + chipH,
                        active ? UiTheme.ACCENT_FILL : UiTheme.PANEL_ALT);
                UiTheme.border(g, chipX, cursorY, chipW, chipH,
                        active ? UiTheme.ACCENT : UiTheme.BORDER);
                g.drawString(font, label, chipX + 4, cursorY + 3,
                        active ? UiTheme.ACCENT : UiTheme.TEXT_DIM, false);
                levelHits.add(new int[]{chipX, cursorY, chipW, chipH, lv});
                chipX += chipW + 3;
            }
            cursorY += chipH + 7;
        }

        String key = meta.getKey() + '#' + level + '#' + innerWidth;
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            // 外部附魔没有 [附魔等级] 这套占位符约定，一律按公式档排版；
            // 它们的 %1$s 已经在 getDescription(level) 里填好了
            layoutCache = DescriptionText.layout(meta.getDescription(level).getString(),
                    DescriptionText.FORMULA, font, innerWidth - bulletIndent, bulletIndent);
        }
        for (DescriptionText.Line line : layoutCache) {
            int indent = bulletIndent + (line.getIndent() == 0 ? 0 : bulletIndent);
            if (line.isBlockStart()) {
                g.fill(left + 1, cursorY + 3, left + 4, cursorY + 6, UiTheme.ACCENT);
            }
            g.drawString(font, line.getText(), left + indent, cursorY, UiTheme.TEXT, false);
            cursorY += font.lineHeight + 2;
        }

        if (meta.isDescriptionSupplied()) {
            cursorY += 2;
            cursorY = UiTheme.wrapped(g, font,
                    Component.translatable("carianstyle.codex.foreign.desc_by_us"),
                    left + bulletIndent, cursorY, innerWidth - bulletIndent, UiTheme.OFF);
        }
        return cursorY + 8;
    }

    /**
     * 把查看等级收拢到当前附魔的合法范围。
     *
     * @return 收拢后的等级
     */
    private int clampViewLevel() {
        return Math.max(1, Math.min(viewLevel, meta.getMaxLevel()));
    }

    /**
     * 基础信息区：等级范围、权重、可交易 / 可发现。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderBasics(@Nonnull GuiGraphics g, @Nonnull Font font,
                             int cursorY, int innerWidth) {
        int left = x + PADDING;
        cursorY = UiTheme.sectionHeader(g, font,
                Component.translatable("carianstyle.codex.section.basics"), left, cursorY, innerWidth);

        // 「可交易」「可被发现」原来放在这里，但它们回答的是「怎么拿到」，
        // 已经挪进「获取方式」区块——同一个问题的答案不该散在两处
        String levels = meta.getMinLevel() == meta.getMaxLevel()
                ? UiTheme.roman(meta.getMaxLevel())
                : UiTheme.roman(meta.getMinLevel()) + " ~ " + UiTheme.roman(meta.getMaxLevel());
        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.max_level"),
                levels + " (" + meta.getMaxLevel() + ")", left, cursorY, innerWidth, UiTheme.TEXT);

        // 生效槽位靠反射读取，拿不到就整行略过——纯展示信息，缺了不影响任何玩法
        List<EquipmentSlot> slots = EnchantmentSlots.of(meta.getEnchantment());
        if (!slots.isEmpty()) {
            StringBuilder slotText = new StringBuilder();
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) {
                    slotText.append("  ");
                }
                slotText.append(Component.translatable(
                        "carianstyle.codex.slot." + slots.get(i).getName()).getString());
            }
            cursorY = UiTheme.keyValue(g, font,
                    Component.translatable("carianstyle.codex.field.slots"),
                    slotText.toString(), left, cursorY, innerWidth, UiTheme.TEXT);
        }

        return cursorY + 8;
    }

    /**
     * 适用物品种类区。
     * <p>
     * 绘制交给 {@link DetailSections#itemTypes}，与「附魔百科」页共用同一段代码。
     * 本方法只负责算出种类名——外部附魔没有注解，靠 {@link ItemCategoryNamer}
     * 从实测结果反推。
     * </p>
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderItems(@Nonnull GuiGraphics g, @Nonnull Font font,
                            int cursorY, int innerWidth) {
        return DetailSections.itemTypes(g, font,
                ItemCategoryNamer.describe(meta.getApplicableItems()),
                meta.getApplicableItems(), x + PADDING, cursorY, innerWidth);
    }

    /**
     * 获取方式区。
     *
     * <h3>能推出多少</h3>
     * <p>
     * 外部附魔没有本模组那套配置开关，但 {@code Enchantment} 本身就暴露了
     * 四个决定获取途径的标志：{@code isTreasureOnly}（能否从附魔台抽到）、
     * {@code isDiscoverable}（能否被随机抽中）、{@code isTradeable}（能否出现在交易里）、
     * {@code isCurse}（是不是诅咒）。这四个组合起来足够说清「怎么拿到」。
     * </p>
     * <p>
     * 这些都是原版附魔台与村民交易实际读取的字段，不是猜的。
     * </p>
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderAcquisition(@Nonnull GuiGraphics g, @Nonnull Font font,
                                  int cursorY, int innerWidth) {
        int left = x + PADDING;
        cursorY = UiTheme.sectionHeader(g, font,
                Component.translatable("carianstyle.codex.section.acquisition"),
                left, cursorY, innerWidth);

        Enchantment enchantment = meta.getEnchantment();

        if (!enchantment.isDiscoverable()) {
            // 这一条排最前：不可被发现意味着上面所有途径都无从谈起
            cursorY = bullet(g, font,
                    Component.translatable("carianstyle.codex.acquire.not_discoverable"),
                    left, cursorY, innerWidth, UiTheme.WARN);
        } else if (enchantment.isTreasureOnly()) {
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.treasure"),
                    left, cursorY, innerWidth, UiTheme.WARN);
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.loot"),
                    left, cursorY, innerWidth, UiTheme.TEXT);
        } else {
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.table"),
                    left, cursorY, innerWidth, UiTheme.ON);
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.loot"),
                    left, cursorY, innerWidth, UiTheme.TEXT);
        }

        cursorY = bullet(g, font, Component.translatable(enchantment.isTradeable()
                        ? "carianstyle.codex.acquire.villager"
                        : "carianstyle.codex.acquire.no_villager"),
                left, cursorY, innerWidth,
                enchantment.isTradeable() ? UiTheme.TEXT : UiTheme.OFF);

        if (enchantment.isCurse()) {
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.curse"),
                    left, cursorY, innerWidth, UiTheme.DANGER);
        }

        return cursorY + 8;
    }

    /**
     * 附魔台数据区：权重与等级需求。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderEnchantingData(@Nonnull GuiGraphics g, @Nonnull Font font,
                                     int cursorY, int innerWidth) {
        int left = x + PADDING;
        cursorY = UiTheme.sectionHeader(g, font,
                Component.translatable("carianstyle.codex.section.enchanting"),
                left, cursorY, innerWidth);

        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.weight"),
                String.valueOf(meta.getWeight()), left, cursorY, innerWidth, UiTheme.TEXT);

        Enchantment enchantment = meta.getEnchantment();
        int minCost = enchantment.getMinCost(enchantment.getMinLevel());
        int maxCost = enchantment.getMinCost(enchantment.getMaxLevel());
        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.cost"),
                minCost == maxCost ? String.valueOf(minCost) : minCost + " ~ " + maxCost,
                left, cursorY, innerWidth, UiTheme.TEXT);

        return cursorY + 8;
    }

    /**
     * 画一行带项目符号的文字，超宽自动换行。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param text       文字
     * @param left       左边界
     * @param cursorY    当前纵坐标
     * @param innerWidth 可用宽度
     * @param color      颜色
     * @return 绘制后的纵坐标
     */
    private int bullet(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component text,
                       int left, int cursorY, int innerWidth, int color) {
        g.drawString(font, "\u2022", left + 1, cursorY, color, false);
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, innerWidth - 10)) {
            g.drawString(font, line, left + 9, cursorY, color, false);
            cursorY += font.lineHeight + 1;
        }
        return cursorY + 1;
    }

    /**
     * 冲突区：外部附魔与本模组附魔都可点击跳转。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @param mouseX     鼠标 X
     * @param mouseY     鼠标 Y
     * @return 绘制后的纵坐标
     */
    private int renderConflicts(@Nonnull GuiGraphics g, @Nonnull Font font, int cursorY,
                                int innerWidth, int mouseX, int mouseY) {
        int left = x + PADDING;
        cursorY = UiTheme.sectionHeader(g, font,
                Component.translatable("carianstyle.codex.section.conflicts"),
                left, cursorY, innerWidth);

        List<String> foreign = meta.getForeignConflicts();
        List<String> carian = meta.getCarianConflicts();
        if (foreign.isEmpty() && carian.isEmpty()) {
            return UiTheme.wrapped(g, font,
                    Component.translatable("carianstyle.codex.conflicts.none"),
                    left, cursorY, innerWidth, UiTheme.TEXT_DIM) + 8;
        }

        int badgeX = left;
        int badgeY = cursorY;
        int badgeHeight = font.lineHeight + 4;

        // 与「附魔百科」页同一条规则：原版最前，其余按模组显示名，同模组内按附魔名
        List<String> sortedForeign = new ArrayList<>(foreign);
        sortedForeign.sort((a, b) -> {
            ForeignMeta ma = ForeignCodex.get(a);
            ForeignMeta mb = ForeignCodex.get(b);
            if (ma == null || mb == null) {
                return 0;
            }
            int byMod = ModNames.compare(ma.getRegistryName().getNamespace(),
                    mb.getRegistryName().getNamespace());
            if (byMod != 0) {
                return byMod;
            }
            return ma.getDisplayName().getString().compareTo(mb.getDisplayName().getString());
        });

        for (String other : sortedForeign) {
            ForeignMeta target = ForeignCodex.get(other);
            if (target == null) {
                continue;
            }
            int[] pos = placeBadge(g, font, target.getDisplayName(), left, innerWidth,
                    badgeX, badgeY, badgeHeight, UiTheme.DANGER, mouseX, mouseY, other);
            badgeX = pos[0];
            badgeY = pos[1];
        }
        // 本模组的附魔用金色边框，与上面的外部附魔区分开：
        // 点它会切到另一个标签页，视觉上先给个提示
        List<String> sortedCarian = new ArrayList<>(carian);
        sortedCarian.sort((a, b) -> {
            EnchantmentMeta ma = EnchantmentCodex.get(a);
            EnchantmentMeta mb = EnchantmentCodex.get(b);
            if (ma == null || mb == null) {
                return 0;
            }
            return ma.getDisplayName().getString().compareTo(mb.getDisplayName().getString());
        });
        for (String id : sortedCarian) {
            EnchantmentMeta target = EnchantmentCodex.get(id);
            if (target == null) {
                continue;
            }
            int[] pos = placeBadge(g, font, target.getDisplayName(), left, innerWidth,
                    badgeX, badgeY, badgeHeight, UiTheme.ACCENT, mouseX, mouseY, id);
            badgeX = pos[0];
            badgeY = pos[1];
        }

        cursorY = badgeY + badgeHeight + 4;
        cursorY = UiTheme.wrapped(g, font, Component.translatable("carianstyle.codex.conflicts.hint"),
                left, cursorY, innerWidth, UiTheme.TEXT_DIM);
        return cursorY + 8;
    }

    /**
     * 画一个可点击徽章并登记命中区域，必要时换行。
     *
     * @param g           绘制上下文
     * @param font        字体
     * @param label       文字
     * @param left        内容左边界
     * @param innerWidth  可用宽度
     * @param badgeX      当前横坐标
     * @param badgeY      当前纵坐标
     * @param badgeHeight 徽章高度
     * @param color       颜色
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     * @param targetKey   点击后要跳转到的键
     * @return 更新后的 {@code {badgeX, badgeY}}
     */
    @Nonnull
    private int[] placeBadge(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component label,
                             int left, int innerWidth, int badgeX, int badgeY, int badgeHeight,
                             int color, int mouseX, int mouseY, @Nonnull String targetKey) {
        int badgeWidth = font.width(label) + 8;
        if (badgeX + badgeWidth > left + innerWidth) {
            badgeX = left;
            badgeY += badgeHeight + 3;
        }
        boolean hovered = UiTheme.hit(mouseX, mouseY, badgeX, badgeY, badgeWidth, badgeHeight);
        UiTheme.badge(g, font, label, badgeX, badgeY, color, hovered);
        hits.add(new HitBox(badgeX, badgeY, badgeWidth, badgeHeight, targetKey));
        return new int[]{badgeX + badgeWidth + 3, badgeY};
    }

    /**
     * 处理点击。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 被点中的目标键；未命中时返回 null
     */
    @Nullable
    public String mouseClicked(double mouseX, double mouseY) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return null;
        }
        for (int[] chip : levelHits) {
            if (UiTheme.hit(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3])) {
                viewLevel = chip[4];
                layoutKey = null;
                return null;
            }
        }
        for (HitBox box : hits) {
            if (UiTheme.hit(mouseX, mouseY, box.x, box.y, box.width, box.height)) {
                return box.targetKey;
            }
        }
        return null;
    }

    /**
     * 处理滚轮。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param delta  滚轮增量
     * @return 是否消费了本次事件
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        scroll -= (int) (delta * 16);
        int max = Math.max(0, contentHeight - height + PADDING);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
        return true;
    }

    /**
     * 一个可点击区域。
     */
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
}
