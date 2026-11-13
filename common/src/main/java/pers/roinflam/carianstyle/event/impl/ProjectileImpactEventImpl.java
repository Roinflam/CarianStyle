package pers.roinflam.carianstyle.event.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import pers.roinflam.carianstyle.event.ProjectileImpactEvent;

/** {@link ProjectileImpactEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class ProjectileImpactEventImpl extends ProjectileImpactEvent {

    private final Projectile projectile;
    private final HitResult rayTraceResult;

    public ProjectileImpactEventImpl(Projectile projectile, HitResult rayTraceResult) {
        this.projectile = projectile;
        this.rayTraceResult = rayTraceResult;
    }

    @Override
    public Projectile getProjectile() {
        return projectile;
    }

    @Override
    public HitResult getRayTraceResult() {
        return rayTraceResult;
    }

    @Override
    public Entity getEntity() {
        return projectile;
    }
}
