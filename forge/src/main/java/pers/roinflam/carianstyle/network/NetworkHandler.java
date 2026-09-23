package pers.roinflam.carianstyle.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.Optional;

/**
 * 网络处理器
 *
 * <h3>v1.1：补充包方向声明</h3>
 * <p>
 * 此前 {@code registerMessage} 用的是五参重载，没有传 {@link NetworkDirection}。
 * 不声明方向的后果是 Forge <b>两个方向都放行</b>——本包是纯 S2C，
 * 但任何客户端都可以反过来往服务端发一个 {@link ClientSyncEffectPacket}，
 * 服务端会照单全收并把它交给 {@code ClientSyncEffectManager}（一个客户端侧的管理器）处理。
 * </p>
 * <p>
 * 声明方向后，Forge 在 {@code SimpleChannel} 的分发入口就会校验收包方向，
 * 方向不符直接抛异常并断开该连接，处理器根本不会被调用。
 * 这比在处理器里包一层 {@code DistExecutor} 更彻底：后者只是让服务端「不执行」，
 * 包本身仍然被接收和解码；前者从源头拒绝。
 * </p>
 * <p>
 * <b>不影响正常游戏</b>：服务端发、客户端收的既有链路方向完全一致，行为不变。
 * </p>
 *
 * @version 1.3
 */
public class NetworkHandler {

    /**
     * 协议版本。
     *
     * <h3>为什么从 "1" 升到 "2"</h3>
     * <p>
     * v1.2 在末尾追加了 {@link EnchantmentValuesPacket}（包 ID 1）。
     * 若版本号不变，旧客户端与新服务端<b>握手会通过</b>——两边都报 "1"——
     * 然后服务端在玩家登录时发一个包 ID 1，旧客户端没注册这个 ID，
     * 直接抛异常断线，而错误信息完全指不到「版本不一致」上。
     * </p>
     * <p>
     * 升位之后 Forge 在握手阶段就拒绝，并明确告诉玩家模组版本对不上——
     * 这是玩家自己能解决的问题，前者不是。
     * </p>
     *
     * <h3>为什么从 "2" 升到 "3"（模组 2.2.0）</h3>
     * <p>
     * 2.2.0 没有新增或调整任何包，升位是为了<b>强制客户端更新</b>。
     * 隐身的模型隐藏（{@code ClientSyncStealthRenderer}）与碰撞箱屏蔽
     * （{@code MixinEntityRenderDispatcher}）都在客户端执行；协议号不变的话，
     * 2.1.0 客户端照样能进 2.2.0 服务器——而 2.1.0 客户端在独立服上既不藏模型也不藏碰撞箱，
     * 想看穿隐身的人只要不更新模组就行。升位后旧客户端在握手阶段即被拒绝。
     * </p>
     */
    private static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Reference.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /**
     * 注册所有数据包
     * <p>
     * 注册顺序即包 ID，双端必须一致，新增包务必追加在末尾。
     * </p>
     */
    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                ClientSyncEffectPacket.class,
                ClientSyncEffectPacket::encode,
                ClientSyncEffectPacket::decode,
                ClientSyncEffectPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        // v1.2：共享数值下发包。追加在末尾——包 ID 由注册顺序隐式决定，
        // 插在中间会让新旧端之间所有包 ID 错位
        CHANNEL.registerMessage(
                packetId++,
                EnchantmentValuesPacket.class,
                EnchantmentValuesPacket::encode,
                EnchantmentValuesPacket::decode,
                EnchantmentValuesPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
