package pers.roinflam.carianstyle.neoforge.platform;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import pers.roinflam.carianstyle.platform.PlatformNetwork;

import javax.annotation.Nonnull;

/**
 * NeoForge 上的 {@link PlatformNetwork}，判断和发送都照现在发包工具类的写法。
 */
public final class NeoForgePlatformNetwork implements PlatformNetwork {

    @Override
    public boolean canSend(@Nonnull ServerPlayer player, @Nonnull CustomPacketPayload.Type<?> type) {
        if (player instanceof FakePlayer || player.connection == null) {
            return false;
        }
        try {
            return player.connection.hasChannel(type);
        } catch (RuntimeException e) {
            // 别的模组自己拼的假连接可能连协商信息都没有，按不能收处理
            return false;
        }
    }

    @Override
    public void send(@Nonnull ServerPlayer player, @Nonnull CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
}
