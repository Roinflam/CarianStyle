package pers.roinflam.carianstyle.base.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 药水效果基类
 * <p>
 * 所有模组药水效果的基础类，自动注册到事件总线
 * </p>
 */
public abstract class PotionBase extends MobEffect {

    /**
     * 构造函数
     *
     * @param category 效果类型（有益/有害）
     * @param liquidColor 液体颜色
     */
    protected PotionBase(@Nonnull MobEffectCategory category, int liquidColor) {
        // 1.20.1: Potion → MobEffect, isBadEffectIn → MobEffectCategory
        super(category, liquidColor);
        MinecraftForge.EVENT_BUS.register(this);
        // 注意：实际注册通过 DeferredRegister 完成
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        // 1.20.1: performEffect → applyEffectTick
        // 子类重写
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 1.20.1: isReady → isDurationEffectTick
        return duration % 20 == 0;
    }
}