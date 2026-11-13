package pers.roinflam.carianstyle.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import pers.roinflam.carianstyle.platform.Services;

import javax.annotation.Nonnull;

/**
 * 本模组所有服务端 → 客户端的发包都走这里，只发给能收这个包的玩家。
 *
 * <h3>问题</h3>
 * <p>
 * NeoForge 发自定义包时先查对端有没有协商过这个频道（{@code ServerCommonPacketListenerImpl.send} 第一行
 * {@code NetworkRegistry.checkPacket}），没有就抛 {@code UnsupportedOperationException}。
 * 真玩家都协商过；NeoForge 自己的 {@code FakePlayer} 发包是空操作。但别的模组用普通 {@code ServerPlayer}
 * 加假连接做的机器人 / 假人也在玩家列表里，{@code PacketDistributor} 的按维度、按范围广播会挨个发到它们，
 * 异常从服务器 tick 里的同步逻辑冒出来，轻则这一轮同步中断，重则整个 tick 出错。
 * 1.20.1 的 {@code SimpleChannel} 不查对端频道，照发不误，没有这个问题。
 * </p>
 *
 * <h3>为什么这么改</h3>
 * <p>
 * 广播的几种范围照 {@code PlayerList.broadcastAll} / {@code broadcast} 原样自己遍历，逐个先判
 * {@code connection.hasChannel(包类型)}，不支持的跳过；包对象只建一次，和原来一样。
 * 能不能收、单发交给平台接口；广播时多人共用一个原版的自定义包对象，直接 {@code connection.send}，
 * 省得每人新建一个（各加载器发自定义包最后都是这个原版包，结果一样）。
 * </p>
 *
 * <h3>行为影响</h3>
 * <p>
 * 真玩家收到的包、范围、顺序都不变；没协商频道的假人不再收包，也不再抛异常。
 * </p>
 */
public final class PayloadSend {

    private PayloadSend() {
    }

    /**
     * 这个玩家能不能收这个包：不是假人、连接还在、协商过这个包的频道。
     * 判断交给平台（{@code PlatformNetwork.canSend}）：NeoForge 上就是原来这几条，
     * 不是 {@code FakePlayer}、{@code connection} 不为空、{@code hasChannel(包类型)}，查的时候抛异常按不能收。
     */
    public static boolean canReceive(@Nonnull ServerPlayer player, @Nonnull CustomPacketPayload payload) {
        return Services.network().canSend(player, payload.type());
    }

    /** 发给一个玩家（对应 {@code PacketDistributor.sendToPlayer}），发送交给平台（{@code PlatformNetwork.send}） */
    public static void toPlayer(@Nonnull ServerPlayer player, @Nonnull CustomPacketPayload payload) {
        if (canReceive(player, payload)) {
            Services.network().send(player, payload);
        }
    }

    /** 发给某个维度里的全部玩家（对应 {@code PacketDistributor.sendToPlayersInDimension}） */
    public static void toDimension(@Nonnull ServerLevel level, @Nonnull CustomPacketPayload payload) {
        ResourceKey<Level> dimension = level.dimension();
        Packet<?> packet = null;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() == dimension && canReceive(player, payload)) {
                if (packet == null) {
                    packet = new ClientboundCustomPayloadPacket(payload);
                }
                player.connection.send(packet);
            }
        }
    }

    /**
     * 发给某个维度里离 (x, y, z) 不到 {@code radius} 格的玩家（对应 {@code PacketDistributor.sendToPlayersNear}，
     * 距离判断同 {@code PlayerList.broadcast}）。
     */
    public static void toNear(@Nonnull ServerLevel level, double x, double y, double z, double radius,
                              @Nonnull CustomPacketPayload payload) {
        ResourceKey<Level> dimension = level.dimension();
        double r2 = radius * radius;
        Packet<?> packet = null;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() != dimension) {
                continue;
            }
            double dx = x - player.getX();
            double dy = y - player.getY();
            double dz = z - player.getZ();
            if (dx * dx + dy * dy + dz * dz < r2 && canReceive(player, payload)) {
                if (packet == null) {
                    packet = new ClientboundCustomPayloadPacket(payload);
                }
                player.connection.send(packet);
            }
        }
    }

    /**
     * 发给全部在线玩家（对应 {@code PacketDistributor.sendToAllPlayers}）。
     * 服务器由调用方给（调用方手里本来就有），不去全局取当前服务器；{@code server} 为 null 时什么都不做，
     * 和以前「没有在运行的服务器就不发」一样。
     */
    public static void toAll(MinecraftServer server, @Nonnull CustomPacketPayload payload) {
        if (server == null) {
            return;
        }
        Packet<?> packet = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (canReceive(player, payload)) {
                if (packet == null) {
                    packet = new ClientboundCustomPayloadPacket(payload);
                }
                player.connection.send(packet);
            }
        }
    }
}
