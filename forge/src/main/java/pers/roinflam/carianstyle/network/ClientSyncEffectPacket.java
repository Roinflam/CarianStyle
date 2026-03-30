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
 * @version 2.1
 */
public class ClientSyncEffectPacket {

    /** 操作类型 */
    public enum Action {
        /** 添加单个实体 */
        ADD(0),
        /** 移除单个实体 */
        REMOVE(1),
        /** 全量同步（登录/切维度） */
        FULL_SYNC(2);

        final int id;
        Action(int id) { this.id = id; }

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

    /** 解码数据包 */
    public static ClientSyncEffectPacket decode(FriendlyByteBuf buf) {
        int serialNumber = buf.readInt();
        Action action = Action.fromId(buf.readByte());
        int size = buf.readInt();
        List<Integer> entityIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entityIds.add(buf.readInt());
        }
        return new ClientSyncEffectPacket(serialNumber, action, entityIds);
    }

    /** 处理数据包（客户端） */
    public static void handle(ClientSyncEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientSyncEffectManager.handlePacket(packet.serialNumber, packet.action, packet.entityIds);
        });
        ctx.get().setPacketHandled(true);
    }
}
