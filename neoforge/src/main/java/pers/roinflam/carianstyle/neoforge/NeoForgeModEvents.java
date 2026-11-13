package pers.roinflam.carianstyle.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import pers.roinflam.carianstyle.annotation.pack.DisabledCreativeFilter;
import pers.roinflam.carianstyle.annotation.pack.GeneratedDataPack;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.network.CarianNetwork;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NeoForge 模组总线上的几个平台事件，转给通用代码：网络包登记、配置加载 / 重载、挂内置数据包、创造物品栏去掉禁用附魔的书
 * <p>
 * 由主类构造器调 {@link #registerNetwork}、{@link #register} 挂上，不靠注解自动登记，入口里一眼能看到挂了什么。
 * </p>
 */
public final class NeoForgeModEvents {

    private NeoForgeModEvents() {
    }

    /** 挂网络包登记（双端都会触发；客户端要登记了解码和处理器才收得到包） */
    public static void registerNetwork(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeModEvents::onRegisterPayloadHandlers);
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeModEvents::onConfigLoading);
        modEventBus.addListener(NeoForgeModEvents::onConfigReloading);
        modEventBus.addListener(NeoForgeModEvents::onAddPackFinders);
        // 最低优先级：别的模组往物品栏页里加的附魔书也一起过滤
        modEventBus.addListener(EventPriority.LOWEST, NeoForgeModEvents::onBuildCreativeTabContents);
    }

    /**
     * 照 {@link CarianNetwork#PACKETS} 逐条登记成只许服务端发往客户端的包。
     * 每个版本号用一个登记器，默认设置：处理器在主线程跑、包不是可选的（对端没有或版本不同就拒绝连接）。
     */
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        Map<String, PayloadRegistrar> registrars = new LinkedHashMap<>();
        for (CarianNetwork.Entry<?> entry : CarianNetwork.PACKETS) {
            registerPacket(registrars.computeIfAbsent(entry.version(), event::registrar), entry);
        }
    }

    private static <T extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar,
                                                                       CarianNetwork.Entry<T> entry) {
        registrar.playToClient(entry.type(), entry.codec(), (payload, context) -> entry.clientHandler().accept(payload));
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            ConfigLoader.onLoaded();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            ConfigLoader.onLoaded();
        }
    }

    /**
     * 只给服务端数据包仓库加包；包在仓库每次扫描包源时现建，按当时的配置生成内容。
     */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(GeneratedDataPack.createPack()));
    }

    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        removeDisabled(event, event.getParentEntries(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        removeDisabled(event, event.getSearchEntries(), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
    }

    private static void removeDisabled(BuildCreativeModeTabContentsEvent event, Collection<ItemStack> entries,
                                       CreativeModeTab.TabVisibility visibility) {
        List<ItemStack> doomed = new ArrayList<>();
        for (ItemStack stack : entries) {
            if (DisabledCreativeFilter.storesDisabled(stack)) {
                doomed.add(stack);
            }
        }
        for (ItemStack stack : doomed) {
            event.remove(stack, visibility);
        }
    }
}
