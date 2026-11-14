package pers.roinflam.carianstyle.event.impl;

import net.minecraft.server.MinecraftServer;
import pers.roinflam.carianstyle.event.ServerStartedEvent;

/** {@link ServerStartedEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class ServerStartedEventImpl extends ServerStartedEvent {

    private final MinecraftServer server;

    public ServerStartedEventImpl(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public MinecraftServer getServer() {
        return server;
    }
}
