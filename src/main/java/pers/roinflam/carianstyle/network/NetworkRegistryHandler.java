package pers.roinflam.carianstyle.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络注册处理器
 * <p>
 * 负责处理客户端与服务端之间的网络通信
 * </p>
 */
public class NetworkRegistryHandler {

    /**
     * 注册所有网络通道
     */
    public static void register() {
        RenderingEffect.CHANNEL.register(RenderingEffect.class);
    }

    /**
     * 渲染效果网络通道
     * <p>
     * 用于同步需要特殊渲染的实体ID列表
     * </p>
     */
    public static class RenderingEffect {

        private static final String NAME = Reference.NAME + "_RENDER";
        private static final FMLEventChannel CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(NAME);

        /** 序列号 -> 实体ID列表的映射 */
        private static final Map<Integer, List<Integer>> ENTITIES_ID = new HashMap<>();

        static {
            // 每10分钟清理一次缓存，防止内存泄漏
            new SynchronizationTask(10 * 1200, 10 * 1200) {
                @Override
                public void run() {
                    ENTITIES_ID.clear();
                }
            }.start();
        }

        /**
         * 客户端接收数据包
         */
        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public static void onClientCustomPacket(@Nonnull FMLNetworkEvent.ClientCustomPacketEvent evt) {
            ByteBuf byteBuf = evt.getPacket().payload();
            Minecraft minecraft = Minecraft.getMinecraft();

            minecraft.addScheduledTask(() -> {
                int serialNumber = byteBuf.readInt();
                int length = byteBuf.readInt();

                List<Integer> entities = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    entities.add(byteBuf.readInt());
                }

                ENTITIES_ID.put(serialNumber, entities);
            });
        }

        /**
         * 向客户端发送数据包
         *
         * @param serialNumber 序列号（用于区分不同类型的渲染效果）
         * @param entityPlayer 目标玩家
         * @param id           实体ID
         * @param add          true=添加，false=移除
         */
        public static void sendClientCustomPacket(int serialNumber, EntityPlayer entityPlayer, Integer id, boolean add) {
            List<Integer> entityIds = ENTITIES_ID.getOrDefault(serialNumber, new ArrayList<>());

            if (add) {
                if (!entityIds.contains(id)) {
                    entityIds.add(id);
                }
            } else {
                entityIds.remove(id);
            }

            PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer());
            packetBuffer.writeInt(serialNumber);
            packetBuffer.writeInt(entityIds.size());
            for (Integer entityId : entityIds) {
                packetBuffer.writeInt(entityId);
            }

            CHANNEL.sendTo(new FMLProxyPacket(packetBuffer, NAME), (EntityPlayerMP) entityPlayer);
        }

        /**
         * 获取指定序列号的实体ID列表
         *
         * @param serialNumber 序列号
         * @return 实体ID列表
         */
        public static List<Integer> getEntitiesID(int serialNumber) {
            return ENTITIES_ID.getOrDefault(serialNumber, new ArrayList<>());
        }
    }
}