package pers.roinflam.kaliastyle.utils.util;

import com.google.common.base.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import pers.roinflam.kaliastyle.utils.Reference;

import java.lang.reflect.Field;
import java.util.List;

public class EntityUtil {

    public static int getFire(Entity entity) {
        try {
            Field field = Entity.class.getDeclaredField("fire");
            field.setAccessible(true);
            return (int) field.get(entity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            try {
                Field field = Entity.class.getDeclaredField("field_190534_ay");
                field.setAccessible(true);
                return (int) field.get(entity);
            } catch (NoSuchFieldException | IllegalAccessException e1) {
                e.printStackTrace();
            }
        }
        return -999;
    }

    public static List<Entity> getNearbyEntities(Entity entity, double radius, double height, Predicate<? super Entity> predicate) {
        return getNearbyEntities(entity.world, entity.getPosition(), radius, height, predicate);
    }

    public static List<Entity> getNearbyEntities(World world, BlockPos blockPos, double radius, double height, Predicate<? super Entity> predicate) {
        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(
                blockPos.getX() - radius,
                blockPos.getY() - height,
                blockPos.getZ() - radius,
                blockPos.getX() + radius,
                blockPos.getY() + height,
                blockPos.getZ() + radius
        );
        return world.getEntitiesWithinAABB(Entity.class, axisAlignedBB, predicate);
    }

    public static void renderFireInFirstPerson(String iconName) {
        Minecraft minecraft = Minecraft.getMinecraft();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.9F);
        GlStateManager.depthFunc(519);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        for (int i = 0; i < 2; i++) {
            GlStateManager.pushMatrix();
            TextureAtlasSprite sprite = minecraft.getTextureMapBlocks().getAtlasSprite(iconName);
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

    public static void renderEntityOnFire(Entity entity, double posX, double posY, double posZ, String iconName_0, String iconName_1) {
        Minecraft minecraft = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite blueFireLayer1 = textureMap.getAtlasSprite(iconName_0);
        TextureAtlasSprite blueFireLayer2 = textureMap.getAtlasSprite(iconName_1);
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
        builder.begin(7, DefaultVertexFormats.POSITION_TEX);

        while (height > 0.0F) {
            TextureAtlasSprite sprite = stage % 2 == 0 ? blueFireLayer1 : blueFireLayer2;
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
}
