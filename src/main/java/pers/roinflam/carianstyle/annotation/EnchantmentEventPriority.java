package pers.roinflam.carianstyle.annotation;

import net.minecraftforge.eventbus.api.EventPriority;

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
     */
    HIGHEST(EventPriority.HIGHEST),

    /**
     * 高优先级
     */
    HIGH(EventPriority.HIGH),

    /**
     * 普通优先级（默认）
     */
    NORMAL(EventPriority.NORMAL),

    /**
     * 低优先级
     */
    LOW(EventPriority.LOW),

    /**
     * 最低优先级（最后执行）
     */
    LOWEST(EventPriority.LOWEST);

    private final EventPriority forgeEventPriority;

    EnchantmentEventPriority(EventPriority forgeEventPriority) {
        this.forgeEventPriority = forgeEventPriority;
    }

    /**
     * 获取对应的Forge事件优先级
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