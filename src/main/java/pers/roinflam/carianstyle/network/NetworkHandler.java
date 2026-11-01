package pers.roinflam.carianstyle.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 网络处理器：{@code carianstyle:main} 通道，承载效果同步和共享数值同步
 *
 * <h3>只有服务端发、客户端收</h3>
 * <p>
 * 两个包都只注册在客户端一侧（{@code registerMessage(..., Side.CLIENT)}）。
 * 1.12.2 的 {@code SimpleNetworkWrapper} 按收包一侧查处理器，客户端反过来往服务端发这两种包，
 * 服务端没有登记处理器，包根本不会被处理。这对应 1.20.1 注册时声明的 {@code PLAY_TO_CLIENT}。
 * </p>
 *
 * <h3>包编号</h3>
 * <p>
 * 编号写成各包类里的 {@code ID} 常量，只追加、永不复用、永不改含义。
 * 通道名在 1.12.2 握手里按最多 20 个字符读，超了会断线，{@code carianstyle:main} 是 16 个。
 * </p>
 *
 * <h3>协议版本</h3>
 * <p>
 * 1.12.2 的 {@code SimpleNetworkWrapper} 没有每个通道的版本号，起作用的是 FML 握手时的模组列表检查。
 * 主类用 {@code @NetworkCheckHandler} 换掉了默认检查器（默认的只有服务端会拒绝客户端，
 * 客户端从来不拒绝服务端），两边都按 {@link #PROTOCOL_SINCE} 比对对方的模组版本。
 * </p>
 * <p>
 * 改了任何包格式、加了包、改了效果序号的含义、注册表有增删，就把 {@link #PROTOCOL_VERSION} 加一、
 * {@link #PROTOCOL_SINCE} 改成这次发布的版本号。2.3.0 本身就是新协议的起点：
 * 隐藏药水删除、光源方块变成 16 个状态，注册表都变了。
 * </p>
 */
public class NetworkHandler {

    /** 1.12.2 自己的协议代数，只做记录 */
    public static final String PROTOCOL_VERSION = "1";

    /** 当前协议从哪个模组版本开始；对方的版本低于它就拒绝连接 */
    public static final String PROTOCOL_SINCE = "2.3.0";

    /** 通道 */
    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID + ":main");

    private static boolean registered = false;

    /**
     * 注册所有数据包（preInit，两端各一次；重复调用安全）
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(ClientSyncEffectPacket.Handler.class, ClientSyncEffectPacket.class,
                ClientSyncEffectPacket.ID, Side.CLIENT);
        // 共享数值下发包
        CHANNEL.registerMessage(EnchantmentValuesPacket.Handler.class, EnchantmentValuesPacket.class,
                EnchantmentValuesPacket.ID, Side.CLIENT);
    }
}
