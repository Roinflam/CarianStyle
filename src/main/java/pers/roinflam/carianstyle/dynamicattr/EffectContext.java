package pers.roinflam.carianstyle.dynamicattr;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nonnull;

/**
 * 效果上下文
 * 提供Tick回调时的各种信息
 */
public class EffectContext {
    private final LivingEntity entity;
    private final DynamicAttributeInstance instance;
    private final int totalDuration;
    private final int remainingDuration;
    private final int elapsedDuration;
    private final int totalTicks;
    private final int elapsedTicks;

    public EffectContext(@Nonnull LivingEntity entity,
                         @Nonnull DynamicAttributeInstance instance,
                         int totalTicks,
                         int elapsedTicks) {
        this.entity = entity;
        this.instance = instance;
        this.totalDuration = instance.getInitialDuration();
        this.remainingDuration = instance.getDuration();
        this.elapsedDuration = totalDuration - remainingDuration;
        this.totalTicks = totalTicks;
        this.elapsedTicks = elapsedTicks;
    }

    @Nonnull
    public LivingEntity getEntity() {
        return entity;
    }

    @Nonnull
    public DynamicAttributeInstance getInstance() {
        return instance;
    }

    public int getAmplifier() {
        return instance.getAmplifier();
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public int getRemainingDuration() {
        return remainingDuration;
    }

    public int getElapsedDuration() {
        return elapsedDuration;
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    /**
     * 获取进度(0.0-1.0)
     */
    public double getProgress() {
        if (totalDuration <= 0) return 1.0;
        return (double) elapsedDuration / totalDuration;
    }

    /**
     * 获取剩余进度(1.0-0.0)
     */
    public double getRemainingProgress() {
        return 1.0 - getProgress();
    }

    /**
     * 是否处于开始阶段(前20%)
     */
    public boolean isStartPhase() {
        return getProgress() < 0.2;
    }

    /**
     * 是否处于结束阶段(后20%)
     */
    public boolean isEndPhase() {
        return getProgress() >= 0.8;
    }

    /**
     * 剩余时间是否少于指定tick数
     */
    public boolean remainingLessThan(int ticks) {
        return remainingDuration < ticks;
    }

    /**
     * 已过时间是否超过指定tick数
     */
    public boolean elapsedMoreThan(int ticks) {
        return elapsedDuration > ticks;
    }
}