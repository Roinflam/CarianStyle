package pers.roinflam.carianstyle.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import pers.roinflam.carianstyle.fabric.network.FabricHandshake;
import pers.roinflam.carianstyle.fabric.network.FabricNetwork;
import pers.roinflam.carianstyle.network.CarianNetwork;

/**
 * Fabric 客户端的收包处理器：版本握手的回包，和 {@link CarianNetwork#PACKETS} 里各个包的处理。
 * <p>
 * 包类型由主入口的 {@link FabricNetwork#register()} 两端都登记过了，这里只挂处理器，不再登记类型。
 * </p>
 */
public final class FabricClientNetwork {

    private static boolean registered;

    private FabricClientNetwork() {
    }

    /** 由客户端入口调一次。 */
    public static synchronized void register() {
        if (registered) {
            throw new IllegalStateException("客户端收包处理器已经挂过了");
        }
        registered = true;

        // 配置阶段：服务端发来它的版本号，回本端的版本号，由服务端核对（对不上由服务端断开并写明原因）。
        // 不挂这个处理器的话，服务端的握手任务一直等不到回包，连接停在配置阶段。
        // 处理器在网络线程上执行，只回包，不碰游戏状态。
        ClientConfigurationNetworking.registerGlobalReceiver(FabricHandshake.Hello.TYPE,
                (hello, context) -> context.responseSender().sendPacket(FabricHandshake.Reply.current()));

        for (CarianNetwork.Entry<?> entry : CarianNetwork.PACKETS) {
            registerPlay(entry);
        }
    }

    /** 游戏阶段的包：Fabric 在渲染线程（客户端主线程）上调处理器，和包表约定的执行线程一致。 */
    private static <T extends CustomPacketPayload> void registerPlay(CarianNetwork.Entry<T> entry) {
        ClientPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) -> entry.clientHandler().accept(payload));
    }
}
