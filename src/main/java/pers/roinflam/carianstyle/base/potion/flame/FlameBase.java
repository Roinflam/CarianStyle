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

public abstract class FlameBase extends NetworkBase {

    protected FlameBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SideOnly(Side.CLIENT)
    public void renderFireInFirstPerson(@Nonnull String iconName) {
        @Nonnull Minecraft minecraft = Minecraft.getMinecraft();

        @Nonnull Tessellator tessellator = Tessellator.getInstance();
        @Nonnull BufferBuilder builder = tessellator.getBuffer();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.9F);
        GlStateManager.depthFunc(519);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        for (int i = 0; i < 2; i++) {
            GlStateManager.pushMatrix();
            @Nonnull TextureAtlasSprite sprite = minecraft.getTextureMapBlocks().getAtlasSprite(iconName);
            minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            float minU = sprite.getMinU();
            float maxU = sprite.getMaxU();
            float minV = sprite.getMinV();
            float maxV = sprite.getMaxV();
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

    @SideOnly(Side.CLIENT)
    public void renderEntityOnFire(@Nonnull Entity entity, double posX, double posY, double posZ, @Nonnull String iconName_0, @Nonnull String iconName_1) {
        @Nonnull Minecraft minecraft = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        @Nonnull TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        @Nonnull TextureAtlasSprite blueFireLayer1 = textureMap.getAtlasSprite(iconName_0);
        @Nonnull TextureAtlasSprite blueFireLayer2 = textureMap.getAtlasSprite(iconName_1);
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) posX, (float) posY, (float) posZ);
        float scale = entity.width * 1.4F;
        GlStateManager.scale(scale, scale, scale);
        @Nonnull Tessellator tessellator = Tessellator.getInstance();
        @Nonnull BufferBuilder builder = tessellator.getBuffer();
        float renderX = 0.5F;
        float height = entity.height / scale;
        float renderY = (float) (entity.posY - entity.getEntityBoundingBox().minY);
        GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(0.0F, 0.0F, -0.3F + (float) ((int) height) * 0.02F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        float renderZ = 0.0F;
        int stage = 0;
        builder.begin(7, DefaultVertexFormats.POSITION_TEX);

        while (height > 0.0F) {
            @Nonnull TextureAtlasSprite sprite = stage % 2 == 0 ? blueFireLayer1 : blueFireLayer2;
            minecraft.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            float minU = sprite.getMinU();
            float minV = sprite.getMinV();
            float maxU = sprite.getMaxU();
            float maxV = sprite.getMaxV();

            if (stage / 2 % 2 == 0) {
                float tempU = maxU;
                maxU = minU;
                minU = tempU;
            }

            builder.pos((renderX - 0.0F), (0.0F - renderY), renderZ).tex(maxU, maxV).endVertex();
            builder.pos((-renderX - 0.0F), (0.0F - renderY), renderZ).tex(minU, maxV).endVertex();
            builder.pos((-renderX - 0.0F), (1.4F - renderY), renderZ).tex(minU, minV).endVertex();
            builder.pos((renderX - 0.0F), (1.4F - renderY), renderZ).tex(maxU, minV).endVertex();
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

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderLiving(@Nonnull RenderLivingEvent.Specials.Post evt) {
        EntityLivingBase entityLivingBase = evt.getEntity();
        if (isAction(entityLivingBase.getEntityId())) {
            float partialTicks = evt.getPartialRenderTick();
            double posX = evt.getX() + (entityLivingBase.posX - entityLivingBase.lastTickPosX) * (double) partialTicks;
            double posY = evt.getY() + (entityLivingBase.posY - entityLivingBase.lastTickPosY + 1) * (double) partialTicks;
            double posZ = evt.getZ() + (entityLivingBase.posZ - entityLivingBase.lastTickPosZ) * (double) partialTicks;
            renderEntityOnFire(entityLivingBase, posX, posY, posZ, getLevelOneName(), getLevelTwoName());
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderSpecificHand(RenderSpecificHandEvent evt) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (isAction(player.getEntityId())) {
            renderFireInFirstPerson(getLevelTwoName());
        }
    }

    protected abstract String getLevelOneName();

    protected abstract String getLevelTwoName();

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
