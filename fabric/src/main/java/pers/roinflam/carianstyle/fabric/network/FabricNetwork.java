package pers.roinflam.carianstyle.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import pers.roinflam.carianstyle.network.CarianNetwork;
import pers.roinflam.carianstyle.utils.util.LogUtil;

/**
 * Fabric 上的网络登记（两端共用的部分和服务端的部分）。
 * <ul>
 *   <li>{@link CarianNetwork#PACKETS} 里的包逐个登记成服务端发往客户端的游戏阶段包。包类型两端都要登记，
 *       客户端的收包处理器由客户端入口另外挂。</li>
 *   <li>握手的两个包登记到配置阶段，服务端挂上发起和核对的逻辑（{@link FabricHandshake}）；
 *       客户端回包的处理器由客户端入口挂。</li>
 * </ul>
 * <p>
 * 由主入口调一次，要早于任何收包处理器的登记（Fabric 要求先登记包类型）。
 * </p>
 */
public final class FabricNetwork {

    private static boolean registered;

    private FabricNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            throw new IllegalStateException("网络包已经登记过了");
        }
        registered = true;

        for (CarianNetwork.Entry<?> entry : CarianNetwork.PACKETS) {
            registerPlay(entry);
        }

        PayloadTypeRegistry.configurationS2C().register(FabricHandshake.Hello.TYPE, FabricHandshake.Hello.STREAM_CODEC);
        PayloadTypeRegistry.configurationC2S().register(FabricHandshake.Reply.TYPE, FabricHandshake.Reply.STREAM_CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register(FabricNetwork::onConfigure);
        ServerConfigurationNetworking.registerGlobalReceiver(FabricHandshake.Reply.TYPE, (reply, context) ->
                onReply(reply, context.networkHandler()));
    }

    private static <T extends CustomPacketPayload> void registerPlay(CarianNetwork.Entry<T> entry) {
        PayloadTypeRegistry.playS2C().register(entry.type(), entry.codec());
    }

    /**
     * 配置阶段开始：单人游戏的房主不核（同一个进程，模组一定一样）；客户端没登记握手包（没装本模组）直接断开；
     * 否则排一个握手任务，等客户端回包。在网络线程上执行，不碰服务器状态。
     */
    private static void onConfigure(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        if (server.isSingleplayerOwner(handler.getOwner())) {
            return;
        }
        if (!ServerConfigurationNetworking.canSend(handler, FabricHandshake.Hello.TYPE)) {
            LogUtil.info("卡利亚式附魔 - 拒绝连接：%s 的客户端没有安装本模组", handler.getOwner().getName());
            handler.disconnect(Component.literal(
                    "这个服务器需要安装卡利亚式附魔（CarianStyle）模组，请安装与服务器相同的版本后再连接。\n"
                            + "This server requires the CarianStyle mod. Install the same version as the server and reconnect."));
            return;
        }
        handler.addTask(new FabricHandshake.Task());
    }

    /** 收到客户端回包：版本号都一致就完成握手任务，否则断开并写明两边的版本号。也在网络线程上执行。 */
    private static void onReply(FabricHandshake.Reply reply, ServerConfigurationPacketListenerImpl handler) {
        if (reply.matchesCurrent()) {
            handler.completeTask(FabricHandshake.Task.TYPE);
            return;
        }
        FabricHandshake.Hello server = FabricHandshake.Hello.current();
        LogUtil.info("卡利亚式附魔 - 拒绝连接：%s 的模组网络版本 %s / %s，服务器 %s / %s",
                handler.getOwner().getName(), reply.main(), reply.visual(), server.main(), server.visual());
        handler.disconnect(Component.literal(
                "卡利亚式附魔（CarianStyle）的版本和服务器不一致，请安装与服务器相同的版本。"
                        + "（网络版本：客户端 " + reply.main() + " / " + reply.visual()
                        + "，服务器 " + server.main() + " / " + server.visual() + "）\n"
                        + "CarianStyle version mismatch. Install the same version as the server. "
                        + "(network version: client " + reply.main() + " / " + reply.visual()
                        + ", server " + server.main() + " / " + server.visual() + ")"));
    }
}
