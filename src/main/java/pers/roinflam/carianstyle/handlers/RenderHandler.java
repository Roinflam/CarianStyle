package pers.roinflam.carianstyle.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderItem;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.entity.render.GlintbladesRender;
import pers.roinflam.carianstyle.init.CarianStyleItem;

import javax.annotation.Nonnull;

public class RenderHandler {

    public static void registerEntityRenders() {
        RenderingRegistry.registerEntityRenderingHandler(EntityGlintblades.class, manager -> {
            @Nonnull RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
            return new GlintbladesRender<EntityGlintblades>(manager, CarianStyleItem.GLINTBLADES, renderItem);
        });
    }

}
