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

import java.util.List;
import java.util.Optional;

/**
 * {@link ClientPlatform} 的 Fabric 实现，四个登记类方法都当场登记。
 */
public final class FabricClientPlatform implements ClientPlatform {

    @Override
    public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
                                                          EntityRendererProvider<T> provider) {
        EntityRendererRegistry.register(type, provider);
    }

    @Override
    public void setBlockRenderLayer(Block block, RenderType layer) {
        BlockRenderLayerMap.INSTANCE.putBlock(block, layer);
    }

    @Override
    public KeyMapping registerKeyMapping(String name, int keyCode, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, category));
    }

    /**
     * 1.20.1 的 Fabric HUD 回调在 {@code Gui#render} 末尾、玩家列表之后，和 Forge 的 {@code registerAboveAll}
     * 一样排在原版全部内容后面；两边都不另挪 z，所以这里也不挪。{@code id} 在 Fabric 上用不到。
     */
    @Override
    public void registerHudAboveAll(ResourceLocation id, HudLayer layer) {
        HudRenderCallback.EVENT.register(layer::render);
    }

    @Override
    public void applyItemTransform(BakedModel model, ItemDisplayContext context, PoseStack poseStack,
                                   boolean leftHand) {
        model.getTransforms().getTransform(context).apply(leftHand, poseStack);
    }

    @Override
    public void renderItemTooltip(GuiGraphics graphics, Font font, List<Component> lines,
                                  Optional<TooltipComponent> image, ItemStack stack, int mouseX, int mouseY) {
        graphics.renderTooltip(font, lines, image, mouseX, mouseY);
    }

    // Fabric API 在原版这个渲染器的入口先查自己的内置物品渲染器表，别的模组经 Fabric API 登记的也会生效
    @Override
    public boolean renderCustomItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                    MultiBufferSource buffers, int light, int overlay) {
        BlockEntityWithoutLevelRenderer renderer =
                ((ItemRendererAccessor) Minecraft.getInstance().getItemRenderer()).carianstyle$getBlockEntityRenderer();
        if (renderer == null) {
            return false;
        }
        renderer.renderByItem(stack, context, poseStack, buffers, light, overlay);
        return true;
    }

    @Override
    public Optional<String> modDisplayName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace).map(c -> c.getMetadata().getName());
    }
}
