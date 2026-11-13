package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.PlayerTickEvent;

/** {@link PlayerTickEvent.Pre} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class PlayerTickPreEventImpl extends PlayerTickEvent.Pre {

    private final Player entity;

    public PlayerTickPreEventImpl(Player entity) {
        this.entity = entity;
    }

    @Override
    public Player getEntity() {
        return entity;
    }
}
