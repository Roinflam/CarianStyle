package pers.roinflam.carianstyle.event.client;

import pers.roinflam.carianstyle.bus.Event;

/** 客户端 tick。只用到结尾这一发。 */
public abstract class ClientTickEvent extends Event {

    /** {@code Minecraft#tick} 最后。 */
    public abstract static class Post extends ClientTickEvent {
    }
}
