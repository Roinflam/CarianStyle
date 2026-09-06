package pers.roinflam.carianstyle.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.Optional;

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
 * <h3>v3：三个包全部补上方向声明</h3>
 * <p>
 * 本通道的三个包<b>都是纯 S2C</b>，但此前用的是不带 {@link NetworkDirection} 的
 * 五参 {@code registerMessage} 重载，Forge 因此会放行两个方向。
 * 结果是任意客户端都能反向发包给服务端，让服务端去执行
 * {@code StackHudManager} / {@code AoeEffectManager} / {@code CombatArtEffectManager}
 * 这些本该只在客户端跑的逻辑。
 * </p>
 * <p>
 * {@link AoeEffectPacket} 与 {@link CombatArtEffectPacket} 的类注释里已经解释过
 * 「为何不用 {@code DistExecutor} 包裹处理器」——为了避开 Mohist 等混合端上
 * 服务端引用 {@code @OnlyIn(CLIENT)} 类导致的类加载问题。<b>那个判断是对的</b>，
 * 而声明方向恰好是这个问题的正解：包在分发入口就被方向校验拦下，
 * 服务端连处理器都不会进，自然也谈不上加载客户端类。
 * </p>
 * <p>
 * <b>不改变任何正常链路的行为</b>：服务端发、客户端收，方向与声明一致。
 * </p>
 *
 * @author FlameForge
 * @version 3
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

    /**
     * 本通道全部包共用的方向：服务端 -> 客户端。
     * <p>抽成常量而非每处写一遍，是为了让「本通道只有 S2C 包」这条约束一眼可见；
     * 将来若真要加 C2S 包，改动会集中暴露在这里而不是散落在三处注册调用里。</p>
     */
    private static final Optional<NetworkDirection> S2C = Optional.of(NetworkDirection.PLAY_TO_CLIENT);

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
                StackDisplayPacket::handle,
                S2C
        );
        // 定点 AOE 自绘特效包（S2C）
        CHANNEL.registerMessage(
                packetId++,
                AoeEffectPacket.class,
                AoeEffectPacket::encode,
                AoeEffectPacket::decode,
                AoeEffectPacket::handle,
                S2C
        );
        // v2：战技自绘特效包（S2C，带朝向）
        CHANNEL.registerMessage(
                packetId++,
                CombatArtEffectPacket.class,
                CombatArtEffectPacket::encode,
                CombatArtEffectPacket::decode,
                CombatArtEffectPacket::handle,
                S2C
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
