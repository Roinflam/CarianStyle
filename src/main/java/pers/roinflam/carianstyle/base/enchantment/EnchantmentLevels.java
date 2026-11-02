package pers.roinflam.carianstyle.base.enchantment;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 *   <li>{@link #all}：附魔书读 {@code stored_enchantments}，其它读 {@code enchantments}（和 1.20.1 的
 *       {@code EnchantmentHelper.getEnchantments} 一样），按注册表键换成行为对象，不是本模组的跳过。</li>
 * </ul>
 *
 * <h3>为什么不走 {@code stack.getEnchantmentLevel(holder)}</h3>
 * <p>
 * NeoForge 的这个方法会先问物品自己的 {@code getEnchantmentLevel}，再发全局 {@code GetEnchantmentLevelEvent}，
 * 要传真 Holder，监听者还可能对查表 Holder 调 {@code value()}（它没绑定值，会抛异常）。
 * 读原始数据的代价：别的模组物品覆写等级钩子给本模组附魔加的等级，1.21.1 不生效（1.20.1 生效）。实际影响很小。
 * </p>
 *
 * <h3>行为说明</h3>
 * <p>
 * {@link #all} 照 1.20.1：主手拿附魔书时书里存的附魔也算进来（老问题，三个版本一起修，这里不改）。
 * </p>
 */
public final class EnchantmentLevels {

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
     * 物品上全部本模组附魔及其原始等级（附魔书读书里存的）。
     * <p>
     * 返回的表保持组件里的迭代顺序；没有本模组附魔时返回不可变的空表（调用方只读）。
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
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (enchantments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<EnchantmentBase, Integer> result = null;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            EnchantmentBase base = EnchantmentRegistry.byHolder(entry.getKey());
            if (base == null) {
                continue;
            }
            if (result == null) {
                result = new LinkedHashMap<>();
            }
            result.put(base, entry.getIntValue());
        }
        return result == null ? Collections.emptyMap() : result;
    }
}
