package pers.roinflam.carianstyle.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import pers.roinflam.carianstyle.fabric.mixin.network.ServerLoginPacketListenerAccessor;
import pers.roinflam.carianstyle.network.CarianNetwork;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;


/**
 * 进服时的版本握手（登录阶段）。
 * <p>
 * Forge 在登录时按每个通道登记的版本号自动核对，对不上就拒绝连接；Fabric 没有这一步，
 * 没装本模组的客户端照样能进服（注册表同步对没装的客户端直接跳过），进来后碰到本模组的方块、物品就出错，
 * 隐身之类在客户端执行的效果也不起作用。所以 Fabric 上自己握手：服务端在登录查询阶段往
 * {@link #CHANNEL} 发两组版本号，客户端原样回自己的两组，没听懂（没装本模组）或者对不上就断开。
 * </p>
 * <p>
 * 版本号就是 {@link CarianNetwork#MAIN_VERSION}、{@link CarianNetwork#VISUAL_VERSION}，和 Forge 上两个通道登记的版本号是同一对。
 * 装了本模组的客户端进没装的服时服务端不发查询，照常放行。
 * </p>
 */
public final class FabricHandshake {

    public static final ResourceLocation CHANNEL = new ResourceLocation(Reference.MOD_ID, "handshake");

    // 版本号是几个字符的短串，读的时候卡上限，免得畸形包让服务端分配大缓冲
    private static final int MAX_VERSION_LENGTH = 32;

    private FabricHandshake() {
    }

    /** 两组版本号 */
    public record Versions(String main, String visual) {

        public static Versions current() {
            return new Versions(CarianNetwork.MAIN_VERSION, CarianNetwork.VISUAL_VERSION);
        }

        public static Versions read(FriendlyByteBuf buf) {
            return new Versions(buf.readUtf(MAX_VERSION_LENGTH), buf.readUtf(MAX_VERSION_LENGTH));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(main, MAX_VERSION_LENGTH);
            buf.writeUtf(visual, MAX_VERSION_LENGTH);
        }

        public boolean matchesCurrent() {
            return CarianNetwork.MAIN_VERSION.equals(main) && CarianNetwork.VISUAL_VERSION.equals(visual);
        }
    }

    /** 本端两组版本号写成的缓冲：服务端的查询和客户端的回包都用它。 */
    public static FriendlyByteBuf currentVersions() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        Versions.current().write(buf);
        return buf;
    }

    /** 服务端：挂上发起查询和核对回包。由主入口调一次。 */
    static void registerServer() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            // 单人游戏的房主走内存连接，同一个进程，模组一定一样；局域网加入的照常核对
            if (((ServerLoginPacketListenerAccessor) handler).carianstyle$getConnection().isMemoryConnection()) {
                return;
            }
            sender.sendPacket(CHANNEL, currentVersions());
        });
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL,
                (server, handler, understood, buf, synchronizer, responseSender) -> onReply(handler, understood, buf));
    }

    /** 在网络线程上执行，只读回包、不碰服务器状态。 */
    private static void onReply(ServerLoginPacketListenerImpl handler, boolean understood,
                                FriendlyByteBuf buf) {
        if (!understood) {
            LogUtil.info("卡利亚式附魔 - 拒绝连接：%s 的客户端没有安装本模组", handler.getUserName());
            handler.disconnect(Component.literal(
                    "这个服务器需要安装卡利亚式附魔（CarianStyle）模组，请安装与服务器相同的版本后再连接。\n"
                            + "This server requires the CarianStyle mod. Install the same version as the server and reconnect."));
            return;
        }
        Versions client;
        try {
            client = Versions.read(buf);
        } catch (RuntimeException e) {
            client = new Versions("?", "?");
        }
        if (client.matchesCurrent()) {
            return;
        }
        Versions server = Versions.current();
        LogUtil.info("卡利亚式附魔 - 拒绝连接：%s 的模组网络版本 %s / %s，服务器 %s / %s",
                handler.getUserName(), client.main(), client.visual(), server.main(), server.visual());
        handler.disconnect(Component.literal(
                "卡利亚式附魔（CarianStyle）的版本和服务器不一致，请安装与服务器相同的版本。"
                        + "（网络版本：客户端 " + client.main() + " / " + client.visual()
                        + "，服务器 " + server.main() + " / " + server.visual() + "）\n"
                        + "CarianStyle version mismatch. Install the same version as the server. "
                        + "(network version: client " + client.main() + " / " + client.visual()
                        + ", server " + server.main() + " / " + server.visual() + ")"));
    }
}
