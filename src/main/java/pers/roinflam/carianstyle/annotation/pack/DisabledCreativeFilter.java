package pers.roinflam.carianstyle.annotation.pack;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 创造物品栏里不列被禁用附魔的附魔书
 *
 * <h3>问题</h3>
 * <p>
 * 1.20.1 创造物品栏的附魔书按 {@code isAllowedOnBooks} 列，被 {@code uninstallEnchantment} 禁用的附魔不会出现。
 * 1.21.1 的 {@code CreativeModeTabs} 直接列附魔注册表里的全部附魔，禁用的附魔仍然注册着（存档里带它的物品要读得回来），
 * 不另外处理的话它们的附魔书会出现在「原材料」页和搜索页里。
 * </p>
 *
 * <h3>做法</h3>
 * <p>
 * 每个物品栏页填内容时，删掉存储附魔里有 {@code #carianstyle:disabled} 成员的附魔书。这个标签由服务器按服务器的配置生成、
 * 同步给客户端，所以看的是服务器的禁用列表，客户端不读本地配置。优先级放最低，别的模组往页里加的附魔书也一起过滤。
 * </p>
 *
 * <h3>行为影响</h3>
 * <p>
 * 和 1.20.1 一致。只在物品栏页重建时生效：进世界、切换操作权限时会重建；同一次游戏里服务器 {@code /reload} 改了禁用列表，
 * 要重进世界物品栏才跟上（1.20.1 物品栏也是缓存着，不会随配置立即重建）。
 * </p>
 *
 * @author RoinFlam
 */
@EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DisabledCreativeFilter {

    /** 内置数据包生成的「被禁用的本模组附魔」标签 */
    public static final TagKey<Enchantment> DISABLED =
            TagKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "disabled"));

    private DisabledCreativeFilter() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        removeDisabled(event, event.getParentEntries(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        removeDisabled(event, event.getSearchEntries(), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
    }

    private static void removeDisabled(BuildCreativeModeTabContentsEvent event, Collection<ItemStack> entries,
                                       CreativeModeTab.TabVisibility visibility) {
        List<ItemStack> doomed = new ArrayList<>();
        for (ItemStack stack : entries) {
            if (storesDisabled(stack)) {
                doomed.add(stack);
            }
        }
        for (ItemStack stack : doomed) {
            event.remove(stack, visibility);
        }
    }

    /**
     * 附魔书的存储附魔里有没有被禁用的本模组附魔
     */
    static boolean storesDisabled(@Nonnull ItemStack stack) {
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        for (Holder<Enchantment> holder : stored.keySet()) {
            if (holder.is(DISABLED)) {
                return true;
            }
        }
        return false;
    }
}
