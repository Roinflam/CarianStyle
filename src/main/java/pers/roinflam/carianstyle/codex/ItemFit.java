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
 * PORT-STUB(G)
 *
 * 「这个附魔能不能附到这件物品上」的判定结果。
 *
 * <h3>为什么不复用 {@link ItemProbe}</h3>
 * <p>
 * {@code ItemProbe} 回答的是「这个附魔能附在哪几类东西上」，拿一组原版代表物品去测，
 * 物品本身是干净的。这里回答的是玩家手里<b>那一件</b>：它可能已经带着附魔
 * （会冲突、会重复），可能是模组物品（有的模组枪械全靠 NBT 区分型号，
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
 * @author RoinFlam
 * @version 1.0
 */
@Deprecated
public final class ItemFit {

    public enum Status {
;

        public static final Status FITS = null; // PORT-STUB
        public static final Status OWNED = null; // PORT-STUB
        public static final Status MAXED = null; // PORT-STUB
        public static final Status BLOCKED = null; // PORT-STUB
        public static final Status NONE = null; // PORT-STUB
    }
    public static final int ANVIL_LIMIT = 40;
    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());
    private ItemFit(@Nonnull Status status, boolean viaTable, boolean viaAnvil, int anvilCost, boolean stackEnchanted, int ownedLevel, int maxLevel, @Nonnull List<EnchantmentInstance> blockers) {
    }
    @Nonnull public static Context prepare(@Nonnull ItemStack stack) {
        return null;
    }
    @Nonnull public static ItemFit evaluate(@Nonnull Enchantment enchantment, @Nonnull Context context) {
        return null;
    }

    @FunctionalInterface
    private interface Check {
        boolean test();
    }
    @Nonnull public Status getStatus() {
        return null;
    }
    public boolean isViaTable() {
        return false;
    }
    public boolean isViaAnvil() {
        return false;
    }
    public int getAnvilCost() {
        return 0;
    }
    public boolean isAnvilTooExpensive() {
        return false;
    }
    public boolean isStackEnchanted() {
        return false;
    }
    public boolean isActionable() {
        return false;
    }
    public int getOwnedLevel() {
        return 0;
    }
    public int getMaxLevel() {
        return 0;
    }
    @Nonnull public List<EnchantmentInstance> getBlockers() {
        return java.util.List.of();
    }

    public static final class Context {
        private Context(@Nonnull ItemStack stack) {
        }
        public boolean isEnchanted() {
            return false;
        }
        public int getBaseRepairCost() {
            return 0;
        }
        public boolean isMultiple() {
            return false;
        }
        @Nonnull public ItemStack getStack() {
            return null;
        }
    }
}

