package pers.roinflam.carianstyle.base.potion.flame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.NetworkBase;

import javax.annotation.Nonnull;

/**
 * 火焰效果药水基类
 * <p>
 * 为实体渲染自定义颜色的火焰效果
 * 子类需要实现getLevelOneName()和getLevelTwoName()返回火焰纹理名称
 * </p>
 */
public abstract class FlameBase extends NetworkBase {

    protected FlameBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 获取火焰纹理层1名称
     */
    @Nonnull
    protected abstract String getLevelOneName();

    /**
     * 获取火焰纹理层2名称
     */
    @Nonnull
    protected abstract String getLevelTwoName();

    /**
     * 渲染第一人称火焰效果
     */
    @SideOnly(Side.CLIENT)
    public void renderFireInFirstPerson(@Nonnull String iconName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();

        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.9F);
        GlStateManager.depthFunc(519);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        TextureAtlasSprite sprite = minecraft.getTextureMapBlocks().getAtlasSprite(iconName);
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();

        for (int i = 0; i < 2; i++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
            GlStateManager.rotate((float) (i * 2 - 1) * 10.0F, 0.0F, 1.0F, 0.0F);

            builder.begin(7, DefaultVertexFormats.POSITION_TEX);
            builder.pos(-0.5D, -0.5D, -0.5D).tex(maxU, maxV).endVertex();
            builder.pos(0.5D, -0.5D, -0.5D).tex(minU, maxV).endVertex();
            builder.pos(0.5D, 0.5D, -0.5D).tex(minU, minV).endVertex();
            builder.pos(-0.5D, 0.5D, -0.5D).tex(maxU, minV).endVertex();
            tessellator.draw();

            GlStateManager.popMatrix();
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
        GlStateManager.enableLighting();
        GlStateManager.enableAlpha();
    }

    /**
     * 渲染实体身上的火焰效果
     */
    @SideOnly(Side.CLIENT)
    public void renderEntityOnFire(@Nonnull Entity entity, double posX, double posY, double posZ,
                                   @Nonnull String iconName0, @Nonnull String iconName1) {
        Minecraft minecraft = Minecraft.getMinecraft();
        TextureMap textureMap = minecraft.getTextureMapBlocks();

        TextureAtlasSprite fireLayer0 = textureMap.getAtlasSprite(iconName0);
        TextureAtlasSprite fireLayer1 = textureMap.getAtlasSprite(iconName1);

        GlStateManager.disableLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) posX, (float) posY, (float) posZ);

        float scale = entity.width * 1.4F;
        GlStateManager.scale(scale, scale, scale);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();

        float renderX = 0.5F;
        float height = entity.height / scale;
        float renderY = (float) (entity.posY - entity.getEntityBoundingBox().minY);

        GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, -0.3F + (float) ((int) height) * 0.02F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        float renderZ = 0.0F;
        int stage = 0;

        minecraft.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        builder.begin(7, DefaultVertexFormats.POSITION_TEX);

        while (height > 0.0F) {
            TextureAtlasSprite sprite = (stage % 2 == 0) ? fireLayer0 : fireLayer1;

            float minU = sprite.getMinU();
            float minV = sprite.getMinV();
            float maxU = sprite.getMaxU();
            float maxV = sprite.getMaxV();

            // 交替翻转纹理
            if (stage / 2 % 2 == 0) {
                float temp = maxU;
                maxU = minU;
                minU = temp;
            }

            builder.pos(renderX, -renderY, renderZ).tex(maxU, maxV).endVertex();
            builder.pos(-renderX, -renderY, renderZ).tex(minU, maxV).endVertex();
            builder.pos(-renderX, 1.4F - renderY, renderZ).tex(minU, minV).endVertex();
            builder.pos(renderX, 1.4F - renderY, renderZ).tex(maxU, minV).endVertex();

            height -= 0.45F;
            renderY -= 0.45F;
            renderX *= 0.9F;
            renderZ += 0.03F;
            stage++;
        }

        tessellator.draw();
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
    }

    /**
     * 实体渲染后事件：渲染火焰效果
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderLiving(@Nonnull RenderLivingEvent.Specials.Post evt) {
        EntityLivingBase entity = evt.getEntity();
        if (!isAction(entity.getEntityId())) {
            return;
        }

        float partialTicks = evt.getPartialRenderTick();
        double posX = evt.getX() + (entity.posX - entity.lastTickPosX) * partialTicks;
        double posY = evt.getY() + (entity.posY - entity.lastTickPosY + 1) * partialTicks;
        double posZ = evt.getZ() + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        renderEntityOnFire(entity, posX, posY, posZ, getLevelOneName(), getLevelTwoName());
    }

    /**
     * 手部渲染事件：渲染第一人称火焰
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderSpecificHand(RenderSpecificHandEvent evt) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null && isAction(player.getEntityId())) {
            renderFireInFirstPerson(getLevelTwoName());
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean shouldRender(PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderInvText(PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderHUD(PotionEffect effect) {
        return false;
    }
}