package pers.roinflam.carianstyle.fabric.platform;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import pers.roinflam.carianstyle.platform.PlatformNetwork;

import javax.annotation.Nonnull;

/**
 * Fabric 上的 {@link PlatformNetwork}：判断和发送都经 {@link ServerPlayNetworking}，判断条件同 NeoForge。
 */
public final class FabricPlatformNetwork implements PlatformNetwork {

    @Override
    public boolean canSend(@Nonnull ServerPlayer player, @Nonnull CustomPacketPayload.Type<?> type) {
        if (player instanceof FakePlayer || player.connection == null) {
            return false;
        }
        try {
            return ServerPlayNetworking.canSend(player, type);
        } catch (RuntimeException e) {
            // 别的模组自己拼的假连接可能连协商信息都没有，按不能收处理
            return false;
        }
    }

    @Override
    public void send(@Nonnull ServerPlayer player, @Nonnull CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
