package pers.roinflam.carianstyle.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.carianstyle.fabric.FabricClientEvents;

/**
 * 第一人称手部渲染事件（第一人称火焰用）。Fabric API 没有对应回调，自己注入。
 * <p>
 * NeoForge 在 {@code renderHandsWithItems} 里主手、副手各自调 {@code renderArmWithItem} 之前各发一次，取消就跳过那只手。
 * 这里包住那两处调用，每只手要画时发一次，取消返回 false、那次调用不执行。
 * 两次视角晃动的 {@code mulPose} 都在这两处调用之前，拿到的姿态栈状态和 NeoForge 事件里的一样；
 * 插值、姿态栈、缓冲都直接取被包调用的参数，和 NeoForge 传进事件的是同一批值。
 * </p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRendererHands {

    @WrapWithCondition(method = "renderHandsWithItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            expect = 2)
    private boolean carianstyle$renderHand(ItemInHandRenderer self, AbstractClientPlayer player, float partialTick,
                                           float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
                                           float equippedProgress, PoseStack poseStack,
                                           MultiBufferSource bufferSource, int packedLight) {
        return FabricClientEvents.renderHand(partialTick, poseStack, bufferSource);
    }
}
