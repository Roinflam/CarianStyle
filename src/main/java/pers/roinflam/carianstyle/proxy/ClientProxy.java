package pers.roinflam.carianstyle.proxy;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import pers.roinflam.carianstyle.handlers.RenderHandler;

import javax.annotation.Nonnull;

/**
 * 客户端代理
 * <p>
 * 处理客户端特有的初始化逻辑，如模型加载、渲染器注册等
 * </p>
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void registerItemRenderer(@Nonnull Item item, int meta, String id) {
        if (item.getRegistryName() == null) {
            return;
        }
        ModelLoader.setCustomModelResourceLocation(
                item,
                meta,
                new ModelResourceLocation(item.getRegistryName(), id)
        );
    }

    @Override
    public void registerEntityRenderer() {
        RenderHandler.registerEntityRenders();
    }
}