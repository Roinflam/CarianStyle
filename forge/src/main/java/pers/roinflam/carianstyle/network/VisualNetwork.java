package pers.roinflam.carianstyle.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 可视化系统专用网络通道（与现有 {@code NetworkHandler} 完全独立）。
 * <p>
 * 单独建一条 SimpleChannel，避免改动现有 NetworkHandler，
 * 因而不会影响已经在工作的火焰/隐身同步逻辑。本通道承载：叠层显示包、定点 AOE 自绘特效包、
 * 战技自绘特效包。
 * <p>
 * 注意：{@link #register()} 必须在 mod 加载阶段（如主类构造或 FMLCommonSetupEvent）
 * 于双端各调用一次——客户端需要注册解码/处理器才能接收 S2C 包。
 * <p>
 * <b>v2 新增：</b>{@link CombatArtEffectPacket}（战技特效，带朝向 yaw）。
 * 包 ID 追加在既有两个包之后，<b>不改动既有包的注册顺序</b>——SimpleChannel 的包 ID 由
 * 注册顺序隐式决定，双端必须一致；在末尾追加是唯一安全的扩展方式，插在中间会导致
 * 新旧客户端/服务端之间所有包 ID 错位。
 *
 * @author FlameForge
 * @version 2
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
     * <p><b>注册顺序即包 ID，新增包务必追加在末尾</b>（详见类注释）。</p>
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
        // 定点 AOE 自绘特效包（S2C）
        CHANNEL.registerMessage(
                packetId++,
                AoeEffectPacket.class,
                AoeEffectPacket::encode,
                AoeEffectPacket::decode,
                AoeEffectPacket::handle
        );
        // v2：战技自绘特效包（S2C，带朝向）
        CHANNEL.registerMessage(
                packetId++,
                CombatArtEffectPacket.class,
                CombatArtEffectPacket::encode,
                CombatArtEffectPacket::decode,
                CombatArtEffectPacket::handle
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

    /**
     * 把包广播给某点附近一定范围内的玩家（用于定点 AOE 自绘特效）。
     * <p>
     * 使用 {@link PacketDistributor#NEAR}，原版只会发给该维度内距 (x,y,z) 在 {@code range} 格内、
     * 且正在追踪该区域的玩家，因此带宽随近场玩家数自然受限。
     * </p>
     *
     * @param level  服务端世界（取其维度键）
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param range  广播半径（格）
     * @param packet 待发送包
     */
    public static void sendToNearby(ServerLevel level, double x, double y, double z,
                                    double range, AoeEffectPacket packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(
                () -> new PacketDistributor.TargetPoint(x, y, z, range, level.dimension())), packet);
    }

    /**
     * 把战技特效包广播给某点附近一定范围内的玩家（v2 新增）。
     * <p>与上方的 AOE 版本同款语义，仅包类型不同；两者分开重载而非泛化，
     * 是为了在编译期就区分两条特效链路，避免误把包发到错误的处理器上。</p>
     *
     * @param level  服务端世界（取其维度键）
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param range  广播半径（格）
     * @param packet 待发送包
     */
    public static void sendToNearby(ServerLevel level, double x, double y, double z,
                                    double range, CombatArtEffectPacket packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(
                () -> new PacketDistributor.TargetPoint(x, y, z, range, level.dimension())), packet);
    }
}
