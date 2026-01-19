package pers.roinflam.carianstyle.base.potion.flame;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 火焰效果药水基类（1.20.1 版本）
 * <p>
 * 特性：
 * - 自动同步效果到周围64格内的所有玩家
 * - 每tick自动同步，确保效果消失也能同步
 * - 实体死亡时自动清理效果
 * </p>
 */
public abstract class FlameBase extends HideBase {

    /**
     * 静态初始化标记，确保只注册一次
     */
    private static boolean eventsRegistered = false;

    /**
     * 同步范围（格）
     */
    private static final double SYNC_RANGE = 64.0;

    protected FlameBase(@Nonnull MobEffectCategory category, int liquidColor) {
        super(category, liquidColor);

        // 只在客户端且只注册一次渲染事件
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && !eventsRegistered) {
            registerRenderEvents();
        }

        // 在服务端注册死亡事件（只注册一次）
        if (!eventsRegistered) {
            MinecraftForge.EVENT_BUS.register(FlameEffectManager.class);
            eventsRegistered = true;
        }
    }

    /**
     * 注册渲染事件（只在客户端调用一次）
     */
    @OnlyIn(Dist.CLIENT)
    private static void registerRenderEvents() {
        MinecraftForge.EVENT_BUS.register(FlameRenderHandler.class);
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
     * 每tick应用效果
     * <p>
     * 重写此方法以实现自动同步
     * </p>
     */
    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        // 只在服务端执行同步
        if (!entity.level().isClientSide) {
            syncEffectToNearbyPlayers(entity);
        }
    }

    /**
     * 同步效果到周围64格内的所有玩家
     *
     * @param entity 拥有效果的实体
     */
    private void syncEffectToNearbyPlayers(@Nonnull LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 获取实体的效果实例
        MobEffectInstance effectInstance = entity.getEffect(this);
        if (effectInstance == null) {
            return;
        }

        // 获取周围64格内的所有玩家
        AABB searchBox = new AABB(
                entity.getX() - SYNC_RANGE, entity.getY() - SYNC_RANGE, entity.getZ() - SYNC_RANGE,
                entity.getX() + SYNC_RANGE, entity.getY() + SYNC_RANGE, entity.getZ() + SYNC_RANGE
        );

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                searchBox
        );

        // 向每个玩家发送同步包
        for (ServerPlayer player : nearbyPlayers) {
            player.connection.send(
                    new ClientboundUpdateMobEffectPacket(entity.getId(), effectInstance)
            );
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    /**
     * 渲染第一人称火焰效果
     */
    @OnlyIn(Dist.CLIENT)
    public void renderFireInFirstPerson(@Nonnull String iconName, @Nonnull PoseStack poseStack,
                                        @Nonnull MultiBufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();

        ResourceLocation textureLocation = new ResourceLocation(iconName);
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(textureLocation);

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

    /**
     * 渲染实体身上的火焰效果
     */
    @OnlyIn(Dist.CLIENT)
    public void renderEntityOnFire(@Nonnull Entity entity, @Nonnull PoseStack poseStack,
                                   @Nonnull MultiBufferSource bufferSource,
                                   @Nonnull String iconName0, @Nonnull String iconName1) {
        Minecraft minecraft = Minecraft.getInstance();

        ResourceLocation texture0 = new ResourceLocation(iconName0);
        ResourceLocation texture1 = new ResourceLocation(iconName1);

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
     * 火焰效果管理器
     * <p>
     * 处理实体死亡时的效果清理和同步
     * </p>
     */
    public static class FlameEffectManager {

        /**
         * 实体死亡事件：清理火焰效果并同步
         */
        @SubscribeEvent
        public static void onLivingDeath(@Nonnull LivingDeathEvent event) {
            LivingEntity entity = event.getEntity();

            // 只在服务端处理
            if (entity.level().isClientSide) {
                return;
            }

            // 检查是否有火焰效果
            for (MobEffectInstance effectInstance : entity.getActiveEffects()) {
                MobEffect effect = effectInstance.getEffect();

                if (effect instanceof FlameBase) {
                    // 移除效果
                    entity.removeEffect(effect);

                    // 同步移除到客户端
                    if (entity.level() instanceof ServerLevel serverLevel) {
                        AABB searchBox = new AABB(
                                entity.getX() - SYNC_RANGE, entity.getY() - SYNC_RANGE, entity.getZ() - SYNC_RANGE,
                                entity.getX() + SYNC_RANGE, entity.getY() + SYNC_RANGE, entity.getZ() + SYNC_RANGE
                        );

                        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                                ServerPlayer.class,
                                searchBox
                        );

                        for (ServerPlayer player : nearbyPlayers) {
                            player.connection.send(
                                    new ClientboundRemoveMobEffectPacket(entity.getId(), effect)
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * 静态渲染事件处理器
     */
    @OnlyIn(Dist.CLIENT)
    public static class FlameRenderHandler {

        /**
         * 实体渲染后事件：渲染火焰效果
         */
        @SubscribeEvent
        public static void onRenderLiving(@Nonnull RenderLivingEvent.Post<?, ?> event) {
            LivingEntity entity = event.getEntity();

            // 遍历所有活动效果
            for (MobEffectInstance effectInstance : entity.getActiveEffects()) {
                MobEffect effect = effectInstance.getEffect();

                // 检查是否是 FlameBase 的实例
                if (effect instanceof FlameBase) {
                    FlameBase flameEffect = (FlameBase) effect;

                    flameEffect.renderEntityOnFire(
                            entity,
                            event.getPoseStack(),
                            event.getMultiBufferSource(),
                            flameEffect.getLevelOneName(),
                            flameEffect.getLevelTwoName()
                    );
                }
            }
        }

        /**
         * 手部渲染事件：渲染第一人称火焰
         */
        @SubscribeEvent
        public static void onRenderHand(@Nonnull RenderHandEvent event) {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }

            // 遍历所有活动效果
            for (MobEffectInstance effectInstance : player.getActiveEffects()) {
                MobEffect effect = effectInstance.getEffect();

                // 检查是否是 FlameBase 的实例
                if (effect instanceof FlameBase) {
                    FlameBase flameEffect = (FlameBase) effect;

                    flameEffect.renderFireInFirstPerson(
                            flameEffect.getLevelTwoName(),
                            event.getPoseStack(),
                            event.getMultiBufferSource()
                    );

                    // 只渲染第一个火焰效果
                    break;
                }
            }
        }
    }
}