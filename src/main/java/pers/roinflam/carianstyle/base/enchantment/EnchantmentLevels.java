package pers.roinflam.carianstyle.base.enchantment;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读物品上本模组附魔的等级
 *
 * <h3>为什么要有</h3>
 * <p>
 * 1.20.1 读等级用 {@code EnchantmentHelper.getItemEnchantmentLevel(附魔对象, 物品)}，附魔对象就是注册表里的那个。
 * 1.21.1 物品上的附魔是 {@code minecraft:enchantments} 组件，键是注册表 {@code Holder}；本模组的附魔类不再是
 * {@code Enchantment}，要查等级得有一个能当键用的 Holder。
 * </p>
 *
 * <h3>做法</h3>
 * <ul>
 *   <li>{@link #get}：直接读组件原始数据 {@code stack.getTagEnchantments().getLevel(查表 Holder)}。
 *       查表 Holder 是 {@link EnchantmentBase} 里按注册表键建的 {@code DeferredHolder}：NeoForge 给
 *       {@code Holder.Reference} 和 {@code DeferredHolder} 都补了按键的 {@code equals / hashCode}，
 *       组件内部是按 Holder 查的哈希表，所以不用拿世界里的真 Holder 也能查到。</li>
 *   <li>{@link #all}：同样只读物品自身的 {@code enchantments} 组件，按注册表键换成行为对象，不是本模组的跳过。</li>
 * </ul>
 *
 * <h3>为什么不走 {@code stack.getEnchantmentLevel(holder)}</h3>
 * <p>
 * NeoForge 的这个方法会先问物品自己的 {@code getEnchantmentLevel}，再发全局 {@code GetEnchantmentLevelEvent}，
 * 要传真 Holder，监听者还可能对查表 Holder 调 {@code value()}（它没绑定值，会抛异常）。
 * 读原始数据的代价：别的模组物品覆写等级钩子给本模组附魔加的等级，1.21.1 不生效（1.20.1 生效）。实际影响很小。
 * </p>
 *
 * <h3>附魔书不算</h3>
 * <p>
 * <b>问题</b>：1.20.1 的 {@code EnchantmentHelper.getEnchantments} 对附魔书读书里存的附魔，照搬过来时 {@link #all}
 * 用的是 1.21.1 对应的 {@code getEnchantmentsForCrafting}。所有「拿在手上 / 穿在身上就生效」的扫描都经过 {@link #all}
 * （玩家装备缓存、死亡 / 治疗 / 暴击扫描、药水效果修正、光环显示），结果主手拿一本暗月附魔书就有夜视，
 * 拿其它附魔书也一样会触发对应效果。
 * </p>
 * <p>
 * <b>为什么这么改</b>：附魔书是用来在铁砧上转移附魔的材料，不是装备。{@link #all} 只给「这件装备身上有什么附魔」用，
 * 改成读 {@code getTagEnchantments()}（物品自身的 {@code enchantments} 组件），和 {@link #get} 口径一致。
 * 要看书里存了什么的地方（图鉴、物品筛选）自己调 {@code getEnchantmentsForCrafting}，不走这里。
 * </p>
 * <p>
 * <b>行为影响</b>：手持、穿戴附魔书不再触发任何本模组附魔，也不显示光环；装备上的附魔不受影响。
 * </p>
 *
 * <h3>{@link #all} 的顺序</h3>
 * <p>
 * <b>问题</b>：同一件物品上几个附魔挂在同一优先级、一个加一个乘（血斩 + 波纹石魔法、凶兆的真伤……），结果取决于谁先结算。
 * 1.20.1 按 NBT 列表顺序结算，跟着物品走、开服前后不变。1.21.1 的附魔组件底层是哈希表，键的哈希是
 * {@code ResourceKey} 的身份哈希，每次开服迭代顺序都可能不同，同一把剑重启服务器后伤害就可能变。
 * </p>
 * <p>
 * <b>为什么这么改</b>：旧物品的 NBT 顺序在升级成组件时已经丢了，恢复不了，只能定一个固定顺序：按附魔 id 字典序。
 * </p>
 * <p>
 * <b>行为影响</b>：同一件物品每次开服结算顺序都一样；和 1.20.1 的差别是顺序按 id 而不是按附魔时的先后。
 * </p>
 */
public final class EnchantmentLevels {

    /** 按附魔 id 排，见类注释「{@link #all} 的顺序」 */
    private static final Comparator<Map.Entry<EnchantmentBase, Integer>> BY_ID =
            Comparator.comparing(e -> e.getKey().getId());

    private EnchantmentLevels() {
    }

    /**
     * 物品上这个本模组附魔的等级（读 {@code minecraft:enchantments}，附魔书里存的不算，和 1.20.1 一样）。
     *
     * @param enchantment 附魔行为对象，null 时返回 0
     * @param stack       物品
     * @return 原始等级，没有为 0
     */
    public static int get(@Nullable EnchantmentBase enchantment, @Nonnull ItemStack stack) {
        if (enchantment == null || stack.isEmpty()) {
            return 0;
        }
        Holder<Enchantment> holder = enchantment.lookupHolder();
        if (holder == null) {
            return 0;
        }
        return stack.getTagEnchantments().getLevel(holder);
    }

    /**
     * 物品上全部本模组附魔及其原始等级（只读物品自身的 {@code enchantments}，附魔书里存的不算）。
     * <p>
     * 返回的表按附魔 id 字典序（见类注释「{@link #all} 的顺序」）；没有本模组附魔时返回不可变的空表（调用方只读）。
     * </p>
     *
     * @param stack 物品
     * @return 行为对象 → 等级
     */
    @Nonnull
    public static Map<EnchantmentBase, Integer> all(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return Collections.emptyMap();
        }
        ItemEnchantments enchantments = stack.getTagEnchantments();
        if (enchantments.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map.Entry<EnchantmentBase, Integer>> found = null;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            EnchantmentBase base = EnchantmentRegistry.byHolder(entry.getKey());
            if (base == null) {
                continue;
            }
            if (found == null) {
                found = new ArrayList<>();
            }
            found.add(Map.entry(base, entry.getIntValue()));
        }
        if (found == null) {
            return Collections.emptyMap();
        }
        if (found.size() > 1) {
            found.sort(BY_ID);
        }
        Map<EnchantmentBase, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<EnchantmentBase, Integer> e : found) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }
}
