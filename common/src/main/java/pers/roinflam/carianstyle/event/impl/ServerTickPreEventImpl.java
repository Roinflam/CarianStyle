package pers.roinflam.carianstyle.event.impl;

import net.minecraft.server.MinecraftServer;
import pers.roinflam.carianstyle.event.ServerTickEvent;

/** {@link ServerTickEvent.Pre} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class ServerTickPreEventImpl extends ServerTickEvent.Pre {

    private final MinecraftServer server;

    public ServerTickPreEventImpl(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public MinecraftServer getServer() {
        return server;
    }
}
