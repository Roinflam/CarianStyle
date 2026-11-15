package pers.roinflam.carianstyle.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.forge.platform.ForgePlatformNetwork;
import pers.roinflam.carianstyle.network.CarianNetwork;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Forge 模组总线上的平台接线：把 Forge 自己的生命周期事件转给 common 的入口方法，网络通道也在这里照表登记。
 */
public final class ForgeModEvents {

    private ForgeModEvents() {
    }

    /**
     * 照 {@link CarianNetwork#PACKETS} 建两个通道并逐条登记，只许服务端发往客户端。
     * 通道按表里第一次出现的先后建（先主通道），通道内编号从 0 按表里的先后排，和以前两个通道类各自登记时一模一样；
     * 两端都要调，客户端登记了解码和处理器才收得到包。
     */
    public static void registerNetwork() {
        Map<CarianNetwork.Channel, SimpleChannel> channels = new LinkedHashMap<>();
        Map<CarianNetwork.Channel, Integer> nextIndex = new HashMap<>();
        Map<Class<?>, SimpleChannel> byType = new HashMap<>();
        for (CarianNetwork.Entry<?> entry : CarianNetwork.PACKETS) {
            SimpleChannel channel = channels.computeIfAbsent(entry.channel(), ForgeModEvents::newChannel);
            int index = nextIndex.merge(entry.channel(), 1, Integer::sum) - 1;
            registerPacket(channel, index, entry);
            byType.put(entry.type(), channel);
        }
        ForgePlatformNetwork.bind(byType);
    }

    private static SimpleChannel newChannel(CarianNetwork.Channel channel) {
        String version = channel.version();
        return NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Reference.MOD_ID, channel.name()),
                () -> version,
                version::equals,
                version::equals
        );
    }

    private static <T> void registerPacket(SimpleChannel channel, int index, CarianNetwork.Entry<T> entry) {
        channel.registerMessage(
                index,
                entry.type(),
                entry.encoder(),
                entry.decoder(),
                (packet, ctx) -> {
                    ctx.get().enqueueWork(() -> entry.clientHandler().accept(packet));
                    ctx.get().setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    /** 配置加载 / 重载回调，要在登记配置之前挂上。 */
    public static void register(@Nonnull IEventBus modEventBus) {
        modEventBus.addListener(ForgeModEvents::onConfigLoading);
        modEventBus.addListener(ForgeModEvents::onConfigReloading);
    }

    private static void onConfigLoading(@Nonnull ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            ConfigLoader.onLoaded();
        }
    }

    private static void onConfigReloading(@Nonnull ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            ConfigLoader.onLoaded();
        }
    }
}
