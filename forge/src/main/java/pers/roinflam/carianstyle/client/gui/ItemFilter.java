package pers.roinflam.carianstyle.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.codex.CodexEntry;
import pers.roinflam.carianstyle.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.codex.ForeignCodex;
import pers.roinflam.carianstyle.codex.ForeignMeta;
import pers.roinflam.carianstyle.codex.ItemFit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 百科的「放入物品」筛选：玩家从背包里挑一件东西放上去，列表只留下与它有关的附魔。
 *
 * <h3>放上去的是副本</h3>
 * <p>
 * 百科是普通 {@code Screen}，不是容器界面，服务端根本不知道它开着。
 * 这里拿的是客户端背包里那一格的<b>副本</b>，只用来判定，不移动、不改动任何物品，
 * 也就不需要任何网络包——不存在刷物品或者和服务端对不上的问题。
 * </p>
 *
 * <h3>记住的是格子，不是物品</h3>
 * <p>
 * 关掉百科再打开时，筛选按「上次是哪一格、那一格还是不是同一种东西」恢复，
 * 并且重新读那一格的<b>当前</b>内容。典型用法是：看完能附什么 → 关界面去铁砧附一个 →
 * 回来接着看。若恢复成当初那份副本，刚附上的附魔不会出现，冲突也不会更新，
 * 玩家会以为没附成功。
 * </p>
 *
 * <h3>分组</h3>
 * <p>
 * 筛选时列表按「可以附上 / 铁砧过于昂贵 / 已经有了 / 被已有附魔挡住」分组，组内保持百科原有顺序。
 * 原来的题材分组在这时让位：玩家此刻问的是「这件东西还能附什么」，
 * 答案应该是一眼看到的第一组，而不是散在七八个题材里。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class ItemFilter {

    /** 上次放入物品的背包格（{@link Inventory#getItem} 的下标），-1 表示没有 */
    private static int lastSlot = -1;

    /** 上次放入的物品种类，用于判断那一格是否已经换成了别的东西 */
    @Nullable
    private static Item lastItem = null;

    /** 当前放入的物品副本；null 表示未启用筛选 */
    @Nullable
    private ItemStack stack;

    /** 来源格子；-1 表示不来自背包（目前不会出现，留给以后） */
    private int sourceSlot = -1;

    /** 判定上下文 */
    @Nullable
    private ItemFit.Context context;

    /** 判定结果缓存，按附魔实例 */
    private final Map<Enchantment, ItemFit> cache = new IdentityHashMap<>();

    /** 两份百科合计：这一件现在就能附上的数量 */
    private int fitCount;

    /** 两份百科合计：被挡住的数量 */
    private int blockedCount;

    /** 两份百科合计：这类物品收、但这一件在铁砧上过于昂贵的数量 */
    private int expensiveCount;

    /**
     * 放入一件物品。
     *
     * @param source 背包里那一格的物品（会复制，不持有原对象）
     * @param slot   来源格子
     */
    public void set(@Nonnull ItemStack source, int slot) {
        if (source.isEmpty()) {
            clear();
            return;
        }
        this.stack = source.copy();
        this.sourceSlot = slot;
        this.context = ItemFit.prepare(this.stack);
        this.cache.clear();
        lastSlot = slot;
        lastItem = source.getItem();
        recount();
    }

    /**
     * 取下物品，恢复完整列表。
     */
    public void clear() {
        if (stack == null) {
            return;
        }
        stack = null;
        sourceSlot = -1;
        context = null;
        cache.clear();
        fitCount = 0;
        blockedCount = 0;
        expensiveCount = 0;
        lastSlot = -1;
        lastItem = null;
    }

    /**
     * 界面首次打开时恢复上次的筛选（见类注释「记住的是格子」）。
     *
     * @param inventory 玩家背包
     */
    public void restore(@Nonnull Inventory inventory) {
        int slot = lastSlot;
        Item item = lastItem;
        if (slot < 0 || item == null || slot >= inventory.getContainerSize()) {
            return;
        }
        ItemStack current = inventory.getItem(slot);
        if (!current.isEmpty() && current.is(item)) {
            set(current, slot);
        } else {
            // 那一格已经换了东西：不要猜玩家现在想看什么，直接不恢复
            lastSlot = -1;
            lastItem = null;
        }
    }

    /**
     * 每 tick 调一次：来源格子里还是同一种东西、但身上的附魔变了时跟着更新。
     * <p>
     * 百科不暂停游戏，开着界面的时候背包仍然会变。换成了别的东西则不跟——
     * 放上去的那一件是玩家选的，不能被背包的变动悄悄替换掉。
     * </p>
     * <p>
     * 只看附魔、不看耐久和其它标签：多人服里开着百科挨打，盔甲耐久每掉一点都会重建列表，
     * 列表就会一次次跳回顶部。耐久不影响任何判定，没有理由为它重算。
     * </p>
     *
     * @param inventory 玩家背包
     * @return 是否发生了更新
     */
    public boolean follow(@Nonnull Inventory inventory) {
        if (stack == null || sourceSlot < 0 || sourceSlot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack current = inventory.getItem(sourceSlot);
        if (current.isEmpty() || !ItemStack.isSameItem(current, stack)) {
            return false;
        }
        if (EnchantmentHelper.getEnchantments(current).equals(EnchantmentHelper.getEnchantments(stack))) {
            return false;
        }
        set(current, sourceSlot);
        return true;
    }

    /**
     * 用同一件物品重算一遍（数值表或配置重载之后，宝藏、禁用与否可能变了）。
     */
    public void reevaluate() {
        if (stack != null) {
            set(stack, sourceSlot);
        }
    }

    /**
     * @return 是否启用了筛选
     */
    public boolean isActive() {
        return stack != null;
    }

    /**
     * @return 放入的物品副本；未启用时为 null
     */
    @Nullable
    public ItemStack getStack() {
        return stack;
    }

    /**
     * @return 来源格子；未启用时为 -1
     */
    public int getSourceSlot() {
        return stack == null ? -1 : sourceSlot;
    }

    /**
     * @return 放入的是否一叠多于一个
     */
    public boolean isStackMultiple() {
        return context != null && context.isMultiple();
    }

    /**
     * @return 放入物品的累计铁砧惩罚
     */
    public int getBaseRepairCost() {
        return context == null ? 0 : context.getBaseRepairCost();
    }

    /**
     * @return 两份百科合计这一件现在就能附上的附魔数
     */
    public int getFitCount() {
        return fitCount;
    }

    /**
     * @return 两份百科合计这类物品收、但这一件在铁砧上过于昂贵的附魔数
     */
    public int getExpensiveCount() {
        return expensiveCount;
    }

    /**
     * @return 两份百科合计被已有附魔挡住的附魔数
     */
    public int getBlockedCount() {
        return blockedCount;
    }

    /**
     * 取某个附魔对当前物品的判定。
     *
     * @param enchantment 附魔
     * @return 判定结果；未启用筛选时为 null
     */
    @Nullable
    public ItemFit fitOf(@Nonnull Enchantment enchantment) {
        if (context == null) {
            return null;
        }
        ItemFit fit = cache.get(enchantment);
        if (fit == null) {
            fit = ItemFit.evaluate(enchantment, context);
            cache.put(enchantment, fit);
        }
        return fit;
    }

    /**
     * 统计两份百科的合计数，给顶栏那一行小字用。
     */
    private void recount() {
        int[] counts = new int[Group.values().length];
        for (EnchantmentMeta meta : EnchantmentCodex.getAll()) {
            Group group = groupOf(fitOf(meta.getEnchantment()));
            if (group != null) {
                counts[group.ordinal()]++;
            }
        }
        for (ForeignMeta meta : ForeignCodex.getAll()) {
            Group group = groupOf(fitOf(meta.getEnchantment()));
            if (group != null) {
                counts[group.ordinal()]++;
            }
        }
        fitCount = counts[Group.FITS.ordinal()];
        expensiveCount = counts[Group.EXPENSIVE.ordinal()];
        blockedCount = counts[Group.BLOCKED.ordinal()];
    }

    /**
     * 判定结果落在哪一组。
     *
     * @param fit 判定结果
     * @return 分组；附不上时为 null
     */
    @Nullable
    private static Group groupOf(@Nonnull ItemFit fit) {
        switch (fit.getStatus()) {
            case FITS:
                // 这类物品收它，但这一件哪条路都走不通（附过魔进不了附魔台、铁砧又嫌贵）：
                // 单独成组。混在「可以附上」里，玩家照着去铁砧只会看到「过于昂贵」
                return fit.isActionable() ? Group.FITS : Group.EXPENSIVE;
            case OWNED:
            case MAXED:
                return Group.OWNED;
            case BLOCKED:
                return Group.BLOCKED;
            case NONE:
            default:
                return null;
        }
    }

    /**
     * 给背包选择器用：某件物品上还能附（或还能升级）多少个附魔。
     * <p>
     * 选择器打开时对背包每一格各算一次，用来压暗「什么都附不上了」的格子，
     * 以及在悬停提示里写「还能附 N 个」。不走本对象的缓存，不影响当前筛选。
     * </p>
     *
     * @param candidate 背包里的物品
     * @return 现在就能附上、或能再升一级的附魔数（与详情卡片的结论一致，见 {@link ItemFit#isActionable()}）
     */
    public static int countUsable(@Nonnull ItemStack candidate) {
        if (candidate.isEmpty()) {
            return 0;
        }
        // prepare 不改动传入的物品，直接用背包里那一份，不必复制
        ItemFit.Context ctx = ItemFit.prepare(candidate);
        int count = 0;
        for (EnchantmentMeta meta : EnchantmentCodex.getAll()) {
            if (ItemFit.evaluate(meta.getEnchantment(), ctx).isActionable()) {
                count++;
            }
        }
        for (ForeignMeta meta : ForeignCodex.getAll()) {
            if (ItemFit.evaluate(meta.getEnchantment(), ctx).isActionable()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 按当前物品筛选并重新分组。
     *
     * @param entries 百科按搜索词筛好的条目（保持原顺序）
     * @return 筛选后的条目；未启用筛选时原样返回
     */
    @Nonnull
    public List<? extends CodexEntry> apply(@Nonnull List<? extends CodexEntry> entries) {
        if (context == null) {
            return entries;
        }
        int groups = Group.values().length;
        List<List<CodexEntry>> byGroup = new ArrayList<>(groups);
        List<List<ItemFit>> fitsByGroup = new ArrayList<>(groups);
        for (int i = 0; i < groups; i++) {
            byGroup.add(new ArrayList<>());
            fitsByGroup.add(new ArrayList<>());
        }

        int total = 0;
        for (CodexEntry entry : entries) {
            ItemFit fit = fitOf(entry.getEnchantment());
            Group group = groupOf(fit);
            if (group == null) {
                continue;
            }
            byGroup.get(group.ordinal()).add(entry);
            fitsByGroup.get(group.ordinal()).add(fit);
            total++;
        }

        List<CodexEntry> result = new ArrayList<>(total);
        for (Group group : Group.values()) {
            wrap(result, byGroup.get(group.ordinal()), fitsByGroup.get(group.ordinal()), group);
        }
        return result;
    }

    private static void wrap(@Nonnull List<CodexEntry> out, @Nonnull List<CodexEntry> entries,
                             @Nonnull List<ItemFit> fits, @Nonnull Group group) {
        // 组名里带数量：分组标题是这一组唯一一行不随滚动离开视线的信息，
        // 数量写在这里，玩家不用往下数也知道「还能附 12 个、有 3 个被挡住」
        Component name = Component.translatable(group.langKey, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            out.add(new FitEntry(entries.get(i), fits.get(i), group, name));
        }
    }

    /**
     * 筛选时的分组，声明顺序即列表顺序。
     */
    private enum Group {
        FITS("carianstyle.codex.fit.group.fits", UiTheme.ON),
        EXPENSIVE("carianstyle.codex.fit.group.expensive", UiTheme.WARN),
        OWNED("carianstyle.codex.fit.group.owned", UiTheme.ACCENT),
        BLOCKED("carianstyle.codex.fit.group.blocked", UiTheme.DANGER);

        private final String langKey;
        private final int color;

        Group(String langKey, int color) {
            this.langKey = langKey;
            this.color = color;
        }
    }

    /**
     * 筛选时的列表条目：内容照搬原条目，只换分组并加上右端说明。
     * <p>
     * 键不变，所以列表选中、冲突跳转、后退历史都照常工作——
     * 它们只认键，不在乎条目是不是被包了一层。
     * </p>
     */
    private static final class FitEntry implements CodexEntry {

        private final CodexEntry delegate;
        private final ItemFit fit;
        private final Group group;
        private final Component groupName;

        private FitEntry(@Nonnull CodexEntry delegate, @Nonnull ItemFit fit,
                         @Nonnull Group group, @Nonnull Component groupName) {
            this.delegate = delegate;
            this.fit = fit;
            this.group = group;
            this.groupName = groupName;
        }

        @Nonnull
        @Override
        public String getKey() {
            return delegate.getKey();
        }

        @Nonnull
        @Override
        public Component getDisplayName() {
            return delegate.getDisplayName();
        }

        @Override
        public int getMaxLevel() {
            return delegate.getMaxLevel();
        }

        @Nonnull
        @Override
        public Component getRarityName() {
            return delegate.getRarityName();
        }

        @Override
        public int getRarityColor() {
            return delegate.getRarityColor();
        }

        @Override
        public int getRarityOrder() {
            return delegate.getRarityOrder();
        }

        @Override
        public int getConflictCount() {
            return delegate.getConflictCount();
        }

        @Nonnull
        @Override
        public String getGroupKey() {
            return "fit." + group.name();
        }

        @Nonnull
        @Override
        public Component getGroupName() {
            return groupName;
        }

        @Override
        public int getGroupColor() {
            return group.color;
        }

        @Override
        public boolean matches(@Nullable String lowerKeyword) {
            return delegate.matches(lowerKeyword);
        }

        @Nonnull
        @Override
        public Enchantment getEnchantment() {
            return delegate.getEnchantment();
        }

        @Nullable
        @Override
        public Component getRowNote() {
            switch (fit.getStatus()) {
                case FITS:
                    if (group == Group.EXPENSIVE) {
                        return Component.translatable("carianstyle.codex.fit.note.expensive");
                    }
                    return Component.translatable(fitNoteKey());
                case OWNED:
                    return Component.translatable("carianstyle.codex.fit.note.owned",
                            UiTheme.roman(fit.getOwnedLevel()));
                case MAXED:
                    return Component.translatable("carianstyle.codex.fit.note.maxed",
                            UiTheme.roman(fit.getOwnedLevel()));
                case BLOCKED:
                    return Component.translatable("carianstyle.codex.fit.note.blocked");
                case NONE:
                default:
                    return null;
            }
        }

        /**
         * 「可以附上」一组的行备注：按这一件<b>现在实际</b>能走的路写，与详情卡片说法一致。
         * <p>
         * 附过魔的物品进不了附魔台，这时写「附魔台」会让玩家拿着东西白跑一趟；
         * 铁砧嫌贵的也不能算作一条路。
         * </p>
         *
         * @return 语言键
         */
        @Nonnull
        private String fitNoteKey() {
            boolean table = fit.isViaTable() && !fit.isStackEnchanted();
            boolean anvil = fit.isViaAnvil() && !fit.isAnvilTooExpensive();
            if (table && anvil) {
                return "carianstyle.codex.fit.note.table";
            }
            if (table) {
                return "carianstyle.codex.fit.note.table_only";
            }
            // 只剩铁砧。附过魔的物品本来就只能走铁砧，同类新品又能上附魔台——这是常态，不必标成「仅」
            return fit.isViaTable() ? "carianstyle.codex.fit.note.anvil" : "carianstyle.codex.fit.note.anvil_only";
        }

        @Override
        public int getRowNoteColor() {
            switch (fit.getStatus()) {
                case FITS:
                    if (group == Group.EXPENSIVE) {
                        return UiTheme.WARN;
                    }
                    // 两条路都能走、或附过魔的常规走铁砧，用绿色；只剩一条路的提醒一下
                    String key = fitNoteKey();
                    return key.endsWith("_only") ? UiTheme.WARN : UiTheme.ON;
                case OWNED:
                    // 还能升级的用金色，升不了的（被挡、嫌贵、物品不收书）压暗
                    return fit.isActionable() ? UiTheme.ACCENT : UiTheme.TEXT_DIM;
                case BLOCKED:
                    return UiTheme.DANGER;
                case MAXED:
                case NONE:
                default:
                    return UiTheme.TEXT_DIM;
            }
        }

        @Override
        public boolean isRowDimmed() {
            return group == Group.BLOCKED || group == Group.EXPENSIVE;
        }
    }
}
