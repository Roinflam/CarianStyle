package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.event.CriticalHitEvent;

/** {@link CriticalHitEvent} 的字段型实现。倍率初值由发出点照平台给（原版暴击 1.5，否则 1.0），不要一律写 1.5。 */
public final class CriticalHitEventImpl extends CriticalHitEvent {

    private final Player entity;
    private final Entity target;
    private float damageMultiplier;
    private final boolean vanillaCritical;

    public CriticalHitEventImpl(Player entity, Entity target, float damageMultiplier, boolean vanillaCritical) {
        this.entity = entity;
        this.target = target;
        this.damageMultiplier = damageMultiplier;
        this.vanillaCritical = vanillaCritical;
    }

    @Override
    public Player getEntity() {
        return entity;
    }

    @Override
    public Entity getTarget() {
        return target;
    }

    @Override
    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    @Override
    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    @Override
    public boolean isVanillaCritical() {
        return vanillaCritical;
    }

    /**
     * 这一击最终算不算暴击。本模组的监听不改「是否暴击」，所以就等于原版判定；
     * Fabric 侧的发出点用它的返回值写回原版的暴击布尔。
     */
    public boolean isCriticalHit() {
        return vanillaCritical;
    }
}
