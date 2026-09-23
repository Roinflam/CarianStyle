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
 * <p>
 * 注意：同一个效果可能两次经过这里。Mohist 系混合端（CraftBukkit）在药水 tick 期间把 addEffect 入队、
 * tick 末尾再调一次 addEffect 重放，两次都从本方法头进来。处理器在入队那次原样放行、只在重放时改写
 * （2026-09-23，见 {@code handler/BukkitTickingEffectsProbe}）。不要改成往 Mohist 的入队/重放代码里注入：
 * 那段是 CraftBukkit 加的，原版 Forge 上找不到注入点，{@code defaultRequire = 1} 下单机会崩。
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