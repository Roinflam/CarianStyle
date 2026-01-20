package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端同步效果数据包
 * <p>
 * 服务端发送给客户端，告知哪些实体应该渲染特定的客户端效果（火焰、隐身等）
 * </p>
 */
public class ClientSyncEffectPacket {

    /**
     * 序列号：用于区分不同类型的客户端效果
     */
    private final int serialNumber;

    /**
     * 实体ID列表：这些实体应该渲染该效果
     */
    private final List<Integer> entityIds;

    public ClientSyncEffectPacket(int serialNumber, List<Integer> entityIds) {
        this.serialNumber = serialNumber;
        this.entityIds = new ArrayList<>(entityIds);
    }

    /**
     * 编码数据包
     */
    public static void encode(ClientSyncEffectPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.serialNumber);
        buf.writeInt(packet.entityIds.size());
        for (Integer id : packet.entityIds) {
            buf.writeInt(id);
        }
    }

    /**
     * 解码数据包
     */
    public static ClientSyncEffectPacket decode(FriendlyByteBuf buf) {
        int serialNumber = buf.readInt();
        int size = buf.readInt();
        List<Integer> entityIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entityIds.add(buf.readInt());
        }

        return new ClientSyncEffectPacket(serialNumber, entityIds);
    }

    /**
     * 处理数据包（客户端）
     */
    public static void handle(ClientSyncEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 更新客户端缓存
            ClientSyncEffectManager.updateClientCache(packet.serialNumber, packet.entityIds);
        });
        ctx.get().setPacketHandled(true);
    }
}