package pers.roinflam.carianstyle.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;


/**
 * 服务端往客户端发本模组的包。
 * <p>
 * 只有服务端到客户端这一个方向。别的模组用普通 {@code ServerPlayer} 加假连接做的机器人也在玩家列表里，
 * 没协商过本模组频道的连接收到包会出错，所以发之前先问 {@link #canSend}。
 * </p>
 */
public interface PlatformNetwork {

    /** 这个玩家能不能收这种包：不是假玩家、连接还在、协商过这个包的频道。 */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    /** 发一个包；不检查 {@link #canSend}，调用方先问。 */
    void send(ServerPlayer player, CustomPacketPayload payload);
}
