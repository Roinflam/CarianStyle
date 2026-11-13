package pers.roinflam.carianstyle.event.client;

import pers.roinflam.carianstyle.bus.Event;

/** 单测夹具：放在客户端事件包下、不碰任何客户端类的事件，用来测双端订阅类的参数检查。 */
public final class FakeClientEvent extends Event {
}
