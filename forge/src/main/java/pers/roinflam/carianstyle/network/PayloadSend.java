package pers.roinflam.carianstyle.network;

import net.minecraft.network.protocol.Packet;
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
 * <h3>为什么这么写</h3>
 * <p>
 * 以前直接用 Forge {@code SimpleChannel} 的 {@code PacketDistributor}，发包代码绑死在一个加载器上。
 * 现在能不能收、单发交给平台接口；广播的几种范围照 Forge {@code PacketDistributor} 里调的
 * {@code PlayerList.broadcastAll} / {@code broadcast} 原样自己遍历，逐个先判能不能收，
 * 原版包只建一次、多人共用，和 {@code SimpleChannel.send} 一样只编码一次。
 * </p>
 *
 * <h3>行为影响</h3>
 * <p>
 * 真玩家握手时都协商过本模组的两个通道（版本不同或没装模组的连不进来），收到的包、范围、顺序和字节都不变；
 * 假玩家和没协商通道的假连接不再收包（{@code FakePlayer} 的连接本来就丢弃发给它的包）。
 * </p>
 */
public final class PayloadSend {

    private PayloadSend() {
    }

    /** 这个玩家能不能收这个包：不是假人、连接还在、协商过这个包所在的通道 */
    public static boolean canReceive(@Nonnull ServerPlayer player, @Nonnull Object packet) {
        return Services.network().canSend(player, packet.getClass());
    }

    /** 发给一个玩家（对应 {@code PacketDistributor.PLAYER}） */
    public static void toPlayer(@Nonnull ServerPlayer player, @Nonnull Object packet) {
        if (canReceive(player, packet)) {
            Services.network().send(player, packet);
        }
    }

    /** 发给某个维度里的全部玩家（对应 {@code PacketDistributor.DIMENSION}，即 {@code PlayerList.broadcastAll(包, 维度)}） */
    public static void toDimension(@Nonnull ServerLevel level, @Nonnull Object packet) {
        ResourceKey<Level> dimension = level.dimension();
        Packet<?> vanilla = null;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() == dimension && canReceive(player, packet)) {
                if (vanilla == null) {
                    vanilla = Services.network().toVanillaPacket(packet);
                }
                player.connection.send(vanilla);
            }
        }
    }

    /**
     * 发给某个维度里离 (x, y, z) 不到 {@code radius} 格的玩家（对应 {@code PacketDistributor.NEAR}，
     * 距离判断同 {@code PlayerList.broadcast}）。
     */
    public static void toNear(@Nonnull ServerLevel level, double x, double y, double z, double radius,
                              @Nonnull Object packet) {
        ResourceKey<Level> dimension = level.dimension();
        double r2 = radius * radius;
        Packet<?> vanilla = null;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() != dimension) {
                continue;
            }
            double dx = x - player.getX();
            double dy = y - player.getY();
            double dz = z - player.getZ();
            if (dx * dx + dy * dy + dz * dz < r2 && canReceive(player, packet)) {
                if (vanilla == null) {
                    vanilla = Services.network().toVanillaPacket(packet);
                }
                player.connection.send(vanilla);
            }
        }
    }

    /**
     * 发给全部在线玩家（对应 {@code PacketDistributor.ALL}）。
     * 服务器由调用方给（调用方手里本来就有），不去全局取当前服务器；{@code server} 为 null 时什么都不做，
     * 和以前「没有在运行的服务器就不发」一样。
     */
    public static void toAll(MinecraftServer server, @Nonnull Object packet) {
        if (server == null) {
            return;
        }
        Packet<?> vanilla = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (canReceive(player, packet)) {
                if (vanilla == null) {
                    vanilla = Services.network().toVanillaPacket(packet);
                }
                player.connection.send(vanilla);
            }
        }
    }
}
