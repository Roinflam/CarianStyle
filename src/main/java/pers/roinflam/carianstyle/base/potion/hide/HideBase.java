package pers.roinflam.carianstyle.base.potion.hide;

import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.base.potion.PotionBase;

/**
 * 隐藏药水效果基类
 * <p>
 * 不显示在物品栏和HUD上的药水效果
 * 用于内部机制效果，不需要玩家看到
 * </p>
 */
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