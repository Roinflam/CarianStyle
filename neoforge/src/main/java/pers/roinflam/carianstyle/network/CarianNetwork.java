package pers.roinflam.carianstyle.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.function.Consumer;

/**
 * 本模组全部网络包的登记表：类型、编解码、版本号、客户端收到后怎么处理。
 * <p>
 * 包都是服务端发给客户端的。各加载器的入口照这张表登记（NeoForge 在包登记事件里逐条 {@code playToClient}），
 * 包类自己不再碰加载器的网络接口；发包一律走 {@link PayloadSend}。客户端处理器都在主线程跑。
 * </p>
 *
 * <h3>两组版本号</h3>
 * <p>
 * 主通道（效果同步、数值下发）用 {@link #MAIN_VERSION}，可视化通道（叠层显示、两种自绘特效）用
 * {@link #VISUAL_VERSION}，两组各管各的，和以前分成两个通道时一样。每个包都不是可选的：
 * 对端没有这个包、或者版本号不同，握手时就拒绝连接。
 * </p>
 * <p>
 * 包按 id（{@code carianstyle:client_sync_effect} 这类）区分，不靠登记顺序编号，所以表里的先后不影响协议；
 * 新包照样追加在所属那一组末尾，并升那一组的版本号。
 * </p>
 *
 * <h3>主通道版本号的来历</h3>
 * <ul>
 *     <li>"1" → "2"：追加了 {@link EnchantmentValuesPacket}。版本号不变的话旧客户端能进新服，
 *     登录时收到不认识的包就断线，报错完全看不出是版本不一致；升位后握手阶段就拒绝，并明确提示模组版本对不上。</li>
 *     <li>"2" → "3"（2.2.0）：没加包，为了强制客户端更新。隐身的模型隐藏和碰撞箱屏蔽都在客户端执行，
 *     2.1.0 客户端在独立服上两样都不做，不更新模组就能看穿隐身。</li>
 *     <li>"3" → "4"（2.3.1）：也没加包。隐藏光源方块多了亮度属性，方块状态数量变了，旧客户端进服后方块状态 id 全部错位；
 *     岩石剑的「无法跳跃」也在客户端执行，旧客户端照样能跳。</li>
 * </ul>
 * <p>
 * 客户端行为有反作弊意义的改动，也要升主通道版本号，否则旧客户端能进新服。
 * </p>
 */
public final class CarianNetwork {

    /** 主通道版本号（来历见类注释） */
    public static final String MAIN_VERSION = "4";

    /** 可视化通道版本号 */
    public static final String VISUAL_VERSION = "1";

    /**
     * 一种包的登记信息。
     *
     * @param type          包类型（含 id）
     * @param codec         编解码
     * @param version       所属那一组的版本号
     * @param clientHandler 客户端收到后在主线程调用
     */
    public record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                       StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                                       String version,
                                                       Consumer<T> clientHandler) {
    }

    /** 全部包，先主通道、再可视化通道，组内按加入的先后 */
    public static final List<Entry<?>> PACKETS = List.of(
            new Entry<>(ClientSyncEffectPacket.TYPE, ClientSyncEffectPacket.STREAM_CODEC, MAIN_VERSION,
                    ClientSyncEffectPacket::handle),
            new Entry<>(EnchantmentValuesPacket.TYPE, EnchantmentValuesPacket.STREAM_CODEC, MAIN_VERSION,
                    EnchantmentValuesPacket::handle),
            new Entry<>(StackDisplayPacket.TYPE, StackDisplayPacket.STREAM_CODEC, VISUAL_VERSION,
                    StackDisplayPacket::handle),
            new Entry<>(AoeEffectPacket.TYPE, AoeEffectPacket.STREAM_CODEC, VISUAL_VERSION,
                    AoeEffectPacket::handle),
            new Entry<>(CombatArtEffectPacket.TYPE, CombatArtEffectPacket.STREAM_CODEC, VISUAL_VERSION,
                    CombatArtEffectPacket::handle));

    private CarianNetwork() {
    }
}
