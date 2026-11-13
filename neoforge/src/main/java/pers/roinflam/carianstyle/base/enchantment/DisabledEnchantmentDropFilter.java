package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 被禁用附魔的掉落物兜底清洗
 *
 * <h3>问题</h3>
 * <p>
 * 被禁用的附魔不进任何获取途径的标签（附魔台、战利品、交易，1.20.1 是覆写 isDiscoverable 等方法），
 * 不带附魔列表的 {@code enchant_randomly} 和创造物品栏另有过滤，都拿不到被禁用的附魔。
 * 但还有这几条来路不经过这些判断：
 * <ul>
 *   <li>战利品表里写死了附魔列表的 {@code enchant_randomly}：列表非空时原版直接从列表里抽，不看 isDiscoverable；</li>
 *   <li>其它模组自己遍历附魔注册表挑附魔，或者在配置加载之前就把「可发现的附魔」缓存成列表、之后一直用它；</li>
 *   <li>其它模组直接构造附魔书，再塞进 LivingDropsEvent 或丢进世界。</li>
 * </ul>
 * 这些东西最后大多以掉落物实体的形式出现在世界里（怪物死亡掉落、LivingDropsEvent 里追加的掉落、
 * 钓鱼、以物易物、玩家丢出……），都要经过 {@code EntityJoinLevelEvent}。
 * </p>
 *
 * <h3>为什么这么改</h3>
 * <p>
 * 掉落物实体加入服务端世界时，从物品的附魔与附魔书存储的附魔（1.21.1 的 {@code minecraft:enchantments} 与
 * {@code minecraft:stored_enchantments} 组件，1.20.1 的 Enchantments 与 StoredEnchantments 标签）里剔除被禁用的附魔，
 * 不针对任何一个具体模组。附魔书剔完一条不剩就换成同数量的普通书：
 * 原版 {@code enchant_randomly} 找不到可用附魔时给的也是原样的书，玩家看到的结果和「这次没抽到附魔」一样；
 * 而且掉落物照常生成，不取消实体，依赖 addFreshEntity 返回值的模组不会出岔子。
 * </p>
 * <p>
 * 优先级取 HIGHEST：有的模组会在这个事件里把掉落物直接收进玩家背包并取消实体，要赶在它们之前处理。
 * </p>
 *
 * <h3>开销</h3>
 * <p>
 * 这个事件每个实体加入世界都会触发。禁用列表为空时第一行就返回；
 * 只看新生成的掉落物（从存档读回来的不管），两个组件都没有附魔的物品直接跳过；
 * 只有本模组命名空间的附魔才会去查行为对象；没有需要剔除的条目时不复制物品。
 * </p>
 *
 * <h3>行为影响</h3>
 * <ul>
 *   <li>玩家自己丢出、死亡掉落的物品上若带着被禁用的附魔，同样会被剔除（被禁用的附魔本来就不产生任何效果）。
 *       之后把它从禁用列表里删掉，已经剔除的不会回来。</li>
 *   <li>直接放进容器或背包、不经过掉落物实体的来路（战利品箱、进度奖励、指令给予等）这里管不到。</li>
 * </ul>
 *
 * @author RoinFlam
 */
@EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DisabledEnchantmentDropFilter {

    private DisabledEnchantmentDropFilter() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        String[] blacklist = ConfigLoader.uninstallEnchantment;
        if (blacklist == null || blacklist.length == 0) {
            return;
        }
        if (!(evt.getEntity() instanceof ItemEntity itemEntity) || evt.loadedFromDisk() || evt.getLevel().isClientSide()) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        ItemStack cleaned = withoutDisabledEnchantments(stack);
        if (cleaned != stack) {
            itemEntity.setItem(cleaned);
        }
    }

    /**
     * 剔除物品上被禁用的本模组附魔。
     *
     * @param stack 物品
     * @return 没有要剔除的返回原对象；否则返回剔除后的副本（附魔书剔空了换成同数量的普通书）
     */
    @Nonnull
    public static ItemStack withoutDisabledEnchantments(@Nonnull ItemStack stack) {
        if (stack.isEmpty()
                || (!containsDisabled(stack.get(DataComponents.ENCHANTMENTS))
                && !containsDisabled(stack.get(DataComponents.STORED_ENCHANTMENTS)))) {
            return stack;
        }

        ItemStack copy = stack.copy();
        removeDisabled(copy, DataComponents.ENCHANTMENTS);
        boolean storedEmpty = removeDisabled(copy, DataComponents.STORED_ENCHANTMENTS);

        if (copy.is(Items.ENCHANTED_BOOK) && storedEmpty) {
            return new ItemStack(Items.BOOK, copy.getCount());
        }
        return copy;
    }

    private static boolean containsDisabled(@Nullable ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) {
            return false;
        }
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (isDisabled(holder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从一个组件里剔除被禁用的附魔。
     *
     * @return 剔完后这个组件是否没有附魔了（原来就没有也算）
     */
    private static boolean removeDisabled(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments enchantments = stack.get(type);
        if (enchantments == null || enchantments.isEmpty()) {
            return true;
        }
        if (!containsDisabled(enchantments)) {
            return false;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
        mutable.removeIf(DisabledEnchantmentDropFilter::isDisabled);
        ItemEnchantments result = mutable.toImmutable();
        if (result.isEmpty()) {
            // 不留空的附魔组件：和物品原型不同的组件会让它和同种无附魔物品叠不到一起，退回原型的值（原型没有就去掉）
            ItemEnchantments prototype = stack.getPrototype().get(type);
            if (prototype != null) {
                stack.set(type, prototype);
            } else {
                stack.remove(type);
            }
            return true;
        }
        stack.set(type, result);
        return false;
    }

    private static boolean isDisabled(Holder<Enchantment> holder) {
        EnchantmentBase base = EnchantmentRegistry.byHolder(holder);
        return base != null && base.isDisabled();
    }
}
