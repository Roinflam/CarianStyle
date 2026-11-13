package pers.roinflam.carianstyle.bus;

/**
 * 可取消的事件。语义照 NeoForge / Forge 的总线：某个监听取消之后，排在它后面、{@code receiveCanceled = false}
 * 的监听全部跳过；{@code receiveCanceled = true} 的照常收到，并且能读到 {@link #isCanceled()} 为真，也能撤销取消。
 * <p>
 * 默认实现把状态记在 {@link Event} 的字段上（Fabric 侧、测试台直接 new 的字段型事件用这个）；
 * NeoForge / Forge 的直通包装覆写这两个方法，读写平台事件本身的取消状态，
 * 这样本模组和别的模组在同一个平台事件上取消 / 撤销取消，彼此都看得见。
 * </p>
 * <p>
 * 只能由 {@link Event} 的子类实现。
 * </p>
 */
public interface ICancellableEvent {

    default boolean isCanceled() {
        return ((Event) this).canceled;
    }

    default void setCanceled(boolean canceled) {
        ((Event) this).canceled = canceled;
    }
}
