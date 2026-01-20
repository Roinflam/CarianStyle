package pers.roinflam.carianstyle.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 网络处理器
 */
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Reference.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /**
     * 注册所有数据包
     */
    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                ClientSyncEffectPacket.class,
                ClientSyncEffectPacket::encode,
                ClientSyncEffectPacket::decode,
                ClientSyncEffectPacket::handle
        );
    }
}