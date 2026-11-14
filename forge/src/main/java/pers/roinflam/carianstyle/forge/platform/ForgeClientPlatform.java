package pers.roinflam.carianstyle.forge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
import pers.roinflam.carianstyle.platform.ClientPlatform;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

// 客户端拆分那一批（GB4a）才填；提前被调用直接抛，比静默什么都不做好查
public final class ForgeClientPlatform implements ClientPlatform {

    @Nonnull
    private static UnsupportedOperationException notYet(@Nonnull String method) {
        return new UnsupportedOperationException("Forge 客户端平台实现还没接上：" + method);
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(@Nonnull EntityType<? extends T> type,
                                                          @Nonnull EntityRendererProvider<T> provider) {
        throw notYet("registerEntityRenderer");
    }

    @Override
    public void setBlockRenderLayer(@Nonnull Block block, @Nonnull RenderType layer) {
        throw notYet("setBlockRenderLayer");
    }

    @Override
    @Nonnull
    public KeyMapping registerKeyMapping(@Nonnull String name, int keyCode, @Nonnull String category) {
        throw notYet("registerKeyMapping");
    }

    @Override
    public void registerHudAboveAll(@Nonnull ResourceLocation id, @Nonnull HudLayer layer) {
        throw notYet("registerHudAboveAll");
    }

    @Override
    public void applyItemTransform(@Nonnull BakedModel model, @Nonnull ItemDisplayContext context,
                                   @Nonnull PoseStack poseStack, boolean leftHand) {
        throw notYet("applyItemTransform");
    }

    @Override
    public void renderItemTooltip(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull List<Component> lines,
                                  @Nonnull Optional<TooltipComponent> image, @Nonnull ItemStack stack, int mouseX, int mouseY) {
        throw notYet("renderItemTooltip");
    }

    @Override
    public boolean renderCustomItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext context,
                                    @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffers, int light, int overlay) {
        throw notYet("renderCustomItem");
    }

    @Override
    @Nonnull
    public Optional<String> modDisplayName(@Nonnull String namespace) {
        throw notYet("modDisplayName");
    }
}
