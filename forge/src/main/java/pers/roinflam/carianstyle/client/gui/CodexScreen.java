package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.codex.CodexTheme;
import pers.roinflam.carianstyle.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.codex.ForeignCodex;
import pers.roinflam.carianstyle.codex.ForeignMeta;
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
 * 卡利亚式附魔 —— 百科与特效开关主界面。
 *
 * <h3>两个标签页</h3>
 * <ul>
 *   <li><b>附魔百科</b>：左栏可搜索的附魔列表，右栏详情（描述 / 适用物品 / 等级上限 /
 *       冲突关系 / 数值）。冲突条目可点击跳转，带前进后退历史。</li>
 *   <li><b>特效开关</b>：按分组列出全部世界特效与 HUD，逐项开关，默认全开。</li>
 * </ul>
 *
 * <h3>放入物品</h3>
 * <p>
 * 搜索框右边的物品槽：点开弹出背包，挑一件放上去，两个百科页的列表就只剩与它有关的附魔，
 * 按「可以附上 / 已经有了 / 被已有附魔挡住」分组，详情最上方给出结论。
 * 玩家最常问的就是「我这把东西能附什么」，以前得把一百多个附魔的适用物品挨个对一遍。
 * 判定逻辑见 {@link pers.roinflam.carianstyle.codex.ItemFit}，状态见 {@link ItemFilter}。
 * </p>
 *
 * <h3>为什么不是暂停界面</h3>
 * <p>
 * {@link #isPauseScreen()} 返回 false，单人游戏时世界继续渲染。
 * 这样玩家开着特效开关面板就能<b>实时</b>看到关掉某个特效的效果，
 * 不用「关界面 → 看一眼 → 再开界面」来回折腾。
 * </p>
 *
 * <h3>数据准备时机</h3>
 * <p>
 * {@link EnchantmentCodex} 是惰性构建的，第一次打开本界面时才扫描注册表并读语言文件。
 * 之所以不在模组初始化时预建，是因为搜索索引依赖语言表，而语言表在客户端资源加载完成后才可用。
 * 首次打开会有几十毫秒的构建耗时，之后走缓存。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class CodexScreen extends Screen {

    /** 顶栏高度 */
    /**
     * 顶栏高度。
     * <p>
     * 从 52 提到 62：标签页占 y+4~y+22，搜索框原来放在 y+26，而它的外框要从
     * {@code y-5} 开始画，正好压在标签页底边上。现在搜索框下移到 y+32，
     * 外框上沿落在 y+28，与标签页之间留出 6px。
     * </p>
     */
    private static final int HEADER_HEIGHT = 62;

    /** 界面四周留白 */
    private static final int MARGIN = 16;

    /** 左栏宽度 */
    private static final int LIST_WIDTH = 190;

    /** 标签页 */
    private enum Tab {
        /** 附魔百科 */
        CODEX("carianstyle.codex.tab.codex"),
        /** 特效开关 */
        EFFECTS("carianstyle.codex.tab.effects"),
        /** 其他附魔（原版与其它模组） */
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

    /** 上一次打开时停留的标签页，跨开关界面保留 */
    private static Tab lastTab = Tab.CODEX;

    /** 上一次选中的附魔 id，跨开关界面保留 */
    private static String lastSelectedId = null;

    /** 开场动画时长（毫秒） */
    private static final float ANIM_MS = 220f;

    /**
     * 收场动画时长（毫秒）。
     * <p>比开场短：关界面时玩家已经决定要走了，让他多等是烦人；
     * 而开场时他正准备开始看，稍慢一点反而显得稳。</p>
     */
    private static final float CLOSE_MS = 180f;

    /** 开场时面板从下方多少像素滑入 */
    private static final float OPEN_SLIDE = 12f;

    /**
     * 各稀有度档的光尘数量、亮度、速度。
     *
     * <h3>为什么不只靠颜色</h3>
     * <p>
     * 上一版只把装饰色往稀有度偏了四成，结果「普通」和「极稀有」的色值分别是
     * {@code #C0A672} 和 {@code #DBAC63}——放在一起对比都费劲，单独看根本分不出。
     * </p>
     * <p>
     * 颜色在暗底小面积上本来就不敏感。<b>数量和流速是一眼能感觉到的</b>：
     * 打开一个极稀有附魔时满屏金尘缓缓涌动，打开普通的只有零星几点，
     * 不用比较也知道这两个不是一档。
     * </p>
     * <p>下标即 {@code getRarityOrder()}：0 最常见，3 最稀有。</p>
     */
    private static final int[] MOTE_COUNT = {14, 26, 42, 64};

    /** 各稀有度档的光尘亮度系数 */
    private static final float[] MOTE_ALPHA = {0.5f, 0.7f, 0.9f, 1.15f};

    /** 各稀有度档的光尘上升速度倍率 */
    private static final float[] MOTE_SPEED = {0.6f, 0.85f, 1.15f, 1.6f};

    /**
     * 收场时遮罩保持不透明的比例。
     * <p>在这之前面板一直有背景垫着，不会悬空在世界上。</p>
     */
    private static final float SCRIM_HOLD = 0.45f;

    /**
     * 收场时面板向下滑出的距离系数（相对屏幕高度）。
     * <p>
     * 收场没有内容淡出——文字是直接画上去的，没有全局透明度可调——
     * 所以必须让面板<b>真的滑出可视区</b>，而不是挪一小段后原地消失。
     * 取屏幕高度的 1.05 倍，保证最下面那一行也出了画面。
     * </p>
     */
    private static final float CLOSE_SLIDE_RATIO = 1.05f;

    /** 界面打开的时刻 */
    private long openedAt;

    /**
     * 请求关闭的时刻；0 表示尚未请求关闭。
     * <p>
     * 原版 {@code Screen} 的关闭是同步的——{@code onClose()} 一调界面就没了，
     * 没有留给收场动画的时间。这里把 ESC 拦下来只做标记，
     * 等 {@link #render} 把进度跑到 0 再真正关闭。
     * </p>
     */
    private long closingAt;

    /** 当前标签页 */
    private Tab tab = lastTab;

    /** 左栏列表 */
    private final CodexList list = new CodexList();

    /** 右栏详情（本模组附魔） */
    private final CodexDetail detail = new CodexDetail();

    /** 右栏详情（外部附魔） */
    private final ForeignDetail foreignDetail = new ForeignDetail();

    /** 「其他附魔」标签页的左栏列表 */
    private final CodexList foreignList = new CodexList();

    /** 「其他附魔」标签页当前选中的键 */
    private static String lastForeignKey = null;

    /** 特效开关面板 */
    private final TogglePanel togglePanel = new TogglePanel();

    /** 跳转历史（后退栈） */
    private final Deque<String> history = new ArrayDeque<>();

    /** 搜索框 */
    private EditBox searchBox;

    /**
     * 自绘按钮的矩形（x, y, w, h）。
     * <p>
     * 不再用原版 {@code Button}：它是九宫格贴图的圆角灰蓝按钮，跟本界面的暗褐 + 金色
     * 完全是两套视觉语言，而且贴图会跟着资源包变，旁边自绘的部分却不会变。
     * 详见 {@link UiTheme#button}。
     * </p>
     */
    private final int[] backRect = new int[4];

    /** 「重载数值」按钮矩形 */
    private final int[] reloadRect = new int[4];

    /** 「放入物品」槽的矩形 */
    private final int[] filterRect = new int[4];

    /** 「放入物品」槽右侧的清除按钮矩形；只在放了物品时出现 */
    private final int[] filterClearRect = new int[4];

    /** 窄窗口：物品槽挤进搜索框右端，旁边不写说明文字（改为悬停提示） */
    private boolean filterCompact;

    /** 本帧鼠标是否停在物品槽上（由 renderFilterSlot 写入，render 末尾据此画提示） */
    private boolean filterHovered;

    /** 放入物品筛选 */
    private final ItemFilter filter = new ItemFilter();

    /** 背包选择器 */
    private final InventoryPicker picker = new InventoryPicker();

    /**
     * 搜索框的外框矩形。
     * <p>
     * 单独存一份而不是用 {@code searchBox} 自己的边界，是因为
     * {@code setBordered(false)} 之后 {@code EditBox} 把文本画在
     * {@code (getX(), getY())}——既不左缩进也不垂直居中。
     * 要让文本在框里居中，输入框的坐标就必须和视觉外框错开，
     * 于是「点哪里算点中输入框」也得由外框说了算，而不是输入框自己的边界。
     * </p>
     */
    private final int[] searchRect = new int[4];

    /** 顶部标签页按钮的命中区域，随窗口尺寸重算 */
    private final int[] tabX = new int[Tab.values().length];

    /** 标签页按钮宽度 */
    private final int[] tabWidth = new int[Tab.values().length];

    /** 标签页按钮纵坐标 */
    private int tabY;

    /** 标签页指示条当前的插值位置，用于切换时滑动而不是瞬移 */
    private float indicatorX = -1f;

    /** 标签页指示条当前宽度 */
    private float indicatorW;

    public CodexScreen() {
        super(Component.translatable("carianstyle.codex.title"));
    }

    @Override
    protected void init() {
        VisualToggle.ensureLoaded();
        boolean firstInit = openedAt == 0L;
        if (firstInit) {
            openedAt = System.currentTimeMillis();
        }
        // 改窗口大小会重新 init，选择器的位置是按旧布局算的，直接收起
        picker.close();
        detail.setFilter(filter);
        foreignDetail.setFilter(filter);

        int contentTop = MARGIN + HEADER_HEIGHT;
        int contentHeight = this.height - contentTop - MARGIN;
        int contentLeft = MARGIN;
        int contentWidth = this.width - MARGIN * 2;

        list.setBounds(contentLeft, contentTop, LIST_WIDTH, contentHeight);
        detail.setBounds(contentLeft + LIST_WIDTH + 8, contentTop,
                contentWidth - LIST_WIDTH - 8, contentHeight);
        foreignList.setBounds(contentLeft, contentTop, LIST_WIDTH, contentHeight);
        foreignDetail.setBounds(contentLeft + LIST_WIDTH + 8, contentTop,
                contentWidth - LIST_WIDTH - 8, contentHeight);
        togglePanel.setBounds(contentLeft, contentTop, contentWidth, contentHeight);

        // ==================== 顶栏控件 ====================

        // 放入物品槽（20）+ 清除按钮（12）+ 间隙，共 34px。
        // 窗口够宽时放在搜索框右边；缩放后宽度不到四百左右时，靠右对齐的「返回 / 重载数值」
        // 会压到它们身上——被盖住的槽还会抢走按钮的点击。这时改从搜索框右端让出位置，
        // 两组控件在任何宽度下都不重叠
        int filterBlock = 20 + 2 + 12;
        int buttonsLeft = this.width - MARGIN - 132;
        filterCompact = buttonsLeft < contentLeft + LIST_WIDTH + 8 + filterBlock + 6;
        // 紧凑时左侧控件（搜索框 + 物品槽）整体只用到按钮左边 6px 为止，搜索框吃剩下的宽度。
        // 最窄的 320 宽下按钮从 172 开始，而左栏本来要到 206——只挪物品槽不缩搜索框的话还是会撞
        int leftEnd = filterCompact ? Math.min(contentLeft + LIST_WIDTH, buttonsLeft - 6) : 0;

        // 外框 20px 高；字高 8px，所以文本 y 要落在 框顶 + 6 才是居中
        setRect(searchRect, contentLeft + 1, MARGIN + 30,
                filterCompact ? leftEnd - 1 - filterBlock - 4 - (contentLeft + 1) : LIST_WIDTH - 2, 20);
        searchBox = new EditBox(this.font,
                searchRect[0] + 6, searchRect[1] + 6, searchRect[2] - 12, 8,
                Component.translatable("carianstyle.codex.search"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(text -> refreshList());
        // 关掉原版边框，改由本界面自己画一圈，与面板/按钮同一套描边
        searchBox.setBordered(false);
        addRenderableWidget(searchBox);

        setRect(backRect, this.width - MARGIN - 132, MARGIN + 31, 62, 18);
        setRect(reloadRect, this.width - MARGIN - 66, MARGIN + 31, 66, 18);

        // 放入物品槽紧挨搜索框右边、与它同高：两者都是「缩小左栏范围」的手段，放在一起
        setRect(filterRect, filterCompact
                ? leftEnd - 1 - filterBlock
                : contentLeft + LIST_WIDTH + 8, searchRect[1], 20, 20);
        setRect(filterClearRect, filterRect[0] + filterRect[2] + 2, searchRect[1], 12, 20);

        // 标签页命中区域
        tabY = MARGIN + 4;
        int cursorX = contentLeft;
        for (Tab value : Tab.values()) {
            int width = this.font.width(value.title()) + 18;
            tabX[value.ordinal()] = cursorX;
            tabWidth[value.ordinal()] = width;
            cursorX += width + 4;
        }

        if (firstInit && this.minecraft != null && this.minecraft.player != null) {
            filter.restore(this.minecraft.player.getInventory());
        }

        refreshList();

        // 恢复上次选中项
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

        updateWidgetVisibility();
    }

    /**
     * 写入一个矩形。
     *
     * @param rect   目标数组（x, y, w, h）
     * @param x      左
     * @param y      上
     * @param width  宽
     * @param height 高
     */
    private static void setRect(int[] rect, int x, int y, int width, int height) {
        rect[0] = x;
        rect[1] = y;
        rect[2] = width;
        rect[3] = height;
    }

    /**
     * 按当前搜索词重建左栏列表。
     */
    private void refreshList() {
        String keyword = searchBox == null ? "" : searchBox.getValue();
        // 放入物品筛选叠在搜索之上：先按关键字筛，再按物品筛并重新分组
        List<EnchantmentMeta> ownHits = EnchantmentCodex.search(keyword, (CodexTheme) null);
        list.setEntries(filter.apply(ownHits));
        list.select(list.getSelectedId());
        list.setEmptyHint(emptyHint(keyword, ownHits.isEmpty()));
        // 两个标签页共用同一个搜索框：切过去时结果已经是筛好的，
        // 不必再输一遍关键字
        List<ForeignMeta> foreignHits = ForeignCodex.search(keyword);
        foreignList.setEntries(filter.apply(foreignHits));
        foreignList.select(foreignList.getSelectedId());
        foreignList.setEmptyHint(emptyHint(keyword, foreignHits.isEmpty()));
    }

    /**
     * 列表为空时的提示，按「是搜不到，还是搜到了但放不上去」区分。
     * <p>
     * 两个列表各自判断：同一个关键字可能在一边搜到、另一边搜不到。
     * 搜到了却被物品筛掉时若还说「没有匹配的附魔」，玩家会以为是关键字打错了，
     * 而真正的答案——「这个附魔上不了这件东西」——正是这个功能要回答的问题。
     * </p>
     *
     * @param keyword     搜索词
     * @param searchEmpty 按关键字搜索的结果是否为空
     * @return 提示
     */
    @Nonnull
    private Component emptyHint(@Nonnull String keyword, boolean searchEmpty) {
        if (searchEmpty || !filter.isActive()) {
            return Component.translatable("carianstyle.codex.list.empty_search");
        }
        return Component.translatable(keyword.trim().isEmpty()
                ? "carianstyle.codex.list.empty_fit"
                : "carianstyle.codex.list.empty_search_fit");
    }

    /**
     * 选中某个本模组附魔并同步详情面板。
     *
     * <p>
     * 不在这里记历史：历史由 {@link #navigateTo} 统一负责。
     * 早先这个方法带一个 {@code pushHistory} 参数，两条入口各传各的，
     * 结果「列表点选记历史、跨页跳转不记」这种不一致要靠调用方自觉——
     * 把职责收到一处之后就不会再有这个问题。
     * </p>
     *
     * @param id 附魔 id
     */
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

    /**
     * 选中一个外部附魔。
     *
     * @param key {@code 命名空间:路径}
     */
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

    /**
     * 按键跳转，自动判断该去哪个标签页。
     *
     * <p>
     * 判据是键里有没有冒号：本模组的附魔用裸 id（{@code gravitas}），
     * 外部附魔用完整注册名（{@code minecraft:knockback}）。
     * 这不是巧合——{@code CodexEntry.getKey()} 就是按这个约定设计的，
     * 正好让跳转不需要额外传一个「目标标签页」参数。
     * </p>
     *
     * @param key 目标键
     */
    private void navigateTo(@Nullable String key) {
        if (key == null) {
            return;
        }
        // 跳转前把当前位置压栈。历史里存的是完整键，而键本身就带着
        // 「它属于哪个标签页」的信息，所以后退时不必再记一份标签页
        String current = currentKey();
        if (current != null && !current.equals(key)) {
            history.push(current);
            while (history.size() > 64) {
                history.removeLast();
            }
        }
        jumpTo(key);
    }

    /**
     * 不记录历史地跳到某个键。
     *
     * @param key 目标键
     */
    private void jumpTo(@Nullable String key) {
        if (key == null) {
            return;
        }
        if (key.indexOf(':') >= 0) {
            switchTab(Tab.OTHERS);
            selectForeign(key);
        } else {
            switchTab(Tab.CODEX);
            selectEnchantment(key);
        }
    }

    /**
     * @return 当前标签页选中的键；不在百科页或未选中时为 null
     */
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

    /**
     * 取本帧的装饰色调。
     *
     * @return 选中条目的稀有度色；未选中或在特效页时用默认金色
     */
    /**
     * 取当前选中条目的稀有度档位。
     *
     * @return 0~3，越大越稀有；未选中时按最低档
     */
    private int currentRarityOrder() {
        if (tab == Tab.CODEX) {
            EnchantmentMeta meta = EnchantmentCodex.get(list.getSelectedId());
            // 本模组只有三档（普通/稀有/极稀有），映射到 1~3，
            // 让它整体比原版的「常见」亮一档——毕竟这些是模组的核心内容
            return meta == null ? 0 : Math.min(3, meta.getRarityOrder() + 1);
        }
        if (tab == Tab.OTHERS) {
            ForeignMeta meta = ForeignCodex.get(foreignList.getSelectedId());
            return meta == null ? 0 : Math.min(3, meta.getRarityOrder());
        }
        return 0;
    }

    private int currentTint() {
        if (tab == Tab.CODEX) {
            EnchantmentMeta meta = EnchantmentCodex.get(list.getSelectedId());
            if (meta != null) {
                return blendWithAccent(meta.getRarityColor());
            }
        } else if (tab == Tab.OTHERS) {
            ForeignMeta meta = ForeignCodex.get(foreignList.getSelectedId());
            if (meta != null) {
                return blendWithAccent(meta.getRarityColor());
            }
        }
        return UiTheme.ACCENT;
    }

    /**
     * 把稀有度色与基调金色混合。
     *
     * <h3>为什么不直接用稀有度色</h3>
     * <p>
     * 「普通」的稀有度色是灰褐，直接拿来当装饰色会让整个界面失去金色基调，
     * 看起来像掉了色而不是「这是个普通附魔」。混入金色之后，
     * 稀有度只是在基调上偏移一点色相——识别得出来，又不至于换掉整套配色。
     * </p>
     *
     * @param rarityColor 稀有度色
     * @return 混合后的颜色
     */
    private static int blendWithAccent(int rarityColor) {
        int ar = (UiTheme.ACCENT >> 16) & 0xFF;
        int ag = (UiTheme.ACCENT >> 8) & 0xFF;
        int ab = UiTheme.ACCENT & 0xFF;
        int rr = (rarityColor >> 16) & 0xFF;
        int rg = (rarityColor >> 8) & 0xFF;
        int rb = rarityColor & 0xFF;
        // 三七开：稀有度主导，金色只保底。
        // 六四开时「普通」和「极稀有」的结果几乎一样，等于白做
        int r = (ar * 3 + rr * 7) / 10;
        int g = (ag * 3 + rg * 7) / 10;
        int b = (ab * 3 + rb * 7) / 10;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * @return 当前是否处于两个百科页之一
     */
    private boolean isCodexTab() {
        return tab == Tab.CODEX || tab == Tab.OTHERS;
    }

    /**
     * 切换标签页。
     *
     * @param target 目标标签页
     */
    private void switchTab(@Nonnull Tab target) {
        if (tab != target) {
            tab = target;
            lastTab = target;
            picker.close();
            // 切页时收起搜索框焦点：否则切到「特效开关」后输入框已经不画了，
            // 那个闪动的光标却还留在原处
            if (searchBox != null) {
                searchBox.setFocused(false);
            }
            // 手动切页视为重新开始浏览，之前的跳转链路作废——
            // 否则返回按钮会把人送回一个他已经主动离开的页面
            history.clear();
            updateWidgetVisibility();
        }
    }

    /**
     * 后退到上一个浏览过的附魔。
     */
    private void navigateBack() {
        if (history.isEmpty()) {
            return;
        }
        // 用 jumpTo 而不是直接改列表：后退目标可能在另一个标签页，
        // 需要连标签页一起切回去
        jumpTo(history.pop());
        updateWidgetVisibility();
    }

    /**
     * 重载百科索引与数值配置。
     * <p>供服主改完 JSON 后不重启游戏就能看到新值。</p>
     */
    private void reloadAll() {
        EnchantmentValues.reload();
        EnchantmentCodex.invalidate();
        ForeignCodex.invalidate();
        // 数值表与配置变了，宝藏与否、禁用与否都可能跟着变，物品判定要重算
        filter.reevaluate();
        history.clear();
        refreshList();
        String selected = list.getSelectedId();
        detail.setMeta(selected == null ? null : EnchantmentCodex.get(selected));
        updateWidgetVisibility();
    }

    /**
     * 按当前标签页调整控件可见性。
     */
    private void updateWidgetVisibility() {
        boolean codex = tab == Tab.CODEX || tab == Tab.OTHERS;
        if (searchBox != null) {
            searchBox.visible = codex;
            searchBox.active = codex;
            if (!codex) {
                searchBox.setFocused(false);
            }
        }
        // 自绘按钮没有 visible/active 字段，可用性在绘制与点击时按同一条件判断，
        // 见 renderHeader 与 mouseClicked
    }

    /**
     * 当前动画进度：0 = 完全收起，1 = 完全展开。
     *
     * @return 缓动后的进度
     */
    /**
     * 遮罩的独立进度。
     *
     * <h3>为什么要和面板分开</h3>
     * <p>
     * 原来遮罩和面板共用一个进度：遮罩的透明度是线性跟随的，面板的位移却过了缓动曲线。
     * 收场时面板前半程几乎不动，遮罩却已经淡掉大半——观感就是
     * <b>「背景先没了，控件还浮在世界上，然后才消失」</b>。
     * </p>
     * <p>
     * 现在遮罩延后到收场进行到 {@value #SCRIM_HOLD} 之后才开始淡。
     * 顺序变成「面板先滑走 → 背景再淡出」，任何一帧都不会出现控件悬空。
     * </p>
     *
     * @return 遮罩进度，1 为完全不透明
     */
    private float scrimProgress() {
        if (closingAt == 0L) {
            // 开场时遮罩先到位，面板再滑进来，观感是「幕布拉上，东西送上台」
            return Math.min(1f, (System.currentTimeMillis() - openedAt) / (ANIM_MS * 0.6f));
        }
        float t = (System.currentTimeMillis() - closingAt) / CLOSE_MS;
        if (t <= SCRIM_HOLD) {
            return 1f;
        }
        return 1f - UiTheme.easeInCubic((t - SCRIM_HOLD) / (1f - SCRIM_HOLD));
    }

    private float animProgress() {
        long now = System.currentTimeMillis();
        if (closingAt > 0L) {
            // 收场用缓入：先几乎不动，再加速离场。
            //
            // 原来写的是 1 - easeOutCubic()，那是把缓出曲线倒过来用——
            // 按下 ESC 的一瞬间界面猛地弹开一大截，然后慢吞吞磨完剩下那点距离，
            // 观感就是「一顿」。两个方向必须用互为镜像的曲线才顺。
            return 1f - UiTheme.easeInCubic((now - closingAt) / CLOSE_MS);
        }
        return UiTheme.easeOutCubic((now - openedAt) / ANIM_MS);
    }

    @Override
    public void onClose() {
        if (closingAt == 0L) {
            // 只做标记，真正的关闭在 render 里等动画跑完
            closingAt = System.currentTimeMillis();
            return;
        }
        super.onClose();
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float p = animProgress();
        if (closingAt > 0L && p <= 0f) {
            super.onClose();
            return;
        }

        float time = (System.currentTimeMillis() % 600000L) / 1000f;

        // 本帧的装饰色调随选中附魔的稀有度变。
        //
        // 界面上金色是固定基调，但「极稀有」和「普通」看起来完全一样，
        // 稀有度这个信息只体现在一行小字上。让光尘、流光、角标、标题一起换色之后，
        // 打开一个极稀有附魔时整块界面的气质就变了——这是文字说不出来的东西。
        UiTheme.setTint(currentTint());

        // 遮罩走自己的曲线，收场时比面板晚淡出，避免控件悬空
        float scrim = scrimProgress();
        g.fill(0, 0, this.width, this.height, UiTheme.withAlpha(UiTheme.SCRIM, scrim));
        UiTheme.vignette(g, this.width, this.height, scrim);

        // 黄金树质感：缓缓上升的光尘。密度与速度随稀有度变化，
        // 光靠颜色区分不够明显——数量和流速是一眼就能感觉到的差别
        int rarity = currentRarityOrder();
        UiTheme.motes(g, 0, 0, this.width, this.height, time,
                MOTE_COUNT[rarity], scrim * MOTE_ALPHA[rarity], MOTE_SPEED[rarity]);

        // 面板四角的金饰在开场时从零长出来，替代原先那道扫过全屏的光带
        UiTheme.setReveal(closingAt > 0L ? 1f
                : Math.min(1f, (System.currentTimeMillis() - openedAt) / (ANIM_MS * 1.4f)));

        // 整体平移。用 pose 而不是逐个改坐标，文字与图标会一起动，不会各走各的。
        g.pose().pushPose();
        float slide = closingAt > 0L ? this.height * CLOSE_SLIDE_RATIO : OPEN_SLIDE;
        g.pose().translate(0f, (1f - p) * slide, 0f);

        // 选择器开着时它是模态的：底下的东西不响应悬停，免得鼠标移过选择器外的列表时
        // 那边也亮起来，让人以为点下去会选中那一行（实际只会收起选择器）
        boolean modal = picker.isOpen();
        int underX = modal ? -1 : mouseX;
        int underY = modal ? -1 : mouseY;

        filterHovered = false;
        renderHeader(g, underX, underY, time);

        if (tab == Tab.CODEX) {
            list.render(g, this.font, underX, underY);
            detail.render(g, this.font, underX, underY);
        } else if (tab == Tab.OTHERS) {
            foreignList.render(g, this.font, underX, underY);
            foreignDetail.render(g, this.font, underX, underY);
        } else {
            togglePanel.render(g, this.font, underX, underY);
        }

        super.render(g, underX, underY, partialTick);

        // 搜索框占位提示：自绘而不用 EditBox 自带的 hint，
        // 因为要画在 super.render 之后才不会被输入框自身的背景盖住
        if (searchBox != null && searchBox.visible && searchBox.getValue().isEmpty()
                && !searchBox.isFocused()) {
            // 坐标必须与 EditBox 无边框时的渲染点完全一致：(getX(), getY())。
            // 差 2px 就会看出占位文字和真实输入不在一条线上
            g.drawString(this.font, Component.translatable("carianstyle.codex.search"),
                    searchBox.getX(), searchBox.getY(), UiTheme.OFF, false);
        }

        // 选择器最后画，压在一切之上
        if (modal && this.minecraft != null && this.minecraft.player != null) {
            picker.render(g, this.font, this.minecraft.player.getInventory(), mouseX, mouseY,
                    filter.getSourceSlot());
        } else if (filterHovered && closingAt == 0L) {
            renderFilterTooltip(g, mouseX, mouseY);
        }

        g.pose().popPose();
    }

    /**
     * 绘制顶栏：标题、标签页、统计信息。
     *
     * @param g      绘制上下文
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param time   当前时间（秒），用于分隔线流光
     */
    private void renderHeader(@Nonnull GuiGraphics g, int mouseX, int mouseY, float time) {
        int left = MARGIN;
        int right = this.width - MARGIN;

        g.drawString(this.font, this.title, left, MARGIN - 10, UiTheme.ACCENT, false);

        for (Tab value : Tab.values()) {
            int index = value.ordinal();
            boolean active = value == tab;
            boolean hovered = UiTheme.hit(mouseX, mouseY, tabX[index], tabY, tabWidth[index], 18);

            g.fill(tabX[index], tabY, tabX[index] + tabWidth[index], tabY + 18,
                    active ? UiTheme.ACCENT_FILL : (hovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL));
            UiTheme.border(g, tabX[index], tabY, tabWidth[index], 18,
                    active ? UiTheme.ACCENT : UiTheme.BORDER);
            g.drawString(this.font, value.title(), tabX[index] + 9, tabY + 5,
                    active ? UiTheme.ACCENT : UiTheme.TEXT, false);
        }

        // 标签页下方的金色指示条：切换时从旧位置滑到新位置。
        // 瞬移的话，眼睛要重新找一次「现在选的是哪个」；滑动则会被视线自然跟随。
        int targetX = tabX[tab.ordinal()];
        int targetW = tabWidth[tab.ordinal()];
        if (indicatorX < 0f) {
            indicatorX = targetX;
            indicatorW = targetW;
        } else {
            indicatorX += (targetX - indicatorX) * 0.25f;
            indicatorW += (targetW - indicatorW) * 0.25f;
        }
        g.fill((int) indicatorX, tabY + 18, (int) (indicatorX + indicatorW), tabY + 20,
                UiTheme.ACCENT);

        // 搜索框外框：附魔百科与其他附魔两个标签页都有搜索
        if (searchBox != null && searchBox.visible) {
            g.fill(searchRect[0], searchRect[1],
                    searchRect[0] + searchRect[2], searchRect[1] + searchRect[3], UiTheme.PANEL);
            UiTheme.border(g, searchRect[0], searchRect[1], searchRect[2], searchRect[3],
                    searchBox.isFocused() ? UiTheme.ACCENT : UiTheme.BORDER);
        }

        if (isCodexTab()) {
            renderFilterSlot(g, mouseX, mouseY);
        }

        // 返回：只在真的能返回时才画出来。
        //
        // 原来是常驻显示、无处可回时置灰。问题是绝大多数时候它就是灰的——
        // 一个长期灰着的按钮读起来像装饰或者像坏了，没人会去想它是干嘛的。
        // 改成按需出现之后，它出现的那一刻恰好就是「你刚跳转过来」的时刻，
        // 用途不言自明，也不再占着视线。
        if (isCodexTab() && !history.isEmpty()) {
            UiTheme.button(g, this.font, Component.translatable("carianstyle.codex.back"),
                    backRect[0], backRect[1], backRect[2], backRect[3],
                    true, mouseX, mouseY);
        }

        // 重载数值：只重载本模组的 enchantment_values.json，
        // 对原版和其它模组的附魔没有任何作用，所以只在「附魔百科」页出现。
        // 摆一个按下去什么都不会变的按钮，比不摆更糟
        if (tab == Tab.CODEX) {
            UiTheme.button(g, this.font, Component.translatable("carianstyle.codex.reload"),
                    reloadRect[0], reloadRect[1], reloadRect[2], reloadRect[3],
                    true, mouseX, mouseY);
        }

        // 右侧统计
        Component stat;
        if (tab == Tab.CODEX) {
            stat = Component.translatable("carianstyle.codex.stat.count",
                    list.entryCount(), EnchantmentCodex.getAll().size());
        } else if (tab == Tab.OTHERS) {
            stat = Component.translatable("carianstyle.codex.stat.count",
                    foreignList.entryCount(), ForeignCodex.getAll().size());
        } else {
            stat = Component.translatable("carianstyle.codex.stat.effects",
                    enabledEffectCount(), VisualEffectType.values().length);
        }
        int statWidth = this.font.width(stat);
        g.drawString(this.font, stat, right - statWidth, tabY + 5, UiTheme.TEXT_DIM, false);

        int dividerY = MARGIN + HEADER_HEIGHT - 6;
        UiTheme.divider(g, left, dividerY, right - left);
        UiTheme.sweep(g, left, dividerY, right - left, time, 0f);
    }

    /**
     * 绘制「放入物品」槽及其右侧的说明。
     *
     * <h3>说明文字为什么写两行</h3>
     * <p>
     * 空着的时候，一个空方框谁也看不懂是干嘛的——第二行直接写出用途「看它能附哪些附魔」。
     * 放了物品之后第一行是物品名，第二行换成两份百科合计的结果，
     * 不用切标签页就知道原版那边还有几个能附。
     * </p>
     *
     * @param g      绘制上下文
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    private void renderFilterSlot(@Nonnull GuiGraphics g, int mouseX, int mouseY) {
        int fx = filterRect[0];
        int fy = filterRect[1];
        int fw = filterRect[2];
        int fh = filterRect[3];
        boolean hovered = UiTheme.hit(mouseX, mouseY, fx, fy, fw, fh);
        ItemStack stack = filter.getStack();

        g.fill(fx, fy, fx + fw, fy + fh, hovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL);
        UiTheme.border(g, fx, fy, fw, fh,
                picker.isOpen() || hovered ? UiTheme.ACCENT : (stack != null ? UiTheme.TEXT_DIM : UiTheme.BORDER));

        int textX = fx + fw + 6;
        if (stack != null) {
            g.renderItem(stack, fx + 2, fy + 2);
            g.renderItemDecorations(this.font, stack, fx + 2, fy + 2);

            // 清除按钮：一个小「×」，只在放了东西时出现
            int[] c = filterClearRect;
            boolean clearHovered = UiTheme.hit(mouseX, mouseY, c[0], c[1], c[2], c[3]);
            g.fill(c[0], c[1], c[0] + c[2], c[1] + c[3], clearHovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL);
            UiTheme.border(g, c[0], c[1], c[2], c[3], clearHovered ? UiTheme.DANGER : UiTheme.BORDER);
            g.drawString(this.font, "×", c[0] + (c[2] - this.font.width("×")) / 2 + 1, c[1] + 6,
                    clearHovered ? UiTheme.DANGER : UiTheme.TEXT_DIM, false);
            textX = c[0] + c[2] + 6;
        } else {
            // 空槽里画一个淡淡的加号，表示「可以往这里放东西」
            int cx = fx + fw / 2;
            int cy = fy + fh / 2;
            int plus = hovered ? UiTheme.ACCENT : UiTheme.OFF;
            g.fill(cx - 4, cy, cx + 4, cy + 1, plus);
            g.fill(cx, cy - 4, cx + 1, cy + 4, plus);
        }

        // 窄窗口里槽挤在搜索框右端，旁边没地方写字，说明改由悬停提示承担（见 render 末尾）
        filterHovered = hovered;
        if (filterCompact) {
            return;
        }

        // 右边界停在这一帧实际画出来的最左那个按钮左侧（条件与 renderHeader 里的绘制条件一致）。
        // 不能一律按「返回」按钮算：它大部分时候不显示，按它算的话窄屏上只剩十几像素，物品名一个字都放不下
        int limit = this.width - MARGIN;
        if (!history.isEmpty()) {
            limit = backRect[0];
        } else if (tab == Tab.CODEX) {
            limit = reloadRect[0];
        }
        int textWidth = limit - 8 - textX;
        if (textWidth < 24) {
            return;
        }
        if (stack != null) {
            UiTheme.trimmed(g, this.font, stack.getHoverName(), textX, fy + 1, textWidth, UiTheme.TEXT);
            UiTheme.trimmed(g, this.font, filterSummary(), textX, fy + 11, textWidth,
                    filter.getFitCount() > 0 ? UiTheme.ON : UiTheme.TEXT_DIM);
        } else {
            UiTheme.trimmed(g, this.font, Component.translatable("carianstyle.codex.filter.empty_title"),
                    textX, fy + 1, textWidth, hovered ? UiTheme.ACCENT : UiTheme.TEXT_DIM);
            UiTheme.trimmed(g, this.font, Component.translatable("carianstyle.codex.filter.empty_hint"),
                    textX, fy + 11, textWidth, UiTheme.OFF);
        }
    }

    /**
     * @return 顶栏那一行合计：可附 N · 被挡 M（有「铁砧过于昂贵」的再加一段）
     */
    @Nonnull
    private Component filterSummary() {
        if (filter.getExpensiveCount() > 0) {
            return Component.translatable("carianstyle.codex.filter.summary_expensive",
                    filter.getFitCount(), filter.getBlockedCount(), filter.getExpensiveCount());
        }
        return Component.translatable("carianstyle.codex.filter.summary",
                filter.getFitCount(), filter.getBlockedCount());
    }

    /**
     * 物品槽的悬停提示：空槽时说用途，放了物品时给出合计与操作方式。
     * <p>
     * 宽窗口旁边已经写着这些，提示只是重复；但窄窗口下槽旁没有文字，
     * 不靠提示的话一个带加号的小方框没人知道是干什么的。两种宽度统一都给，行为一致。
     * </p>
     *
     * @param g      绘制上下文
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    private void renderFilterTooltip(@Nonnull GuiGraphics g, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>(3);
        ItemStack stack = filter.getStack();
        if (stack == null) {
            lines.add(Component.translatable("carianstyle.codex.filter.empty_title"));
            lines.add(Component.translatable("carianstyle.codex.filter.empty_hint")
                    .withStyle(style -> style.withColor(UiTheme.TEXT_DIM & 0xFFFFFF)));
        } else {
            lines.add(stack.getHoverName());
            lines.add(filterSummary().copy().withStyle(style -> style.withColor(UiTheme.ON & 0xFFFFFF)));
            lines.add(Component.translatable("carianstyle.codex.filter.slot_tip")
                    .withStyle(style -> style.withColor(UiTheme.TEXT_DIM & 0xFFFFFF)));
        }
        // 抬高深度：顶栏槽里的物品图标、详情里的图标都画在 z≈150，提示框要压在它们上面
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 200f);
        g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        g.pose().popPose();
    }

    /**
     * 打开或收起背包选择器。
     */
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

    /**
     * 放入某一格的物品。
     *
     * @param inventory 玩家背包
     * @param slot      格子
     */
    private void putItem(@Nonnull Inventory inventory, int slot) {
        filter.set(inventory.getItem(slot), slot);
        refreshList();
    }

    /**
     * 取下放入的物品，恢复完整列表。
     */
    private void clearItem() {
        filter.clear();
        refreshList();
    }

    @Override
    public void tick() {
        super.tick();
        // 百科不暂停游戏：开着界面时来源那一格的附魔可能变了（多人服里被服务端重新同步背包等），
        // 跟着更新，免得结论和手里的东西对不上
        if (filter.isActive() && this.minecraft != null && this.minecraft.player != null
                && filter.follow(this.minecraft.player.getInventory())) {
            refreshList();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 先收起选择器，再按一次才关界面——和原版下拉菜单的习惯一致
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && picker.isOpen()) {
            picker.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * @return 当前启用的特效数量
     */
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
        // 展开过程中界面整体在平移，命中区域与看到的位置差最多 12px。
        // 动画只有 0.22 秒，这期间直接吞掉点击比让玩家点空一次更好。
        if (animProgress() < 1f) {
            return true;
        }

        // 选择器开着时它独占点击：点格子放入物品，点外面只收起选择器、不穿透到底下
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

        // 搜索框：命中范围由外框决定，而不是 EditBox 自己的（很窄的）边界
        if (searchBox != null && searchBox.visible
                && UiTheme.hit(mouseX, mouseY, searchRect[0], searchRect[1],
                searchRect[2], searchRect[3])) {
            searchBox.setFocused(true);
            setFocused(searchBox);
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            // 点到别处就收起光标，否则那个闪动的下划线会一直挂在那儿
            searchBox.setFocused(false);
        }

        // 自绘按钮：判定条件与 renderHeader 中的绘制条件保持一致。
        // 放在物品槽之前：布局已保证两者不重叠，万一重叠，也该由画在上面的按钮接住点击
        if (tab == Tab.CODEX
                && UiTheme.hit(mouseX, mouseY, reloadRect[0], reloadRect[1], reloadRect[2], reloadRect[3])) {
            reloadAll();
            return true;
        }
        if (isCodexTab() && !history.isEmpty()
                && UiTheme.hit(mouseX, mouseY, backRect[0], backRect[1], backRect[2], backRect[3])) {
            navigateBack();
            return true;
        }

        // 放入物品槽：左键开选择器，右键直接清除（与 JEI 之类幽灵物品槽的习惯一致）；
        // 判定条件与 renderHeader 中的绘制条件保持一致
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

        // 标签页切换
        for (Tab value : Tab.values()) {
            int index = value.ordinal();
            if (UiTheme.hit(mouseX, mouseY, tabX[index], tabY, tabWidth[index], 18)) {
                switchTab(value);
                return true;
            }
        }

        if (tab == Tab.CODEX) {
            String clickedInList = list.mouseClicked(mouseX, mouseY);
            if (clickedInList != null) {
                // 列表点选不记历史。
                //
                // 早先为了「一致」把它也记了，但两者其实不是一回事：
                // 在列表里翻看是浏览，左边一直摆着，不需要「回到上一个」；
                // 而点冲突徽章是跳走——尤其跨标签页时，原来那个附魔在视野里消失了，
                // 那才需要一条回路。
                //
                // 都记的结果是按钮几乎一直亮着，退化成一个「上一次点过什么」的记录，
                // 反而看不出它是为跳转准备的。
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
        } else if (togglePanel.mouseClicked(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (picker.isOpen()) {
            // 选择器是模态的，底下的列表不该在它开着时被滚走
            return true;
        }
        if (tab == Tab.CODEX) {
            if (list.mouseScrolled(mouseX, mouseY, delta) || detail.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        } else if (tab == Tab.OTHERS) {
            if (foreignList.mouseScrolled(mouseX, mouseY, delta)
                    || foreignDetail.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        } else if (togglePanel.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        // 见类注释：不暂停，方便实时预览特效开关效果
        return false;
    }

    /**
     * 特效开关面板（本界面专用，逻辑简单，作为内部类避免多开一个文件）。
     */
    private static final class TogglePanel {

        /** 分组标题行高 */
        private static final int HEADER_HEIGHT = 22;

        /** 开关行高 */
        private static final int ROW_HEIGHT = 26;

        /** 开关方块尺寸 */
        private static final int BOX_SIZE = 12;

        /** 滚动偏移 */
        private int scroll;

        /** 内容总高度 */
        private int contentHeight;

        /** 本帧记录的开关命中区域 */
        private final List<Object[]> hits = new ArrayList<>();

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
        void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /**
         * 绘制面板。
         *
         * @param g      绘制上下文
         * @param font   字体
         * @param mouseX 鼠标 X
         * @param mouseY 鼠标 Y
         */
        void render(@Nonnull GuiGraphics g, @Nonnull Font font,
                    int mouseX, int mouseY) {
            UiTheme.panel(g, x, y, width, height, UiTheme.PANEL);
            hits.clear();

            g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

            int cursorY = y + 6 - scroll;
            int left = x + 10;
            int innerWidth = width - 24;

            for (VisualEffectType.Category category : VisualEffectType.Category.values()) {
                cursorY = renderCategory(g, font, category, cursorY, left, innerWidth, mouseX, mouseY);
            }

            g.disableScissor();

            contentHeight = cursorY - (y + 6 - scroll) + 6;
            UiTheme.scrollbar(g, x + width - 4, y + 1, height - 2, contentHeight, scroll);
        }

        /**
         * 绘制一个分组及其下的全部开关。
         *
         * @param g          绘制上下文
         * @param font       字体
         * @param category   分组
         * @param cursorY    当前纵坐标
         * @param left       内容左边界
         * @param innerWidth 内容宽度
         * @param mouseX     鼠标 X
         * @param mouseY     鼠标 Y
         * @return 绘制后的纵坐标
         */
        private int renderCategory(@Nonnull GuiGraphics g,
                                   @Nonnull Font font,
                                   @Nonnull VisualEffectType.Category category,
                                   int cursorY, int left, int innerWidth, int mouseX, int mouseY) {
            // 分组标题 + 「全开 / 全关」快捷按钮
            g.fill(left - 4, cursorY, left + innerWidth + 4, cursorY + HEADER_HEIGHT, UiTheme.PANEL_ALT);
            g.drawString(font, category.getDisplayName(), left, cursorY + 7, UiTheme.ACCENT, false);

            Component allOn = Component.translatable("carianstyle.codex.effects.all_on");
            Component allOff = Component.translatable("carianstyle.codex.effects.all_off");
            int offWidth = font.width(allOff) + 8;
            int onWidth = font.width(allOn) + 8;
            int offX = left + innerWidth - offWidth;
            int onX = offX - onWidth - 4;

            boolean onHovered = UiTheme.hit(mouseX, mouseY, onX, cursorY + 4, onWidth, 14);
            boolean offHovered = UiTheme.hit(mouseX, mouseY, offX, cursorY + 4, offWidth, 14);

            g.fill(onX, cursorY + 4, onX + onWidth, cursorY + 18,
                    onHovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL);
            UiTheme.border(g, onX, cursorY + 4, onWidth, 14, UiTheme.ON);
            g.drawString(font, allOn, onX + 4, cursorY + 7, UiTheme.ON, false);

            g.fill(offX, cursorY + 4, offX + offWidth, cursorY + 18,
                    offHovered ? UiTheme.PANEL_HOVER : UiTheme.PANEL);
            UiTheme.border(g, offX, cursorY + 4, offWidth, 14, UiTheme.OFF);
            g.drawString(font, allOff, offX + 4, cursorY + 7, UiTheme.TEXT_DIM, false);

            hits.add(new Object[]{onX, cursorY + 4, onWidth, 14, category, Boolean.TRUE});
            hits.add(new Object[]{offX, cursorY + 4, offWidth, 14, category, Boolean.FALSE});

            cursorY += HEADER_HEIGHT + 2;

            for (VisualEffectType type : VisualEffectType.values()) {
                if (type.getCategory() != category) {
                    continue;
                }
                cursorY = renderRow(g, font, type, cursorY, left, innerWidth, mouseX, mouseY);
            }

            return cursorY + 6;
        }

        /**
         * 绘制单个特效的开关行。
         *
         * @param g          绘制上下文
         * @param font       字体
         * @param type       特效类型
         * @param cursorY    当前纵坐标
         * @param left       内容左边界
         * @param innerWidth 内容宽度
         * @param mouseX     鼠标 X
         * @param mouseY     鼠标 Y
         * @return 绘制后的纵坐标
         */
        private int renderRow(@Nonnull GuiGraphics g,
                              @Nonnull Font font,
                              @Nonnull VisualEffectType type,
                              int cursorY, int left, int innerWidth, int mouseX, int mouseY) {
            boolean enabled = VisualToggle.isEnabled(type);
            boolean hovered = UiTheme.hit(mouseX, mouseY, left - 4, cursorY, innerWidth + 8, ROW_HEIGHT);

            if (hovered) {
                g.fill(left - 4, cursorY, left + innerWidth + 4, cursorY + ROW_HEIGHT, UiTheme.PANEL_HOVER);
            }

            // 复选框
            int boxY = cursorY + (ROW_HEIGHT - BOX_SIZE) / 2;
            g.fill(left, boxY, left + BOX_SIZE, boxY + BOX_SIZE,
                    enabled ? UiTheme.ON : UiTheme.PANEL);
            UiTheme.border(g, left, boxY, BOX_SIZE, BOX_SIZE, enabled ? UiTheme.ON : UiTheme.OFF);
            if (enabled) {
                g.fill(left + 3, boxY + 3, left + BOX_SIZE - 3, boxY + BOX_SIZE - 3, UiTheme.PANEL);
            }

            int textLeft = left + BOX_SIZE + 8;
            g.drawString(font, type.getDisplayName(), textLeft, cursorY + 4,
                    enabled ? UiTheme.TEXT : UiTheme.OFF, false);
            UiTheme.trimmed(g, font, type.getDescription(), textLeft, cursorY + 14,
                    innerWidth - BOX_SIZE - 90, UiTheme.TEXT_DIM);

            // 信息类特效关闭时给出提示角标
            if (type.isInformational() && !enabled) {
                Component warn = Component.translatable("carianstyle.codex.effects.info_warning");
                int warnWidth = font.width(warn);
                g.drawString(font, warn, left + innerWidth - warnWidth, cursorY + 9,
                        UiTheme.WARN, false);
            }

            hits.add(new Object[]{left - 4, cursorY, innerWidth + 8, ROW_HEIGHT, type, null});
            return cursorY + ROW_HEIGHT;
        }

        /**
         * 处理点击。
         *
         * @param mouseX 鼠标 X
         * @param mouseY 鼠标 Y
         * @return 是否消费了本次点击
         */
        boolean mouseClicked(double mouseX, double mouseY) {
            if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
                return false;
            }
            for (Object[] hit : hits) {
                int hx = (Integer) hit[0];
                int hy = (Integer) hit[1];
                int hw = (Integer) hit[2];
                int hh = (Integer) hit[3];
                if (!UiTheme.hit(mouseX, mouseY, hx, hy, hw, hh)) {
                    continue;
                }
                Object target = hit[4];
                if (target instanceof VisualEffectType type) {
                    VisualToggle.set(type, !VisualToggle.isEnabled(type));
                } else if (target instanceof VisualEffectType.Category category) {
                    VisualToggle.setCategory(category, Boolean.TRUE.equals(hit[5]));
                }
                return true;
            }
            return false;
        }

        /**
         * 处理滚轮。
         *
         * @param mouseX 鼠标 X
         * @param mouseY 鼠标 Y
         * @param delta  滚轮增量
         * @return 是否消费了本次事件
         */
        boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!UiTheme.hit(mouseX, mouseY, x, y, width, height)) {
                return false;
            }
            scroll -= (int) (delta * ROW_HEIGHT);
            int max = Math.max(0, contentHeight - height + 6);
            if (scroll > max) {
                scroll = max;
            }
            if (scroll < 0) {
                scroll = 0;
            }
            return true;
        }
    }
}
