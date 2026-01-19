package pers.roinflam.carianstyle.base.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
import pers.roinflam.carianstyle.base.potion.PotionBase;

import javax.annotation.Nonnull;

/**
 * 隐藏药水效果基类
 * <p>
 * 不显示在物品栏和HUD上的药水效果
 * 用于内部机制效果，不需要玩家看到
 * </p>
 */
public abstract class HideBase extends PotionBase {

    protected HideBase(@Nonnull MobEffectCategory category, int liquidColor) {
        super(category, liquidColor);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    // 注意：1.20.1 中移除了 shouldRender 等方法
    // 隐藏效果需要通过其他方式实现，例如在客户端事件中过滤
}