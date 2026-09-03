// ClientSetupHandler.java
package pers.roinflam.carianstyle;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.carianstyle.entity.render.GlintbladesRender;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.init.CarianStyleEntity;
import pers.roinflam.carianstyle.init.CarianStyleItem;
import pers.roinflam.carianstyle.visual.client.CarianStyleAuraDisplays;
import pers.roinflam.carianstyle.visual.client.VisualRangeCheck;

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

            // 注册光环显示探测器（客户端光环地面圈数据源；内置 5 个光环，装备对应附魔即显示）
            CarianStyleAuraDisplays.init();

            // 视觉范围契约自检（纯防呆，只读常量 + 记日志，不影响任何渲染行为）：
            // 校验各渲染器的裁剪距离没有超过其数据来源（共享查询 / 光环扫描）的范围上限，
            // 避免「改了某个 CULL 之后远距离特效静默消失」这类极难排查的问题。
            VisualRangeCheck.validate();
        });
    }
}

