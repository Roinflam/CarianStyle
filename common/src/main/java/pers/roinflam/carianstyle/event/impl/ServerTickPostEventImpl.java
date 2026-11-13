package pers.roinflam.carianstyle.event.impl;

import net.minecraft.server.MinecraftServer;
import pers.roinflam.carianstyle.event.ServerTickEvent;

/** {@link ServerTickEvent.Post} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class ServerTickPostEventImpl extends ServerTickEvent.Post {

    private final MinecraftServer server;

    public ServerTickPostEventImpl(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public MinecraftServer getServer() {
        return server;
    }
}
