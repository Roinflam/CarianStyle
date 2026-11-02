package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端同步效果数据包
 * <p>
 * 支持两种模式：
 * - 增量模式（ADD/REMOVE）：只发送单个实体的变化，减少网络开销
 * - 全量模式（FULL_SYNC）：发送完整实体列表，用于登录/切维度等场景
 * </p>
 * <p>
 * 修复记录：
 * - 原实现每次add/remove都广播完整列表，50个着火实体中1个熄灭=给每个玩家发49个ID
 * - 优化为增量式，add/remove只发送1个ID
 * </p>
 *
 * <h3>v2.2：解码期长度校验</h3>
 * <p>
 * 原 {@link #decode} 的写法是：
 * </p>
 * <pre>
 * int size = buf.readInt();
 * List&lt;Integer&gt; entityIds = new ArrayList&lt;&gt;(size);
 * </pre>
 * <p>
 * {@code size} 完全来自字节流。若它是 {@code 2^31-1}，
 * {@code new ArrayList&lt;&gt;(size)} 会当场尝试分配一个 20 亿元素的数组，
 * 客户端立刻 {@code OutOfMemoryError} 退出。
 * </p>
 * <p>
 * <b>这不需要有人恶意攻击。</b>只要双端的模组版本不一致导致包 ID 错位，
 * 某个无关包的中间四个字节就会被当成长度读出来——那大概率是个巨大的随机数。
 * 这类崩溃的现场特征是「一进服就 OOM」，而堆栈完全指不到真正的原因。
 * </p>
 * <p>
 * 现在改为经 {@link PacketGuard#readBoundedSize} 读取，
 * 既有硬上限 {@link #MAX_ENTITY_IDS}，也会拿缓冲区剩余字节数做交叉验证。
 * 越界时抛异常，Forge 会断开该连接——问题定位在断线日志里，
 * 而不是变成一个莫名其妙的 OOM。
 * </p>
 * <p>
 * 上限取 {@value #MAX_ENTITY_IDS}：全量同步发的是「某个序列号下所有带该效果的实体」，
 * 正常情况是个位数到几十，一个区块加载范围内也不可能有四千个同时着火的实体。
 * 这个值留了两个数量级的余量，正常游戏绝不会触及。
 * </p>
 *
 * @version 2.2
 */
public class ClientSyncEffectPacket {

    /**
     * 全量同步允许携带的最大实体数。
     * <p>见类注释：正常量级是个位数到几十，此处留足余量。</p>
     */
    private static final int MAX_ENTITY_IDS = 4096;

    /** 每个实体 id 在缓冲里占用的字节数（{@code writeInt}） */
    private static final int BYTES_PER_ID = 4;

    /** 操作类型 */
    public enum Action {
        /** 添加单个实体 */
        ADD(0),
        /** 移除单个实体 */
        REMOVE(1),
        /** 全量同步（登录/切维度） */
        FULL_SYNC(2);

        final int id;

        Action(int id) {
            this.id = id;
        }

        static Action fromId(int id) {
            for (Action a : values()) if (a.id == id) return a;
            return FULL_SYNC;
        }
    }

    private final int serialNumber;
    private final Action action;
    /** ADD/REMOVE时只有1个元素，FULL_SYNC时为完整列表 */
    private final List<Integer> entityIds;

    public ClientSyncEffectPacket(int serialNumber, Action action, List<Integer> entityIds) {
        this.serialNumber = serialNumber;
        this.action = action;
        this.entityIds = new ArrayList<>(entityIds);
    }

    /** 便捷构造：增量式（单个实体） */
    public static ClientSyncEffectPacket delta(int serialNumber, Action action, int entityId) {
        return new ClientSyncEffectPacket(serialNumber, action, List.of(entityId));
    }

    /** 便捷构造：全量同步 */
    public static ClientSyncEffectPacket fullSync(int serialNumber, List<Integer> entityIds) {
        return new ClientSyncEffectPacket(serialNumber, Action.FULL_SYNC, entityIds);
    }

    /** 编码数据包 */
    public static void encode(ClientSyncEffectPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.serialNumber);
        buf.writeByte(packet.action.id);
        buf.writeInt(packet.entityIds.size());
        for (Integer id : packet.entityIds) {
            buf.writeInt(id);
        }
    }

    /**
     * 解码数据包
     * <p>
     * v2.2：长度经 {@link PacketGuard} 双重校验后才用于分配容器，详见类注释。
     * 线格式（wire format）没有任何变化，新旧端可互通。
     * </p>
     *
     * @param buf 源缓冲
     * @return 解码出的包
     */
    public static ClientSyncEffectPacket decode(FriendlyByteBuf buf) {
        int serialNumber = buf.readInt();
        Action action = Action.fromId(buf.readByte());
        int size = PacketGuard.readBoundedSize(buf, MAX_ENTITY_IDS, BYTES_PER_ID,
                "ClientSyncEffectPacket");
        List<Integer> entityIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entityIds.add(buf.readInt());
        }
        return new ClientSyncEffectPacket(serialNumber, action, entityIds);
    }

    /**
     * 处理数据包（客户端）
     * <p>
     * 本包已在 {@code NetworkHandler} 注册时声明为
     * {@code NetworkDirection.PLAY_TO_CLIENT}，服务端收到反向包会被 Forge
     * 在分发入口直接拒绝，因此这里无需再做端侧判断。
     * </p>
     *
     * @param packet 收到的包
     * @param ctx    网络上下文
     */
    public static void handle(ClientSyncEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientSyncEffectManager.handlePacket(packet.serialNumber, packet.action, packet.entityIds);
        });
        ctx.get().setPacketHandled(true);
    }
}
