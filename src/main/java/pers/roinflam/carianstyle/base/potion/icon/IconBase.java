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
 * 在物品栏和 HUD 上显示自定义图标的药水效果。
 * 1.12.2 版：图标是 {@code textures/effect/<id>.png} 一张 18×18 的图，由 Forge 的
 * {@code renderInventoryEffect} / {@code renderHUDEffect} 画（1.20.1 按注册名自动找 {@code mob_effect/<id>.png}）。
 * </p>
 */
public abstract class IconBase extends PotionBase {

    protected IconBase(boolean isBadEffect, int liquidColor) {
        super(isBadEffect, liquidColor);
    }

    /**
     * 效果图标
     *
     * @return 图标贴图
     */
    @Nonnull
    @SideOnly(Side.CLIENT)
    protected abstract ResourceLocation getIconTexture();

    @SideOnly(Side.CLIENT)
    @Override
    public void renderInventoryEffect(int x, int y, PotionEffect effect, Minecraft minecraft) {
        minecraft.getTextureManager().bindTexture(getIconTexture());
        Gui.drawModalRectWithCustomSizedTexture(x + 6, y + 7, 0, 0, 18, 18, 18, 18);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderHUDEffect(int x, int y, PotionEffect effect, Minecraft minecraft, float alpha) {
        minecraft.getTextureManager().bindTexture(getIconTexture());
        Gui.drawModalRectWithCustomSizedTexture(x + 3, y + 3, 0, 0, 18, 18, 18, 18);
    }
}
