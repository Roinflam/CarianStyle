package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.impl.ProjectileImpactEventImpl;

/**
 * 弹射物命中：只发箭（本模组只认箭）。位置是 {@code AbstractArrow.tick} 穿透循环里 {@code onHit} 之前（偏移 472），
 * 即命中结算之前。原版这里只判了 null，飞行中没碰到东西时也拿着 MISS 每 tick 调到，所以 MISS 不发，同 Forge。
 * 命中结果只在操作数栈上，借 {@code @ModifyArg} 拿到、原样返回。
 */
@Mixin(AbstractArrow.class)
public abstract class FabricAbstractArrowMixin {

    @ModifyArg(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;onHit(Lnet/minecraft/world/phys/HitResult;)V"))
    private HitResult carianstyle$projectileImpact(HitResult hit) {
        if (hit.getType() != HitResult.Type.MISS) {
            CarianBus.post(new ProjectileImpactEventImpl((AbstractArrow) (Object) this, hit));
        }
        return hit;
    }
}
