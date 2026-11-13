package pers.roinflam.carianstyle.neoforge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
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

/**
 * NeoForge 上的 {@link ClientPlatform}。
 * <p>
 * 客户端代码现在还直接调 NeoForge 的接口，没有改成经这里调，所以这里先只占位：每个方法都抛异常，
 * 谁提前用了当场就知道，不会静默什么都不做。客户端代码改过来的时候连同实现一起填上。
 * </p>
 * <p>
 * 这个类引用客户端类，只能在客户端进程里加载；{@code Services.client()} 在专用服上不会去取它。
 * </p>
 */
public final class NeoForgeClientPlatform implements ClientPlatform {

    @Nonnull
    private static UnsupportedOperationException notYet(@Nonnull String method) {
        return new UnsupportedOperationException("NeoForge 客户端平台实现还没接上：" + method);
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
    public void registerHudAboveAll(@Nonnull ResourceLocation id, @Nonnull LayeredDraw.Layer layer) {
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
