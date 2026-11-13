package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.impl.ProjectileImpactEventImpl;

/**
 * 弹射物命中：只发箭（本模组只认箭；以后要认别的弹射物，照 NeoForge 那 7 处补）。
 * <p>
 * 位置：{@code AbstractArrow.tick} 穿透循环里 {@code hitTargetOrDeflectSelf} 调用前（偏移 472），即命中结算之前。
 * 原版在这里只判了 null（偏移 460～466），飞行中没碰到东西时也会拿着 MISS 结果每 tick 调到，所以 MISS 不发，
 * 同 NeoForge。命中结果这时只在操作数栈上，借 {@code @ModifyArg} 拿到、原样返回。
 * 本模组的监听不取消这个事件，所以不写取消分支。
 * </p>
 */
@Mixin(AbstractArrow.class)
public abstract class FabricAbstractArrowMixin {

    @ModifyArg(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"))
    private HitResult carianstyle$projectileImpact(HitResult hit) {
        if (hit.getType() != HitResult.Type.MISS) {
            CarianBus.post(new ProjectileImpactEventImpl((AbstractArrow) (Object) this, hit));
        }
        return hit;
    }
}
