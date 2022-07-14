package pers.roinflam.carianstyle.base.potion.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.PotionBase;

public abstract class IconBase extends PotionBase {

    protected IconBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    protected abstract ResourceLocation getResourceLocation();

    @SideOnly(Side.CLIENT)
    @Override
    public void renderInventoryEffect(int x, int y, PotionEffect effect, Minecraft minecraft) {
        ResourceLocation resourceLocation = getResourceLocation();
        if (resourceLocation != null) {
            minecraft.getTextureManager().bindTexture(resourceLocation);
            Gui.drawModalRectWithCustomSizedTexture(x + 6, y + 7, 0, 0, 18, 18, 18, 18);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderHUDEffect(int x, int y, PotionEffect effect, Minecraft minecraft, float alpha) {
        ResourceLocation resourceLocation = getResourceLocation();
        if (resourceLocation != null) {
            minecraft.getTextureManager().bindTexture(resourceLocation);
            Gui.drawModalRectWithCustomSizedTexture(x + 3, y + 3, 0, 0, 18, 18, 18, 18);
        }
    }
}
