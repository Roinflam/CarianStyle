package pers.roinflam.carianstyle.annotation;

import net.minecraftforge.fml.common.eventhandler.EventPriority;

/**
 * 附魔事件优先级枚举
 * <p>
 * 定义附魔事件处理器的执行优先级顺序
 * 执行顺序：HIGHEST → HIGH → NORMAL → LOW → LOWEST
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public enum EnchantmentEventPriority {
    /**
     * 最高优先级（最早执行）
     * <p>
     * 使用场景：
     * - 完全免疫判定
     * - 早期伤害拦截
     * - 诅咒类效果
     * - 需要在所有其他修正前执行的逻辑
     * </p>
     * <p>
     * 示例附魔：
     * - 死亡触发的复活效果
     * - 完全格挡判定
     * </p>
     */
    HIGHEST(EventPriority.HIGHEST),

    /**
     * 高优先级
     * <p>
     * 使用场景：
     * - 特殊的优先增伤效果
     * - 需要在常规逻辑前执行但不是最早的判定
     * </p>
     * <p>
     * 注意：此优先级在当前代码库中使用较少，主要为特殊需求预留
     * </p>
     */
    HIGH(EventPriority.HIGH),

    /**
     * 普通优先级（默认）
     * <p>
     * 使用场景：
     * - 常规伤害修正
     * - 大多数附魔效果
     * - 标准的游戏逻辑处理
     * </p>
     * <p>
     * 这是最常用的优先级，大部分附魔应该使用此优先级
     * </p>
     */
    NORMAL(EventPriority.NORMAL),

    /**
     * 低优先级
     * <p>
     * 使用场景：
     * - 基于其他修正后数值的计算
     * - 减伤计算（在增伤后执行）
     * - 需要获取其他附魔修正后的值
     * </p>
     * <p>
     * 示例附魔：
     * - 护甲减免相关的附魔
     * - 基于最终伤害值的反馈效果
     * </p>
     */
    LOW(EventPriority.LOW),

    /**
     * 最低优先级（最后执行）
     * <p>
     * 使用场景：
     * - 持续伤害效果
     * - 后处理逻辑
     * - 统计和记录
     * - 不再修改数值的效果结算
     * </p>
     * <p>
     * 注意：在此优先级中，通常不应该再修改伤害值，
     * 因为这是最后的处理阶段，主要用于触发额外效果
     * </p>
     * <p>
     * 示例附魔：
     * - 死亡之刃的持续伤害
     * - 各类DOT（持续伤害）效果
     * </p>
     */
    LOWEST(EventPriority.LOWEST);

    private final EventPriority forgeEventPriority;

    EnchantmentEventPriority(EventPriority forgeEventPriority) {
        this.forgeEventPriority = forgeEventPriority;
    }

    /**
     * 获取对应的Forge事件优先级
     * <p>
     * 用于在实际注册事件监听器时转换为Forge能识别的优先级
     * </p>
     *
     * @return Forge事件优先级
     */
    public EventPriority getForgeEventPriority() {
        return forgeEventPriority;
    }

    /**
     * 从Forge事件优先级转换为附魔事件优先级
     *
     * @param forgeEventPriority Forge事件优先级
     * @return 对应的附魔事件优先级
     */
    public static EnchantmentEventPriority fromForgeEventPriority(EventPriority forgeEventPriority) {
        for (EnchantmentEventPriority priority : values()) {
            if (priority.forgeEventPriority == forgeEventPriority) {
                return priority;
            }
        }
        return NORMAL;
    }
}