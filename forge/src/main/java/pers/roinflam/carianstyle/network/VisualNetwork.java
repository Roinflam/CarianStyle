package pers.roinflam.carianstyle.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 可视化系统专用网络通道（与现有 {@code NetworkHandler} 完全独立）。
 * <p>
 * 单独建一条 SimpleChannel，避免改动现有 NetworkHandler，
 * 因而不会影响已经在工作的火焰/隐身同步逻辑。本通道目前只承载叠层显示包。
 * <p>
 * 注意：{@link #register()} 必须在 mod 加载阶段（如主类构造或 FMLCommonSetupEvent）
 * 于双端各调用一次——客户端需要注册解码/处理器才能接收 S2C 包。
 *
 * @author FlameForge
 */
public final class VisualNetwork {

    /** 协议版本（双端一致即可） */
    private static final String PROTOCOL_VERSION = "1";

    /** 专用通道（命名空间用 mod id，路径 "visual"） */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Reference.MOD_ID, "visual"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /** 包 ID 自增计数 */
    private static int packetId = 0;

    private static boolean registered = false;

    private VisualNetwork() {
    }

    /**
     * 注册全部包。双端各调用一次，重复调用安全。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(
                packetId++,
                StackDisplayPacket.class,
                StackDisplayPacket::encode,
                StackDisplayPacket::decode,
                StackDisplayPacket::handle
        );
    }

    /**
     * 向指定玩家发送叠层快照。
     *
     * @param player 目标玩家
     * @param packet 叠层显示包
     */
    public static void sendToPlayer(ServerPlayer player, StackDisplayPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
