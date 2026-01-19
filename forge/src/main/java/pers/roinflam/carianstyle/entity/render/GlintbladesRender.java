package pers.roinflam.carianstyle.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

import javax.annotation.Nonnull;

/**
 * 魔法剑实体渲染器
 * <p>
 * 渲染悬浮旋转和飞行中的魔法剑
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public class GlintbladesRender extends EntityRenderer<EntityGlintblades> {

    protected final Item item;
    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public GlintbladesRender(
            @Nonnull EntityRendererProvider.Context context,
            @Nonnull Item item) {
        super(context);
        this.item = item;
        // 1.20.1: 从 context 获取 ItemRenderer
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            @Nonnull EntityGlintblades entity,
            float entityYaw,
            float partialTicks,
            @Nonnull PoseStack poseStack,
            @Nonnull MultiBufferSource buffer,
            int packedLight) {

        poseStack.pushPose();

        // 应用实体大小
        float size = entity.getSize();
        poseStack.scale(size, size, size);
        poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw));

        if (entity.isShooted()) {
            // 飞行状态：根据俯仰角旋转
            // 1.20.1: rotationPitch → getXRot()
            float pitch = Math.max(Math.min(80 - entity.getXRot() * 1.25f, 140), 0);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        } else {
            // 悬浮状态：持续旋转
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.tickCount * 20));
        }

        // 渲染物品
        // 1.20.1: renderItem 参数变化
        this.itemRenderer.renderStatic(
                new ItemStack(item),
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    @Nonnull
    public ResourceLocation getTextureLocation(@Nonnull EntityGlintblades entity) {
        // 1.20.1: 返回一个占位符纹理，实际渲染使用物品纹理
        return new ResourceLocation("minecraft", "textures/block/stone.png");
    }
}