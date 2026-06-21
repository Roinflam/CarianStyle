package pers.roinflam.carianstyle.dynamicattr.clientsync;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
 * <p>
 * 负责渲染三种自定义火焰效果：
 * - 序列号1：注定死亡火焰（猩红色）
 * - 序列号2：毁灭火焰（白色）
 * - 序列号3：癫痫火焰（黄色）
 * </p>
 * <p>
 * 视觉说明（材质不变，仅控制渲染表现）：
 * 1. 渲染类型使用 cutout（与原版火焰、树叶一致）：alpha 测试为二值——纹理不透明像素
 *    完全绘制、透明像素直接丢弃，火焰完全不透明、不会透出背景。
 * 2. 火焰为稳定竖直形态，不做任何左右摆动 / 横向飘动 / 大小缩放。
 * 3. 火焰亮度由 uv2(240,240) 拉满光照实现，黑暗中依旧明亮。
 * 4. 火焰外形（含火尖）完全由纹理本身的透明轮廓决定，不再用 alpha 做淡出。
 * 5. 唯一保留的动画是非常细微的亮度闪烁（仅调制顶点 RGB，不改色相）；
 *    若需完全静止，将 FLICKER_AMPLITUDE 设为 0 即可。
 * 6. 性能：ResourceLocation 预创建，避免每帧每实体重复分配对象（渲染热路径）。
 * </p>
 * <p>
 * 注意：cutout 使用 BLOCK 顶点格式（position/color/uv/uv2/normal），不含 overlay 元素，
 * 因此顶点写入不调用 overlayCoords。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientSyncFlameRenderer {

    // ==================== 火焰渲染参数（集中管理，便于手动调整） ====================

    /** 亮度闪烁幅度（0~1，越大明暗变化越明显；设为 0 则火焰亮度完全静止） */
    private static final float FLICKER_AMPLITUDE = 0.08F;
    /** 亮度闪烁速度 */
    private static final float FLICKER_SPEED = 0.60F;

    /**
     * 火焰效果配置
     */
    private static class FlameConfig {
        final int serialNumber;
        // 预创建 ResourceLocation，避免每帧渲染重复 new 对象
        final ResourceLocation layer0;
        final ResourceLocation layer1;

        FlameConfig(int serialNumber, String layer0, String layer1) {
            this.serialNumber = serialNumber;
            this.layer0 = new ResourceLocation(layer0);
            this.layer1 = new ResourceLocation(layer1);
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
     *
     * @param event 实体渲染后事件
     */
    @SubscribeEvent
    public static void onRenderLiving(@Nonnull RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        int entityId = entity.getId();
        // 帧间插值系数，用于保证亮度闪烁逐帧平滑
        float partialTick = event.getPartialTick();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, entityId)) {
                renderEntityOnFire(
                        entity,
                        event.getPoseStack(),
                        event.getMultiBufferSource(),
                        config.layer0,
                        config.layer1,
                        partialTick
                );
                // 只渲染第一个匹配的火焰（避免多个火焰叠加）
                break;
            }
        }
    }

    /**
     * 手部渲染事件：渲染第一人称火焰
     *
     * @param event 手部渲染事件
     */
    @SubscribeEvent
    public static void onRenderHand(@Nonnull RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        int playerId = minecraft.player.getId();
        // 连续动画时间：玩家存活tick + 帧间插值（仅服务于亮度闪烁）
        float time = minecraft.player.tickCount + event.getPartialTick();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, playerId)) {
                renderFireInFirstPerson(
                        config.layer1,
                        event.getPoseStack(),
                        event.getMultiBufferSource(),
                        time
                );
                // 只渲染第一个匹配的火焰
                break;
            }
        }
    }

    /**
     * 渲染实体身上的火焰效果
     *
     * @param entity        目标实体
     * @param poseStack     渲染矩阵栈
     * @param bufferSource  缓冲区源
     * @param layer0Texture 火焰纹理层0
     * @param layer1Texture 火焰纹理层1
     * @param partialTick   帧间插值系数
     */
    private static void renderEntityOnFire(@Nonnull Entity entity,
                                           @Nonnull PoseStack poseStack,
                                           @Nonnull MultiBufferSource bufferSource,
                                           @Nonnull ResourceLocation layer0Texture,
                                           @Nonnull ResourceLocation layer1Texture,
                                           float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        TextureAtlasSprite fireLayer0 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(layer0Texture);
        TextureAtlasSprite fireLayer1 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(layer1Texture);

        // 连续动画时间：实体存活tick + 帧间插值（仅服务于亮度闪烁）
        float time = entity.tickCount + partialTick;
        // 每个实体使用独立相位，使多个火焰的亮度闪烁不完全同步
        float phase = entity.getId() * 0.6F;

        poseStack.pushPose();

        // 固定缩放，不做呼吸脉动
        float scale = entity.getBbWidth() * 1.4F;
        poseStack.scale(scale, scale, scale);

        // 火焰起始高度（缩放空间内）
        float startHeight = entity.getBbHeight() / scale;

        // billboard：火焰始终正面朝向摄像机（不叠加任何摆动）
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                -minecraft.getEntityRenderDispatcher().camera.getYRot()));

        poseStack.translate(0.0F, 0.0F, -0.3F + (float) ((int) startHeight) * 0.02F);

        float renderX = 0.5F;
        float renderY = 0.0F;
        float renderZ = 0.0F;
        float height = startHeight;
        int stage = 0;

        // cutout 渲染类型：alpha 测试二值，火焰完全不透明，不会透出背景
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

            // 亮度闪烁（仅调制顶点 RGB，不改色相，不改透明度）
            float flicker = (1.0F - FLICKER_AMPLITUDE) + FLICKER_AMPLITUDE
                    * Mth.sin(time * FLICKER_SPEED + stage * 0.7F + phase);
            int rgb = clampColor((int) (255.0F * flicker));

            float left = -renderX;
            float right = renderX;
            float bottomY = -renderY;
            float topY = 1.4F - renderY;

            // 底部右顶点（alpha 固定 255，完全不透明）
            builder.vertex(matrix4f, right, bottomY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 底部左顶点
            builder.vertex(matrix4f, left, bottomY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部左顶点
            builder.vertex(matrix4f, left, topY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, minV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部右顶点
            builder.vertex(matrix4f, right, topY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, minV)
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
     * @param poseStack       渲染矩阵栈
     * @param bufferSource    缓冲区源
     * @param time            连续动画时间（玩家tick + 帧间插值）
     */
    private static void renderFireInFirstPerson(@Nonnull ResourceLocation textureLocation,
                                                @Nonnull PoseStack poseStack,
                                                @Nonnull MultiBufferSource bufferSource,
                                                float time) {
        Minecraft minecraft = Minecraft.getInstance();

        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(textureLocation);

        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();

        // 与实体火焰统一使用 cutout，完全不透明
        VertexConsumer builder = bufferSource.getBuffer(RenderType.cutout());

        for (int i = 0; i < 2; i++) {
            // 左右两片火焰使用错开的相位，使亮度闪烁不完全同步
            float sidePhase = i * 2.5F;

            poseStack.pushPose();
            poseStack.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (i * 2 - 1) * 10.0F));

            Matrix4f matrix4f = poseStack.last().pose();
            Matrix3f matrix3f = poseStack.last().normal();

            // 亮度闪烁（仅调制顶点 RGB，不改色相，不改透明度）
            float flicker = (1.0F - FLICKER_AMPLITUDE) + FLICKER_AMPLITUDE
                    * Mth.sin(time * FLICKER_SPEED + sidePhase);
            int rgb = clampColor((int) (255.0F * flicker));

            // 底部左顶点（alpha 固定 255，完全不透明）
            builder.vertex(matrix4f, -0.5F, -0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 底部右顶点
            builder.vertex(matrix4f, 0.5F, -0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部右顶点
            builder.vertex(matrix4f, 0.5F, 0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, minV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部左顶点
            builder.vertex(matrix4f, -0.5F, 0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, minV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            poseStack.popPose();
        }
    }

    /**
     * 将颜色分量限制在 0~255 范围内
     *
     * @param value 原始分量值
     * @return 限制后的分量值
     */
    private static int clampColor(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }
}