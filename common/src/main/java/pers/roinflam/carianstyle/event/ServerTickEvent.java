package pers.roinflam.carianstyle.event;

import net.minecraft.server.MinecraftServer;
import pers.roinflam.carianstyle.bus.Event;

/** 服务器 tick 的开头和结尾。 */
public abstract class ServerTickEvent extends Event {

    public abstract MinecraftServer getServer();

    /**
     * 服务器 tick 开头。
     * <p>
     * 各平台位置略有不同：NeoForge 1.21.1 在 tick 计数自增之后、tick 速率管理器之前，Fabric 1.21.1 晚过速率管理器；
     * Forge 1.20.1 在计数自增之前、Fabric 1.20.1 在自增之后，两者读 {@code getTickCount()} 会差 1。现有监听都不读这些，没有影响。
     * </p>
     */
    public abstract static class Pre extends ServerTickEvent {
    }

    /** 服务器 tick 最后。 */
    public abstract static class Post extends ServerTickEvent {
    }
}
