package pers.roinflam.carianstyle.fabric.mixin.event;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.impl.LivingChangeTargetEventImpl;

/**
 * 换目标（大脑记忆那条路）：{@code StartAttacking} 选好目标、{@code canAttack} 通过后，
 * 写 {@code ATTACK_TARGET} 记忆之前（lambda {@code method_47123} 偏移 61）发，取消 = 这次不开打（返回 false），
 * 同 NeoForge 在这里的补丁。
 * <p>
 * lambda 名：Mojmap 不给 lambda 起名，开发环境的 jar 里保留的就是中间名 {@code method_47123}，
 * 生产环境也是它（1.20.1 / 1.21.1 同名），开发和生产都能命中。
 * 这个 lambda 里 {@code LivingEntity} 类型的局部只有选中的目标一个（槽 9），{@code Mob} 是参数（槽 5）。
 * 猪灵兽 / 僵尸疣猪兽 / 监守者直接写记忆、不经这里，NeoForge 也一样不发。
 * </p>
 */
@Mixin(StartAttacking.class)
public abstract class FabricStartAttackingMixin {

    @Inject(method = "method_47123",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;set(Ljava/lang/Object;)V"),
            cancellable = true)
    private static void carianstyle$changeTarget(CallbackInfoReturnable<Boolean> cir,
                                                 @Local(argsOnly = true) Mob mob,
                                                 @Local LivingEntity target) {
        LivingChangeTargetEvent event = CarianBus.post(new LivingChangeTargetEventImpl(mob, target));
        if (event.isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
