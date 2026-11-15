package pers.roinflam.carianstyle.forge.platform;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.carianstyle.platform.PlatformNetwork;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Forge 上的 {@link PlatformNetwork}：包照旧经各自通道的 {@code SimpleChannel} 编码和发送，线上字节和以前一样。
 * 包类到通道的对应由入口登记通道时交过来（{@link #bind}）。
 */
public final class ForgePlatformNetwork implements PlatformNetwork {

    private static volatile Map<Class<?>, SimpleChannel> channels = Map.of();

    /** 入口登记完通道后调一次 */
    public static void bind(@Nonnull Map<Class<?>, SimpleChannel> byType) {
        channels = Map.copyOf(byType);
    }

    private static SimpleChannel channelOf(Class<?> type) {
        SimpleChannel channel = channels.get(type);
        if (channel == null) {
            throw new IllegalArgumentException("没有登记过的包：" + type.getName());
        }
        return channel;
    }

    @Override
    public boolean canSend(@Nonnull ServerPlayer player, @Nonnull Class<?> packetType) {
        if (player instanceof FakePlayer || player.connection == null) {
            return false;
        }
        SimpleChannel channel = channelOf(packetType);
        try {
            return channel.isRemotePresent(player.connection.connection);
        } catch (RuntimeException e) {
            // 别的模组自己拼的假连接可能连 netty 通道都没有，按不能收处理
            return false;
        }
    }

    @Override
    public void send(@Nonnull ServerPlayer player, @Nonnull Object packet) {
        channelOf(packet.getClass()).send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    public Packet<?> toVanillaPacket(@Nonnull Object packet) {
        return channelOf(packet.getClass()).toVanillaPacket(packet, NetworkDirection.PLAY_TO_CLIENT);
    }
}
