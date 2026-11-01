package pers.roinflam.carianstyle.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 可视化系统专用网络通道（与 {@code NetworkHandler} 完全独立），通道名 {@code carianstyle:visual}
 * <p>
 * 单独建一条通道，演出包再多也不会和效果同步、数值同步挤在一起。本通道承载：叠层显示包、定点 AOE 自绘特效包、
 * 战技自绘特效包。
 * </p>
 * <p>
 * 注意：{@link #register()} 必须在 preInit 于双端各调用一次——客户端需要注册解码 / 处理器才能接收这些包。
 * </p>
 * <p>
 * 包编号写成各包类里的 {@code ID} 常量，只追加、永不复用：0 叠层显示、1 定点 AOE、2 战技。
 * 三个包都只注册在客户端一侧（{@code Side.CLIENT}），对应 1.20.1 注册时声明的 {@code PLAY_TO_CLIENT}：
 * 客户端反向发包，服务端没有登记处理器，不会去执行 {@code StackHudManager} / {@code AoeEffectManager} /
 * {@code CombatArtEffectManager} 这些只该在客户端跑的逻辑。
 * </p>
 *
 * @author RoinFlam
 */
public final class VisualNetwork {

    /** 专用通道（名字不能超过 20 个字符，这个是 18 个） */
    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID + ":visual");

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
        CHANNEL.registerMessage(StackDisplayPacket.Handler.class, StackDisplayPacket.class,
                StackDisplayPacket.ID, Side.CLIENT);
        // 定点 AOE 自绘特效包
        CHANNEL.registerMessage(AoeEffectPacket.Handler.class, AoeEffectPacket.class,
                AoeEffectPacket.ID, Side.CLIENT);
        // 战技自绘特效包（带朝向）
        CHANNEL.registerMessage(CombatArtEffectPacket.Handler.class, CombatArtEffectPacket.class,
                CombatArtEffectPacket.ID, Side.CLIENT);
    }

    /**
     * 向指定玩家发送叠层快照。
     *
     * @param player 目标玩家
     * @param packet 叠层显示包
     */
    public static void sendToPlayer(EntityPlayerMP player, StackDisplayPacket packet) {
        CHANNEL.sendTo(packet, player);
    }

    /**
     * 把包广播给某点附近一定范围内的玩家（用于定点 AOE 自绘特效）。
     * <p>
     * 1.12.2 的 {@code sendToAllAround} 只发给同一维度、距 (x,y,z) 在 {@code range} 格内的玩家，
     * 带宽随近场玩家数自然受限，对应 1.20.1 的 {@code PacketDistributor.NEAR}。
     * </p>
     *
     * @param level  服务端世界（取其维度编号）
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param range  广播半径（格）
     * @param packet 待发送包
     */
    public static void sendToNearby(WorldServer level, double x, double y, double z,
                                    double range, AoeEffectPacket packet) {
        CHANNEL.sendToAllAround(packet, new NetworkRegistry.TargetPoint(
                level.provider.getDimension(), x, y, z, range));
    }

    /**
     * 把战技特效包广播给某点附近一定范围内的玩家。
     * <p>与上方的 AOE 版本同款语义，仅包类型不同；两者分开重载而非泛化，
     * 是为了在编译期就区分两条特效链路，避免误把包发到错误的处理器上。</p>
     *
     * @param level  服务端世界（取其维度编号）
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param range  广播半径（格）
     * @param packet 待发送包
     */
    public static void sendToNearby(WorldServer level, double x, double y, double z,
                                    double range, CombatArtEffectPacket packet) {
        CHANNEL.sendToAllAround(packet, new NetworkRegistry.TargetPoint(
                level.provider.getDimension(), x, y, z, range));
    }
}
