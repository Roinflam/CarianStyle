package pers.roinflam.carianstyle.bus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NeoForge / Forge 式的分段转发：拿另一条总线扮平台总线，上面挂「别的模组」的监听和五档转发监听
 * （和平台模块的转发层一样都是 receiveCanceled = true），检查先后、改值、取消、包装对象身份、弱引用、并发。
 */
class PhasedForwarderTest {

    static final List<String> LOG = new ArrayList<>();

    @BeforeEach
    void clear() {
        LOG.clear();
    }

    // ---------- 本模组的事件和平台事件 ----------

    public abstract static class HealEvent extends Event implements ICancellableEvent {
        public abstract float getAmount();

        public abstract void setAmount(float a);
    }

    /** 假装的平台事件（在平台总线上发）。 */
    public static class PlatformHeal extends Event implements ICancellableEvent {
        float amount;

        PlatformHeal(float a) {
            amount = a;
        }
    }

    /** equals 永远为真的平台事件：转发表必须按身份比较，不能按 equals。 */
    public static final class EqualPlatformHeal extends PlatformHeal {
        EqualPlatformHeal() {
            super(0);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EqualPlatformHeal;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    /** 直通包装，和平台模块里的写法一样：只弱引用平台事件，读写、取消都落在平台事件上。 */
    public static final class Wrapped extends HealEvent {
        final WeakEventRef<PlatformHeal> e;

        Wrapped(PlatformHeal p) {
            e = new WeakEventRef<>(p);
        }

        @Override
        public float getAmount() {
            return e.get().amount;
        }

        @Override
        public void setAmount(float a) {
            e.get().amount = a;
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean c) {
            e.get().setCanceled(c);
        }
    }

    // ---------- 本模组监听 ----------

    public static class ModListeners {
        static int highestId;
        static boolean sameObject = true;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void first(HealEvent e) {
            highestId = System.identityHashCode(e);
            LOG.add("mod.HIGHEST amount=" + e.getAmount());
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void high(HealEvent e) {
            LOG.add("mod.HIGH");
        }

        @SubscribeEvent
        public static void normal(HealEvent e) {
            LOG.add("mod.NORMAL amount=" + e.getAmount());
            e.setAmount(e.getAmount() * 2);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void low(HealEvent e) {
            sameObject &= System.identityHashCode(e) == highestId;
            LOG.add("mod.LOW amount=" + e.getAmount() + " same=" + (System.identityHashCode(e) == highestId));
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void cleanup(HealEvent e) {
            LOG.add("mod.LOWEST(rc) canceled=" + e.isCanceled());
        }
    }

    // ---------- 别的模组（挂在平台总线上） ----------

    public static class OtherMod {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void high(PlatformHeal e) {
            LOG.add("other.HIGH amount=" + e.amount);
            e.amount += 1;
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void low(PlatformHeal e) {
            LOG.add("other.LOW amount=" + e.amount);
        }
    }

    public static class OtherCanceler {
        @SubscribeEvent(priority = EventPriority.NORMAL)
        public static void cancel(PlatformHeal e) {
            LOG.add("other.NORMAL cancel");
            e.setCanceled(true);
        }
    }

    /** 五档转发监听挂到平台总线上，和平台模块转发层的做法一样。 */
    static PhasedForwarder<PlatformHeal, Wrapped> bridge(CarianEventBus platform, CarianEventBus mod) {
        PhasedForwarder<PlatformHeal, Wrapped> f = new PhasedForwarder<>(mod, Wrapped.class, Wrapped::new);
        for (EventPriority p : EventPriority.values()) {
            platform.on(PlatformHeal.class, p, true, e -> f.phase(e, p));
        }
        return f;
    }

    // ---------- 小样原有的断言 ----------

    @Test
    void interleavesWithOtherModsAndKeepsOneWrapper() {
        CarianEventBus mod = new CarianEventBus();
        mod.register(ModListeners.class);
        CarianEventBus platform = new CarianEventBus();
        platform.register(OtherMod.class);
        PhasedForwarder<PlatformHeal, Wrapped> f = bridge(platform, mod);
        PlatformHeal pe = new PlatformHeal(3);
        platform.post(pe);
        assertEquals(List.of("mod.HIGHEST amount=3.0", "other.HIGH amount=3.0", "mod.HIGH", "mod.NORMAL amount=4.0",
                "other.LOW amount=8.0", "mod.LOW amount=8.0 same=true", "mod.LOWEST(rc) canceled=false"), LOG);
        assertEquals(8.0f, pe.amount, "结果写在平台事件上");
        assertEquals(0, f.liveSize(), "最低档之后表里清掉");
    }

    @Test
    void otherModCancelSkipsLaterModListeners() {
        CarianEventBus mod = new CarianEventBus();
        mod.register(ModListeners.class);
        CarianEventBus platform = new CarianEventBus();
        platform.register(OtherMod.class);
        bridge(platform, mod);
        platform.register(OtherCanceler.class); // 登记在转发之后，同档里排在本模组后面
        platform.post(new PlatformHeal(3));
        assertEquals(List.of("mod.HIGHEST amount=3.0", "other.HIGH amount=3.0", "mod.HIGH", "mod.NORMAL amount=4.0",
                "other.NORMAL cancel", "mod.LOWEST(rc) canceled=true"), LOG);
    }

    // ---------- 加项 ----------

    @Test
    void modCancelIsVisibleToOtherMods() {
        CarianEventBus mod = new CarianEventBus();
        mod.on(HealEvent.class, EventPriority.HIGHEST, false, e -> e.setCanceled(true));
        CarianEventBus platform = new CarianEventBus();
        bridge(platform, mod);
        platform.register(OtherMod.class);
        PlatformHeal pe = new PlatformHeal(1);
        platform.post(pe);
        assertTrue(pe.isCanceled());
        assertEquals(List.of(), LOG, "别的模组的普通监听也被本模组的取消挡掉");
    }

    @Test
    void noModListenersMeansNoWrapper() {
        CarianEventBus mod = new CarianEventBus();
        CarianEventBus platform = new CarianEventBus();
        AtomicInteger made = new AtomicInteger();
        PhasedForwarder<PlatformHeal, Wrapped> f = new PhasedForwarder<>(mod, Wrapped.class, p -> {
            made.incrementAndGet();
            return new Wrapped(p);
        });
        for (EventPriority p : EventPriority.values()) {
            platform.on(PlatformHeal.class, p, true, e -> f.phase(e, p));
        }
        platform.register(OtherMod.class);
        PlatformHeal pe = new PlatformHeal(1);
        platform.post(pe);
        assertEquals(0, made.get());
        assertEquals(2.0f, pe.amount, "平台事件照常往下走");
    }

    @Test
    void wrapperTableIsByIdentity() {
        CarianEventBus mod = new CarianEventBus();
        mod.on(HealEvent.class, EventPriority.HIGHEST, false, e -> { });
        PhasedForwarder<PlatformHeal, Wrapped> f = new PhasedForwarder<>(mod, Wrapped.class, Wrapped::new);
        EqualPlatformHeal a = new EqualPlatformHeal();
        EqualPlatformHeal b = new EqualPlatformHeal();
        f.phase(a, EventPriority.HIGHEST);
        f.phase(b, EventPriority.HIGHEST);
        assertNotSame(f.liveWrapper(a), f.liveWrapper(b), "equals 相等的两个平台事件各有各的包装");
        assertSame(a, f.liveWrapper(a).e.get());
    }

    @Test
    void highestAlwaysStartsFresh() {
        // 同一个平台事件对象被再发一次：最高档换新包装，不接着用上一次的
        CarianEventBus mod = new CarianEventBus();
        List<Object> seen = new ArrayList<>();
        mod.on(HealEvent.class, EventPriority.HIGHEST, false, seen::add);
        PhasedForwarder<PlatformHeal, Wrapped> f = new PhasedForwarder<>(mod, Wrapped.class, Wrapped::new);
        PlatformHeal pe = new PlatformHeal(1);
        f.phase(pe, EventPriority.HIGHEST);
        f.phase(pe, EventPriority.HIGHEST);
        assertNotSame(seen.get(0), seen.get(1));
    }

    @Test
    void listenerThrowLeavesEntryButOnlyWeaklyHeld() throws Exception {
        // 监听抛异常，最低档跑不到，表项留着；平台事件只被弱引用，没人用之后照样能回收
        CarianEventBus mod = new CarianEventBus();
        mod.on(HealEvent.class, EventPriority.NORMAL, false, e -> {
            throw new IllegalStateException("boom");
        });
        CarianEventBus platform = new CarianEventBus();
        PhasedForwarder<PlatformHeal, Wrapped> f = bridge(platform, mod);
        WeakReference<PlatformHeal> probe = postAndForget(platform);
        assertEquals(1, f.liveSize(), "抛异常后最低档没跑，表项还在");
        for (int i = 0; i < 100 && probe.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
        }
        assertNull(probe.get(), "表（键和包装对象）都没有强引用平台事件");
        // 弱键表在之后的写操作里清掉失效项；写几次让各个分段都清一遍
        for (int i = 0; i < 1000 && f.liveSize() > 0; i++) {
            f.phase(new PlatformHeal(0), EventPriority.LOWEST);
        }
        assertEquals(0, f.liveSize());
    }

    private static WeakReference<PlatformHeal> postAndForget(CarianEventBus platform) {
        PlatformHeal pe = new PlatformHeal(1);
        assertThrows(IllegalStateException.class, () -> platform.post(pe));
        return new WeakReference<>(pe);
    }

    @Test
    void wrapperUnusableAfterPlatformEventCollected() throws Exception {
        AtomicReference<Wrapped> kept = new AtomicReference<>();
        WeakReference<PlatformHeal> probe = keepWrapper(kept);
        for (int i = 0; i < 100 && probe.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
        }
        assertNull(probe.get());
        assertThrows(IllegalStateException.class, () -> kept.get().getAmount(), "分发结束后再读包装对象要明确报错，而不是读到旧值");
    }

    private static WeakReference<PlatformHeal> keepWrapper(AtomicReference<Wrapped> kept) {
        PlatformHeal pe = new PlatformHeal(1);
        kept.set(new Wrapped(pe));
        assertEquals(1.0f, kept.get().getAmount());
        return new WeakReference<>(pe);
    }

    @Test
    void concurrentThreadsKeepTheirOwnWrappers() throws Exception {
        // 单人游戏里客户端线程和内置服务端线程同时经过转发层：各自的事件全程对应各自的包装，表最后清空
        CarianEventBus mod = new CarianEventBus();
        ThreadLocal<Object> highest = new ThreadLocal<>();
        AtomicInteger mismatch = new AtomicInteger();
        AtomicInteger lowest = new AtomicInteger();
        mod.on(HealEvent.class, EventPriority.HIGHEST, false, highest::set);
        mod.on(HealEvent.class, EventPriority.NORMAL, false, e -> {
            if (highest.get() != e) {
                mismatch.incrementAndGet();
            }
            e.setAmount(e.getAmount() + 1);
        });
        mod.on(HealEvent.class, EventPriority.LOWEST, true, e -> {
            if (highest.get() != e) {
                mismatch.incrementAndGet();
            }
            lowest.incrementAndGet();
        });
        CarianEventBus platform = new CarianEventBus();
        PhasedForwarder<PlatformHeal, Wrapped> f = bridge(platform, mod);
        int threads = 8;
        int per = 5_000;
        Thread[] ts = new Thread[threads];
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicInteger wrongAmount = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    for (int k = 0; k < per; k++) {
                        PlatformHeal pe = platform.post(new PlatformHeal(k));
                        if (pe.amount != k + 1) {
                            wrongAmount.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    err.set(t);
                }
            });
            ts[i].start();
        }
        for (Thread t : ts) {
            t.join();
        }
        assertNull(err.get());
        assertEquals(0, mismatch.get());
        assertEquals(0, wrongAmount.get());
        assertEquals(threads * per, lowest.get());
        assertEquals(0, f.liveSize());
    }

    // ---------- 一次分发用同一份监听表、重入 ----------

    @Test
    void listenersAddedOrRemovedMidDispatchWaitForNextEvent() {
        // 平台总线整次分发用同一份表；转发层在最高档取快照，中途挂上 / 摘掉的监听从下一次事件才生效
        CarianEventBus mod = new CarianEventBus();
        List<CarianEventBus.Handle> added = new ArrayList<>();
        CarianEventBus.Handle normal = mod.on(HealEvent.class, EventPriority.NORMAL, false, e -> LOG.add("normal"));
        mod.on(HealEvent.class, EventPriority.HIGH, false, e -> {
            LOG.add("high");
            if (added.isEmpty()) {
                added.add(mod.on(HealEvent.class, EventPriority.LOW, false, x -> LOG.add("late")));
                normal.run();
            }
        });
        CarianEventBus platform = new CarianEventBus();
        bridge(platform, mod);
        platform.post(new PlatformHeal(1));
        assertEquals(List.of("high", "normal"), LOG, "摘掉的这次照跑，新挂的这次不跑");
        LOG.clear();
        platform.post(new PlatformHeal(1));
        assertEquals(List.of("high", "late"), LOG);
    }

    @Test
    void reentrantPostOfSameEventKeepsOuterWrapper() {
        // 同一个平台事件对象在自己的分发里再发一次：内层用新包装，内层结束后外层后几档接着用外层的
        CarianEventBus mod = new CarianEventBus();
        CarianEventBus platform = new CarianEventBus();
        List<Object> highest = new ArrayList<>();
        List<Object> low = new ArrayList<>();
        AtomicInteger depth = new AtomicInteger();
        mod.on(HealEvent.class, EventPriority.HIGHEST, false, highest::add);
        mod.on(HealEvent.class, EventPriority.NORMAL, false, e -> {
            if (depth.getAndIncrement() == 0) {
                platform.post(((Wrapped) e).e.get());
            }
        });
        mod.on(HealEvent.class, EventPriority.LOW, false, low::add);
        PhasedForwarder<PlatformHeal, Wrapped> f = bridge(platform, mod);
        platform.post(new PlatformHeal(1));
        assertEquals(2, highest.size());
        assertNotSame(highest.get(0), highest.get(1));
        assertEquals(List.of(highest.get(1), highest.get(0)), low, "内层先结束，外层的低档拿的还是外层的包装");
        assertEquals(0, f.liveSize());
    }

    @Test
    void reentrantPostWithNoListenersLeft() {
        // 外层分发途中把监听全摘了再重发：内层没有监听，后几档不能拿外层的包装去分发；外层照自己的快照跑完
        CarianEventBus mod = new CarianEventBus();
        CarianEventBus platform = new CarianEventBus();
        List<CarianEventBus.Handle> hs = new ArrayList<>();
        AtomicInteger depth = new AtomicInteger();
        hs.add(mod.on(HealEvent.class, EventPriority.HIGH, false, e -> {
            if (depth.getAndIncrement() == 0) {
                hs.forEach(Runnable::run);
                platform.post(((Wrapped) e).e.get());
            }
        }));
        hs.add(mod.on(HealEvent.class, EventPriority.LOW, false, e -> LOG.add("low")));
        PhasedForwarder<PlatformHeal, Wrapped> f = bridge(platform, mod);
        platform.post(new PlatformHeal(1));
        assertEquals(1, depth.get(), "内层不能借外层的包装把高档再跑一遍");
        assertEquals(List.of("low"), LOG, "只有外层的低档跑了一次");
        assertEquals(0, f.liveSize());
    }
}
