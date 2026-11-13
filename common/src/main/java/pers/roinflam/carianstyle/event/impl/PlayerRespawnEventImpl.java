package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.PlayerEvent;

/** {@link PlayerEvent.PlayerRespawnEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class PlayerRespawnEventImpl extends PlayerEvent.PlayerRespawnEvent {

    private final Player entity;
    private final boolean endConquered;

    public PlayerRespawnEventImpl(Player entity, boolean endConquered) {
        this.entity = entity;
        this.endConquered = endConquered;
    }

    @Override
    public Player getEntity() {
        return entity;
    }

    @Override
    public boolean isEndConquered() {
        return endConquered;
    }
}
