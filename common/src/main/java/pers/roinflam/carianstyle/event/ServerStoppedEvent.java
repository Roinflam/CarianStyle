package pers.roinflam.carianstyle.event;

import net.minecraft.server.MinecraftServer;
import pers.roinflam.carianstyle.bus.Event;

/** 服务器已经关闭。 */
public abstract class ServerStoppedEvent extends Event {

    public abstract MinecraftServer getServer();
}
