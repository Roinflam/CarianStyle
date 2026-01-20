package pers.roinflam.carianstyle.dynamicattr;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态属性定义
 * 支持属性修改器、Tick回调和自定义事件处理器
 */
public class DynamicAttribute {
    private final String registryName;
    private final Map<Attribute, ModifierConfig> modifierConfigs = new HashMap<>();
    private EffectCallback onTickCallback;
    private int tickInterval = 20;

    // 事件处理器工厂 - 用于创建事件监听器实例
    private EventHandlerFactory eventHandlerFactory;

    // ========== 新增：生命周期回调 ==========
    private LifecycleCallback onAppliedCallback;
    private LifecycleCallback onRemovedCallback;

    /**
     * 构造动态属性
     *
     * @param registryName 注册名,用于唯一标识此属性
     * @throws IllegalArgumentException 如果注册名为空
     */
    public DynamicAttribute(@Nonnull String registryName) {
        if (registryName == null || registryName.trim().isEmpty()) {
            throw new IllegalArgumentException("注册名不能为空");
        }
        this.registryName = registryName;
    }

    // ========== 属性修改器 ==========

    /**
     * 添加属性修改器(固定值)
     *
     * @param attribute 目标属性
     * @param baseValue 基础值,会根据amplifier放大: baseValue * (amplifier + 1)
     * @param operation 运算方式
     * @return this,支持链式调用
     */
    public DynamicAttribute addModifier(Attribute attribute, double baseValue, AttributeModifier.Operation operation) {
        modifierConfigs.put(attribute, new ModifierConfig(baseValue, operation, null));
        return this;
    }

    /**
     * 添加属性修改器(动态计算)
     *
     * @param attribute 目标属性
     * @param operation 运算方式
     * @param calculator 自定义计算器,根据amplifier计算最终值
     * @return this,支持链式调用
     */
    public DynamicAttribute addModifier(Attribute attribute, AttributeModifier.Operation operation,
                                        @Nonnull ValueCalculator calculator) {
        modifierConfigs.put(attribute, new ModifierConfig(0, operation, calculator));
        return this;
    }

    // ========== Tick回调 ==========

    /**
     * 设置Tick回调函数
     *
     * @param callback 回调函数,会按tickInterval间隔触发
     * @return this,支持链式调用
     */
    public DynamicAttribute onTick(@Nonnull EffectCallback callback) {
        this.onTickCallback = callback;
        return this;
    }

    /**
     * 设置Tick触发间隔
     *
     * @param ticks 间隔tick数,必须≥1
     * @return this,支持链式调用
     */
    public DynamicAttribute setTickInterval(int ticks) {
        this.tickInterval = Math.max(1, ticks);
        return this;
    }

    // ========== 生命周期回调 ==========

    /**
     * 设置应用时回调
     * 当此属性被应用到实体时触发
     *
     * @param callback 回调函数
     * @return this,支持链式调用
     */
    public DynamicAttribute onApplied(@Nonnull LifecycleCallback callback) {
        this.onAppliedCallback = callback;
        return this;
    }

    /**
     * 设置移除时回调
     * 当此属性从实体移除时触发
     *
     * @param callback 回调函数
     * @return this,支持链式调用
     */
    public DynamicAttribute onRemoved(@Nonnull LifecycleCallback callback) {
        this.onRemovedCallback = callback;
        return this;
    }

    /**
     * 触发应用回调
     * 由 DynamicAttributeManager 调用
     *
     * @param entity 目标实体
     */
    public void triggerOnApplied(@Nonnull LivingEntity entity) {
        if (onAppliedCallback != null) {
            try {
                onAppliedCallback.accept(entity, this);
            } catch (Exception e) {
                System.err.println("属性应用回调异常: " + registryName);
                e.printStackTrace();
            }
        }
    }

    /**
     * 触发移除回调
     * 由 DynamicAttributeManager 调用
     *
     * @param entity 目标实体
     */
    public void triggerOnRemoved(@Nonnull LivingEntity entity) {
        if (onRemovedCallback != null) {
            try {
                onRemovedCallback.accept(entity, this);
            } catch (Exception e) {
                System.err.println("属性移除回调异常: " + registryName);
                e.printStackTrace();
            }
        }
    }

    // ========== 事件处理器 ==========

