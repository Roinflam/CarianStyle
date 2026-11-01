package pers.roinflam.carianstyle.base.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

import javax.annotation.Nonnull;

/**
 * 药水效果基类
 * <p>
 * 所有模组药水效果的基础类
 * </p>
 * <p>
 * 1.12.2 版：构造参数 {@code isBadEffect} 对应 1.20.1 的 {@code MobEffectCategory}（true = 有害）。
 * 注册名、翻译键由 {@code CarianStylePotion} 的注册器设置。
 * 子类照 1.20.1 覆写 {@link #applyEffectTick} / {@link #isDurationEffectTick}，
 * 这里把 1.12.2 的 {@code performEffect} / {@code isReady} 转过去，从 1.20.1 搬来的效果类不用改方法名。
 * </p>
 */
public abstract class PotionBase extends Potion {

    /**
     * 构造函数
     *
     * @param isBadEffect 是否有害（1.20.1 的 {@code MobEffectCategory.HARMFUL}）
     * @param liquidColor 液体颜色
     */
    protected PotionBase(boolean isBadEffect, int liquidColor) {
        super(isBadEffect, liquidColor);
    }

    /**
     * 每个生效刻调用（1.20.1 名），默认什么都不做
     *
     * @param entity    带着这个效果的生物
     * @param amplifier 效果等级（从 0 开始）
     */
    public void applyEffectTick(@Nonnull EntityLivingBase entity, int amplifier) {
        // 子类重写
    }

    /**
     * 这一刻要不要调 {@link #applyEffectTick}（1.20.1 名），默认每秒一次
     *
     * @param duration  剩余时长
     * @param amplifier 效果等级
     * @return 要不要生效
     */
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public final void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        applyEffectTick(entityLivingBaseIn, amplifier);
    }

    @Override
    public final boolean isReady(int duration, int amplifier) {
        return isDurationEffectTick(duration, amplifier);
    }
}
