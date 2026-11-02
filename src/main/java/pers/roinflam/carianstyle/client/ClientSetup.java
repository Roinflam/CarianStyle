package pers.roinflam.carianstyle.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 客户端渲染设置
 * <p>
 * v2.1修复：移除 @Mod.EventBusSubscriber 注解。
 * ClientSetupHandler 已经在 CarianStyle.clientSetup() 中处理了所有客户端设置
 * （包括实体渲染器注册和火焰方块渲染层设置），
 * 此类的注解会导致火焰方块的 setRenderLayer 被执行两次。
 * </p>
 * <p>
 * 保留此类但不自动注册，仅作为备用/工具类。
 * 如需使用请手动调用 onClientSetup 方法。
 * </p>
 */
public class ClientSetup {

    /**
     * 客户端设置方法（需手动调用，不再自动触发）
     *
     * @param event 客户端设置事件
     */
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 设置火焰方块的渲染层为 cutout（支持透明度）
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.WHITE_FLAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.YELLOW_FLAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.CRIMSON_FLAME.get(), RenderType.cutout());
        });
    }
}
