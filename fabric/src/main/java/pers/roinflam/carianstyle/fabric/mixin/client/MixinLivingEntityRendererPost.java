package pers.roinflam.carianstyle.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.fabric.FabricClientEvents;

/**
 * 生物渲染后事件（第三人称自定义火焰用）。Fabric API 没有对应回调，自己注入。
 * <p>
 * NeoForge 在 {@code LivingEntityRenderer#render} 的最后一句发这个事件，原版方法只有一个 return，TAIL 就是同一位置。
 * 玩家经 {@code PlayerRenderer} 调父类也会到这里；玩家渲染前被取消时父类不会被调，这里也就不发，和 NeoForge 一样。
 * 方法按泛型擦除后的描述符写，避开参数是 {@code Entity} 的桥方法。
 * </p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRendererPost {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"))
    private void carianstyle$renderPost(LivingEntity entity, float entityYaw, float partialTick,
                                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                        CallbackInfo ci) {
        FabricClientEvents.renderLivingPost(entity, partialTick, poseStack, bufferSource);
    }
}
