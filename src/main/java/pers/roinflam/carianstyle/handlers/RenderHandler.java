package pers.roinflam.carianstyle.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderItem;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.entity.render.GlintbladesRender;
import pers.roinflam.carianstyle.init.CarianStyleItem;

/**
 * 渲染处理器
 * <p>
 * 负责注册所有实体的渲染器
 * </p>
 */
public class RenderHandler {

    /**
     * 注册实体渲染器
     * <p>
     * 在客户端预初始化阶段调用
     * </p>
     */
    public static void registerEntityRenders() {
        // 注册魔法剑实体渲染器
        RenderingRegistry.registerEntityRenderingHandler(EntityGlintblades.class, manager -> {
            RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
            return new GlintbladesRender<>(manager, CarianStyleItem.GLINTBLADES, renderItem);
        });
    }
}