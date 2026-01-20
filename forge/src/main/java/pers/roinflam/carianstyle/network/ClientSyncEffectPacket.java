package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSyncEffectPacket.class);

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
        LOGGER.debug("[网络包] 编码 - 序列号: {}, 实体数: {}",
                packet.serialNumber, packet.entityIds.size());

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

        LOGGER.debug("[网络包] 解码 - 序列号: {}, 实体数: {}, 实体列表: {}",
                serialNumber, size, entityIds);

        return new ClientSyncEffectPacket(serialNumber, entityIds);
    }

    /**
     * 处理数据包（客户端）
     */
    public static void handle(ClientSyncEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        LOGGER.info("[网络包] 收到数据包 - 序列号: {}, 实体列表: {}",
                packet.serialNumber, packet.entityIds);

        ctx.get().enqueueWork(() -> {
            LOGGER.info("[网络包] 开始处理数据包 - 序列号: {}", packet.serialNumber);
            // 更新客户端缓存
            ClientSyncEffectManager.updateClientCache(packet.serialNumber, packet.entityIds);
            LOGGER.info("[网络包] 数据包处理完成 - 序列号: {}", packet.serialNumber);
        });
        ctx.get().setPacketHandled(true);
    }
}