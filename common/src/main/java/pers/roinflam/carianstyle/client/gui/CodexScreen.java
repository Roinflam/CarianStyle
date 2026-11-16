package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.carianstyle.client.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.client.codex.ForeignCodex;
import pers.roinflam.carianstyle.client.codex.ForeignMeta;
import pers.roinflam.carianstyle.codex.CodexTheme;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 卡利亚式附魔的百科与特效开关（K 键）。
 * <p>
 * 画面是一册摊在湖夜窗前的典籍：顶上银轨垂下三条书签丝带当标签，左页目录、右页条目，
 * 特效开关页左右两页各放几组开关。百科不暂停游戏，开着开关页就能实时看到特效的变化。
 * </p>
 * <p>
 * 放入物品：搜索框右边的槽放一件背包里的东西，两个百科页的列表只剩和它有关的附魔并重新分组，
 * 判定见 {@link pers.roinflam.carianstyle.codex.ItemFit}，状态见 {@link ItemFilter}。
 * </p>
 */
public final class CodexScreen extends Screen {

    private static final int GUT = 9;
    private static final int BAND = 44;
    private static final int BAND_TOGGLES = 24;
    private static final int TAB_W = 44;

    private enum Tab {
        CODEX("carianstyle.codex.tab.codex"),
        EFFECTS("carianstyle.codex.tab.effects"),
        OTHERS("carianstyle.codex.tab.others");

        private final String langKey;

        Tab(String langKey) {
            this.langKey = langKey;
        }

        @Nonnull
        Component title() {
            return Component.translatable(langKey);
        }
    }

    private static Tab lastTab = Tab.CODEX;
    private static String lastSelectedId = null;
    private static String lastForeignKey = null;

    private Tab tab = lastTab;
    private final CodexList list = new CodexList();
    private final CodexDetail detail = new CodexDetail();
    private final CodexList foreignList = new CodexList();
    private final ForeignDetail foreignDetail = new ForeignDetail();
    private final TogglePage togglePage = new TogglePage();
    private final Deque<String> history = new ArrayDeque<>();
    private final ItemFilter filter = new ItemFilter();
    private final InventoryPicker picker = new InventoryPicker();
    private EditBox searchBox;

    // 书的几何，init 里按窗口算
    private boolean large;
    private int bx;
    private int by;
    private int bw;
    private int bh;
    private int clw;
    private int lw;
    private int spreadTop;
    private int spreadH;
    private int rightX;
    private int rightW;
    private int bgx;
    private int hzy;
    private int panoMoon;
    private int moonSize;

    private final int[] searchRect = new int[4];
    private final int[] filterRect = new int[4];
    private final int[] filterClearRect = new int[4];
    private final int[] backRect = new int[4];
    private final int[] reloadRect = new int[4];
    private final int[] tabX = new int[3];
    private final int[] tabW = new int[3];
    private int titleW;
    private boolean filterCompact;
    private boolean filterHovered;

    private final MoonriseIntro intro = new MoonriseIntro();
    private boolean opened;
    private long turnedAt;
    private long tabDroppedAt;
    private int droppedTab = -1;
    private long railAt;

    public CodexScreen() {
        super(Component.translatable("carianstyle.codex.title"));
    }

