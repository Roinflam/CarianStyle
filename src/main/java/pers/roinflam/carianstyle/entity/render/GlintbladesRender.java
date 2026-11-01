package pers.roinflam.carianstyle.entity.render;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * PORT-STUB(V3)
 * <p>辉剑渲染器：桩里什么都不画。</p>
 */
@Deprecated
@SideOnly(Side.CLIENT)
public class GlintbladesRender extends Render<EntityGlintblades> {

    protected final Item item;

    public GlintbladesRender(@Nonnull RenderManager renderManager, @Nonnull Item item) {
        super(renderManager);
        this.item = item;
    }

    @Override
    public void doRender(@Nonnull EntityGlintblades entity, double x, double y, double z, float entityYaw, float partialTicks) {
        // 桩
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(@Nonnull EntityGlintblades entity) {
        return null;
    }
}