    /**
     * 设置事件处理器工厂
     * 用于创建监听各种Forge事件的处理器对象
     *
     * 使用示例:
     * <pre>
     * new DynamicAttribute("virus")
     *     .withEventHandler(entity -> new Object() {
     *         @SubscribeEvent
     *         public void onHurt(LivingHurtEvent event) {
     *             if (event.getEntity() != entity) return;
     *             event.setAmount(event.getAmount() * 1.25f);
     *         }
     *     });
     * </pre>
     *
     * @param factory 工厂函数,接收LivingEntity参数,返回包含@SubscribeEvent方法的对象
     * @return this,支持链式调用
     */
    public DynamicAttribute withEventHandler(@Nonnull EventHandlerFactory factory) {
        this.eventHandlerFactory = factory;
        return this;
    }

    /**
     * 创建事件处理器实例
     *
     * @param entity 关联的实体
     * @return 事件处理器对象,如果没有设置工厂则返回null
     */
    @Nullable
    public Object createEventHandler(@Nonnull LivingEntity entity) {
        if (eventHandlerFactory == null) return null;
        return eventHandlerFactory.create(entity);
    }

    /**
     * 检查是否有事件处理器
     *
     * @return true表示设置了事件处理器工厂
     */
    public boolean hasEventHandler() {
        return eventHandlerFactory != null;
    }

    // ========== 实例创建 ==========

    /**
     * 创建动态属性实例
     *
     * @param duration 持续时间(tick)
     * @param amplifier 等级(0开始)
     * @return 新的实例
     */
    public DynamicAttributeInstance createInstance(int duration, int amplifier) {
        return new DynamicAttributeInstance(this, duration, amplifier);
    }

    /**
     * 创建0级动态属性实例
     *
     * @param duration 持续时间(tick)
     * @return 新的实例
     */
    public DynamicAttributeInstance createInstance(int duration) {
        return createInstance(duration, 0);
    }

    // ========== Getter方法 ==========

    public String getRegistryName() {
        return registryName;
    }

    public Map<Attribute, ModifierConfig> getModifierConfigs() {
        return modifierConfigs;
    }

    public int getTickInterval() {
        return tickInterval;
    }

    @Nullable
    public EffectCallback getOnTickCallback() {
        return onTickCallback;
    }

    // ========== 内部类和接口 ==========

    /**
     * 生命周期回调接口
     */
    @FunctionalInterface
    public interface LifecycleCallback {
        /**
         * 回调触发
         *
         * @param entity 目标实体
         * @param attribute 触发的属性
         */
        void accept(LivingEntity entity, DynamicAttribute attribute);
    }

    /**
     * 事件处理器工厂接口
     */
    @FunctionalInterface
    public interface EventHandlerFactory {
        /**
         * 创建事件处理器
         *
         * @param entity 关联的实体
         * @return 包含@SubscribeEvent方法的事件处理器对象
         */
        Object create(LivingEntity entity);
    }

    /**
     * 属性修改器配置
     */
    public static class ModifierConfig {
        public final double baseValue;
        public final AttributeModifier.Operation operation;
        public final ValueCalculator calculator;

        public ModifierConfig(double baseValue, AttributeModifier.Operation operation,
                              @Nullable ValueCalculator calculator) {
            this.baseValue = baseValue;
            this.operation = operation;
            this.calculator = calculator;
        }

        /**
         * 根据amplifier计算最终修改值
         *
         * @param amplifier 等级
         * @return 最终修改值
         */
        public double calculate(int amplifier) {
            if (calculator != null) {
                return calculator.calculate(amplifier);
            }
            return baseValue * (amplifier + 1);
        }
    }

    /**
     * 值计算器接口
     */
    @FunctionalInterface
    public interface ValueCalculator {
        /**
         * 根据amplifier计算修改值
         *
         * @param amplifier 等级
         * @return 修改值
         */
        double calculate(int amplifier);
    }

    /**
     * Tick回调接口
     */
    @FunctionalInterface
    public interface EffectCallback {
        /**
         * Tick触发时调用
         *
         * @param context 效果上下文
         */
        void accept(EffectContext context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicAttribute)) return false;
        return registryName.equals(((DynamicAttribute) o).registryName);
    }

    @Override
    public int hashCode() {
        return registryName.hashCode();
    }
}