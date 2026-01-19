package pers.roinflam.carianstyle.base.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nonnull;

/**
 * 药水效果基类
 * <p>
 * 所有模组药水效果的基础类
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
        super(category, liquidColor);
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        // 子类重写
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}