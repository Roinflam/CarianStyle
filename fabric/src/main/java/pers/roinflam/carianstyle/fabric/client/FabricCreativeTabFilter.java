package pers.roinflam.carianstyle.fabric.client;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.annotation.pack.DisabledCreativeFilter;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 创造模式物品栏里去掉存着被禁用附魔的附魔书（判定见 {@link DisabledCreativeFilter#storesDisabled}）。
 * <p>
 * NeoForge 上挂在物品栏内容事件的最低优先级；Fabric 上对应的是所有物品栏共用的内容修改事件，
 * 另开一个排在默认阶段之后的阶段，别的模组往物品栏里加的附魔书也能一起去掉。
 * 本页和搜索页两张表都删，和 NeoForge 一样。物品栏内容只在客户端建，所以放在客户端入口。
 * </p>
 */
public final class FabricCreativeTabFilter {

    private static final ResourceLocation LATE = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "late");

    private FabricCreativeTabFilter() {
    }

    public static void register() {
        ItemGroupEvents.MODIFY_ENTRIES_ALL.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register(LATE, (tab, entries) -> {
            entries.getDisplayStacks().removeIf(DisabledCreativeFilter::storesDisabled);
            entries.getSearchTabStacks().removeIf(DisabledCreativeFilter::storesDisabled);
        });
    }
}
