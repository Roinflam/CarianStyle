package pers.roinflam.carianstyle.base.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import javax.annotation.Nonnull;

/**
 * PORT-STUB(B2a)
 *
 * 药水效果基类
 * <p>
 * 所有模组药水效果的基础类
 * </p>
 */
@Deprecated
public abstract class PotionBase extends MobEffect {
    protected PotionBase(@Nonnull MobEffectCategory category, int liquidColor) {
        super(category, liquidColor);
    }
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
}

