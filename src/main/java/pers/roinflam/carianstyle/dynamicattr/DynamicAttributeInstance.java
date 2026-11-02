package pers.roinflam.carianstyle.dynamicattr;

import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 动态属性实例
 * 表示应用在某个实体上的具体动态属性
 */
public class DynamicAttributeInstance {
    private final DynamicAttribute attribute;
    private final int initialDuration;
    private int duration;
    private final int amplifier;
    private int tickCounter = 0;
    private int totalTicksTriggered = 0;

    // 事件处理器实例
    private Object eventHandler;

    /**
     * 构造动态属性实例
     *
     * @param attribute 属性定义
     * @param duration 持续时间(tick)
     * @param amplifier 等级(0开始)
     */
    public DynamicAttributeInstance(@Nonnull DynamicAttribute attribute, int duration, int amplifier) {
        this.attribute = attribute;
        this.initialDuration = duration;
        this.duration = duration;
        this.amplifier = Math.max(0, amplifier);
    }

    /**
     * 时间流逝
     *
     * @param ticks 流逝的tick数
     * @return true表示已过期
     */
    public boolean tick(int ticks) {
        duration -= ticks;
        tickCounter += ticks;
        return duration <= 0;
    }

    /**
     * 检查是否应该触发Tick回调
     *
     * @return true表示应该触发
     */
    public boolean shouldTriggerTick() {
        int interval = attribute.getTickInterval();
        if (tickCounter >= interval) {
            tickCounter -= interval;
            totalTicksTriggered++;
            return true;
        }
        return false;
    }

    /**
     * 计算总共会触发多少次Tick
     *
     * @return 总触发次数
     */
    public int calculateTotalTicks() {
        return initialDuration / attribute.getTickInterval();
    }

    /**
     * 刷新持续时间
     *
     * @param newDuration 新的持续时间
     */
    public void refresh(int newDuration) {
        this.duration = newDuration;
    }

    /**
     * 判断是否应该覆盖另一个实例
     * 规则: 等级更高,或等级相同但时间更长
     *
     * @param other 另一个实例
     * @return true表示应该覆盖
     */
    public boolean shouldOverride(DynamicAttributeInstance other) {
        return this.attribute.equals(other.attribute) &&
                (this.amplifier > other.amplifier ||
                        (this.amplifier == other.amplifier && this.duration > other.duration));
    }

    // ========== 事件处理器管理 ==========

    /**
     * 设置并注册事件处理器到Forge事件总线
     *
     * @param handler 事件处理器对象(包含@SubscribeEvent方法)
     */
    public void setEventHandler(@Nullable Object handler) {
        if (handler != null) {
            this.eventHandler = handler;
            MinecraftForge.EVENT_BUS.register(handler);
        }
    }

    /**
     * 注销事件处理器
     */
    public void unregisterEventHandler() {
        if (eventHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(eventHandler);
            eventHandler = null;
        }
    }

    @Nullable
    public Object getEventHandler() {
        return eventHandler;
    }

    // ========== Getter方法 ==========

    public DynamicAttribute getAttribute() {
        return attribute;
    }

    public int getInitialDuration() {
        return initialDuration;
    }

    public int getDuration() {
        return duration;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public int getTotalTicksTriggered() {
        return totalTicksTriggered;
    }
}