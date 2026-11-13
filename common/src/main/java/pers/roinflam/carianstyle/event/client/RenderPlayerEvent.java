package pers.roinflam.carianstyle.event.client;

import pers.roinflam.carianstyle.bus.ICancellableEvent;
import pers.roinflam.carianstyle.event.PlayerEvent;

/** 玩家模型渲染。 */
public abstract class RenderPlayerEvent extends PlayerEvent {

    /** 玩家渲染器设好模型状态之后、真正画之前；取消即这一帧不画这个玩家。 */
    public abstract static class Pre extends RenderPlayerEvent implements ICancellableEvent {
    }
}
