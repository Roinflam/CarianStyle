package pers.roinflam.carianstyle.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.network.CarianNetwork;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.HashMap;
import java.util.Map;

/**
 * Fabric 上的网络登记（服务端和两端共用的部分）。
 * <p>
 * {@link CarianNetwork#PACKETS} 里每种包一个频道 {@code carianstyle:<包名>}，包体就是包类自己的编码，
 * 不像 Forge 那样在通道里加编号。客户端收包的处理器由客户端入口照同一张表另外挂（按频道名取 {@link #channelOf}）。
 * 版本核对不走频道，见 {@link FabricHandshake}。
 * </p>
 * <p>
 * 由主入口调一次 {@link #register}，要早于任何发包。
 * </p>
 */
public final class FabricNetwork {

    private static volatile Map<Class<?>, Channel<?>> channels = Map.of();

    private FabricNetwork() {
    }

    private record Channel<T>(ResourceLocation id, CarianNetwork.Entry<T> entry) {

        Packet<?> toVanilla(Object packet) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            entry.encoder().accept(entry.type().cast(packet), buf);
            return ServerPlayNetworking.createS2CPacket(id, buf);
        }
    }

    public static synchronized void register() {
        if (!channels.isEmpty()) {
            throw new IllegalStateException("网络包已经登记过了");
        }
        Map<Class<?>, Channel<?>> byType = new HashMap<>();
        for (CarianNetwork.Entry<?> entry : CarianNetwork.PACKETS) {
            byType.put(entry.type(), new Channel<>(channelId(entry), entry));
        }
        channels = Map.copyOf(byType);
        FabricHandshake.registerServer();
    }

    /** 这种包的频道名，两端都按它登记和收发。 */
    public static ResourceLocation channelId(CarianNetwork.Entry<?> entry) {
        return new ResourceLocation(Reference.MOD_ID, entry.id());
    }

    public static ResourceLocation channelOf(Class<?> packetType) {
        return lookup(packetType).id();
    }

    public static Packet<?> toVanillaPacket(Object packet) {
        return lookup(packet.getClass()).toVanilla(packet);
    }

    private static Channel<?> lookup(Class<?> type) {
        Channel<?> channel = channels.get(type);
        if (channel == null) {
            throw new IllegalArgumentException("没有登记过的包：" + type.getName());
        }
        return channel;
    }
}
