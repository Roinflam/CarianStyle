package pers.roinflam.carianstyle.fabric.platform;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import pers.roinflam.carianstyle.fabric.network.FabricNetwork;
import pers.roinflam.carianstyle.platform.PlatformNetwork;


/**
 * Fabric 上的 {@link PlatformNetwork}：每种包一个频道（{@link FabricNetwork#channelOf}），
 * 判断和发送都经 {@link ServerPlayNetworking}，判断条件同 Forge。
 */
public final class FabricPlatformNetwork implements PlatformNetwork {

    @Override
    public boolean canSend(ServerPlayer player, Class<?> packetType) {
        if (player instanceof FakePlayer || player.connection == null) {
            return false;
        }
        try {
            return ServerPlayNetworking.canSend(player, FabricNetwork.channelOf(packetType));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            // 别的模组自己拼的假连接可能连协商信息都没有，按不能收处理
            return false;
        }
    }

    @Override
    public void send(ServerPlayer player, Object packet) {
        player.connection.send(toVanillaPacket(packet));
    }

    @Override
    public Packet<?> toVanillaPacket(Object packet) {
        return FabricNetwork.toVanillaPacket(packet);
    }
}
