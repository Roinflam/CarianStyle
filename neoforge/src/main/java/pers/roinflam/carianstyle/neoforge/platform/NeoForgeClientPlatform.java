package pers.roinflam.carianstyle.neoforge.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import pers.roinflam.carianstyle.platform.ClientPlatform;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NeoForge 上的 {@link ClientPlatform}。
 * <p>
 * 按键和 HUD 层 NeoForge 只认各自注册事件里的登记，所以这两类先记下，
 * 由 {@link #attach} 挂到模组总线上的监听在事件里照记下的先后交出去；事件发过之后再登记直接抛异常，
 * 不会静默丢掉。实体渲染器和方块渲染层在客户端初始化的主线程任务里当场登记，和原来的写法一样。
 * </p>
 * <p>
 * 其余几个方法照旧调 NeoForge 的扩展方法（模型变换、带物品的提示框、物品自定义渲染器），
 * 别的模组覆写这些扩展的效果照样体现。
 * </p>
 * <p>
 * 这个类引用客户端类，只能在客户端进程里加载；{@code Services.client()} 在专用服上不会去取它，
 * {@link #attach} 也只由客户端入口调。
 * </p>
 */
public final class NeoForgeClientPlatform implements ClientPlatform {

    private static final List<KeyMapping> PENDING_KEYS = new ArrayList<>();
    private static final List<PendingLayer> PENDING_LAYERS = new ArrayList<>();
    private static boolean keysFlushed;
    private static boolean layersFlushed;
    private static boolean attached;

    private record PendingLayer(ResourceLocation id, LayeredDraw.Layer layer) {
    }

    /**
     * 把按键、HUD 层的注册事件监听挂到模组总线上。客户端入口在构造器里调一次。
     *
     * @param modEventBus 本模组的模组总线
     */
    public static synchronized void attach(@Nonnull IEventBus modEventBus) {
        if (attached) {
            throw new IllegalStateException("NeoForge 客户端平台已经挂过模组总线了");
        }
        attached = true;
        modEventBus.addListener(NeoForgeClientPlatform::onRegisterKeyMappings);
        modEventBus.addListener(NeoForgeClientPlatform::onRegisterGuiLayers);
    }

    private static synchronized void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        keysFlushed = true;
        for (KeyMapping key : PENDING_KEYS) {
            event.register(key);
        }
        PENDING_KEYS.clear();
    }

    private static synchronized void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        layersFlushed = true;
        for (PendingLayer pending : PENDING_LAYERS) {
            event.registerAboveAll(pending.id(), pending.layer());
        }
        PENDING_LAYERS.clear();
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
        synchronized (NeoForgeClientPlatform.class) {
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

    private static void requireAttached(Object what) {
        if (!attached) {
            throw new IllegalStateException("NeoForge 客户端平台还没挂上模组总线，登记了也交不出去：" + what);
        }
    }

    @Override
    public void registerHudAboveAll(@Nonnull ResourceLocation id, @Nonnull LayeredDraw.Layer layer) {
        synchronized (NeoForgeClientPlatform.class) {
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
