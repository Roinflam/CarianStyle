package pers.roinflam.carianstyle.bus;

/**
 * 模组自带事件总线上所有事件的基类。
 * <p>
 * 事件类全是模组自己的，NeoForge / Forge / Fabric 三个平台共用同一套：平台模块负责把平台事件变成这里的事件
 * （NeoForge / Forge 是直通包装，Fabric 是字段型实现），监听代码只认这里的类，换平台不用改。
 * </p>
 * <p>
 * 取消状态的默认存放处也在这里（{@link #canceled}），只有实现了 {@link ICancellableEvent} 的事件才会用到；
 * 包装平台事件的子类覆写 {@code isCanceled / setCanceled}，把取消状态直接落在平台事件上。
 * </p>
 */
public abstract class Event {

    /** {@link ICancellableEvent} 默认实现用的取消标记；同包可见，只给接口的默认方法读写。 */
    boolean canceled;

    protected Event() {
    }
}
