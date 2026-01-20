// ClientSetupHandler.java
package pers.roinflam.carianstyle;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.carianstyle.entity.render.GlintbladesRender;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.init.CarianStyleEntity;
import pers.roinflam.carianstyle.init.CarianStyleItem;

@OnlyIn(Dist.CLIENT)
public class ClientSetupHandler {

    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 注册实体渲染器
            EntityRenderers.register(
                    CarianStyleEntity.GLINTBLADES.get(),
                    context -> new GlintbladesRender(context, CarianStyleItem.GLINTBLADES.get())
            );

            // 设置火焰方块的渲染层
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.WHITE_FLAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.YELLOW_FLAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.CRIMSON_FLAME.get(), RenderType.cutout());
        });
    }
}