package pers.roinflam.carianstyle.event.client.impl;

import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.client.RenderPlayerEvent;

/** {@link RenderPlayerEvent.Pre} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class RenderPlayerPreEventImpl extends RenderPlayerEvent.Pre {

    private final Player entity;

    public RenderPlayerPreEventImpl(Player entity) {
        this.entity = entity;
    }

    @Override
    public Player getEntity() {
        return entity;
    }
}
