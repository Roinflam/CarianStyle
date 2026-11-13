package pers.roinflam.carianstyle.bus;

import java.lang.ref.WeakReference;

/**
 * 直通包装对平台事件的引用。只弱引用：包装对象是 {@link PhasedForwarder} 表里的值，
 * 值要是强引用键，弱键就永远回收不了（见 {@link PhasedForwarder}）。
 * <p>
 * 分发期间平台事件由平台总线的调用栈强引用着，读写都正常；分发结束后平台事件可能已被回收，
 * 这时再读包装对象会抛 {@link IllegalStateException}。所以监听不能把事件对象留到分发之外再读
 * （存字段、放进集合、交给下一 tick 的任务）——要的值在监听里当场取出来。
 * </p>
 */
public final class WeakEventRef<P> {

    private final WeakReference<P> ref;

    public WeakEventRef(P platformEvent) {
        this.ref = new WeakReference<>(platformEvent);
    }

    public P get() {
        P p = ref.get();
        if (p == null) {
            throw new IllegalStateException("平台事件已经分发完并被回收，不能在分发结束后再读写事件对象");
        }
        return p;
    }
}
