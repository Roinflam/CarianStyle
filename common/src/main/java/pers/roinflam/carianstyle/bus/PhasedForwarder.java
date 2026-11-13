package pers.roinflam.carianstyle.bus;

import com.google.common.collect.MapMaker;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * NeoForge / Forge 转发层的公共部分：平台事件的每一档各调一次 {@link #phase}，把平台事件包成本模组的事件、
 * 只分发这一档。一个平台事件对象从最高档到最低档对应同一个包装对象——有的监听按事件对象身份缓存中间结果，
 * 五档里换了对象就对不上。
 * <p>
 * 包装对象表用弱键、按身份比较的并发表（Guava {@code MapMaker().weakKeys()}，弱键下按 {@code ==} 比较）：
 * </p>
 * <ul>
 *     <li>单人游戏里客户端线程和内置服务端线程会同时经过转发层，普通 {@code IdentityHashMap} 会偶发损坏。</li>
 *     <li>监听抛异常时平台总线不会再走到最低档，表项清不掉。靠弱键回收：平台事件没人引用之后表项自己消失，
 *     不会把实体、世界拴在表里。前提是包装对象只弱引用平台事件（见 {@link WeakEventRef}），否则值拴住键，弱键白设。</li>
 * </ul>
 * <p>
 * 最高档新建包装对象，同时取一份本模组总线的监听表快照，五档都按这份分发：分发途中登记 / 注销的监听
 * 从下一次事件才生效（平台总线也是整次分发用同一份表）。最高档时没有任何监听就不建包装、不进表，
 * 后面几档直接返回，平台事件照常往下走。
 * </p>
 * <p>
 * 同一个平台事件对象在自己的分发里被再发一次（重入）时，每次最高档压一帧、最低档弹一帧，
 * 内层用自己的包装对象，弹完外层后几档接着用外层的。
 * </p>
 *
 * @param <P> 平台事件类型
 * @param <W> 包装后的本模组事件类型（具体的包装类）
 */
public final class PhasedForwarder<P, W extends Event> {

    private final CarianEventBus bus;
    private final Class<W> wrapperType;
    private final Function<P, W> wrap;
    private final ConcurrentMap<P, Frame<W>> live = new MapMaker().weakKeys().makeMap();
    /**
     * 表里大约有几帧。为 0 时中间几档不用查表（没人监听时每个平台事件只剩最高档一次查表）。
     * 监听抛异常时最低档不会走到，帧靠弱键回收，这个数不会减回去，只是少了这条捷径，结果不受影响。
     */
    private final AtomicInteger frames = new AtomicInteger();

    public PhasedForwarder(CarianEventBus bus, Class<W> wrapperType, Function<P, W> wrap) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.wrapperType = Objects.requireNonNull(wrapperType, "wrapperType");
        this.wrap = Objects.requireNonNull(wrap, "wrap");
    }

    /** 平台总线在 {@code priority} 这一档调到转发监听时调用。 */
    public void phase(P platformEvent, EventPriority priority) {
        if (priority == EventPriority.HIGHEST) {
            CarianEventBus.Snapshot snap = bus.snapshot(wrapperType);
            Frame<W> outer = frames.get() == 0 ? null : live.get(platformEvent);
            if (snap.isEmpty() && outer == null) {
                return;
            }
            // 没有监听但外层还有帧（重入）时也压一个空帧，免得内层后几档拿外层的包装去分发
            Frame<W> f = new Frame<>(snap.isEmpty() ? null : wrap.apply(platformEvent), snap, outer);
            live.put(platformEvent, f);
            frames.incrementAndGet();
            if (f.wrapper != null) {
                bus.postPhase(f.wrapper, priority, snap);
            }
            return;
        }
        if (frames.get() == 0) {
            return;
        }
        Frame<W> f = live.get(platformEvent);
        if (f == null) {
            return;
        }
        try {
            if (f.wrapper != null) {
                bus.postPhase(f.wrapper, priority, f.snapshot);
            }
        } finally {
            if (priority == EventPriority.LOWEST) {
                if (f.prev == null) {
                    live.remove(platformEvent, f);
                } else {
                    live.replace(platformEvent, f, f.prev);
                }
                frames.decrementAndGet();
            }
        }
    }

    /** 表里现在还有几项（弱键已回收但没清理的可能也算在内）。只给测试看。 */
    public int liveSize() {
        return live.size();
    }

    /** 某个平台事件现在（最内层）对应的包装对象，没有返回 null。只给测试看。 */
    public W liveWrapper(P platformEvent) {
        Frame<W> f = live.get(platformEvent);
        return f == null ? null : f.wrapper;
    }

    /** 一次分发（最高档到最低档）的状态。不用 record：出栈按身份比较，record 的 equals 会把两个空帧当成同一个。 */
    private static final class Frame<W> {
        final W wrapper;
        final CarianEventBus.Snapshot snapshot;
        final Frame<W> prev;

        Frame(W wrapper, CarianEventBus.Snapshot snapshot, Frame<W> prev) {
            this.wrapper = wrapper;
            this.snapshot = snapshot;
            this.prev = prev;
        }
    }
}
