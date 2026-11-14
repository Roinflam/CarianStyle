package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.impl.LivingChangeTargetEventImpl;

/**
 * 换目标（目标选择器那条路）：{@code Mob.setTarget} 第一行，取消 = 保持原目标，同 Forge 把整个方法包住的做法。
 * 用大脑记忆选目标的那条路在 {@code FabricStartAttackingMixin}。
 */
@Mixin(Mob.class)
public abstract class FabricMobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void carianstyle$changeTarget(LivingEntity target, CallbackInfo ci) {
        LivingChangeTargetEvent event = CarianBus.post(new LivingChangeTargetEventImpl((Mob) (Object) this, target));
        if (event.isCanceled()) {
            ci.cancel();
        }
    }
}
