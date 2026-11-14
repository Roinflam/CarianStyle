package pers.roinflam.carianstyle.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.fabric.FabricClientEvents;

/**
 * 玩家渲染前事件（隐身藏模型用）。Fabric API 没有可取消的玩家渲染前回调，自己注入。
 * <p>
 * 位置和 Forge 相同：{@code PlayerRenderer#render} 里 {@code setModelProperties} 之后、调父类渲染之前。
 * 取消就直接返回，模型、盔甲、手持物、名牌都不画，生物渲染末尾的事件也不会发；碰撞箱和影子不走这条路，另有 mixin 管。
 * 方法写全描述符，避开编译器生成的两个桥方法。
 * </p>
 */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRendererPre {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/PlayerRenderer;setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void carianstyle$renderPre(AbstractClientPlayer player, float entityYaw, float partialTick,
                                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                       CallbackInfo ci) {
        if (FabricClientEvents.renderPlayerPre(player)) {
            ci.cancel();
        }
    }
}
