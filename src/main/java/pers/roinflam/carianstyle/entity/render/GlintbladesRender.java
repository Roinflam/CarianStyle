package pers.roinflam.carianstyle.entity.render;


import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

import javax.annotation.Nonnull;

@SideOnly(Side.CLIENT)
public class GlintbladesRender<T extends EntityGlintblades> extends Render<T> {
    protected final Item item;
    private final RenderItem itemRenderer;

    public GlintbladesRender(@Nonnull RenderManager renderManagerIn, Item itemIn, RenderItem itemRendererIn) {
        super(renderManagerIn);
        this.item = itemIn;
        this.itemRenderer = itemRendererIn;
    }

    @Override
    public void doRender(@Nonnull T entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(entity.getSize(), entity.getSize(), entity.getSize());
        GlStateManager.rotate(entityYaw, 0.0f, 1.0f, 0);
        if (entity.isShooted()) {
            GlStateManager.rotate((float) Math.max(Math.min(80 - entity.rotationPitch * 1.25, 140), 0), 1.0f, 0.0f, 0);
            GlStateManager.rotate(180.0f, 0.0F, 1.0F, 0.0F);
        } else {
            GlStateManager.rotate(entity.ticksExisted * 20, 0.0F, 1.0F, 0.0F);
        }
        this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        if (this.renderOutlines) {
            GlStateManager.enableColorMaterial();
            GlStateManager.enableOutlineMode(this.getTeamColor(entity));
        }

        this.itemRenderer.renderItem(new ItemStack(item), ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND);

        if (this.renderOutlines) {
            GlStateManager.disableOutlineMode();
            GlStateManager.disableColorMaterial();
        }

        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(T entity) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }

}