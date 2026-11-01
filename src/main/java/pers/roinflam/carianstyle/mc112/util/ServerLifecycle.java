package pers.roinflam.carianstyle.mc112.util;

import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;

import javax.annotation.Nonnull;

/**
 * 停服清理的转发点
 * <p>
 * 1.12.2 的 {@code FMLServerStoppedEvent} 只发给模组主类的 {@code @Mod.EventHandler}，普通订阅类收不到。
 * 主类收到后调这里，这里依次调各个类的清理入口（方法名照 1.20.1 里实际订阅 {@code ServerStoppedEvent} 的那些）。
 * </p>
 * <p>
 * 内置服（单人世界）退出后客户端进程还在，服务端那一侧的静态表不清的话，下一次进别的存档会带着上一个存档的数据。
 * </p>
 */
public final class ServerLifecycle {

    private ServerLifecycle() {
    }

    /**
     * 服务器停止之后
     *
     * @param event FML 停服事件
     */
    public static void onServerStopped(@Nonnull FMLServerStoppedEvent event) {
        ClientSyncEffectManager.clearServerCache();
    }
}
