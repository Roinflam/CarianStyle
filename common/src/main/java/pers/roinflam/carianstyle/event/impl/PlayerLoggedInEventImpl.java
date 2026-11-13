package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.PlayerEvent;

/** {@link PlayerEvent.PlayerLoggedInEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class PlayerLoggedInEventImpl extends PlayerEvent.PlayerLoggedInEvent {

    private final Player entity;

    public PlayerLoggedInEventImpl(Player entity) {
        this.entity = entity;
    }

    @Override
    public Player getEntity() {
        return entity;
    }
}
