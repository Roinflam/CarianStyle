package pers.roinflam.carianstyle.base.potion.flame;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

import javax.annotation.Nonnull;

/**
 * 火焰效果药水基类
 * <p>
 * 为实体渲染自定义颜色的火焰效果
 * 子类需要实现getLevelOneName()和getLevelTwoName()返回火焰纹理名称
 * </p>
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
     * 渲染第一人称火焰效果
     */
    @OnlyIn(Dist.CLIENT)
    public void renderFireInFirstPerson(@Nonnull String iconName, @Nonnull PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();

        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(new ResourceLocation(iconName));

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.9F);

        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            poseStack.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (i * 2 - 1) * 10.0F));

            Matrix4f matrix = poseStack.last().pose();

            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(matrix, -0.5F, -0.5F, -0.5F).uv(maxU, maxV).endVertex();
            builder.vertex(matrix, 0.5F, -0.5F, -0.5F).uv(minU, maxV).endVertex();
            builder.vertex(matrix, 0.5F, 0.5F, -0.5F).uv(minU, minV).endVertex();
            builder.vertex(matrix, -0.5F, 0.5F, -0.5F).uv(maxU, minV).endVertex();
            tesselator.end();

            poseStack.popPose();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * 渲染实体身上的火焰效果
     */
    @OnlyIn(Dist.CLIENT)
    public void renderEntityOnFire(@Nonnull Entity entity, @Nonnull PoseStack poseStack,
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
        float renderY = (float) (entity.getY() - entity.getBoundingBox().minY);

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-minecraft.getEntityRenderDispatcher().camera.getYRot()));
        poseStack.translate(0.0F, 0.0F, -0.3F + (float) ((int) height) * 0.02F);

        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float renderZ = 0.0F;
        int stage = 0;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        Matrix4f matrix = poseStack.last().pose();

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

            builder.vertex(matrix, renderX, -renderY, renderZ).uv(maxU, maxV).endVertex();
            builder.vertex(matrix, -renderX, -renderY, renderZ).uv(minU, maxV).endVertex();
            builder.vertex(matrix, -renderX, 1.4F - renderY, renderZ).uv(minU, minV).endVertex();
            builder.vertex(matrix, renderX, 1.4F - renderY, renderZ).uv(maxU, minV).endVertex();

            height -= 0.45F;
            renderY -= 0.45F;
            renderX *= 0.9F;
            renderZ += 0.03F;
            stage++;
        }

        tesselator.end();
        poseStack.popPose();
    }

    /**
     * 实体渲染后事件：渲染火焰效果
     * <p>
     * 直接检查实体是否有该药水效果 - Minecraft会自动同步药水效果到客户端
     * </p>
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderLiving(@Nonnull RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();

        // 直接检查 - 不需要手动网络同步！
        if (!entity.hasEffect(this)) {
            return;
        }

        renderEntityOnFire(entity, event.getPoseStack(), getLevelOneName(), getLevelTwoName());
    }

    /**
     * 手部渲染事件：渲染第一人称火焰
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderHand(@Nonnull RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(this)) {
            renderFireInFirstPerson(getLevelTwoName(), event.getPoseStack());
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}