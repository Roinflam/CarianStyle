package pers.roinflam.carianstyle.event;

import net.minecraft.server.MinecraftServer;
import pers.roinflam.carianstyle.bus.Event;

/** 服务器启动完成。 */
public abstract class ServerStartedEvent extends Event {

    public abstract MinecraftServer getServer();
}
