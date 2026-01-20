package pers.roinflam.carianstyle.dynamicattr.clientsync;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 客户端火焰渲染处理器
 * 负责渲染三种自定义火焰效果：
 * - 序列号1：注定死亡火焰（猩红色）
 * - 序列号2：毁灭火焰（白色）
 * - 序列号3：癫痫火焰（黄色）
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientSyncFlameRenderer {

    /**
     * 火焰效果配置
     */
    private static class FlameConfig {
        final int serialNumber;
        final String layer0Texture;
        final String layer1Texture;

        FlameConfig(int serialNumber, String layer0, String layer1) {
            this.serialNumber = serialNumber;
            this.layer0Texture = layer0;
            this.layer1Texture = layer1;
        }
    }

    /**
     * 三种火焰配置
     * 根据你的实际纹理路径修改
     */
    private static final FlameConfig[] FLAME_CONFIGS = {
            // 注定死亡火焰（猩红色）- 序列号1
            new FlameConfig(1,
                    Reference.MOD_ID + ":block/crimson_flame_layer_0",
                    Reference.MOD_ID + ":block/crimson_flame_layer_1"),

            // 毁灭火焰（白色）- 序列号2
            new FlameConfig(2,
                    Reference.MOD_ID + ":block/white_flame_layer_0",
                    Reference.MOD_ID + ":block/white_flame_layer_1"),

            // 癫痫火焰（黄色）- 序列号3
            new FlameConfig(3,
                    Reference.MOD_ID + ":block/yellow_flame_layer_0",
                    Reference.MOD_ID + ":block/yellow_flame_layer_1")
    };

    /**
     * 实体渲染后事件：渲染实体身上的火焰效果
     */
    @SubscribeEvent
    public static void onRenderLiving(@Nonnull RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        int entityId = entity.getId();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, entityId)) {
                renderEntityOnFire(
                        entity,
                        event.getPoseStack(),
                        event.getMultiBufferSource(),
                        config.layer0Texture,
                        config.layer1Texture
                );
                // 只渲染第一个匹配的火焰（避免多个火焰叠加）
                break;
            }
        }
    }

    /**
     * 手部渲染事件：渲染第一人称火焰
     */
    @SubscribeEvent
    public static void onRenderHand(@Nonnull RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        int playerId = minecraft.player.getId();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, playerId)) {
                renderFireInFirstPerson(
                        config.layer1Texture,
                        event.getPoseStack(),
                        event.getMultiBufferSource()
                );
                // 只渲染第一个匹配的火焰
                break;
            }
        }
    }

    /**
     * 渲染实体身上的火焰效果
     *
     * @param entity 目标实体
     * @param poseStack 渲染矩阵栈
     * @param bufferSource 缓冲区源
     * @param layer0Texture 火焰纹理层0
     * @param layer1Texture 火焰纹理层1
     */
    private static void renderEntityOnFire(@Nonnull Entity entity,
                                           @Nonnull PoseStack poseStack,
                                           @Nonnull MultiBufferSource bufferSource,
                                           @Nonnull String layer0Texture,
                                           @Nonnull String layer1Texture) {
        Minecraft minecraft = Minecraft.getInstance();

        ResourceLocation texture0 = new ResourceLocation(layer0Texture);
        ResourceLocation texture1 = new ResourceLocation(layer1Texture);

        TextureAtlasSprite fireLayer0 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture0);
        TextureAtlasSprite fireLayer1 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture1);

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
     * 渲染第一人称火焰效果
     *
     * @param textureLocation 火焰纹理位置
     * @param poseStack 渲染矩阵栈
     * @param bufferSource 缓冲区源
     */
    private static void renderFireInFirstPerson(@Nonnull String textureLocation,
                                                @Nonnull PoseStack poseStack,
                                                @Nonnull MultiBufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();

        ResourceLocation texture = new ResourceLocation(textureLocation);
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);

        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();

        VertexConsumer builder = bufferSource.getBuffer(RenderType.cutout());

        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            poseStack.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (i * 2 - 1) * 10.0F));

            Matrix4f matrix4f = poseStack.last().pose();
            Matrix3f matrix3f = poseStack.last().normal();

            builder.vertex(matrix4f, -0.5F, -0.5F, -0.5F)
                    .color(255, 255, 255, 230)
                    .uv(maxU, maxV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(240, 240)
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
}