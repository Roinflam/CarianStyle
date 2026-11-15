package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 本模组全部网络包的登记表：包类、编解码、所属通道、客户端收到后怎么处理。
 * <p>
 * 包都是服务端发给客户端的。各加载器的入口照这张表登记，包类自己不再碰加载器的网络接口；
 * 发包一律走 {@link PayloadSend}。客户端处理器都在主线程跑。
 * </p>
 *
 * <h3>两个通道</h3>
 * <p>
 * 主通道 {@code carianstyle:main}（效果同步、数值下发）版本 {@link #MAIN_VERSION}，可视化通道
 * {@code carianstyle:visual}（叠层显示、两种自绘特效）版本 {@link #VISUAL_VERSION}，各管各的。
 * 对端没有这个通道、或者版本号不同，握手时就拒绝连接。
 * </p>
 * <p>
 * Forge 上同一通道内按表里的先后编号（主通道 0、1，可视化通道 0、1、2），编号就是线上的包头，
 * 所以<b>只能在所属通道末尾追加，并升那个通道的版本号</b>；插在中间会让新旧两端的包全部错位。
 * {@link Entry#id()} 只给按名字区分包的加载器用。
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
     * 一个通道。
     *
     * @param name    通道名的路径部分（命名空间是本模组 id）
     * @param version 版本号
     */
    public record Channel(String name, String version) {
    }

    public static final Channel MAIN = new Channel("main", MAIN_VERSION);

    public static final Channel VISUAL = new Channel("visual", VISUAL_VERSION);

    /**
     * 一种包的登记信息。
     *
     * @param type          包类
     * @param id            包名（路径部分）
     * @param channel       所属通道
     * @param encoder       编码
     * @param decoder       解码
     * @param clientHandler 客户端收到后在主线程调用
     */
    public record Entry<T>(Class<T> type,
                           String id,
                           Channel channel,
                           BiConsumer<T, FriendlyByteBuf> encoder,
                           Function<FriendlyByteBuf, T> decoder,
                           Consumer<T> clientHandler) {
    }

    /** 全部包，先主通道、再可视化通道，通道内按加入的先后 */
    public static final List<Entry<?>> PACKETS = List.of(
            new Entry<>(ClientSyncEffectPacket.class, "client_sync_effect", MAIN,
                    ClientSyncEffectPacket::encode, ClientSyncEffectPacket::decode, ClientSyncEffectPacket::handle),
            new Entry<>(EnchantmentValuesPacket.class, "enchantment_values", MAIN,
                    EnchantmentValuesPacket::encode, EnchantmentValuesPacket::decode, EnchantmentValuesPacket::handle),
            new Entry<>(StackDisplayPacket.class, "stack_display", VISUAL,
                    StackDisplayPacket::encode, StackDisplayPacket::decode, StackDisplayPacket::handle),
            new Entry<>(AoeEffectPacket.class, "aoe_effect", VISUAL,
                    AoeEffectPacket::encode, AoeEffectPacket::decode, AoeEffectPacket::handle),
            new Entry<>(CombatArtEffectPacket.class, "combat_art_effect", VISUAL,
                    CombatArtEffectPacket::encode, CombatArtEffectPacket::decode, CombatArtEffectPacket::handle));

    private CarianNetwork() {
    }
}
