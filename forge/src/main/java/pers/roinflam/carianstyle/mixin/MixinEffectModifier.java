package pers.roinflam.carianstyle.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pers.roinflam.carianstyle.handler.EffectModifierHandler;

/**
 * 药水效果修改 Mixin
 * <p>
 * 拦截实体添加药水效果的过程，调用处理器应用所有附魔的修改
 * 支持任何实现了 {@link pers.roinflam.carianstyle.api.IEffectModifier} 接口的附魔
 * </p>
 *
 * @author RoinFlam
 */
@Mixin(LivingEntity.class)
public class MixinEffectModifier {

    /**
     * 拦截 addEffect 方法的参数，应用附魔修改
     *
     * @param effectInstance 原始药水效果实例
     * @return 处理后的药水效果实例
     */
    @ModifyVariable(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            argsOnly = true
    )
    private MobEffectInstance carianstyle$modifyEffect(MobEffectInstance effectInstance) {
        return EffectModifierHandler.handleEffectModification(
                (LivingEntity) (Object) this,
                effectInstance
        );
    }
}