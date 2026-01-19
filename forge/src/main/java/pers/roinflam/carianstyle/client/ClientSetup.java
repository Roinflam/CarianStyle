package pers.roinflam.carianstyle.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 客户端渲染设置
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 设置火焰方块的渲染层为 cutout（支持透明度）
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.WHITE_FLAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.YELLOW_FLAME.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CarianStyleBlocks.CRIMSON_FLAME.get(), RenderType.cutout());

            System.out.println("[CarianStyle-调试] 火焰方块渲染层已设置为 cutout");
        });
    }
}