package pers.roinflam.carianstyle.event.client;

import pers.roinflam.carianstyle.bus.Event;

/** 本地玩家的连接状态变化。 */
public abstract class ClientPlayerNetworkEvent extends Event {

    /**
     * 断开连接（退出到标题、被踢、单人关世界）。不同平台发出的线程未必是渲染线程，监听里要动客户端状态的先交给主线程。
     */
    public abstract static class LoggingOut extends ClientPlayerNetworkEvent {
    }
}
