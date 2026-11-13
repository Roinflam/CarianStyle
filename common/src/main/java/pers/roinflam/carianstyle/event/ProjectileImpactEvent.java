package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

/**
 * 弹射物命中、结算之前。{@link #getEntity()} 就是弹射物本身。
 * <p>
 * NeoForge 对 7 种弹射物都发；Fabric 侧只发箭（本模组只认箭），以后要认别的弹射物再补发出点。没打中（MISS）不发。
 * </p>
 */
public abstract class ProjectileImpactEvent extends EntityEvent {

    public abstract Projectile getProjectile();

    public abstract HitResult getRayTraceResult();
}
