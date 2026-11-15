package pers.roinflam.carianstyle.platform;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;


/**
 * 服务端往客户端发本模组的包。
 * <p>
 * 只有服务端到客户端这一个方向。包是本模组自己的包类对象，包类型就是包的类。
 * 别的模组用普通 {@code ServerPlayer} 加假连接做的机器人也在玩家列表里，没协商过本模组频道的连接不该收包，
 * 所以发之前先问 {@link #canSend}。
 * </p>
 * <p>
 * 1.20.1 没有原版的自定义包接口，广播时多人共用的那个原版包也只能由平台建（通道名、包头各加载器不一样），
 * 所以比 1.21.1 多一个 {@link #toVanillaPacket}。
 * </p>
 */
public interface PlatformNetwork {

    /** 这个玩家能不能收这种包：不是假玩家、连接还在、协商过这个包所在的频道。 */
    boolean canSend(ServerPlayer player, Class<?> packetType);

    /** 发一个包；不检查 {@link #canSend}，调用方先问。 */
    void send(ServerPlayer player, Object packet);

    /** 把包编成原版包，可以原样发给多个玩家；不检查 {@link #canSend}。 */
    Packet<?> toVanillaPacket(Object packet);
}
