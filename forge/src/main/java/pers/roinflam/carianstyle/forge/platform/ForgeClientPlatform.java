package pers.roinflam.carianstyle.forge.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import pers.roinflam.carianstyle.platform.ClientPlatform;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ForgeClientPlatform implements ClientPlatform {

    private static final List<KeyMapping> PENDING_KEYS = new ArrayList<>();
    private static final List<PendingLayer> PENDING_LAYERS = new ArrayList<>();
    private static boolean keysFlushed;
    private static boolean layersFlushed;
    private static boolean attached;

    private record PendingLayer(ResourceLocation id, HudLayer layer) {
    }

    /**
     * 把按键、HUD 层的注册事件监听挂到模组总线上。客户端入口初始化时调一次。
     *
     * @param modEventBus 本模组的模组总线
     */
    public static synchronized void attach(@Nonnull IEventBus modEventBus) {
        if (attached) {
            throw new IllegalStateException("Forge 客户端平台已经挂过模组总线了");
        }
        attached = true;
        modEventBus.addListener(ForgeClientPlatform::onRegisterKeyMappings);
        modEventBus.addListener(ForgeClientPlatform::onRegisterGuiOverlays);
    }

    private static synchronized void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        keysFlushed = true;
        for (KeyMapping key : PENDING_KEYS) {
            event.register(key);
        }
        PENDING_KEYS.clear();
    }

    private static synchronized void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        layersFlushed = true;
        // Forge 的覆盖层名只收路径，命名空间取事件分发时的当前模组，也就是本模组
        String namespace = ModLoadingContext.get().getActiveNamespace();
        for (PendingLayer pending : PENDING_LAYERS) {
            if (!pending.id().getNamespace().equals(namespace)) {
                throw new IllegalStateException("HUD 层的命名空间要和本模组一致：" + pending.id());
            }
            HudLayer layer = pending.layer();
            event.registerAboveAll(pending.id().getPath(),
                    (gui, graphics, partialTick, width, height) -> layer.render(graphics, partialTick));
        }
        PENDING_LAYERS.clear();
    }

    private static void requireAttached(Object what) {
        if (!attached) {
            throw new IllegalStateException("Forge 客户端平台还没挂上模组总线，登记了也交不出去：" + what);
        }
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(@Nonnull EntityType<? extends T> type,
                                                          @Nonnull EntityRendererProvider<T> provider) {
        EntityRenderers.register(type, provider);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockRenderLayer(@Nonnull Block block, @Nonnull RenderType layer) {
        ItemBlockRenderTypes.setRenderLayer(block, layer);
    }

    @Override
    @Nonnull
    public KeyMapping registerKeyMapping(@Nonnull String name, int keyCode, @Nonnull String category) {
        synchronized (ForgeClientPlatform.class) {
            requireAttached(name);
            if (keysFlushed) {
                throw new IllegalStateException("按键注册事件已经发过了，登记不进去：" + name);
            }
            // KeyMapping 的构造器会把自己放进原版的静态按键表，所以先查完再建，出错时不留下半登记的按键
            KeyMapping key = new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, keyCode, category);
            PENDING_KEYS.add(key);
            return key;
        }
    }

    @Override
    public void registerHudAboveAll(@Nonnull ResourceLocation id, @Nonnull HudLayer layer) {
        synchronized (ForgeClientPlatform.class) {
            requireAttached(id);
            if (layersFlushed) {
                throw new IllegalStateException("HUD 层注册事件已经发过了，登记不进去：" + id);
            }
            PENDING_LAYERS.add(new PendingLayer(id, layer));
        }
    }

    @Override
    public void applyItemTransform(@Nonnull BakedModel model, @Nonnull ItemDisplayContext context,
                                   @Nonnull PoseStack poseStack, boolean leftHand) {
        model.applyTransform(context, poseStack, leftHand);
    }

    @Override
    public void renderItemTooltip(@Nonnull GuiGraphics graphics, @Nonnull Font font, @Nonnull List<Component> lines,
                                  @Nonnull Optional<TooltipComponent> image, @Nonnull ItemStack stack, int mouseX, int mouseY) {
        graphics.renderTooltip(font, lines, image, stack, mouseX, mouseY);
    }

    @Override
    public boolean renderCustomItem(@Nonnull ItemStack stack, @Nonnull ItemDisplayContext context,
                                    @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffers, int light, int overlay) {
        IClientItemExtensions ext = IClientItemExtensions.of(stack);
        if (ext == null) {
            return false;
        }
        BlockEntityWithoutLevelRenderer renderer = ext.getCustomRenderer();
        if (renderer == null) {
            return false;
        }
        renderer.renderByItem(stack, context, poseStack, buffers, light, overlay);
        return true;
    }

    @Override
    @Nonnull
    public Optional<String> modDisplayName(@Nonnull String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName());
    }
}