    @Override
    protected void init() {
        VisualToggle.ensureLoaded();
        boolean firstInit = !opened;
        if (firstInit) {
            opened = true;
            long t = UiTheme.now();
            intro.begin(t);
            railAt = t + MoonriseIntro.UNFOLD_AT;
        }
        picker.close();
        detail.setFilter(filter);
        foreignDetail.setFilter(filter);

        layout();

        int sw = clw;
        int filterBlock = 18 + 5 + 9;
        int reloadW = UiTheme.buttonWidth(this.font, Component.translatable("carianstyle.codex.reload"));
        int backW = UiTheme.buttonWidth(this.font, Component.translatable("carianstyle.codex.back"));
        int right = bx + bw;
        setRect(reloadRect, right - reloadW, by + 27, reloadW, 14);
        int buttonsLeft = right - reloadW - 4 - backW;
        setRect(backRect, buttonsLeft, by + 27, backW, 14);

        // 窄窗口里右边的按钮会压到放入物品槽上，这时槽挤进搜索框右端，说明改成悬停提示
        int pickX = bx + clw + GUT;
        filterCompact = buttonsLeft < pickX + filterBlock + 6;
        int slotX = pickX;
        if (filterCompact) {
            int leftEnd = Math.min(bx + clw, buttonsLeft - 6);
            sw = leftEnd - filterBlock - 4 - bx;
            slotX = leftEnd - filterBlock;
        }
        setRect(searchRect, bx, by + 26, sw, 16);
        setRect(filterRect, slotX, by + 25, 18, 18);
        setRect(filterClearRect, slotX + 18 + 5, by + 29, 9, 9);

        searchBox = new EditBox(this.font, bx + 16, by + 30, Math.max(8, sw - 21), 8,
                Component.translatable("carianstyle.codex.search"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setTextColor(UiTheme.MOON);
        searchBox.setResponder(text -> refreshList());
        addRenderableWidget(searchBox);

        layoutTabs();

        if (firstInit && this.minecraft != null && this.minecraft.player != null) {
            filter.restore(this.minecraft.player.getInventory());
        }
        refreshList();

        if (lastSelectedId != null && EnchantmentCodex.get(lastSelectedId) != null) {
            selectEnchantment(lastSelectedId);
        } else {
            List<EnchantmentMeta> all = EnchantmentCodex.getAll();
            if (!all.isEmpty()) {
                selectEnchantment(all.get(0).getId());
            }
        }
        if (lastForeignKey != null && ForeignCodex.get(lastForeignKey) != null) {
            selectForeign(lastForeignKey);
        } else {
            List<ForeignMeta> all = ForeignCodex.getAll();
            if (!all.isEmpty()) {
                selectForeign(all.get(0).getKey());
            }
        }
        if (firstInit) {
            long land = intro.start() + MoonriseIntro.LAND_AT;
            detail.playIntro(land);
            foreignDetail.playIntro(land);
            if (!VisualToggle.isEnabled(VisualEffectType.CODEX_INTRO)) {
                skipIntro();
            }
        }
        updateWidgetVisibility();
    }

    /** 书的几何：书最大 780×600 居中，窗口宽不到 600 时月亮换小号、页边收窄 */
    private void layout() {
        int gw = this.width;
        int gh = this.height;
        large = gw >= 600;
        int m = large ? 8 : 4;
        bw = Math.min(gw - m * 2, 780);
        bh = Math.min(gh - m * 2, 600);
        bx = (gw - bw) / 2;
        by = (gh - bh) / 2;
        clw = gw < 560 ? 166 : gw < 800 ? 204 : 240;
        moonSize = large ? MoonDisc.LARGE : MoonDisc.SMALL;
        int pad = large ? 14 : 8;

        boolean toggles = tab == Tab.EFFECTS;
        int band = toggles ? BAND_TOGGLES : BAND;
        lw = toggles ? (bw - GUT) / 2 : clw;
        spreadTop = by + band;
        int avail = bh - band;
        rightX = bx + lw + GUT;
        rightW = bw - lw - GUT;

        // 背景画：月心那一列对到卷首月亮的正中，湖岸线对到卷首下沿
        bgx = bx + clw + GUT + 2 + pad + moonSize / 2 - CodexScene.moonColumn();
        hzy = by + BAND + 2 + 4 + moonSize + 5;
        panoMoon = 0;
        spreadH = avail;

        togglePage.setBounds(bx + 2, spreadTop + 2, lw - 4, rightX + 2, rightW - 4, avail - 4, large);
        if (toggles) {
            // 开关页窗口够高时书页收到内容那么高，下面露出湖和满月
            int need = togglePage.contentNeed() + 4;
            int free = avail - need;
            panoMoon = free >= 132 ? MoonDisc.LARGE : free >= 100 ? MoonDisc.SMALL : 0;
            if (panoMoon > 0) {
                spreadH = need;
                hzy = spreadTop + need + 14 + panoMoon + 5;
                togglePage.setBounds(bx + 2, spreadTop + 2, lw - 4, rightX + 2, rightW - 4, need - 4, large);
            }
        }

        list.setBounds(bx + 2, spreadTop + 2, lw - 4, spreadH - 4);
        foreignList.setBounds(bx + 2, spreadTop + 2, lw - 4, spreadH - 4);
        detail.setLarge(large);
        foreignDetail.setLarge(large);
        detail.setBounds(rightX + 2, spreadTop + 2, rightW - 4, spreadH - 4);
        foreignDetail.setBounds(rightX + 2, spreadTop + 2, rightW - 4, spreadH - 4);
    }

    /** 丝带标签跟在标题后面；挤不下时先藏右边的统计，再截短标题 */
    private void layoutTabs() {
        int[] widths = new int[3];
        int total = 0;
        for (Tab value : Tab.values()) {
            widths[value.ordinal()] = Math.max(TAB_W, this.font.width(value.title()) + 12);
            total += widths[value.ordinal()];
        }
        total += 4;
        int titleX = bx + 21;
        titleW = this.font.width(this.title);
        int maxTitle = bx + bw - 2 - total - 8 - titleX;
        titleW = Math.max(0, Math.min(titleW, maxTitle));
        int x = titleX + titleW + 8;
        for (Tab value : Tab.values()) {
            tabX[value.ordinal()] = x;
            tabW[value.ordinal()] = widths[value.ordinal()];
            x += widths[value.ordinal()] + 2;
        }
    }

    private static void setRect(int[] rect, int x, int y, int width, int height) {
        rect[0] = x;
        rect[1] = y;
        rect[2] = width;
        rect[3] = height;
    }

    private void refreshList() {
        String keyword = searchBox == null ? "" : searchBox.getValue();
        List<EnchantmentMeta> ownHits = EnchantmentCodex.search(keyword, (CodexTheme) null);
        list.setEntries(filter.apply(ownHits));
        list.select(list.getSelectedId());
        list.setEmptyHint(emptyHint(keyword, ownHits.isEmpty()));
        // 两个百科页共用一个搜索框，切过去时结果已经筛好
        List<ForeignMeta> foreignHits = ForeignCodex.search(keyword);
        foreignList.setEntries(filter.apply(foreignHits));
        foreignList.select(foreignList.getSelectedId());
        foreignList.setEmptyHint(emptyHint(keyword, foreignHits.isEmpty()));
    }

    /** 搜到了却被物品筛掉时要说清楚，不然玩家会以为关键字打错了 */
    @Nonnull
    private Component emptyHint(@Nonnull String keyword, boolean searchEmpty) {
        if (searchEmpty || !filter.isActive()) {
            return Component.translatable("carianstyle.codex.list.empty_search");
        }
        return Component.translatable(keyword.trim().isEmpty()
                ? "carianstyle.codex.list.empty_fit"
                : "carianstyle.codex.list.empty_search_fit");
    }

    private void selectEnchantment(@Nullable String id) {
        if (id == null) {
            return;
        }
        EnchantmentMeta meta = EnchantmentCodex.get(id);
        if (meta == null) {
            return;
        }
        list.select(id);
        detail.setMeta(meta);
        lastSelectedId = id;
        updateWidgetVisibility();
    }

    private void selectForeign(@Nullable String key) {
        if (key == null) {
            return;
        }
        ForeignMeta meta = ForeignCodex.get(key);
        if (meta == null) {
            return;
        }
        foreignList.select(key);
        foreignDetail.setMeta(meta);
        lastForeignKey = key;
    }

    /** 跳转记历史；键里有冒号的是外部附魔，在「其他附魔」页 */
    private void navigateTo(@Nullable String key) {
        if (key == null) {
            return;
        }
        String current = currentKey();
        if (current != null && !current.equals(key)) {
            history.push(current);
            while (history.size() > 64) {
                history.removeLast();
            }
        }
        jumpTo(key);
    }

    private void jumpTo(@Nullable String key) {
        if (key == null) {
            return;
        }
        if (key.indexOf(':') >= 0) {
            switchTab(Tab.OTHERS, false);
            selectForeign(key);
        } else {
            switchTab(Tab.CODEX, false);
            selectEnchantment(key);
        }
    }

    @Nullable
    private String currentKey() {
        if (tab == Tab.CODEX) {
            return list.getSelectedId();
        }
        if (tab == Tab.OTHERS) {
            return foreignList.getSelectedId();
        }
        return null;
    }

    private boolean isCodexTab() {
        return tab == Tab.CODEX || tab == Tab.OTHERS;
    }

    /** byHand：玩家自己点的标签才清历史；跳转带过去的不清，不然返回按钮刚出现就没了 */
    private void switchTab(@Nonnull Tab target, boolean byHand) {
        if (tab == target) {
            return;
        }
        boolean wasToggles = tab == Tab.EFFECTS;
        tab = target;
        lastTab = target;
        picker.close();
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        if (byHand) {
            history.clear();
        }
        long t = UiTheme.now();
        turnedAt = t;
        tabDroppedAt = t;
        droppedTab = target.ordinal();
        if (wasToggles || target == Tab.EFFECTS) {
            layout();
        }
        if (target == Tab.CODEX) {
            detail.playSelect();
        } else if (target == Tab.OTHERS) {
            foreignDetail.playSelect();
        } else {
            togglePage.resetScroll();
        }
        updateWidgetVisibility();
    }

    private void navigateBack() {
        if (history.isEmpty()) {
            return;
        }
        jumpTo(history.pop());
        updateWidgetVisibility();
    }

    /** 服主改完数值表后不重启就能看到新值 */
    private void reloadAll() {
        EnchantmentValues.reload();
        EnchantmentCodex.invalidate();
        ForeignCodex.invalidate();
        filter.reevaluate();
        history.clear();
        refreshList();
        String selected = list.getSelectedId();
        detail.setMeta(selected == null ? null : EnchantmentCodex.get(selected));
        detail.pulse();
        railAt = UiTheme.now();
        UiTheme.burst(reloadRect[0] + reloadRect[2] / 2, reloadRect[1] + reloadRect[3] / 2, 6);
        updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        boolean codex = isCodexTab();
        if (searchBox != null) {
            searchBox.visible = codex;
            searchBox.active = codex;
            if (!codex) {
                searchBox.setFocused(false);
            }
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long t = UiTheme.now();
        long e = t - intro.start();
        boolean modal = picker.isOpen();
        // 书还没照全时不响应悬停
        boolean hidden = modal || e < MoonriseIntro.UNFOLD_AT + MoonriseIntro.UNFOLD_DUR;
        int underX = hidden ? -1 : mouseX;
        int underY = hidden ? -1 : mouseY;

        boolean codex = isCodexTab();
        int top = spreadTop;
        int bottom = spreadTop + spreadH;
        int hx0 = rightX + 2;
        int hx1 = bx + bw - 10;
        int hy0 = by + BAND + 2;
        int hy1 = hy0 + 4 + moonSize + 5;
        CodexScene.Open open = (x, y) -> {
            if (codex && x >= hx0 && x < hx1 && y >= hy0 && y < hy1) {
                return true;
            }
            return !(x >= bx && x < bx + bw && y >= top && y < bottom);
        };
        // 书从书缝往两边被照出来，没照到的地方书是看不见的
        int seam = bx + lw + GUT / 2;
        float reveal = intro.reveal(e);
        int revL = seam - Math.round((seam - bx) * reveal);
        int revR = seam + Math.round((bx + bw - seam) * reveal);
        boolean veiled = reveal < 1f;
        CodexScene.render(g, this.width, this.height, bgx, hzy, intro.night(e), intro.starLimit(e), open,
                veiled ? (x, y) -> x < revL || x >= revR || open.test(x, y) : open);
        int moonX = bgx + CodexScene.moonColumn();
        if (intro.running(t)) {
            intro.aim(introFrame(t));
        }
        intro.renderPath(g, e, moonX, hzy, this.height);
        intro.renderWash(g, e, this.width, this.height);
        if (tab == Tab.EFFECTS && panoMoon > 0) {
            renderPanoMoon(g, t);
        }

        filterHovered = false;
        if (veiled) {
            g.enableScissor(revL, 0, revR, this.height);
        }
        renderBand(g, underX, underY, t);
        renderSpread(g, underX, underY, t, e);

        super.render(g, underX, underY, partialTick);
        if (searchBox != null && searchBox.visible && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            UiTheme.trimmed(g, this.font, Component.translatable("carianstyle.codex.search"),
                    searchBox.getX(), searchBox.getY(), searchRect[2] - 21, UiTheme.OFF);
        }
        if (veiled) {
            intro.renderShade(g, e, revL, by - 12, revR, bottom);
            g.disableScissor();
        }
        intro.renderEdges(g, e, revL, revR, by, bottom);
        intro.renderSeam(g, e, seam, by, bottom);
        if (intro.running(t)) {
            renderIntroMoon(g, t, e, moonX);
        }

        if (modal && this.minecraft != null && this.minecraft.player != null) {
            picker.render(g, this.font, this.minecraft.player.getInventory(), mouseX, mouseY, filter.getSourceSlot());
        } else if (filterHovered) {
            renderFilterTooltip(g, mouseX, mouseY);
        }
        UiTheme.renderSparks(g);
    }

    /** 开关页湖上那轮月亮没有对应的条目，一直是满月，配卡利亚王室的蓝 */
    private void renderPanoMoon(@Nonnull GuiGraphics g, long t) {
        int n = panoMoon;
        int mx = bgx + CodexScene.moonColumn() - n / 2;
        int my = hzy - 5 - n;
        int rise = 0;
        float breath;
        if (turnedAt != 0L) {
            long e = t - turnedAt - 100;
            rise = Math.round((1f - UiTheme.steps(e, 600, 14)) * n * 0.62f);
            breath = UiTheme.breathe(Math.max(0, e - 600), 4800, 8, 0.4f);
        } else {
            // 打开时停在开关页：开场的大月落到这一轮上之前不画
            long e = t - intro.start() - MoonriseIntro.LAND_AT;
            if (e < 0) {
                return;
            }
            breath = UiTheme.breathe(Math.max(0, e - 400), 4800, 8, 0.4f);
        }
        g.enableScissor(mx, my, mx + n, hzy);
        MoonDisc.drawMoon(g, mx, my + rise, n, MoonDisc.FRAMES - 1, CodexTheme.CARIAN_ROYALTY.getAccentColor(), breath);
        g.disableScissor();
    }

    /** 大月要盈到的月相：卷首那一轮的月相，开关页是满月 */
    private int introFrame(long t) {
        if (tab == Tab.CODEX) {
            return detail.introFrame(t);
        }
        if (tab == Tab.OTHERS) {
            return foreignDetail.introFrame(t);
        }
        return MoonDisc.FRAMES - 1;
    }

    /** 开场的大月最后落到哪：卷首月盒，开关页湖上那一轮，都没有就淡掉 */
    private void renderIntroMoon(@Nonnull GuiGraphics g, long t, long e, int moonX) {
        int n = moonSize;
        int color = CodexTheme.CARIAN_ROYALTY.getAccentColor();
        boolean fade = false;
        if (tab == Tab.CODEX) {
            color = detail.moonColor();
        } else if (tab == Tab.OTHERS) {
            color = foreignDetail.moonColor();
        } else if (panoMoon > 0) {
            n = panoMoon;
        } else {
            fade = true;
        }
        intro.renderMoon(g, e, moonX, hzy, n, MoonriseIntro.bigSize(n, hzy), color, fade);
        if (intro.takeLanding(e) && !fade) {
            UiTheme.burst(moonX, hzy - 5 - n / 2, 8);
        }
    }

    /** 开场播到一半被点了或按了键：直接到播完的样子，这一下操作照常处理 */
    private void skipIntro() {
        long t = UiTheme.now();
        intro.finish(t);
        if (railAt > t - 700) {
            railAt = t - 1000;
        }
        detail.skipIntro();
        foreignDetail.skipIntro();
    }

    private void renderBand(@Nonnull GuiGraphics g, int mouseX, int mouseY, long t) {
        // 银轨：一道银线压一道暗线，翻开和重载时有一道月光沿着走
        g.fill(bx, by, bx + bw, by + 1, UiTheme.EDGE);
        g.fill(bx, by + 1, bx + bw, by + 2, UiTheme.SHADOW);
        long re = t - railAt;
        if (re >= 0 && re < 700) {
            int gx = bx - 60 + Math.round(UiTheme.steps(re, 700, 28) * (bw + 60));
            g.enableScissor(bx, by, bx + bw, by + 1);
            for (int i = 0; i < 60; i++) {
                float a = 1f - Math.abs(i - 29.5f) / 30f;
                g.fill(gx + i, by, gx + i + 1, by + 1, UiTheme.withAlpha(0xFFFFFFFF, a));
            }
            g.disableScissor();
        }

        UiTheme.sprite(g, "crest", bx + 2, by + 4, 15, 15);
        if (titleW > 0) {
            UiTheme.text(g, this.font, UiTheme.trim(this.font, this.title.getString(), titleW), bx + 21, by + 7, UiTheme.MOON);
        }

        int tabsEnd = bx;
        for (Tab value : Tab.values()) {
            int i = value.ordinal();
            boolean on = value == tab;
            boolean hovered = !on && UiTheme.hit(mouseX, mouseY, tabX[i], by + 2, tabW[i], 18);
            int h = on ? 22 : hovered ? 20 : 18;
            int dy = tabDrop(t, i);
            ribbon(g, tabX[i], by + 2 + dy, tabW[i], h, on ? "ribbon_on" : hovered ? "ribbon_hover" : "ribbon_idle");
            Component label = value.title();
            int lx = tabX[i] + (tabW[i] - this.font.width(label)) / 2;
            if (on) {
                g.drawString(this.font, label, lx, by + 7 + dy, UiTheme.INK_DARK, false);
            } else {
                UiTheme.text(g, this.font, label, lx, by + 7 + dy, hovered ? 0xFFFFFFFF : 0xFFA3B1E0);
            }
            tabsEnd = tabX[i] + tabW[i];
        }

        Component stat = statLine();
        int statW = this.font.width(stat);
        int statX = bx + bw - 2 - statW;
        if (statX >= tabsEnd + 8) {
            UiTheme.text(g, this.font, stat, statX, by + 7, UiTheme.TEXT_DIM);
        }

        if (!isCodexTab()) {
            return;
        }
        boolean focused = searchBox != null && searchBox.isFocused();
        boolean searchHot = UiTheme.hit(mouseX, mouseY, searchRect[0], searchRect[1], searchRect[2], searchRect[3]);
        UiTheme.plate(g, searchRect[0], searchRect[1], searchRect[2], searchRect[3],
                focused ? 0xF00A1028 : UiTheme.PANEL,
                focused ? UiTheme.ACCENT : searchHot ? UiTheme.EDGE : UiTheme.BORDER);
        UiTheme.sprite(g, "mag", searchRect[0] + 5, searchRect[1] + 4, 7, 7);

        renderFilterSlot(g, mouseX, mouseY);

        if (!history.isEmpty()) {
            int[] r = backButtonRect();
            UiTheme.button(g, this.font, Component.translatable("carianstyle.codex.back"),
                    r[0], r[1], r[2], 14, true, mouseX, mouseY);
        }
        if (tab == Tab.CODEX) {
            UiTheme.button(g, this.font, Component.translatable("carianstyle.codex.reload"),
                    reloadRect[0], reloadRect[1], reloadRect[2], 14, true, mouseX, mouseY);
        }
    }

    /** 返回按钮的实际位置：附魔百科页在重载数值左边，其他附魔页没有重载数值，靠右 */
    private int[] backButtonRect() {
        if (tab == Tab.CODEX) {
            return backRect;
        }
        return new int[]{reloadRect[0] + reloadRect[2] - backRect[2], reloadRect[1], backRect[2], 14};
    }

    /** 丝带落下：-12 落到 +2 再回到 0，三条依次错开 60 毫秒 */
    private int tabDrop(long t, int index) {
        long e;
        float dur;
        int n;
        if (droppedTab == index && t - tabDroppedAt < 280) {
            e = t - tabDroppedAt;
            dur = 280;
            n = 7;
        } else {
            e = t - intro.start() - MoonriseIntro.TABS_AT - index * 60L;
            dur = 300;
            n = 8;
            if (e >= dur) {
                return 0;
            }
        }
        float p = UiTheme.steps(e, dur, n);
        float y = p < 0.7f ? -12 + 14 * (p / 0.7f) : 2 - 2 * ((p - 0.7f) / 0.3f);
        return Math.round(y);
    }

    /** 丝带贴图 44 宽，字长的标签从中间那一列拉宽 */
    private static void ribbon(@Nonnull GuiGraphics g, int x, int y, int w, int h, @Nonnull String name) {
        if (w <= TAB_W) {
            UiTheme.sprite(g, name, x, y, TAB_W, h);
            return;
        }
        UiTheme.sprite(g, name, x, y, 22, h, 0, 0, 22, h, TAB_W, h);
        UiTheme.sprite(g, name, x + 22, y, w - TAB_W, h, 21, 0, 1, h, TAB_W, h);
        UiTheme.sprite(g, name, x + w - 22, y, 22, h, 22, 0, 22, h, TAB_W, h);
    }

    @Nonnull
    private Component statLine() {
        if (tab == Tab.CODEX) {
            return Component.translatable("carianstyle.codex.stat.count",
                    num(list.entryCount(), UiTheme.MOON), num(EnchantmentCodex.getAll().size(), UiTheme.MOON));
        }
        if (tab == Tab.OTHERS) {
            return Component.translatable("carianstyle.codex.stat.count",
                    num(foreignList.entryCount(), UiTheme.MOON), num(ForeignCodex.getAll().size(), UiTheme.MOON));
        }
        return Component.translatable("carianstyle.codex.stat.effects",
                num(enabledEffectCount(), UiTheme.MOON), num(VisualEffectType.values().length, UiTheme.MOON));
    }

    @Nonnull
    private static Component num(int value, int color) {
        return Component.literal(String.valueOf(value)).withStyle(s -> s.withColor(color & 0xFFFFFF));
    }

    private void renderSpread(@Nonnull GuiGraphics g, int mouseX, int mouseY, long t, long e) {
        int top = spreadTop;
        int h = spreadH;
        boolean toggles = tab == Tab.EFFECTS;

        // 两页从书缝往外展开：开场跟着月光走，切标签 0.26 秒
        float unfold = intro.reveal(e);
        if (turnedAt != 0L && t - turnedAt < 260) {
            unfold = Math.min(unfold, UiTheme.steps(t - turnedAt, 260, 8));
        }
        int lVisible = Math.round(lw * unfold);
        int rVisible = Math.round(rightW * unfold);

        renderGutter(g, bx + lw, top, h, e - MoonriseIntro.UNFOLD_AT - 60);

        // 没展开的页也照画（裁成零宽）：开关、等级牌的点击位置是画的时候记的，开场中途点下去也要点得中
        g.enableScissor(bx + lw - lVisible, top, bx + lw, top + h);
        UiTheme.page(g, bx, top, lw, h, true);
        if (toggles) {
            togglePage.renderLeft(g, this.font, mouseX, mouseY);
        } else if (tab == Tab.CODEX) {
            list.render(g, this.font, mouseX, mouseY);
        } else {
            foreignList.render(g, this.font, mouseX, mouseY);
        }
        UiTheme.pageCorners(g, bx, top, lw, h);
        g.disableScissor();

        g.enableScissor(rightX, top, rightX + rVisible, top + h);
        UiTheme.page(g, rightX, top, rightW, h, toggles);
        if (toggles) {
            togglePage.renderRight(g, this.font, mouseX, mouseY);
        } else if (tab == Tab.CODEX) {
            detail.render(g, this.font, mouseX, mouseY);
        } else {
            foreignDetail.render(g, this.font, mouseX, mouseY);
        }
        UiTheme.pageCorners(g, rightX, top, rightW, h);
        g.disableScissor();
    }

    /** 书缝：左右暗到中间，上面垂一条淡金书签带 */
    private void renderGutter(@Nonnull GuiGraphics g, int x, int top, int h, long dropElapsed) {
        for (int i = 0; i < GUT; i++) {
            float p = (i + 0.5f) / GUT;
            float k = p < 0.45f ? p / 0.45f : p > 0.55f ? (1f - p) / 0.45f : 1f;
            int edge = 0xE60C122C;
            int mid = 0xF203050C;
            int a = Math.round(0xE6 + (0xF2 - 0xE6) * k);
            g.fill(x + i, top, x + i + 1, top + h, (a << 24) | (UiTheme.mix(edge, mid, k) & 0xFFFFFF));
        }
        int gh = large ? 26 : 18;
        int gx = x + 2;
        int gy = top - 3;
        float drop = UiTheme.steps(dropElapsed, 500, 10);
        int visible = Math.round((gh + 3) * drop);
        if (visible <= 0) {
            return;
        }
        g.enableScissor(gx, gy, gx + 5, gy + visible);
        g.fill(gx, gy, gx + 1, gy + gh, 0xFFE9D59A);
        g.fill(gx + 1, gy, gx + 4, gy + gh, 0xFFCDB36A);
        g.fill(gx + 4, gy, gx + 5, gy + gh, 0xFF8F7A40);
        UiTheme.sprite(g, "gtail", gx, gy + gh, 5, 3);
        g.disableScissor();
    }

    private void renderFilterSlot(@Nonnull GuiGraphics g, int mouseX, int mouseY) {
        int fx = filterRect[0];
        int fy = filterRect[1];
        boolean hovered = UiTheme.hit(mouseX, mouseY, fx, fy, 18, 18);
        boolean hot = hovered || picker.isOpen();
        ItemStack stack = filter.getStack();
        UiTheme.plate(g, fx, fy, 18, 18, hot ? UiTheme.PANEL_HOVER : UiTheme.PANEL, hot ? UiTheme.SILVER : UiTheme.BORDER);

        int textX = fx + 23;
        if (stack != null) {
            g.renderItem(stack, fx + 1, fy + 1);
            g.renderItemDecorations(this.font, stack, fx + 1, fy + 1);
            int[] c = filterClearRect;
            boolean clearHovered = UiTheme.hit(mouseX, mouseY, c[0], c[1], c[2], c[3]);
            UiTheme.plate(g, c[0], c[1], 9, 9, UiTheme.PANEL, clearHovered ? UiTheme.DANGER : UiTheme.BORDER);
            UiTheme.sprite(g, clearHovered ? "x_h" : "x", c[0] + 2, c[1] + 2, 5, 5);
            textX = c[0] + 9 + 5;
        } else {
            int plus = hot ? 0xFFFFFFFF : UiTheme.TEXT_DIM;
            g.fill(fx + 6, fy + 8, fx + 12, fy + 10, plus);
            g.fill(fx + 8, fy + 6, fx + 10, fy + 12, plus);
        }

        filterHovered = hovered;
        if (filterCompact) {
            return;
        }
        int limit = bx + bw;
        if (!history.isEmpty()) {
            limit = backButtonRect()[0];
        } else if (tab == Tab.CODEX) {
            limit = reloadRect[0];
        }
        int textWidth = limit - 8 - textX;
        if (textWidth < 24) {
            return;
        }
        if (stack != null) {
            UiTheme.trimmed(g, this.font, stack.getHoverName(), textX, fy, textWidth, UiTheme.TEXT, true);
            UiTheme.trimmed(g, this.font, filterSummary(true), textX, fy + 10, textWidth, UiTheme.TEXT_DIM, false);
        } else {
            UiTheme.trimmed(g, this.font, Component.translatable("carianstyle.codex.filter.empty_title"),
                    textX, fy, textWidth, hovered ? 0xFFFFFFFF : UiTheme.TEXT, true);
            UiTheme.trimmed(g, this.font, Component.translatable("carianstyle.codex.filter.empty_hint"),
                    textX, fy + 10, textWidth, UiTheme.TEXT_DIM, false);
        }
    }

    /** 顶栏那一行合计；colored 时数字分色（可附绿、被挡红、过贵黄） */
    @Nonnull
    private Component filterSummary(boolean colored) {
        Object fit = colored ? num(filter.getFitCount(), UiTheme.ON) : filter.getFitCount();
        Object blocked = colored ? num(filter.getBlockedCount(), UiTheme.DANGER) : filter.getBlockedCount();
        if (filter.getExpensiveCount() > 0) {
            Object exp = colored ? num(filter.getExpensiveCount(), UiTheme.WARN) : filter.getExpensiveCount();
            return Component.translatable("carianstyle.codex.filter.summary_expensive", fit, blocked, exp);
        }
        return Component.translatable("carianstyle.codex.filter.summary", fit, blocked);
    }

    private void renderFilterTooltip(@Nonnull GuiGraphics g, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>(3);
        ItemStack stack = filter.getStack();
        if (stack == null) {
            lines.add(Component.translatable("carianstyle.codex.filter.empty_title"));
            lines.add(Component.translatable("carianstyle.codex.filter.empty_hint")
                    .withStyle(style -> style.withColor(UiTheme.TEXT_DIM & 0xFFFFFF)));
        } else {
            lines.add(stack.getHoverName());
            lines.add(filterSummary(false).copy().withStyle(style -> style.withColor(UiTheme.ON & 0xFFFFFF)));
            lines.add(Component.translatable("carianstyle.codex.filter.slot_tip")
                    .withStyle(style -> style.withColor(UiTheme.TEXT_DIM & 0xFFFFFF)));
        }
        // 物品图标画在 z≈150，提示框要压在上面
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        g.pose().popPose();
    }

    private void togglePicker() {
        if (picker.isOpen()) {
            picker.close();
            return;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        picker.open(filterRect[0], filterRect[1] + filterRect[3] + 4, this.width, this.height,
                this.minecraft.player.getInventory());
    }

    private void putItem(@Nonnull Inventory inventory, int slot) {
        filter.set(inventory.getItem(slot), slot);
        refreshList();
        turnedAt = UiTheme.now();
    }

    private void clearItem() {
        filter.clear();
        refreshList();
        turnedAt = UiTheme.now();
    }

    @Override
    public void tick() {
        super.tick();
        // 不暂停游戏，来源那一格的附魔可能在开着界面时变了，跟着更新
        if (filter.isActive() && this.minecraft != null && this.minecraft.player != null
                && filter.follow(this.minecraft.player.getInventory())) {
            refreshList();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (intro.running(UiTheme.now())) {
            skipIntro();
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && picker.isOpen()) {
            picker.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int enabledEffectCount() {
        int count = 0;
        for (VisualEffectType type : VisualEffectType.values()) {
            if (VisualToggle.isEnabled(type)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (intro.running(UiTheme.now())) {
            skipIntro();
        }
        if (picker.isOpen()) {
            if (this.minecraft == null || this.minecraft.player == null) {
                picker.close();
                return true;
            }
            Inventory inventory = this.minecraft.player.getInventory();
            int result = picker.mouseClicked(mouseX, mouseY, inventory);
            if (result >= 0) {
                picker.close();
                putItem(inventory, result);
            } else if (result == InventoryPicker.CLEAR) {
                picker.close();
                clearItem();
            }
            return true;
        }

        if (searchBox != null && searchBox.visible
                && UiTheme.hit(mouseX, mouseY, searchRect[0], searchRect[1], searchRect[2], searchRect[3])) {
            searchBox.setFocused(true);
            setFocused(searchBox);
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
        }

        if (tab == Tab.CODEX && UiTheme.hit(mouseX, mouseY, reloadRect[0], reloadRect[1], reloadRect[2], reloadRect[3])) {
            reloadAll();
            return true;
        }
        int[] back = backButtonRect();
        if (isCodexTab() && !history.isEmpty() && UiTheme.hit(mouseX, mouseY, back[0], back[1], back[2], back[3])) {
            navigateBack();
            return true;
        }

        // 左键开选择器，右键直接清除（和幽灵物品槽的习惯一致）
        if (isCodexTab()) {
            if (filter.isActive() && UiTheme.hit(mouseX, mouseY, filterClearRect[0], filterClearRect[1],
                    filterClearRect[2], filterClearRect[3])) {
                clearItem();
                return true;
            }
            if (UiTheme.hit(mouseX, mouseY, filterRect[0], filterRect[1], filterRect[2], filterRect[3])) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    clearItem();
                } else {
                    togglePicker();
                }
                return true;
            }
        }

        for (Tab value : Tab.values()) {
            int i = value.ordinal();
            if (UiTheme.hit(mouseX, mouseY, tabX[i], by + 2, tabW[i], value == tab ? 22 : 18)) {
                switchTab(value, true);
                return true;
            }
        }

        if (tab == Tab.CODEX) {
            // 列表点选不记历史：左边一直摆着，用不着「回到上一个」；跳走的冲突牌才记
            String clickedInList = list.mouseClicked(mouseX, mouseY);
            if (clickedInList != null) {
                selectEnchantment(clickedInList);
                return true;
            }
            String clickedConflict = detail.mouseClicked(mouseX, mouseY);
            if (clickedConflict != null) {
                navigateTo(clickedConflict);
                return true;
            }
        } else if (tab == Tab.OTHERS) {
            String clickedInList = foreignList.mouseClicked(mouseX, mouseY);
            if (clickedInList != null) {
                selectForeign(clickedInList);
                return true;
            }
            String clickedConflict = foreignDetail.mouseClicked(mouseX, mouseY);
            if (clickedConflict != null) {
                navigateTo(clickedConflict);
                return true;
            }
        } else if (togglePage.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (intro.running(UiTheme.now())) {
            skipIntro();
        }
        if (picker.isOpen()) {
            return true;
        }
        if (tab == Tab.CODEX) {
            if (list.mouseScrolled(mouseX, mouseY, delta) || detail.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        } else if (tab == Tab.OTHERS) {
            if (foreignList.mouseScrolled(mouseX, mouseY, delta) || foreignDetail.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        } else if (togglePage.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
