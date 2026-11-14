package pers.roinflam.carianstyle.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.fabric.FabricClientEvents;

/**
 * 世界渲染的「半透明方块之后」阶段，和渲染阶段事件要用的 partialTick。
 * <p>
 * 主发出点是 {@code renderChunkLayer} 里唯一一次 {@code RenderType#clearRenderState} 之前，和 Forge 发这个阶段是同一句；
 * 回退点包住 {@code renderLevel} 里全部 7 处 {@code renderChunkLayer} 调用，半透明那次返回后主点没发就补发。
 * 两处都 {@code require = 0}：渲染模组改掉这些位置时照常启动，只是范围特效不显示，由世界渲染结束时的警告看出来。
 * </p>
 * <p>
 * 优先级必须高于 1000：Sodium 用默认优先级 {@code @Overwrite} 了 {@code renderChunkLayer}，Mixin 不许更低或同级的
 * mixin 往被别人合并过的方法里注入，那时不看 {@code require} 直接报错，客户端启动即崩。
 * </p>
 */
@Mixin(value = LevelRenderer.class, priority = 1500)
public abstract class MixinLevelRendererStages {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void carianstyle$recordPartialTick(PoseStack poseStack, float partialTick, long finishNanoTime,
                                               boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                               LightTexture lightTexture, Matrix4f projectionMatrix,
                                               CallbackInfo ci) {
        FabricClientEvents.onRenderLevelStart(partialTick);
    }

    @Inject(method = "renderChunkLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V"),
            require = 0)
    private void carianstyle$beforeClearRenderState(RenderType renderType, PoseStack poseStack, double camX,
                                                    double camY, double camZ, Matrix4f projectionMatrix,
                                                    CallbackInfo ci) {
        if (renderType == RenderType.translucent()) {
            FabricClientEvents.onTranslucentLayerDrawn(poseStack);
        }
    }

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V"),
            require = 0, expect = 7)
    private void carianstyle$wrapChunkLayer(LevelRenderer self, RenderType renderType, PoseStack poseStack,
                                            double camX, double camY, double camZ, Matrix4f projectionMatrix,
                                            Operation<Void> original) {
        if (renderType != RenderType.translucent()) {
            original.call(self, renderType, poseStack, camX, camY, camZ, projectionMatrix);
            return;
        }
        FabricClientEvents.beginTranslucentLayer();
        original.call(self, renderType, poseStack, camX, camY, camZ, projectionMatrix);
        FabricClientEvents.endTranslucentLayer(poseStack);
    }
}
