package pers.roinflam.carianstyle.base.potion.flame;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.FireBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

import javax.annotation.Nonnull;

/**
 * 火焰效果药水基类（1.20.1 版本）
 */
public abstract class FlameBase extends HideBase {

    protected FlameBase(@Nonnull MobEffectCategory category, int liquidColor) {
        super(category, liquidColor);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 获取火焰纹理层1名称
     */
    @Nonnull
    protected abstract String getLevelOneName();

    /**
     * 获取火焰纹理层2名称
     */
    @Nonnull
    protected abstract String getLevelTwoName();

    /**
     * 渲染第一人称火焰效果（1.20.1 版本）
     */
    @OnlyIn(Dist.CLIENT)
    public void renderFireInFirstPerson(@Nonnull String iconName, @Nonnull PoseStack poseStack,
                                        @Nonnull MultiBufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(new ResourceLocation(iconName));

        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();

        // 使用 RenderType.eyes() 以获得发光效果，或使用 RenderType.cutout() 以获得透明效果
        VertexConsumer builder = bufferSource.getBuffer(RenderType.cutout());

        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            poseStack.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (i * 2 - 1) * 10.0F));

            Matrix4f matrix4f = poseStack.last().pose();
            Matrix3f matrix3f = poseStack.last().normal();


            FireBlock
            // 使用 vertex() 构建顶点
            builder.vertex(matrix4f, -0.5F, -0.5F, -0.5F)
                    .color(255, 255, 255, 230)
                    .uv(maxU, maxV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240) // 最大亮度
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            builder.vertex(matrix4f, 0.5F, -0.5F, -0.5F)
                    .color(255, 255, 255, 230)
                    .uv(minU, maxV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            builder.vertex(matrix4f, 0.5F, 0.5F, -0.5F)
                    .color(255, 255, 255, 230)
                    .uv(minU, minV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            builder.vertex(matrix4f, -0.5F, 0.5F, -0.5F)
                    .color(255, 255, 255, 230)
                    .uv(maxU, minV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            poseStack.popPose();
        }
    }

    /**
     * 渲染实体身上的火焰效果（1.20.1 版本）
     */
    @OnlyIn(Dist.CLIENT)
    public void renderEntityOnFire(@Nonnull Entity entity, @Nonnull PoseStack poseStack,
                                   @Nonnull MultiBufferSource bufferSource,
                                   @Nonnull String iconName0, @Nonnull String iconName1) {
        Minecraft minecraft = Minecraft.getInstance();

        TextureAtlasSprite fireLayer0 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(new ResourceLocation(iconName0));
        TextureAtlasSprite fireLayer1 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(new ResourceLocation(iconName1));

        poseStack.pushPose();

        float scale = entity.getBbWidth() * 1.4F;
        poseStack.scale(scale, scale, scale);

        float renderX = 0.5F;
        float height = entity.getBbHeight() / scale;
        float renderY = 0.0F;

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                -minecraft.getEntityRenderDispatcher().camera.getYRot()));
        poseStack.translate(0.0F, 0.0F, -0.3F + (float) ((int) height) * 0.02F);

        float renderZ = 0.0F;
        int stage = 0;

        VertexConsumer builder = bufferSource.getBuffer(RenderType.cutout());

        Matrix4f matrix4f = poseStack.last().pose();
        Matrix3f matrix3f = poseStack.last().normal();

        while (height > 0.0F) {
            TextureAtlasSprite sprite = (stage % 2 == 0) ? fireLayer0 : fireLayer1;

            float minU = sprite.getU0();
            float minV = sprite.getV0();
            float maxU = sprite.getU1();
            float maxV = sprite.getV1();

            if (stage / 2 % 2 == 0) {
                float temp = maxU;
                maxU = minU;
                minU = temp;
            }

            // 绘制火焰层
            builder.vertex(matrix4f, renderX, -renderY, renderZ)
                    .color(255, 255, 255, 255)
                    .uv(maxU, maxV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            builder.vertex(matrix4f, -renderX, -renderY, renderZ)
                    .color(255, 255, 255, 255)
                    .uv(minU, maxV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            builder.vertex(matrix4f, -renderX, 1.4F - renderY, renderZ)
                    .color(255, 255, 255, 255)
                    .uv(minU, minV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            builder.vertex(matrix4f, renderX, 1.4F - renderY, renderZ)
                    .color(255, 255, 255, 255)
                    .uv(maxU, minV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            height -= 0.45F;
            renderY -= 0.45F;
            renderX *= 0.9F;
            renderZ += 0.03F;
            stage++;
        }

        poseStack.popPose();
    }

    /**
     * 实体渲染后事件：渲染火焰效果
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderLiving(@Nonnull RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();

        if (!entity.hasEffect(this)) {
            return;
        }

        renderEntityOnFire(entity, event.getPoseStack(), event.getMultiBufferSource(),
                getLevelOneName(), getLevelTwoName());
    }

    /**
     * 手部渲染事件：渲染第一人称火焰
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderHand(@Nonnull RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(this)) {
            renderFireInFirstPerson(getLevelTwoName(), event.getPoseStack(),
                    event.getMultiBufferSource());
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}