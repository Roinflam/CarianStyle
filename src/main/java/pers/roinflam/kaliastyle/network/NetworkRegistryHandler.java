package pers.roinflam.kaliastyle.network;

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
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;

import java.util.ArrayList;
import java.util.List;

public class NetworkRegistryHandler {

    public static void register() {
        DoomeDeathBurning.CHANNEL.register(DoomeDeathBurning.class);
    }

    public static class DoomeDeathBurning {
        private static final String NAME = "DOOMED_DEATH_BURNING";
        private static final FMLEventChannel CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(NAME);

        private static final List<Integer> ENTITIES_ID = new ArrayList<>();

        static {
            new SynchronizationTask(10 * 12000, 10 * 1200) {

                @Override
                public void run() {
                    ENTITIES_ID.clear();
                }

            }.start();
        }

        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public static void onClientCustomPacket(FMLNetworkEvent.ClientCustomPacketEvent evt) {
            ByteBuf byteBuf = evt.getPacket().payload();
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                EntityPlayer player = minecraft.player;
                int length = byteBuf.readInt();
                ENTITIES_ID.clear();
                for (int i = 0; i < length; i++) {
                    ENTITIES_ID.add(byteBuf.readInt());
                }
            });
        }

        public static void sendClientCustomPacket(EntityPlayer entityPlayer, Integer id, boolean add) {
            if (add) {
                if (!ENTITIES_ID.contains(id)) {
                    ENTITIES_ID.add(id);
                }
            } else {
                ENTITIES_ID.remove(id);
            }

            PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer());
            packetBuffer.writeInt(ENTITIES_ID.size());
            for (Integer integer : ENTITIES_ID) {
                packetBuffer.writeInt(integer);
            }

            CHANNEL.sendTo(new FMLProxyPacket(packetBuffer, NAME), (EntityPlayerMP) entityPlayer);
        }

        public static List<Integer> getEntitiesID() {
            return ENTITIES_ID;
        }
    }

    public static class DestructionFireBurning {
        private static final String NAME = "DESTRUCTION_FIRE_BURNING";
        private static final FMLEventChannel CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(NAME);

        private static final List<Integer> ENTITIES_ID = new ArrayList<>();

        static {
            new SynchronizationTask(10 * 12000, 10 * 1200) {

                @Override
                public void run() {
                    ENTITIES_ID.clear();
                }

            }.start();
        }

        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public static void onClientCustomPacket(FMLNetworkEvent.ClientCustomPacketEvent evt) {
            ByteBuf byteBuf = evt.getPacket().payload();
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                EntityPlayer player = minecraft.player;
                int length = byteBuf.readInt();
                ENTITIES_ID.clear();
                for (int i = 0; i < length; i++) {
                    ENTITIES_ID.add(byteBuf.readInt());
                }
            });
        }

        public static void sendClientCustomPacket(EntityPlayer entityPlayer, Integer id, boolean add) {
            if (add) {
                if (!ENTITIES_ID.contains(id)) {
                    ENTITIES_ID.add(id);
                }
            } else {
                ENTITIES_ID.remove(id);
            }

            PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer());
            packetBuffer.writeInt(ENTITIES_ID.size());
            for (Integer integer : ENTITIES_ID) {
                packetBuffer.writeInt(integer);
            }

            CHANNEL.sendTo(new FMLProxyPacket(packetBuffer, NAME), (EntityPlayerMP) entityPlayer);
        }

        public static List<Integer> getEntitiesID() {
            return ENTITIES_ID;
        }
    }

    public static class EpilepsyFireBurning {
        private static final String NAME = "EPILEPSY_FIRE_BURNING";
        private static final FMLEventChannel CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(NAME);

        private static final List<Integer> ENTITIES_ID = new ArrayList<>();

        static {
            new SynchronizationTask(10 * 12000, 10 * 1200) {

                @Override
                public void run() {
                    ENTITIES_ID.clear();
                }

            }.start();
        }

        @SubscribeEvent
        @SideOnly(Side.CLIENT)
        public static void onClientCustomPacket(FMLNetworkEvent.ClientCustomPacketEvent evt) {
            ByteBuf byteBuf = evt.getPacket().payload();
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                EntityPlayer player = minecraft.player;
                int length = byteBuf.readInt();
                ENTITIES_ID.clear();
                for (int i = 0; i < length; i++) {
                    ENTITIES_ID.add(byteBuf.readInt());
                }
            });
        }

        public static void sendClientCustomPacket(EntityPlayer entityPlayer, Integer id, boolean add) {
            if (add) {
                if (!ENTITIES_ID.contains(id)) {
                    ENTITIES_ID.add(id);
                }
            } else {
                ENTITIES_ID.remove(id);
            }

            PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer());
            packetBuffer.writeInt(ENTITIES_ID.size());
            for (Integer integer : ENTITIES_ID) {
                packetBuffer.writeInt(integer);
            }

            CHANNEL.sendTo(new FMLProxyPacket(packetBuffer, NAME), (EntityPlayerMP) entityPlayer);
        }

        public static List<Integer> getEntitiesID() {
            return ENTITIES_ID;
        }
    }
}
