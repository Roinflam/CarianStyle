package pers.roinflam.carianstyle.base.potion.hide;

import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.base.potion.PotionBase;

public abstract class HideBase extends PotionBase {

    protected HideBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
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
