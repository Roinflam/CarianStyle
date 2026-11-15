package pers.roinflam.carianstyle.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import pers.roinflam.carianstyle.fabric.network.FabricHandshake;
import pers.roinflam.carianstyle.fabric.network.FabricNetwork;
import pers.roinflam.carianstyle.network.CarianNetwork;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric 客户端的收包处理器：登录阶段版本握手的回包，和 {@link CarianNetwork#PACKETS} 里各个包的处理。
 */
public final class FabricClientNetwork {

    private static boolean registered;

    private FabricClientNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            throw new IllegalStateException("客户端收包处理器已经挂过了");
        }
        registered = true;

        // 不挂这个的话，服务端的登录查询得到「没听懂」，会当成没装本模组断开。只回版本号，核对由服务端做。
        ClientLoginNetworking.registerGlobalReceiver(FabricHandshake.CHANNEL,
                (client, handler, buf, listenerAdder) -> CompletableFuture.completedFuture(FabricHandshake.currentVersions()));

        for (CarianNetwork.Entry<?> entry : CarianNetwork.PACKETS) {
            registerPlay(entry);
        }
    }

    // 按频道名登记的处理器在网络线程上跑：包体在这里解完（buf 回调返回后就释放），处理转到客户端主线程
    private static <T> void registerPlay(CarianNetwork.Entry<T> entry) {
        ClientPlayNetworking.registerGlobalReceiver(FabricNetwork.channelId(entry), (client, handler, buf, responseSender) -> {
            T packet = entry.decoder().apply(buf);
            client.execute(() -> entry.clientHandler().accept(packet));
        });
    }
}
