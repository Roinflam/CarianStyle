package pers.roinflam.carianstyle.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 可视化系统专用网络通道（与现有 {@code NetworkHandler} 完全独立）。
 * <p>
 * 单独建一条 SimpleChannel，避免改动现有 NetworkHandler，
 * 因而不会影响已经在工作的火焰/隐身同步逻辑。本通道承载：叠层显示包、定点 AOE 自绘特效包、
 * 战技自绘特效包。
 * <p>
 * 三个包的登记信息（类型、编解码、版本号、客户端处理器）在 {@link CarianNetwork#PACKETS}，
 * 由各加载器的入口照表登记；这里只剩发包的几个便捷方法。
 * <p>
 * <b>v2 新增：</b>{@link CombatArtEffectPacket}（战技特效，带朝向 yaw）。
 * 包 ID 追加在既有两个包之后，<b>不改动既有包的注册顺序</b>——SimpleChannel 的包 ID 由
 * 注册顺序隐式决定，双端必须一致；在末尾追加是唯一安全的扩展方式，插在中间会导致
 * 新旧客户端/服务端之间所有包 ID 错位。
 *
 * <h3>v3：三个包全部补上方向声明</h3>
 * <p>
 * 本通道的三个包<b>都是纯 S2C</b>，但此前用的是不带 {@code NetworkDirection} 的
 * 五参 {@code registerMessage} 重载，Forge 因此会放行两个方向。
 * 结果是任意客户端都能反向发包给服务端，让服务端去执行
 * {@code StackHudManager} / {@code AoeEffectManager} / {@code CombatArtEffectManager}
 * 这些本该只在客户端跑的逻辑。
 * </p>
 * <p>
 * {@link AoeEffectPacket} 与 {@link CombatArtEffectPacket} 的类注释里已经解释过
 * 「为何不用 {@code DistExecutor} 包裹处理器」——为了避开 Mohist 等混合端上
 * 服务端引用仅客户端的类导致的类加载问题。<b>那个判断是对的</b>，
 * 而声明方向恰好是这个问题的正解：包在分发入口就被方向校验拦下，
 * 服务端连处理器都不会进，自然也谈不上加载客户端类。
 * </p>
 * <p>
 * <b>不改变任何正常链路的行为</b>：服务端发、客户端收，方向与声明一致。
 * </p>
 *
 * <h3>1.21.1（NeoForge）</h3>
 * <p>
 * {@code SimpleChannel} 换成按 id 登记，三个包各有自己的 id
 * （{@code carianstyle:stack_display}、{@code aoe_effect}、{@code combat_art_effect}），版本号照旧是
 * {@link CarianNetwork#VISUAL_VERSION}（按包记，和主通道的 "4" 互不影响），都不是可选包。
 * 都只许服务端发往客户端，方向由加载器校验，客户端反向发来的包在入口就被拒，
 * 与上面 v3 的方向声明等价。发送走 {@link PayloadSend}（只发给协商过频道的玩家，范围同 NeoForge 的 {@code PacketDistributor}）。
 * </p>
 *
 * @author RoinFlam
 * @version 4
 */
public final class VisualNetwork {

    private VisualNetwork() {
    }

    /**
     * 向指定玩家发送叠层快照。
     *
     * @param player 目标玩家
     * @param packet 叠层显示包
     */
    public static void sendToPlayer(ServerPlayer player, StackDisplayPacket packet) {
        PayloadSend.toPlayer(player, packet);
    }

    /**
     * 把包广播给某点附近一定范围内的玩家（用于定点 AOE 自绘特效）。
     * <p>
     * 走 {@link PayloadSend#toNear}（范围同 NeoForge 的 {@code PacketDistributor.sendToPlayersNear}），只会发给该维度内距 (x,y,z) 在 {@code range} 格内、
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
        PayloadSend.toNear(level, x, y, z, range, packet);
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
        PayloadSend.toNear(level, x, y, z, range, packet);
    }
}
