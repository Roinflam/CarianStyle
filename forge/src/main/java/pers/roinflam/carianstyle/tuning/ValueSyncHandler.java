package pers.roinflam.carianstyle.tuning;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.carianstyle.network.EnchantmentValuesPacket;
import pers.roinflam.carianstyle.network.NetworkHandler;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;

/**
 * 共享数值的同步收发。
 *
 * <h3>服务端：登录时下发一次</h3>
 * <p>
 * 数值在服务器运行期间只会因为管理员手动重载而变化，不需要持续同步，
 * 因此只在 {@link PlayerEvent.PlayerLoggedInEvent} 发一次。
 * 管理员改完配置后可以调用 {@link #broadcast()} 重发给全部在线玩家。
 * </p>
 *
 * <h3>客户端：断开时清空</h3>
 * <p>
 * 这一步不能省。玩家从「力场半径改成 20」的服务器退回单人游戏，
 * 如果覆盖值还留着，单人世界的渲染就会按 20 画圈，而单人服务端按本地配置的 12 判定——
 * 变成了原本要修的那个 bug 的镜像版本。
 * </p>
 * <p>
 * 用 {@link ClientPlayerNetworkEvent.LoggingOut} 而不是维度切换之类的事件：
 * 只有真正断开连接时本地值才重新成为权威。
 * </p>
 *
 * <h3>注册方式</h3>
 * <p>
 * 两个订阅都走注解自注册（服务端侧在 FORGE 总线，客户端侧额外限定
 * {@code Dist.CLIENT}），<b>不需要在模组主类里加任何接线</b>。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ValueSyncHandler {

    private ValueSyncHandler() {
    }

    /**
     * 玩家登录：把全部共享数值下发给他。
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(@Nonnull PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        sendTo(player);
    }

    /**
     * 向单个玩家下发共享数值。
     *
     * @param player 目标玩家
     */
    public static void sendTo(@Nonnull ServerPlayer player) {
        if (EnchantmentValues.sharedHandles().isEmpty()) {
            return;
        }
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                EnchantmentValuesPacket.ofCurrent());
    }

    /**
     * 向全部在线玩家重发共享数值。
     * <p>管理员在服务端重载配置后调用；本模组目前没有提供这样的命令，
     * 留作接口是为了将来加 {@code /carianstyle reload} 时不必再改这里。</p>
     *
     * @param server 当前服务器实例
     */
    public static void broadcast(@Nonnull MinecraftServer server) {
        if (EnchantmentValues.sharedHandles().isEmpty()) {
            return;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(),
                EnchantmentValuesPacket.ofCurrent());
        LogUtil.info("卡利亚式附魔 - 已向 %d 名在线玩家重发共享数值",
                server.getPlayerList().getPlayerCount());
    }

    /**
     * 客户端侧：断开连接时清空服务端下发的覆盖值。
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
            value = Dist.CLIENT)
    public static final class ClientSide {

        private ClientSide() {
        }

        /**
         * 断开连接：回落到本地配置。
         *
         * @param event 客户端登出事件
         */
        @SubscribeEvent
        public static void onLoggingOut(@Nonnull ClientPlayerNetworkEvent.LoggingOut event) {
            EnchantmentValues.clearSynced();
        }
    }
}
