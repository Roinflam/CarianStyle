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

public class NetworkRegistryHandler {

    public static void register() {
        RenderingEffect.CHANNEL.register(RenderingEffect.class);
    }

    public static class RenderingEffect {
        private static final String NAME = Reference.NAME + "_RENDER";
        private static final FMLEventChannel CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(NAME);

        private static final HashMap<Integer, List<Integer>> ENTITIES_ID = new HashMap<>();

        static {
            new SynchronizationTask(10 * 1200, 10 * 1200) {

                @Override
                public void run() {
                    ENTITIES_ID.clear();
                }

            }.start();
        }

        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public static void onClientCustomPacket(@Nonnull FMLNetworkEvent.ClientCustomPacketEvent evt) {
            ByteBuf byteBuf = evt.getPacket().payload();
            @Nonnull Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                EntityPlayer player = minecraft.player;
                int serialNumber = byteBuf.readInt();
                int length = byteBuf.readInt();
                @Nonnull List<Integer> entities = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    entities.add(byteBuf.readInt());
                }
                ENTITIES_ID.put(serialNumber, entities);
            });
        }

        public static void sendClientCustomPacket(int serialNumber, EntityPlayer entityPlayer, Integer id, boolean add) {
            List<Integer> entites_id = ENTITIES_ID.getOrDefault(serialNumber, new ArrayList<>());
            if (add) {
                if (!entites_id.contains(id)) {
                    entites_id.add(id);
                }
            } else {
                entites_id.remove(id);
            }

            @Nonnull PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer());
            packetBuffer.writeInt(serialNumber);
            packetBuffer.writeInt(entites_id.size());
            for (Integer integer : entites_id) {
                packetBuffer.writeInt(integer);
            }

            CHANNEL.sendTo(new FMLProxyPacket(packetBuffer, NAME), (EntityPlayerMP) entityPlayer);
        }

        public static List<Integer> getEntitiesID(int serialNumber) {
            return ENTITIES_ID.getOrDefault(serialNumber, new ArrayList<>());
        }
    }

}
