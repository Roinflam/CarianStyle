package pers.roinflam.carianstyle.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.fabric.FabricClientEvents;

/**
 * 世界渲染的「半透明方块之后」阶段。
 * <p>
 * 主发出点在 {@code renderSectionLayer} 里唯一一次 {@code RenderType#clearRenderState} 之前，层是半透明时才发：
 * 和 NeoForge 发这个阶段的位置是同一句，此时半透明层的渲染状态还生效、模型视图栈里带着相机旋转，
 * 特效批量绘制按单位矩阵画就是对的，画面和 NeoForge 逐像素一致。
 * 这个方法可能被大改渲染的模组整段替换，所以这里 {@code require = 0}，找不到注入点不报错。
 * Sodium 用 {@code @Overwrite} 整段替换了它（默认优先级 1000），而 Mixin 只许优先级更高的 mixin 往被别人合并过的方法里注入，
 * 否则不管 {@code require} 直接报错、客户端启动即崩，所以本类优先级要高于 1000。
 * </p>
 * <p>
 * 回退点包住 {@code renderLevel} 里全部对 {@code renderSectionLayer} 的调用（原版 7 处，半透明的两处分别在
 * 极佳画质和其它画质两支里），半透明那次返回后主发出点还没发，就补发一次；这时还在模型视图栈压入相机旋转的范围内。
 * 回退点同样 {@code require = 0}：别的渲染模组改掉了这些调用时游戏照常启动，只是特效可能不显示；
 * 两处都没发出时由 {@code FabricClientEvents} 在世界渲染结束时打一次警告。{@code expect = 7} 只在开了
 * {@code mixin.debug.countInjections} 时检查，开发时用来发现少挂了几处。
 * </p>
 */
@Mixin(value = LevelRenderer.class, priority = 1500)
public abstract class MixinLevelRendererStages {

    @Inject(method = "renderSectionLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V"),
            require = 0)
    private void carianstyle$beforeClearRenderState(RenderType renderType, double camX, double camY, double camZ,
                                                    Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                                    CallbackInfo ci) {
        if (renderType == RenderType.translucent()) {
            FabricClientEvents.onTranslucentLayerDrawn();
        }
    }

    @WrapOperation(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            require = 0, expect = 7)
    private void carianstyle$wrapSectionLayer(LevelRenderer self, RenderType renderType, double camX, double camY,
                                              double camZ, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                              Operation<Void> original) {
        if (renderType != RenderType.translucent()) {
            original.call(self, renderType, camX, camY, camZ, frustumMatrix, projectionMatrix);
            return;
        }
        FabricClientEvents.beginTranslucentLayer();
        original.call(self, renderType, camX, camY, camZ, frustumMatrix, projectionMatrix);
        FabricClientEvents.endTranslucentLayer();
    }
}
