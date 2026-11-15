package pers.roinflam.carianstyle.fabric.mixin.network;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 握手要认单人游戏房主的内存连接，1.20.1 这个字段不是 public。 */
@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginPacketListenerAccessor {

    @Accessor("connection")
    Connection carianstyle$getConnection();
}
