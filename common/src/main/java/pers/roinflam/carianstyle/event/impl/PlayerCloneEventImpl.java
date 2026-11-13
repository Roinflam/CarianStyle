package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.PlayerEvent;

/** {@link PlayerEvent.Clone} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class PlayerCloneEventImpl extends PlayerEvent.Clone {

    private final Player entity;
    private final Player original;
    private final boolean wasDeath;

    public PlayerCloneEventImpl(Player entity, Player original, boolean wasDeath) {
        this.entity = entity;
        this.original = original;
        this.wasDeath = wasDeath;
    }

    @Override
    public Player getEntity() {
        return entity;
    }

    @Override
    public Player getOriginal() {
        return original;
    }

    @Override
    public boolean isWasDeath() {
        return wasDeath;
    }
}
