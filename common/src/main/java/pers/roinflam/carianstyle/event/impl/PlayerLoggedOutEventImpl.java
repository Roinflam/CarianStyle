package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.PlayerEvent;

/** {@link PlayerEvent.PlayerLoggedOutEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class PlayerLoggedOutEventImpl extends PlayerEvent.PlayerLoggedOutEvent {

    private final Player entity;

    public PlayerLoggedOutEventImpl(Player entity) {
        this.entity = entity;
    }

    @Override
    public Player getEntity() {
        return entity;
    }
}
