package pers.roinflam.carianstyle.codex;

import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「这个附魔能不能附到这件物品上」的判定结果。
 *
 * <h3>为什么不复用 {@link ItemProbe}</h3>
 * <p>
 * {@code ItemProbe} 回答的是「这个附魔能附在哪几类东西上」，拿一组原版代表物品去测，
 * 物品本身是干净的。这里回答的是玩家手里<b>那一件</b>：它可能已经带着附魔
 * （会冲突、会重复），可能是模组物品（TACZ 的枪全靠 NBT 区分型号，
 * {@code canApplyAtEnchantingTable} 可能看 NBT），也可能是一本附魔书。
 * 所以必须拿玩家那一份 {@link ItemStack} 原样去问，而不是只看它是什么 {@code Item}。
 * </p>
 *
 * <h3>判定照抄游戏自己的两条路</h3>
 * <p>
 * 下面两条都是对照 Forge 47.4.13 打过补丁的字节码核过的，不是凭印象写的：
 * </p>
 * <ul>
 *   <li><b>铁砧</b>（{@code AnvilMenu.createResult}，右边放附魔书）：
 *       {@code canEnchant(左边物品)} 为真——左边是附魔书时不查——
 *       并且与左边物品上<b>每一个</b>其它附魔 {@code isCompatibleWith}（升级已有附魔时也照样查）；
 *       Forge 还会问一次 {@code 左边物品.isBookEnchantable(右边那本书)}，模组物品可以借此整体拒收附魔书。
 *       花费 = 左边的累计惩罚 + 每个附魔的「稀有度倍率 ÷ 2（至少 1）× 结果等级」，左边一叠多于一个时记 40；
 *       到 40 就「过于昂贵」，出不了结果。</li>
 *   <li><b>附魔台</b>（{@code EnchantmentHelper.getAvailableEnchantmentResults}）：
 *       非宝藏、{@code isDiscoverable}、{@code canApplyAtEnchantingTable(物品)}；
 *       放的是书时改看 {@code isAllowedOnBooks}。附魔台的格子本身还要求物品
 *       {@code isEnchantable()}（没附过魔）且附魔能力大于 0。另外附魔台能给出的等级有上限，
 *       附魔的最低需求够不着就永远抽不出来，见 {@link Context#tableReach}。</li>
 * </ul>
 * <p>
 * Forge 版的 {@code canEnchant} 默认就是转调 {@code canApplyAtEnchantingTable}，
 * 两条路大多数时候结论相同；但原版的伤害类附魔覆写了 {@code canEnchant} 放行斧头，
 * 于是「锋利上斧头」只能走铁砧——这正是玩家最容易搞错、最需要百科说清楚的那类情况。
 * </p>
 *
 * <h3>只按生存规则</h3>
 * <p>
 * 创造模式下铁砧跳过 {@code canEnchant} 和 40 级上限，任何附魔都能合到任何物品上。
 * 这里有意不模拟：否则创造模式里几乎所有附魔都会被列成「可以附上」，筛选就没有意义了，
 * 管理员拿创造模式替生存玩家查也会得到错误答案。
 * </p>
 *
 * <h3>「附魔台」按这一类物品说，不按这一件说</h3>
 * <p>
 * 已经附过魔的物品进不了附魔台。若严格按「这一件」判，带附魔的装备上所有条目都会变成「仅铁砧」，
 * 这个标注就失去了区分度。所以附魔台一项拿<b>去掉附魔的副本</b>来判，
 * 回答的是「这类东西能不能在附魔台上直接出这个附魔」——玩家据此决定是去刷附魔台还是去找书。
 * 冲突、已有等级、铁砧花费则一律按这一件实际的状态算；界面措辞按 {@link #isStackEnchanted()} 区分。
 * </p>
 *
 * <h3>配置取自本地</h3>
 * <p>
 * 本模组附魔的禁用、宝藏、附魔难度都来自 {@code carianstyle-common.toml}，这份配置不会同步到客户端。
 * 与百科原有的「获取方式」一段一样，这里读到的是客户端本地那份；服务端改过而客户端没跟上时结论会有出入。
 * </p>
 *
 * <h3>开销与调用点</h3>
 * <p>
 * 单次判定就是几次方法调用加一次小对象分配（能过 {@code canEnchant} 时建一本书去问 {@code isBookEnchantable}）。
 * 调用点有三处，都不是每帧跑：
 * </p>
 * <ul>
 *   <li>{@code ItemFilter.set}（放入、换物品、重载数值、来源格子的附魔变化时）：全部附魔各算一次并缓存；</li>
 *   <li>{@code InventoryPicker.open}：背包每个非空格子各算一遍全部附魔，不缓存；</li>
 *   <li>{@code InventoryPicker.renderRow}：选择器开着时某一格的物品对象被替换（服务端重发了这一格），只重算那一格。</li>
 * </ul>
 * <p>
 * 所以这里不能加重活（I/O、大量分配、遍历注册表之类）。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class ItemFit {

    /** 判定结论 */
    public enum Status {
        /** 能附上（这类物品收它、也没被挡；铁砧会不会嫌贵另看 {@link #isAnvilTooExpensive()}） */
        FITS,
        /** 物品上已有，还没满级 */
        OWNED,
        /** 物品上已有，已满级（或超过常规上限） */
        MAXED,
        /** 这类物品能附，但被物品上已有的附魔挡住 */
        BLOCKED,
        /** 附不上 */
        NONE
    }

    /** 铁砧花费到这个数就「过于昂贵」（生存模式），与原版客户端显示一致 */
    public static final int ANVIL_LIMIT = 40;

    /** 判定时抛过异常的附魔，每个只记一次日志，免得玩家反复换物品把日志刷满 */
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());

    private final Status status;
    private final boolean viaTable;
    private final boolean viaAnvil;
    private final int anvilCost;
    private final boolean stackEnchanted;
    private final int ownedLevel;
    private final int maxLevel;
    private final List<EnchantmentInstance> blockers;

    private ItemFit(@Nonnull Status status, boolean viaTable, boolean viaAnvil, int anvilCost,
                    boolean stackEnchanted, int ownedLevel, int maxLevel,
                    @Nonnull List<EnchantmentInstance> blockers) {
        this.status = status;
        this.viaTable = viaTable;
        this.viaAnvil = viaAnvil;
        this.anvilCost = anvilCost;
        this.stackEnchanted = stackEnchanted;
        this.ownedLevel = ownedLevel;
        this.maxLevel = maxLevel;
        this.blockers = blockers;
    }

    /**
     * 为一件物品准备判定上下文。
     * <p>
     * 已有附魔表和「去掉附魔的副本」对所有附魔都一样，拆出来只算一次，
     * 免得一百多个附魔每个都复制一遍物品。
     * </p>
     *
     * @param stack 玩家放入的物品（调用方持有的副本，本类不修改它）
     * @return 判定上下文
     */
    @Nonnull
    public static Context prepare(@Nonnull ItemStack stack) {
        return new Context(stack);
    }

    /**
     * 判定一个附魔。
     *
     * @param enchantment 附魔
     * @param context     {@link #prepare} 得到的上下文
     * @return 判定结果
     */
    @Nonnull
    public static ItemFit evaluate(@Nonnull Enchantment enchantment, @Nonnull Context context) {
        int max = enchantment.getMaxLevel();
        boolean enchanted = context.isEnchanted();

        // 已经带着的，不管它是怎么来的（命令、战利品、别的模组），先如实报「已有」
        Integer owned = context.existing.get(enchantment);
        if (owned != null) {
            if (owned >= max) {
                return new ItemFit(Status.MAXED, false, false, -1, enchanted, owned, max, Collections.emptyList());
            }
            // 没满级时要回答「还能不能用同级书再升一级」。铁砧对已有的附魔照样逐个查冲突，
            // 物品上若带着互斥的一对（命令、旧版本留下的），升级同样出不了结果
            List<EnchantmentInstance> blockers = blockersOf(enchantment, context);
            boolean anvil = blockers.isEmpty() && anvilAccepts(enchantment, context);
            int cost = anvil ? anvilCost(enchantment, context, Math.min(owned + 1, max)) : -1;
            return new ItemFit(Status.OWNED, false, anvil, cost, enchanted, owned, max, blockers);
        }

        boolean anvil = anvilAccepts(enchantment, context);
        boolean table = tableAccepts(enchantment, context);
        if (!anvil && !table) {
            return new ItemFit(Status.NONE, false, false, -1, enchanted, 0, max, Collections.emptyList());
        }
        if (!anvil && enchanted) {
            // 只剩附魔台一条路，而这一件已经附过魔、进不了附魔台：对这一件来说就是附不上。
            // 留着 viaTable，让界面能解释「换一件没附过魔的同类物品就行」
            return new ItemFit(Status.NONE, true, false, -1, enchanted, 0, max, Collections.emptyList());
        }

        // 按铁砧结算的写法：右边放一本 1 级书，结果等级 1
        int cost = anvil ? anvilCost(enchantment, context, 1) : -1;
        List<EnchantmentInstance> blockers = blockersOf(enchantment, context);
        if (!blockers.isEmpty()) {
            return new ItemFit(Status.BLOCKED, table, anvil, cost, enchanted, 0, max, blockers);
        }
        return new ItemFit(Status.FITS, table, anvil, cost, enchanted, 0, max, Collections.emptyList());
    }

    /**
     * 物品上与它互斥的已有附魔。
     * <p>
     * 铁砧对右边书上的每个附魔，都拿左边物品上<b>除它自己以外</b>的每一个附魔查一遍。
     * {@code isCompatibleWith} 是双向的（两边的 checkCompatibility 都要点头），不用再反查。
     * </p>
     *
     * @param enchantment 附魔
     * @param context     上下文
     * @return 互斥的已有附魔（含等级）；没有时为空表
     */
    @Nonnull
    private static List<EnchantmentInstance> blockersOf(@Nonnull Enchantment enchantment, @Nonnull Context context) {
        List<EnchantmentInstance> blockers = null;
        for (Map.Entry<Enchantment, Integer> entry : context.existing.entrySet()) {
            Enchantment other = entry.getKey();
            if (other == enchantment || compatible(enchantment, other)) {
                continue;
            }
            if (blockers == null) {
                blockers = new ArrayList<>(2);
            }
            blockers.add(new EnchantmentInstance(other, entry.getValue()));
        }
        return blockers == null ? Collections.emptyList() : Collections.unmodifiableList(blockers);
    }

    /**
     * 铁砧这条路：右边放一本带这个附魔的书，左边放这件物品，能不能合上去（不管花费）。
     *
     * @param enchantment 附魔
     * @param context     上下文
     * @return 能否合上
     */
    private static boolean anvilAccepts(@Nonnull Enchantment enchantment, @Nonnull Context context) {
        ItemStack stack = context.stack;
        // 左边是附魔书时铁砧不查 canEnchant——书可以合进任何附魔
        if (stack.is(Items.ENCHANTED_BOOK)) {
            return true;
        }
        if (!guard(enchantment, () -> enchantment.canEnchant(stack))) {
            return false;
        }
        // Forge 在铁砧结算末尾补的一刀：物品可以按「右边是哪本书」整体拒收
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1));
        return guard(enchantment, () -> stack.isBookEnchantable(book));
    }

    /**
     * 估算在铁砧上合一本书的最低花费。
     * <p>
     * 右边按一本全新的书算（累计惩罚 0）。书上附魔的单价是稀有度倍率减半、至少 1，
     * 乘以合完之后的等级；左边一叠多于一个时这一项直接记 40。
     * 这个值只用来判断会不会「过于昂贵」，不追求和铁砧显示的数字分毫不差。
     * </p>
     *
     * @param enchantment 附魔
     * @param context     上下文
     * @param resultLevel 合完之后的等级
     * @return 最低花费
     */
    private static int anvilCost(@Nonnull Enchantment enchantment, @Nonnull Context context, int resultLevel) {
        if (context.multiple) {
            return context.baseRepairCost + ANVIL_LIMIT;
        }
        int weight;
        try {
            switch (enchantment.getRarity()) {
                case COMMON:
                    weight = 1;
                    break;
                case UNCOMMON:
                    weight = 2;
                    break;
                case RARE:
                    weight = 4;
                    break;
                case VERY_RARE:
                    weight = 8;
                    break;
                default:
                    weight = 0;
                    break;
            }
        } catch (Exception e) {
            warnOnce(enchantment, e);
            weight = 1;
        }
        return context.baseRepairCost + Math.max(1, weight / 2) * Math.max(1, resultLevel);
    }

    /**
     * 附魔台这条路：这类物品（去掉附魔后）放上附魔台，这个附魔有没有可能被抽出来。
     *
     * @param enchantment 附魔
     * @param context     上下文
     * @return 能否在附魔台出现
     */
    private static boolean tableAccepts(@Nonnull Enchantment enchantment, @Nonnull Context context) {
        if (!context.tableSlotAccepts) {
            return false;
        }
        return guard(enchantment, () -> {
            if (enchantment.isTreasureOnly() || !enchantment.isDiscoverable()) {
                return false;
            }
            ItemStack clean = context.clean;
            boolean accepts = enchantment.canApplyAtEnchantingTable(clean)
                    || (clean.is(Items.BOOK) && enchantment.isAllowedOnBooks());
            if (!accepts) {
                return false;
            }
            // 附魔台给出的等级有上限：只要有一个等级的最低需求够得着，就有机会抽到。
            // 本模组的附魔难度配置会把需求整体放大（整合包里是 2 倍），
            // 不查这一条的话，重力、催眠烟雾这类会被标成「附魔台」，玩家白刷
            for (int level = enchantment.getMinLevel(); level <= enchantment.getMaxLevel(); level++) {
                if (enchantment.getMinCost(level) <= context.tableReach) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * @param a 附魔 A
     * @param b 附魔 B
     * @return 能否共存；判定出错时按不能共存处理（宁可多报一条冲突，不能让玩家白花经验）
     */
    private static boolean compatible(@Nonnull Enchantment a, @Nonnull Enchantment b) {
        try {
            return a.isCompatibleWith(b);
        } catch (Exception e) {
            warnOnce(a, e);
            return false;
        }
    }

    /**
     * 包一层第三方代码调用。附魔和物品的这些方法都可能是别的模组覆写的，
     * 一个出错不该让整个筛选失败。
     *
     * @param enchantment 正在判定的附魔（只用于日志）
     * @param check       判定
     * @return 判定结果；出错时为 false
     */
    private static boolean guard(@Nonnull Enchantment enchantment, @Nonnull Check check) {
        try {
            return check.test();
        } catch (Exception e) {
            warnOnce(enchantment, e);
            return false;
        }
    }

    private static void warnOnce(@Nonnull Enchantment enchantment, @Nonnull Exception e) {
        if (WARNED.add(enchantment.getDescriptionId())) {
            LogUtil.warn("卡利亚式附魔 - 百科判定附魔 %s 能否附到物品上时出错（同一附魔只提示一次）：%s",
                    enchantment.getDescriptionId(), e);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean test();
    }

    /**
     * @return 判定结论
     */
    @Nonnull
    public Status getStatus() {
        return status;
    }

    /**
     * @return 这类物品在附魔台上能否直接出这个附魔。对 FITS / BLOCKED 有意义；
     *         NONE 时为 true 表示「只能走附魔台，但这一件已经附过魔了」
     */
    public boolean isViaTable() {
        return viaTable;
    }

    /**
     * @return 能否用附魔书在铁砧上合到这一件上（不管花费）。对 FITS / BLOCKED 有意义；
     *         OWNED 时表示能否再用同级书升一级（已含冲突检查）
     */
    public boolean isViaAnvil() {
        return viaAnvil;
    }

    /**
     * @return 走铁砧的最低花费估算；不能走铁砧时为 -1
     */
    public int getAnvilCost() {
        return anvilCost;
    }

    /**
     * @return 能走铁砧，但这一件合一本书就会「过于昂贵」
     */
    public boolean isAnvilTooExpensive() {
        return viaAnvil && anvilCost >= ANVIL_LIMIT;
    }

    /**
     * @return 这一件是否已带附魔（带了就进不了附魔台）
     */
    public boolean isStackEnchanted() {
        return stackEnchanted;
    }

    /**
     * 这一件现在实际能不能往上加（或升级）这个附魔。
     * <p>
     * FITS 只说明这类物品收它；这一件附过魔、铁砧又嫌贵的话，其实哪条路都走不通。
     * 选择器的「还能附 N 个」和压暗都按这个算，与详情卡片的结论保持一致。
     * </p>
     *
     * @return 能否实际加上
     */
    public boolean isActionable() {
        switch (status) {
            case FITS:
                return (viaAnvil && !isAnvilTooExpensive()) || (viaTable && !stackEnchanted);
            case OWNED:
                return viaAnvil && !isAnvilTooExpensive();
            default:
                return false;
        }
    }

    /**
     * @return 物品上已有的等级；未拥有时为 0
     */
    public int getOwnedLevel() {
        return ownedLevel;
    }

    /**
     * @return 附魔的常规最高等级
     */
    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * @return 挡住它的已有附魔（含等级）：BLOCKED 时是挡住它附上去的，OWNED 时是挡住它升级的；否则为空
     */
    @Nonnull
    public List<EnchantmentInstance> getBlockers() {
        return blockers;
    }

    /**
     * 一件物品的判定上下文：对所有附魔都相同的那部分，只算一次。
     */
    public static final class Context {

        /** 玩家放入的物品 */
        private final ItemStack stack;

        /** 去掉附魔的副本，用于判附魔台 */
        private final ItemStack clean;

        /** 物品上已有的附魔；附魔书读的是它存着的那些 */
        private final Map<Enchantment, Integer> existing;

        /** 这类物品能不能放上附魔台并出选项 */
        private final boolean tableSlotAccepts;

        /**
         * 这类物品在附魔台上能拿到的最高修正等级。
         * <p>
         * 满书架时第三档是 30 级；抽附魔时再加 {@code 1 + 两次 nextInt(附魔能力/4 + 1)}，
         * 然后乘以 {@code 1 + f}，f 落在 [-0.15, 0.15)。所以上限是
         * {@code round((31 + 2 × (附魔能力/4)) × 1.15)}：书、弓这类附魔能力 1 的是 36，金护甲（25）是 49。
         * 这是按原版 15 个书架算的；别的模组借 {@code EnchantmentLevelSetEvent} 抬高等级的情况不考虑。
         * </p>
         */
        private final int tableReach;

        /** 左边物品的累计铁砧惩罚 */
        private final int baseRepairCost;

        /** 是否一叠多于一个（铁砧上整组合书一律过于昂贵） */
        private final boolean multiple;

        private Context(@Nonnull ItemStack stack) {
            this.stack = stack;
            // getEnchantments 对附魔书读 StoredEnchantments，对其它物品读 Enchantments，
            // 与铁砧取左边物品附魔用的是同一个方法
            this.existing = EnchantmentHelper.getEnchantments(stack);
            this.multiple = stack.getCount() > 1;

            // 数量取 1：附魔台的格子只放得下一个，书的 isEnchantable 还要求数量恰好为 1。
            // 附魔书去掉 Enchantments 标签也还是附魔书（存的是 StoredEnchantments），
            // 它的 isEnchantable 恒为 false，附魔台一项自然判否，不用单独处理
            ItemStack copy = stack.copyWithCount(1);
            copy.removeTagKey("Enchantments");
            this.clean = copy;

            boolean accepts;
            int reach = 0;
            try {
                // 附魔台格子的门槛：isEnchantable()（可附魔且还没附过）、附魔能力大于 0——
                // 能力为 0 时三档消耗全是 0，一个选项都不会出
                int value = copy.getEnchantmentValue();
                accepts = copy.isEnchantable() && value > 0;
                if (accepts) {
                    reach = Math.round((31 + 2 * (value / 4)) * 1.15f);
                }
            } catch (Exception e) {
                accepts = false;
            }
            this.tableSlotAccepts = accepts;
            this.tableReach = reach;

            int repairCost;
            try {
                repairCost = Math.max(0, stack.getBaseRepairCost());
            } catch (Exception e) {
                repairCost = 0;
            }
            this.baseRepairCost = repairCost;
        }

        /**
         * @return 物品上是否已带附魔
         */
        public boolean isEnchanted() {
            return !existing.isEmpty();
        }

        /**
         * @return 左边物品的累计铁砧惩罚
         */
        public int getBaseRepairCost() {
            return baseRepairCost;
        }

        /**
         * @return 是否一叠多于一个
         */
        public boolean isMultiple() {
            return multiple;
        }

        /**
         * @return 玩家放入的物品
         */
        @Nonnull
        public ItemStack getStack() {
            return stack;
        }
    }
}
