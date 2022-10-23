package pers.roinflam.carianstyle.proxy;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import pers.roinflam.carianstyle.handlers.RenderHandler;

import javax.annotation.Nonnull;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerItemRenderer(@Nonnull Item item, int meta, String id) {
        ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(item.getRegistryName(), id));
    }

    @Override
    public void registerEntityRenderer() {
        RenderHandler.registerEntityRenders();
    }

}
