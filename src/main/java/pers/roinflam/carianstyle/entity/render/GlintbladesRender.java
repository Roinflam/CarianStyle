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

/**
 * 魔法剑实体渲染器
 * <p>
 * 渲染悬浮旋转和飞行中的魔法剑
 * </p>
 */
@SideOnly(Side.CLIENT)
public class GlintbladesRender<T extends EntityGlintblades> extends Render<T> {

    protected final Item item;
    private final RenderItem itemRenderer;

    public GlintbladesRender(@Nonnull RenderManager renderManager, Item item, RenderItem itemRenderer) {
        super(renderManager);
        this.item = item;
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void doRender(@Nonnull T entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.enableRescaleNormal();

        // 应用实体大小
        float size = entity.getSize();
        GlStateManager.scale(size, size, size);
        GlStateManager.rotate(entityYaw, 0.0f, 1.0f, 0.0f);

        if (entity.isShooted()) {
            // 飞行状态：根据俯仰角旋转
            float pitch = Math.max(Math.min(80 - entity.rotationPitch * 1.25f, 140), 0);
            GlStateManager.rotate(pitch, 1.0f, 0.0f, 0.0f);
            GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f);
        } else {
            // 悬浮状态：持续旋转
            GlStateManager.rotate(entity.ticksExisted * 20, 0.0f, 1.0f, 0.0f);
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