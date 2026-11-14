package pers.roinflam.carianstyle.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import pers.roinflam.carianstyle.fabric.mixin.client.ItemRendererAccessor;
import pers.roinflam.carianstyle.platform.ClientPlatform;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

/**
 * {@link ClientPlatform} 的 Fabric 实现，经 {@code META-INF/services} 由 {@code Services.client()} 取到。
 * <p>
 * Fabric 没有注册事件可等，四个登记类方法都当场交给 Fabric API。
 * 后三个在 NeoForge 上保留平台扩展（物品客户端扩展），Fabric 没有这些扩展，按原版写法做。
 * </p>
 */
public final class FabricClientPlatform implements ClientPlatform {

    /**
     * HUD 层画之前往屏幕外（z 正方向）挪的距离。
     * <p>
     * <b>为什么要挪：</b>Fabric 的 HUD 回调在 {@code Gui#render} 末尾，原版各层画完、已经把每层叠加的 z 偏移退回 0。
     * 原版每画一层往外挪 {@link LayeredDraw#Z_SEPARATION}，聊天栏、玩家列表落在 z 两三千的位置，
     * 本模组的层在 z = 0 画、又带深度测试，和它们重叠的地方会被挡住。
     * NeoForge 上「画在所有层最上面」的层是逐层挪到最后的，原版二十五层之后，大约 z = 5000；
     * 这里挪同样的距离，层序和 NeoForge 一致。离 GUI 投影的远近裁剪面（±10000）还有足够余量。
     * </p>
     */
    private static final float HUD_ABOVE_ALL_Z = LayeredDraw.Z_SEPARATION * 25;

    @Override
    public <T extends Entity> void registerEntityRenderer(@Nonnull EntityType<? extends T> type,
                                                          @Nonnull EntityRendererProvider<T> provider) {
        EntityRendererRegistry.register(type, provider);
    }

    @Override
    public void setBlockRenderLayer(@Nonnull Block block, @Nonnull RenderType layer) {
        BlockRenderLayerMap.INSTANCE.putBlock(block, layer);
    }

    @Override
    @Nonnull
    public KeyMapping registerKeyMapping(@Nonnull String name, int keyCode, @Nonnull String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, category));
    }

    /**
     * 挂到 Fabric 的 HUD 回调上。回调在原版各层之后，登记在本模组之后的别的回调画在更上面。
     * {@code id} 在 Fabric 上用不到（这个版本的 Fabric API 没有按 id 排序的 HUD 层）。
     * 按 F1 隐藏界面时回调照样触发，层自己判断要不要画（和 NeoForge 上一样）。
     */
    @Override
    public void registerHudAboveAll(@Nonnull ResourceLocation id, @Nonnull LayeredDraw.Layer layer) {
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, HUD_ABOVE_ALL_Z);
            try {
                layer.render(graphics, deltaTracker);
            } finally {
                graphics.pose().popPose();
            }
        });
    }

    @Override
    public void applyItemTransform(@Nonnull BakedModel model, @Nonnull ItemDisplayContext context,
                                   @Nonnull PoseStack poseStack, boolean leftHand) {
        model.getTransforms().getTransform(context).apply(leftHand, poseStack);
    }

    /** 原版的提示框不带物品参数，{@code stack} 在 Fabric 上用不到。 */
    @Override
    public void renderItemTooltip(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull List<Component> lines,
                                  @Nonnull Optional<TooltipComponent> image, @Nonnull ItemStack stack, int mouseX, int mouseY) {
        graphics.renderTooltip(font, lines, image, mouseX, mouseY);
    }

    /**
     * 交给原版的方块实体样式物品渲染器。Fabric API 在这个渲染器入口先查自己的内置物品渲染器表，
     * 别的模组经 Fabric API 登记的内置渲染器也在这一步生效，不用另查。
     */
    @Override
    public boolean renderCustomItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext context,
                                    @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffers, int light, int overlay) {
        BlockEntityWithoutLevelRenderer renderer =
                ((ItemRendererAccessor) Minecraft.getInstance().getItemRenderer()).carianstyle$getBlockEntityRenderer();
        if (renderer == null) {
            return false;
        }
        renderer.renderByItem(stack, context, poseStack, buffers, light, overlay);
        return true;
    }

    @Override
    @Nonnull
    public Optional<String> modDisplayName(@Nonnull String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace).map(c -> c.getMetadata().getName());
    }
}
