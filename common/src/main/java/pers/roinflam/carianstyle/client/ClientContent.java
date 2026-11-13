package pers.roinflam.carianstyle.client;

import net.minecraft.client.renderer.RenderType;
import pers.roinflam.carianstyle.entity.render.GlintbladesRender;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.init.CarianStyleEntity;
import pers.roinflam.carianstyle.init.CarianStyleItem;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.visual.client.StackHudRegister;

import javax.annotation.Nonnull;

/**
 * 客户端要交给加载器登记的四类东西：按键、HUD 层、实体渲染器、方块渲染层。
 * <p>
 * 内容在这里列一次，怎么登记、什么时候登记由各加载器的客户端入口决定：
 * 有的加载器要在各自的注册事件里登记，客户端平台实现先记下、到时候再交出去；
 * 有的当场就能登记。
 * </p>
 */
public final class ClientContent {

    private ClientContent() {
    }

    /** 按键。 */
    public static void registerKeyMappings(@Nonnull ClientPlatform platform) {
        CarianStyleKeys.register(platform);
    }

    /** HUD 层。 */
    public static void registerHudLayers(@Nonnull ClientPlatform platform) {
        StackHudRegister.register(platform);
    }

    /** 实体渲染器。 */
    public static void registerEntityRenderers(@Nonnull ClientPlatform platform) {
        platform.registerEntityRenderer(
                CarianStyleEntity.GLINTBLADES.get(),
                context -> new GlintbladesRender(context, CarianStyleItem.GLINTBLADES.get())
        );
    }

    /** 方块渲染层：三种火焰方块用 cutout（贴图带透明）。 */
    public static void registerBlockRenderLayers(@Nonnull ClientPlatform platform) {
        platform.setBlockRenderLayer(CarianStyleBlocks.WHITE_FLAME.get(), RenderType.cutout());
        platform.setBlockRenderLayer(CarianStyleBlocks.YELLOW_FLAME.get(), RenderType.cutout());
        platform.setBlockRenderLayer(CarianStyleBlocks.CRIMSON_FLAME.get(), RenderType.cutout());
    }
}
