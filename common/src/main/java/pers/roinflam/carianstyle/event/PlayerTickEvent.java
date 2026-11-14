package pers.roinflam.carianstyle.event;

/** 玩家 tick。只用到 tick 开头这一发。 */
public abstract class PlayerTickEvent extends PlayerEvent {

    /** {@code Player#tick} 第一行，双端都发。 */
    public abstract static class Pre extends PlayerTickEvent {
    }
}
