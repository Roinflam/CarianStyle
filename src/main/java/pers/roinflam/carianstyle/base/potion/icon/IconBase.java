package pers.roinflam.carianstyle.base.potion.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.PotionBase;

import javax.annotation.Nonnull;

/**
 * 带图标的药水效果基类
 * <p>
 * 在物品栏和HUD上显示自定义图标的药水效果
 * </p>
 */
public abstract class IconBase extends PotionBase {

    protected IconBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    /**
     * 获取效果图标资源位置
     *
     * @return 图标资源位置
     */
    @Nonnull
    protected abstract ResourceLocation getResourceLocation();

    @SideOnly(Side.CLIENT)
    @Override
    public void renderInventoryEffect(int x, int y, PotionEffect effect, Minecraft minecraft) {
        ResourceLocation resourceLocation = getResourceLocation();
        minecraft.getTextureManager().bindTexture(resourceLocation);
        Gui.drawModalRectWithCustomSizedTexture(x + 6, y + 7, 0, 0, 18, 18, 18, 18);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderHUDEffect(int x, int y, PotionEffect effect, Minecraft minecraft, float alpha) {
        ResourceLocation resourceLocation = getResourceLocation();
        minecraft.getTextureManager().bindTexture(resourceLocation);
        Gui.drawModalRectWithCustomSizedTexture(x + 3, y + 3, 0, 0, 18, 18, 18, 18);
    }
}