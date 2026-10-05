package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.codex.ModNames;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 百科右栏：单个附魔的详情。
 *
 * <h3>两遍绘制的原因</h3>
 * <p>
 * 详情内容高度不固定（描述长度、适用物品数量、冲突数量、数值条目数都不一样），
 * 而滚动条需要知道内容总高度。这里的做法是：绘制过程中<b>累加</b> y 偏移，
 * 一遍下来既画完了也算出了总高，下一帧的滚动条就用上一帧算出的高度。
 * 内容在两帧之间不会变，所以看不出差异；比预先算一遍再画一遍省掉一半开销。
 * </p>
 *
 * <h3>冲突跳转</h3>
 * <p>
 * 冲突附魔画成可点击的徽章。命中区域在绘制时记入 {@link #conflictHits}，
 * {@link #mouseClicked} 直接查这张表——不需要在点击时重新推算布局，
 * 也就不会出现「画的位置和点的位置对不上」这种经典 GUI bug。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class CodexDetail {

    /** 内容左右内边距 */
    private static final int PADDING = 10;

    /** 当前展示的附魔 */
    private EnchantmentMeta meta;

    /** 界面的放入物品筛选，见 {@link #setFilter} */
    @Nullable
    private ItemFilter filter;

    /** 滚动偏移 */
    private int scroll;

    /** 上一帧算出的内容总高度 */
    private int contentHeight;

    /** 本帧记录的冲突徽章命中区域 */
    private final List<HitBox> conflictHits = new ArrayList<>();

    /**
     * 描述的观看偏好，<b>跨附魔保持</b>。
     *
     * <h3>为什么要记住</h3>
     * <p>
     * 首次打开落在最高级：多数人点开一个附魔，想知道的是「练满了什么样」。
     * </p>
     * <p>
     * 但确实有一类人是冲着公式来的——平衡数值的服主、跨附魔比强度的、写攻略的。
     * 对他们来说公式才是完整信息，代入某一级只是它的一个投影。
     * 早先这个字段在每次 {@link #setMeta} 时都被重置回最高级，
     * 于是这些人每点开一个新附魔就要再点一次「公式」，翻十个附魔点十次。
     * </p>
     * <p>
     * 做成静态字段之后，选择会一直跟着走，直到本人改回去为止。
     * </p>
     */
    private static int viewLevel = Integer.MAX_VALUE;

    /**
     * 把观看偏好收拢到当前附魔的合法范围。
     * <p>
     * 偏好是全局的，而各附魔的最高等级不同：在最高 V 级的附魔上选了 V，
     * 切到最高只有 III 级的附魔时得降到 III，否则会显示一个它根本达不到的等级。
     * 用 {@link Integer#MAX_VALUE} 作为「跟随最高级」的初始值，
     * 这样首次打开会自然落到各自的上限，而不需要额外一个布尔标志。
     * </p>
     *
     * @param meta 当前附魔
     * @return 收拢后的等级
     */
    private static int clampViewLevel(@Nonnull EnchantmentMeta meta) {
        // 只有一级时忽略偏好，直接按 1 级代入——公式档对它没有意义，
        // 显示「持续[附魔等级]×20秒」不如直接显示「持续20秒」
        if (meta.getMaxLevel() <= 1) {
            return 1;
        }
        if (viewLevel == DescriptionText.FORMULA) {
            return DescriptionText.FORMULA;
        }
        return Math.max(1, Math.min(viewLevel, meta.getMaxLevel()));
    }

    /** 本帧记录的等级选择条命中区域，元素为 {x, y, w, h, level} */
    private final List<int[]> levelHits = new ArrayList<>();

    /** 上次切换附魔的时刻，用于内容淡入 */
    private long switchedAt;

    /** 描述排版结果缓存的键：附魔 id + 观看等级 + 可用宽度，三者不变结果就不变 */
    private String layoutKey;

    /**
     * 已排好版的描述行。
     *
     * <h3>为什么必须缓存</h3>
     * <p>
     * {@code render} 每帧调用一次，而排版要把一百多字分词、逐词测宽、
     * 再为每个片段建一个 {@code Component}——一次约一百多个短命对象。
     * 不缓存的话 60fps 下每秒制造近万个对象扔给 GC。
     * </p>
     * <p>
     * 帧率不会因此掉下来，但这属于每帧重算一份根本不会变的东西，
     * 白烧 CPU 和内存带宽，而修它只要三行。
     * </p>
     */
    private List<DescriptionText.Line> layoutCache = Collections.emptyList();

    // 布局区域
    private int x;
    private int y;
    private int width;
    private int height;

    /**
     * 接上界面的放入物品筛选。启用时详情最上方多一张「对这件物品怎么样」的卡片。
     *
     * @param filter 筛选；null 表示不显示卡片
     */
    public void setFilter(@Nullable ItemFilter filter) {
        this.filter = filter;
    }

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
     * 切换展示的附魔，并把滚动位置复位。
     *
     * @param meta 元数据，可为 null（显示空态）
     */
    public void setMeta(@Nullable EnchantmentMeta meta) {
        this.meta = meta;
        this.scroll = 0;
        // 观看偏好不在这里改写。
        //
        // 早先这里写的是 viewLevel = clampViewLevel(meta)，看着像「收拢一下」，
        // 实际是把偏好钉死了：先看一个最高 III 级的附魔，MAX_VALUE 就被覆盖成 3，
        // 之后再看最高 V 级的附魔也只显示到 III，而且再也回不去「跟随最高级」。
        // 收拢只在读取时做，字段本身始终保存玩家的原始选择。
        this.switchedAt = System.currentTimeMillis();
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
        conflictHits.clear();
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

        // 切换附魔时内容轻微上浮淡入：跳转冲突徽章后整页内容会换掉，
        // 没有过渡的话看起来像闪了一下，分不清是换了页还是界面卡了
        float swap = UiTheme.easeOutCubic((System.currentTimeMillis() - switchedAt) / 160f);
        g.pose().pushPose();
        g.pose().translate(0f, (1f - swap) * 6f, 0f);

        int cursorY = y + PADDING - scroll;
        cursorY = renderTitle(g, font, cursorY, innerWidth);
        cursorY = DetailSections.fitCard(g, font, filter, meta.getEnchantment(),
                x + PADDING, cursorY, innerWidth);
        cursorY = renderDescription(g, font, cursorY, innerWidth);
        cursorY = renderBasics(g, font, cursorY, innerWidth);
        cursorY = renderItems(g, font, cursorY, innerWidth, mouseX, mouseY);
        cursorY = renderAcquisition(g, font, cursorY, innerWidth);
        cursorY = renderEnchantingData(g, font, cursorY, innerWidth);
        cursorY = renderConflicts(g, font, cursorY, innerWidth, mouseX, mouseY);
        cursorY = renderValues(g, font, cursorY, innerWidth);

        g.pose().popPose();
        g.disableScissor();

        contentHeight = cursorY - (y + PADDING - scroll) + PADDING;
        // 内容变矮时（放入物品的结论卡片消失、换成更短的卡片）把滚动收回来，
        // 否则底部会空出一截，滚动条的滑块还会画到面板外面。只收不放：变高不会越界
        int maxScroll = Math.max(0, contentHeight - height + PADDING);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        UiTheme.scrollbar(g, x + width - 4, y + 1, height - 2, contentHeight, scroll);
    }

    /**
     * 标题区：名称 + 分组徽章 + 稀有度徽章 + 特性徽章。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderTitle(@Nonnull GuiGraphics g, @Nonnull Font font, int cursorY, int innerWidth) {
        int left = x + PADDING;

        // 标题行：左侧附魔名，右侧注册 ID
        //
        // ID 原本单独占一行、字号与标题相同，看起来像个副标题——可它是给
        // enchantment_values.json 和 uninstallEnchantment 用的机器名，不是名字的一部分。
        // 现在压到同一行的右端并加上「ID」前缀：需要它的人一眼能抄走，
        // 不需要它的人视线会直接掠过去。
        g.drawString(font, meta.getDisplayName(), left, cursorY, UiTheme.tint(), false);
        DetailSections.idChip(g, font, meta.getId(), left, cursorY, innerWidth,
                left + font.width(meta.getDisplayName()) + 16);
        cursorY += font.lineHeight + 8;

        int badgeX = left;
        badgeX += UiTheme.badge(g, font, meta.getTheme().getDisplayName(), badgeX, cursorY,
                meta.getTheme().getAccentColor(), false);
        // 稀有度徽章用稀有度自己的颜色，与列表右侧的圆点对得上；
        // 原来一律用 TEXT（白），三个档位看起来完全一样，等于白占一格
        badgeX += UiTheme.badge(g, font,
                Component.translatable("carianstyle.codex.rarity."
                        + meta.getRarity().name().toLowerCase(Locale.ROOT)),
                badgeX, cursorY, rarityColor(), false);
        if (meta.isTreasure()) {
            badgeX += UiTheme.badge(g, font, Component.translatable("carianstyle.codex.badge.treasure"),
                    badgeX, cursorY, UiTheme.WARN, false);
        }
        if (meta.isCurse()) {
            UiTheme.badge(g, font, Component.translatable("carianstyle.codex.badge.curse"),
                    badgeX, cursorY, UiTheme.DANGER, false);
        }
        cursorY += font.lineHeight + 12;

        UiTheme.divider(g, left, cursorY, innerWidth);
        return cursorY + 9;
    }

    /**
     * 当前附魔的稀有度颜色（与列表右侧圆点同一套）。
     *
     * @return ARGB 颜色
     */
    private int rarityColor() {
        switch (meta.getRarity()) {
            case VERY_RARE:
                return 0xFFE0A05A;
            case RARE:
                return 0xFFC0A4E0;
            case UNCOMMON:
            default:
                return UiTheme.TEXT_DIM;
        }
    }

    /**
     * 描述区：语言文件里的 {@code .desc} 文本，按面板宽度自动换行。
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

        // 等级选择条：公式 / I / II / ... / 最高级
        //
        // 描述里像「持续[附魔等级]×20秒」这样的式子，一段里能有五六处。
        // 让玩家自己乘是不现实的——点一下等级，全部算好摆在这里。
        int maxLevel = meta.getMaxLevel();

        // 只有一级的附魔不显示等级条。
        // 「公式」和「I 级」对它来说是同一个结果——[附魔等级]×N 恒等于 N，
        // 摆两个按钮让人以为切换会有区别，实际点了什么都不变
        int chipX = left;
        int chipH = font.lineHeight + 4;
        for (int lv = DescriptionText.FORMULA; maxLevel > 1 && lv <= maxLevel; lv++) {
            Component label = DescriptionText.chipLabel(lv);
            int chipW = font.width(label) + 8;
            if (chipX + chipW > left + innerWidth) {
                chipX = left;
                cursorY += chipH + 3;
            }
            boolean active = lv == clampViewLevel(meta);
            g.fill(chipX, cursorY, chipX + chipW, cursorY + chipH,
                    active ? UiTheme.ACCENT_FILL : UiTheme.PANEL_ALT);
            UiTheme.border(g, chipX, cursorY, chipW, chipH,
                    active ? UiTheme.ACCENT : UiTheme.BORDER);
            g.drawString(font, label, chipX + 4, cursorY + 3,
                    active ? UiTheme.ACCENT : UiTheme.TEXT_DIM, false);
            levelHits.add(new int[]{chipX, cursorY, chipW, chipH, lv});
            chipX += chipW + 3;
        }
        if (maxLevel > 1) {
            cursorY += chipH + 7;
        }

        // 按标点结构逐行排版，断行由 DescriptionText 自己做——
        // 原版换行器对中文是按宽度硬切的，会把「180秒」劈成「18」「0秒」。
        int bulletIndent = 9;
        int bodyIndent = 9;
        int level = clampViewLevel(meta);
        String key = meta.getId() + '#' + level + '#' + innerWidth;
        if (!key.equals(layoutKey)) {
            layoutKey = key;
            layoutCache = DescriptionText.layout(meta.getDescription().getString(), level,
                    font, innerWidth - bulletIndent, bodyIndent);
        }

        for (DescriptionText.Line line : layoutCache) {

            int indent = bulletIndent + (line.getIndent() == 0 ? 0 : bodyIndent);
            if (line.isBlockStart()) {
                g.fill(left + 1, cursorY + 3, left + 4, cursorY + 6, UiTheme.ACCENT);
            }
            g.drawString(font, line.getText(), left + indent, cursorY, UiTheme.TEXT, false);
            cursorY += font.lineHeight + 2;
        }
        return cursorY + 6;
    }

    /**
     * 基础信息区：最大等级、装备槽位。
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

        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.max_level"),
                UiTheme.roman(meta.getMaxLevel()) + " ("
                        + meta.getMaxLevel() + ")", left, cursorY, innerWidth, UiTheme.TEXT);

        StringBuilder slotText = new StringBuilder();
        List<EquipmentSlot> slots = meta.getSlots();
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) {
                slotText.append("  ");
            }
            slotText.append(Component.translatable(
                    "carianstyle.codex.slot." + slots.get(i).getName()).getString());
        }
        if (slotText.length() == 0) {
            slotText.append("-");
        }
        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.slots"),
                slotText.toString(), left, cursorY, innerWidth, UiTheme.TEXT);

        // 模组内部实现分类：列表已经不按它分组了，但它决定附魔走哪条触发路径，
        // 对想读代码或配怪物触发开关的人仍然有用，所以留在这里作为一行信息
        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.category"),
                meta.getGroup().getDisplayName().getString(), left, cursorY, innerWidth, UiTheme.TEXT_DIM);

        return cursorY + 8;
    }

    /**
     * 适用物品种类区。
     * <p>
     * 绘制交给 {@link DetailSections#itemTypes}——与「其他附魔」页共用同一段代码，
     * 两边不会再出现「一边带字一边不带」这种分叉。本方法只负责算出种类名。
     * </p>
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @param mouseX     鼠标 X
     * @param mouseY     鼠标 Y
     * @return 绘制后的纵坐标
     */
    private int renderItems(@Nonnull GuiGraphics g, @Nonnull Font font, int cursorY,
                            int innerWidth, int mouseX, int mouseY) {
        // 种类名取自注解的 type / customType；没登记翻译时回退到原始键，
        // 至少还能和注解里写的对上
        String key = "carianstyle.codex.category." + meta.getCategoryKey();
        Component category = I18n.exists(key)
                ? meta.getCategoryName()
                : Component.literal(meta.getCategoryKey());

        return DetailSections.itemTypes(g, font, category, meta.getApplicableItems(),
                x + PADDING, cursorY, innerWidth);
    }

    /**
     * 获取方式区：这个附魔现在能怎么拿到。
     *
     * <h3>为什么必须跟着配置走</h3>
     * <p>
     * 「宝藏附魔」不是写死在附魔上的属性——{@code isTreasureVeryRaryEnchantment} 之类的开关
     * 可以把整个稀有度档位一次性变成宝藏；{@code allowVillagerBookTrade} 决定它会不会
     * 出现在图书管理员的交易里；{@code uninstallEnchantment} 更是能直接让它不存在。
     * </p>
     * <p>
     * 也就是说同一个附魔在两台服务器上的获取途径可能完全不同。百科要是写死一句
     * 「可从附魔台获得」，在把稀有档全调成宝藏的整合包里就是错的。
     * 这里全部实时读当前配置，玩家看到的就是这台服务器的真实情况。
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
        boolean disabled = enchantment instanceof EnchantmentBase base && base.isDisabled();

        if (disabled) {
            // 被禁用时其余途径全部无意义，直接说明并收尾
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.disabled"),
                    left, cursorY, innerWidth, UiTheme.DANGER);
            return cursorY + 8;
        }

        boolean treasure = enchantment.isTreasureOnly();
        if (treasure) {
            // 宝藏原因接在同一条后面：它是对上一句的补充说明，
            // 单开一条项目符号会让人以为是并列的另一种获取途径
            Component reason = reasonForTreasure();
            Component treasureLine = reason == null
                    ? Component.translatable("carianstyle.codex.acquire.treasure")
                    : Component.translatable("carianstyle.codex.acquire.treasure")
                            .copy().append(" ").append(reason);
            cursorY = bullet(g, font, treasureLine, left, cursorY, innerWidth, UiTheme.WARN);
        } else {
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.table"),
                    left, cursorY, innerWidth, UiTheme.ON);
        }

        cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.loot"),
                left, cursorY, innerWidth, UiTheme.TEXT);

        if (enchantment.isTradeable() && ConfigLoader.allowVillagerBookTrade) {
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.villager"),
                    left, cursorY, innerWidth, UiTheme.TEXT);
        } else {
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.acquire.no_villager"),
                    left, cursorY, innerWidth, UiTheme.OFF);
        }

        return cursorY + 8;
    }

    /**
     * 解释这个附魔为什么是宝藏附魔。
     *
     * @return 说明文案；无法归因时返回 null
     */
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
     * 附魔台数据区：抽取权重与等级消耗。
     *
     * <h3>权重为什么直接读附魔实例</h3>
     * <p>
     * 权重来自 {@code Enchantment.Rarity#getWeight()}，而本模组的 {@code EnchantmentBase}
     * <b>覆写了 {@code getRarity()}</b>——{@code useVanillaRarityWeight} 关闭时它会返回
     * 最高权重档。所以这里读到的就是附魔台实际用来抽取的那个值，
     * 不需要在界面里再复刻一遍配置判断逻辑（复刻就意味着以后改配置要改两处）。
     * </p>
     * <p>
     * 等级消耗同理：{@code getMinCost} / {@code getMaxCost} 内部已经乘过
     * {@code enchantingDifficulty}，读出来就是当前难度下的真实数字。
     * </p>
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

        Enchantment enchantment = meta.getEnchantment();
        int weight = enchantment.getRarity().getWeight();

        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.weight"),
                String.valueOf(weight), left, cursorY, innerWidth,
                ConfigLoader.useVanillaRarityWeight ? UiTheme.TEXT : UiTheme.WARN);

        // 权重的含义对多数玩家是空的，补一行对照：原版最常见的附魔是 10
        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.weight_mode"),
                Component.translatable(ConfigLoader.useVanillaRarityWeight
                        ? "carianstyle.codex.weight.by_rarity"
                        : "carianstyle.codex.weight.flat").getString(),
                left, cursorY, innerWidth, UiTheme.TEXT_DIM);

        int minCost = enchantment.getMinCost(1);
        int maxCost = enchantment.getMinCost(meta.getMaxLevel());
        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.field.cost"),
                minCost + " ~ " + maxCost, left, cursorY, innerWidth, UiTheme.TEXT);

        if (ConfigLoader.enchantingDifficulty != 1.0D) {
            cursorY = UiTheme.keyValue(g, font,
                    Component.translatable("carianstyle.codex.field.difficulty"),
                    "x" + trimDouble(ConfigLoader.enchantingDifficulty),
                    left, cursorY, innerWidth, UiTheme.WARN);
        }

        if (ConfigLoader.levelLimit) {
            cursorY = bullet(g, font, Component.translatable("carianstyle.codex.field.level_limit_on"),
                    left, cursorY, innerWidth, UiTheme.WARN);
        }

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
        for (FormattedCharSequence line : font.split(text, innerWidth - 10)) {
            g.drawString(font, line, left + 9, cursorY, color, false);
            cursorY += font.lineHeight + 1;
        }
        return cursorY + 1;
    }

    /**
     * 冲突区：可点击跳转的徽章。
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

        List<String> conflicts = meta.getConflictIds();
        List<Enchantment> foreign = meta.getForeignConflicts();

        if (conflicts.isEmpty() && foreign.isEmpty()) {
            return UiTheme.wrapped(g, font,
                    Component.translatable("carianstyle.codex.conflicts.none"),
                    left, cursorY, innerWidth, UiTheme.TEXT_DIM) + 8;
        }

        // 整类互斥折叠成一句。
        //
        // 战技之间是两两互斥的，逐个列出来就是 24 个徽章，把真正特殊的那几条冲突淹掉了。
        // 折叠之后，下面的徽章区只剩「跨分类的冲突」，那才是需要玩家记住的信息。
        java.util.Set<String> sameGroup = new java.util.HashSet<>();
        if (meta.conflictsWithWholeGroup()) {
            cursorY = bullet(g, font, Component.translatable(
                            "carianstyle.codex.conflicts.whole_group",
                            meta.getGroup().getDisplayName(),
                            meta.getGroupMemberCount() - 1),
                    left, cursorY, innerWidth, UiTheme.DANGER);
            for (String id : conflicts) {
                EnchantmentMeta other = EnchantmentCodex.get(id);
                if (other != null && other.getGroup() == meta.getGroup()) {
                    sameGroup.add(id);
                }
            }
            cursorY += 2;
        }

        int badgeX = left;
        int badgeY = cursorY;
        int badgeHeight = font.lineHeight + 4;
        boolean drewAny = false;

        for (String conflictId : conflicts) {
            if (sameGroup.contains(conflictId)) {
                continue;
            }
            EnchantmentMeta other = EnchantmentCodex.get(conflictId);
            if (other == null) {
                continue;
            }
            Component label = other.getDisplayName();
            int badgeWidth = font.width(label) + 8;
            if (badgeX + badgeWidth > left + innerWidth) {
                badgeX = left;
                badgeY += badgeHeight + 3;
            }
            boolean hovered = UiTheme.hit(mouseX, mouseY, badgeX, badgeY, badgeWidth, badgeHeight);
            UiTheme.badge(g, font, label, badgeX, badgeY, UiTheme.DANGER, hovered);
            conflictHits.add(new HitBox(badgeX, badgeY, badgeWidth, badgeHeight, conflictId));
            badgeX += badgeWidth + 3;
            drewAny = true;
        }

        // 原版 / 其它模组的附魔：现在「其他附魔」标签页收录了它们，所以同样可点击，
        // 点了会切到那个标签页。用灰蓝色与本模组的红色徽章区分——
        // 颜色不同是在提示「点下去会换页」，而不是单纯的分类色
        // 按所属模组排序：原版最前，其余按模组显示名。
        // 不排的话是注册表遍历顺序——那个顺序对玩家毫无意义，看着就是乱的
        List<Enchantment> sortedForeign = new ArrayList<>(foreign);
        sortedForeign.sort((a, b) -> {
            ResourceLocation ka = ForgeRegistries.ENCHANTMENTS.getKey(a);
            ResourceLocation kb = ForgeRegistries.ENCHANTMENTS.getKey(b);
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
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(other);
            if (id == null) {
                continue;
            }
            Component label = Component.translatable(other.getDescriptionId());
            int badgeWidth = font.width(label) + 8;
            if (badgeX + badgeWidth > left + innerWidth) {
                badgeX = left;
                badgeY += badgeHeight + 3;
            }
            boolean hovered = UiTheme.hit(mouseX, mouseY, badgeX, badgeY, badgeWidth, badgeHeight);
            UiTheme.badge(g, font, label, badgeX, badgeY, 0xFF8FB8E8, hovered);
            conflictHits.add(new HitBox(badgeX, badgeY, badgeWidth, badgeHeight, id.toString()));
            badgeX += badgeWidth + 3;
            drewAny = true;
        }

        if (drewAny) {
            cursorY = badgeY + badgeHeight + 4;
            cursorY = UiTheme.wrapped(g, font,
                    Component.translatable("carianstyle.codex.conflicts.hint"),
                    left, cursorY, innerWidth, UiTheme.TEXT_DIM) + 3;
        }
        return cursorY + 8;
    }

    /**
     * 数值区：该附魔已接入数值配置的全部条目。
     *
     * @param g          绘制上下文
     * @param font       字体
     * @param cursorY    当前纵坐标
     * @param innerWidth 内容宽度
     * @return 绘制后的纵坐标
     */
    private int renderValues(@Nonnull GuiGraphics g, @Nonnull Font font,
                             int cursorY, int innerWidth) {
        int left = x + PADDING;
        cursorY = UiTheme.sectionHeader(g, font,
                Component.translatable("carianstyle.codex.section.values"),
                left, cursorY, innerWidth);

        List<EnchantmentValues.Handle> handles = EnchantmentValues.handlesOf(meta.getId());
        if (handles.isEmpty()) {
            return UiTheme.wrapped(g, font, Component.translatable("carianstyle.codex.values.none"),
                    left, cursorY, innerWidth, UiTheme.TEXT_DIM) + 8;
        }

        // 表头：明确右侧那一列是「当前生效值」，不是默认值也不是范围
        cursorY = UiTheme.keyValue(g, font,
                Component.translatable("carianstyle.codex.values.header_name"),
                Component.translatable("carianstyle.codex.values.header_current").getString(),
                left, cursorY, innerWidth, UiTheme.TEXT_DIM);
        UiTheme.divider(g, left, cursorY - 1, innerWidth);
        cursorY += 3;

        boolean anyOverridden = false;
        for (EnchantmentValues.Handle handle : handles) {
            boolean overridden = handle.isOverridden();
            anyOverridden |= overridden;

            String current = format(handle, handle.get());

            // 数值名优先用中文；没登记翻译时回退到原始键，至少还能对上配置文件
            Component label = valueName(handle);
            UiTheme.trimmed(g, font, label, left, cursorY, innerWidth - font.width(current) - 12,
                    UiTheme.TEXT);
            int currentWidth = font.width(current);
            g.drawString(font, current, left + innerWidth - currentWidth, cursorY,
                    overridden ? UiTheme.WARN : UiTheme.TEXT, false);
            cursorY += font.lineHeight + 1;

            // 第二行：默认值与允许范围。被改过时把默认值标出来，方便判断改了多少
            String detail = Component.translatable("carianstyle.codex.values.detail",
                    format(handle, handle.getDefaultValue()),
                    format(handle, handle.getMin()) + " ~ " + format(handle, handle.getMax())
            ).getString();
            g.drawString(font, detail, left + 8, cursorY,
                    overridden ? UiTheme.WARN : UiTheme.OFF, false);
            cursorY += font.lineHeight + 4;
        }

        cursorY += 2;
        if (anyOverridden) {
            for (FormattedCharSequence line : font.split(
                    Component.translatable("carianstyle.codex.values.desc_warning"), innerWidth)) {
                g.drawString(font, line, left, cursorY, UiTheme.WARN, false);
                cursorY += font.lineHeight + 1;
            }
        }
        return cursorY + 8;
    }

    /**
     * 取某项可调数值的显示名。
     *
     * <h3>为什么要专门做这件事</h3>
     * <p>
     * 句柄的键是给配置文件用的机器名（{@code damage_per_level}、{@code max_search_radius}），
     * 直接摆在界面上等于让玩家自己去猜英文缩写的含义。
     * </p>
     * <p>
     * 这里查语言键 {@code carianstyle.value.<附魔id>.<键>}，查得到就显示中文名。
     * 用 {@link I18n#exists} 判断而不是比较翻译结果与键本身——后者在键名恰好等于译文时会误判，
     * 而且要多做一次字符串比较。
     * </p>
     * <p>
     * 查不到时回退到原始键：这既是新加数值忘记补翻译的兜底，也让玩家在界面上看到的名字
     * 与配置文件里要改的那一行完全对得上。
     * </p>
     *
     * @param handle 数值句柄
     * @return 显示名
     */
    @Nonnull
    private Component valueName(@Nonnull EnchantmentValues.Handle handle) {
        String key = "carianstyle.value." + handle.getEnchantmentId() + "." + handle.getKey();
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(handle.getKey());
    }

    /**
     * 按句柄的整数/浮点语义格式化数值。
     *
     * @param handle 句柄
     * @param value  待格式化的值
     * @return 文本
     */
    @Nonnull
    private static String format(@Nonnull EnchantmentValues.Handle handle, double value) {
        return handle.isIntegral() ? String.valueOf(Math.round(value)) : trimDouble(value);
    }

    /**
     * 把浮点数格式化成不带多余零的短文本。
     *
     * @param value 数值
     * @return 文本
     */
    @Nonnull
    private static String trimDouble(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * 处理点击，用于冲突徽章跳转。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 被点中的冲突附魔 id；未命中时返回 null
     */
    @Nullable
    public String mouseClicked(double mouseX, double mouseY) {
        if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
            return null;
        }
        for (int[] chip : levelHits) {
            if (UiTheme.hit(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3])) {
                viewLevel = chip[4];
                return null;
            }
        }
        for (HitBox box : conflictHits) {
            if (UiTheme.hit(mouseX, mouseY, box.x, box.y, box.width, box.height)) {
                return box.targetId;
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
        private final String targetId;

        HitBox(int x, int y, int width, int height, String targetId) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.targetId = targetId;
        }
    }
}
