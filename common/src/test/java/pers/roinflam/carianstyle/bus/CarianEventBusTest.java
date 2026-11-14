package pers.roinflam.carianstyle.bus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自带总线的语义：直接分发（Fabric 侧和模组自己发的事件走这条路）。
 * 前几项照总线小样的断言，后面是小样之外加的：五档、取消、同档排序值、去重、转发登记检查、运行期登记的先后、登记时的各种报错。
 */
class CarianEventBusTest {

    static final List<String> LOG = new ArrayList<>();

    @BeforeEach
    void clear() {
        LOG.clear();
    }

    // ---------- 事件 ----------

    public abstract static class LivingLike extends Event {
    }

    public abstract static class HealEvent extends LivingLike implements ICancellableEvent {
        public abstract float getAmount();

        public abstract void setAmount(float a);
    }

    public static final class SimpleHeal extends HealEvent {
        float amount;

        SimpleHeal(float a) {
            amount = a;
        }

        @Override
        public float getAmount() {
            return amount;
        }

        @Override
        public void setAmount(float a) {
            amount = a;
        }
    }

    public static final class OtherEvent extends Event {
    }

    // ---------- 监听 ----------

    public static class ModListeners {
        static int lastId;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void first(HealEvent e) {
            LOG.add("mod.HIGHEST amount=" + e.getAmount());
        }

        @SubscribeEvent
        public static void normal(HealEvent e) {
            LOG.add("mod.NORMAL amount=" + e.getAmount());
            e.setAmount(e.getAmount() * 2);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void low(HealEvent e) {
            LOG.add("mod.LOW amount=" + e.getAmount() + " id=" + (System.identityHashCode(e) == lastId));
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void cleanup(HealEvent e) {
            LOG.add("mod.LOWEST(rc) canceled=" + e.isCanceled());
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void parent(LivingLike e) {
            LOG.add("mod.HIGH(parent type)");
            lastId = System.identityHashCode(e);
        }
    }

    public static class Canceler {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void cancel(HealEvent e) {
            LOG.add("mod.HIGH cancel");
            e.setCanceled(true);
        }
    }

    public static class Nested {
        static CarianEventBus bus;
        static int depth;

        @SubscribeEvent
        public static void on(SimpleHeal e) {
            LOG.add("nested depth=" + depth + " amount=" + e.getAmount());
            if (depth++ < 2) {
                SimpleHeal inner = bus.post(new SimpleHeal(e.getAmount() + 10));
                LOG.add("back depth=" + (--depth) + " inner=" + inner.getAmount());
            }
        }
    }

    // ---------- 小样原有的断言 ----------

    @Test
    void directDispatchOrderAndValue() {
        CarianEventBus bus = new CarianEventBus();
        bus.register(ModListeners.class);
        SimpleHeal e = bus.post(new SimpleHeal(3));
        assertEquals(List.of("mod.HIGHEST amount=3.0", "mod.HIGH(parent type)", "mod.NORMAL amount=3.0",
                "mod.LOW amount=6.0 id=true", "mod.LOWEST(rc) canceled=false"), LOG);
        assertEquals(6.0f, e.getAmount());
    }

    @Test
    void cancelLeavesOnlyReceiveCanceled() {
        CarianEventBus bus = new CarianEventBus();
        bus.register(ModListeners.class);
        Canceler c = new Canceler();
        bus.register(c);
        bus.post(new SimpleHeal(3));
        assertEquals(List.of("mod.HIGHEST amount=3.0", "mod.HIGH(parent type)", "mod.HIGH cancel", "mod.LOWEST(rc) canceled=true"), LOG);
        bus.unregister(c);
        LOG.clear();
        bus.post(new SimpleHeal(3));
        assertEquals(5, LOG.size(), "摘掉取消监听后恢复全部五个");
    }

    @Test
    void nestedDispatch() {
        CarianEventBus bus = new CarianEventBus();
        Nested.bus = bus;
        Nested.depth = 0;
        bus.register(Nested.class);
        bus.post(new SimpleHeal(1));
        assertEquals(List.of("nested depth=0 amount=1.0", "nested depth=1 amount=11.0", "nested depth=2 amount=21.0",
                "back depth=2 inner=21.0", "back depth=1 inner=11.0"), LOG);
    }

    @Test
    void dumpListsInExecutionOrder() {
        CarianEventBus bus = new CarianEventBus();
        bus.register(ModListeners.class);
        String c = ModListeners.class.getName();
        String h = "(L" + HealEvent.class.getName().replace('.', '/') + ";)V";
        String l = "(L" + LivingLike.class.getName().replace('.', '/') + ";)V";
        assertEquals(List.of(
                "0-HIGHEST\t000\t" + c + "#first" + h + "\tfalse\t0",
                "1-HIGH\t000\t" + c + "#parent" + l + "\tfalse\t0",
                "2-NORMAL\t000\t" + c + "#normal" + h + "\tfalse\t0",
                "3-LOW\t000\t" + c + "#low" + h + "\tfalse\t0",
                "4-LOWEST\t000\t" + c + "#cleanup" + h + "\ttrue\t0"), bus.dump(SimpleHeal.class));
    }

    // ---------- 加项：五档、取消、排序值 ----------

    public static class FivePriorities {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void a(OtherEvent e) {
            LOG.add("LOWEST");
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void b(OtherEvent e) {
            LOG.add("LOW");
        }

        @SubscribeEvent
        public static void c(OtherEvent e) {
            LOG.add("NORMAL");
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void d(OtherEvent e) {
            LOG.add("HIGH");
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void e(OtherEvent e) {
            LOG.add("HIGHEST");
        }
    }

    @Test
    void fivePrioritiesRunInOrderRegardlessOfNames() {
        CarianEventBus bus = new CarianEventBus();
        bus.register(FivePriorities.class);
        bus.post(new OtherEvent());
        assertEquals(List.of("HIGHEST", "HIGH", "NORMAL", "LOW", "LOWEST"), LOG);
        // 分档分发：每档只跑自己那一档
        LOG.clear();
        OtherEvent ev = new OtherEvent();
        for (EventPriority p : new EventPriority[]{EventPriority.LOW, EventPriority.HIGHEST}) {
            bus.postPhase(ev, p);
        }
        assertEquals(List.of("LOW", "HIGHEST"), LOG);
    }

    public static class CancelChain {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void cancel(SimpleHeal e) {
            LOG.add("cancel");
            e.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void skippedSameTier(SimpleHeal e) {
            LOG.add("skippedSameTier");
        }

        @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
        public static void undo(SimpleHeal e) {
            LOG.add("undo canceled=" + e.isCanceled());
            e.setCanceled(false);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void afterUndo(SimpleHeal e) {
            LOG.add("afterUndo canceled=" + e.isCanceled());
        }
    }

    @Test
    void cancelSemanticsMatchPlatformBus() {
        // 同档里排在取消之后的普通监听跳过；receiveCanceled 的读得到取消、也能撤销；撤销后后面的普通监听恢复
        CarianEventBus bus = new CarianEventBus();
        bus.register(CancelChain.class);
        SimpleHeal e = bus.post(new SimpleHeal(1));
        assertEquals(List.of("cancel", "undo canceled=true", "afterUndo canceled=false"), LOG);
        assertTrue(!e.isCanceled());
    }

    @Test
    void cancelStateReadableAfterPost() {
        CarianEventBus bus = new CarianEventBus();
        bus.on(SimpleHeal.class, EventPriority.NORMAL, false, ev -> ev.setCanceled(true));
        SimpleHeal e = bus.post(new SimpleHeal(1));
        assertTrue(e.isCanceled());
    }

    public static class OrderA {
        @SubscribeEvent(order = Integer.MAX_VALUE)
        public static void last(OtherEvent e) {
            LOG.add("A.last");
        }

        @SubscribeEvent
        public static void plain(OtherEvent e) {
            LOG.add("A.plain");
        }
    }

    public static class OrderB {
        @SubscribeEvent
        public static void plain(OtherEvent e) {
            LOG.add("B.plain");
        }

        @SubscribeEvent(order = -5)
        public static void early(OtherEvent e) {
            LOG.add("B.early");
        }
    }

    @Test
    void sameTierOrderThenRegistration() {
        CarianEventBus bus = new CarianEventBus();
        bus.register(OrderA.class);
        bus.register(OrderB.class);
        bus.post(new OtherEvent());
        assertEquals(List.of("B.early", "A.plain", "B.plain", "A.last"), LOG);
    }

    public static class RuntimeListener {
        @SubscribeEvent
        public void dynamic(OtherEvent e) {
            LOG.add("runtime");
        }
    }

    public static class StaticLate {
        @SubscribeEvent
        public static void z(OtherEvent e) {
            LOG.add("staticLate");
        }
    }

    @Test
    void runtimeRegistrationRunsAfterEarlierStaticInSameTier() {
        CarianEventBus bus = new CarianEventBus();
        bus.register(OrderA.class);
        bus.register(new RuntimeListener());
        bus.post(new OtherEvent());
        assertEquals(List.of("A.plain", "runtime", "A.last"), LOG, "运行期登记的排在同档已登记的静态监听之后（排序值相同时）");
        LOG.clear();
        bus.register(StaticLate.class);
        bus.post(new OtherEvent());
        assertEquals(List.of("A.plain", "runtime", "staticLate", "A.last"), LOG, "之后再登记的照登记序号往后排");
    }

    // ---------- 加项：去重 ----------

    public static class Counter {
        static int n;

        @SubscribeEvent
        public static void count(OtherEvent e) {
            n++;
        }
    }

    public static class InstanceCounter {
        int n;

        @SubscribeEvent
        public void count(OtherEvent e) {
            n++;
        }
    }

    @Test
    void registerDedupesByTarget() {
        CarianEventBus bus = new CarianEventBus();
        Counter.n = 0;
        bus.register(Counter.class);
        bus.register(Counter.class);
        bus.register((Object) Counter.class);
        bus.post(new OtherEvent());
        assertEquals(1, Counter.n, "同一个类登记三次只跑一次");

        InstanceCounter a = new InstanceCounter();
        InstanceCounter b = new InstanceCounter();
        bus.register(a);
        bus.register(a);
        bus.register(b);
        bus.post(new OtherEvent());
        assertEquals(1, a.n, "同一个实例登记两次只跑一次");
        assertEquals(1, b.n, "不同实例各跑各的");

        bus.unregister(Counter.class);
        bus.register(Counter.class);
        Counter.n = 0;
        bus.post(new OtherEvent());
        assertEquals(1, Counter.n, "摘掉后可以重新登记");
    }

    @Test
    void onHandleUnregisters() {
        CarianEventBus bus = new CarianEventBus();
        CarianEventBus.Handle h = bus.on(OtherEvent.class, EventPriority.NORMAL, false, e -> LOG.add("x"));
        bus.post(new OtherEvent());
        h.run();
        h.run();
        bus.post(new OtherEvent());
        assertEquals(List.of("x"), LOG);
    }

    // ---------- 加项：转发登记 ----------

    @Test
    void listenerWithoutForwardFailsAtStartupCheck() {
        CarianEventBus bus = new CarianEventBus();
        bus.provide(SimpleHeal.class);
        bus.register(ModListeners.class); // 监听 HealEvent / LivingLike，都是已转发事件的父类：通过
        bus.register(FivePriorities.class); // 监听 OtherEvent，没人转发
        IllegalStateException ex = assertThrows(IllegalStateException.class, bus::verifyProvided);
        assertTrue(ex.getMessage().contains(OtherEvent.class.getName()), ex.getMessage());
        assertTrue(ex.getMessage().contains("FivePriorities#a"), "缺的一次列全");
        assertTrue(!ex.getMessage().contains("ModListeners"), "已转发的不列");
    }

    @Test
    void afterVerifyRegistrationIsCheckedImmediately() {
        CarianEventBus bus = new CarianEventBus();
        bus.provide(SimpleHeal.class);
        bus.verifyProvided();
        bus.register(ModListeners.class);
        assertThrows(IllegalStateException.class, () -> bus.register(Counter.class));
        assertThrows(IllegalStateException.class, () -> bus.on(OtherEvent.class, EventPriority.NORMAL, false, e -> { }));
        // 登记失败的不留在总线上，之后补声明再登记可以成功
        bus.provide(OtherEvent.class);
        Counter.n = 0;
        bus.register(Counter.class);
        bus.post(new OtherEvent());
        assertEquals(1, Counter.n);
    }

    @Test
    void providingNonEventIsRejected() {
        CarianEventBus bus = new CarianEventBus();
        assertThrows(IllegalArgumentException.class, () -> bus.provide(String.class));
    }

    // ---------- 加项：登记时的报错 ----------

    public static class InstanceOnly {
        @SubscribeEvent
        public void x(OtherEvent e) {
        }
    }

    public static class StaticOnly {
        @SubscribeEvent
        public static void x(OtherEvent e) {
        }
    }

    public static class NoListeners {
        public static void x(OtherEvent e) {
        }
    }

    public static class NotPublic {
        @SubscribeEvent
        static void x(OtherEvent e) {
        }
    }

    public static class WrongParams {
        @SubscribeEvent
        public static void x(OtherEvent e, int extra) {
        }
    }

    public static class NotAnEvent {
        @SubscribeEvent
        public static void x(String e) {
        }
    }

    @Test
    void badRegistrationsThrow() {
        CarianEventBus bus = new CarianEventBus();
        assertThrows(IllegalArgumentException.class, () -> bus.register(InstanceOnly.class));
        assertThrows(IllegalArgumentException.class, () -> bus.register(new StaticOnly()));
        assertThrows(IllegalArgumentException.class, () -> bus.register(NoListeners.class));
        assertThrows(IllegalArgumentException.class, () -> bus.register(NotPublic.class));
        assertThrows(IllegalArgumentException.class, () -> bus.register(WrongParams.class));
        assertThrows(IllegalArgumentException.class, () -> bus.register(NotAnEvent.class));
        // 失败的登记不算登记过：改正（这里换成正确的类）后照常
        bus.register(StaticOnly.class);
        assertEquals(1, bus.dump(OtherEvent.class).size());
    }

    // ---------- 加项：异常、并发 ----------

    public static class Thrower {
        @SubscribeEvent
        public static void boom(OtherEvent e) {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void listenerExceptionPropagatesUnchanged() {
        CarianEventBus bus = new CarianEventBus();
        bus.register(Thrower.class);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> bus.post(new OtherEvent()));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void registerDuringDispatchAffectsOnlyLaterPosts() {
        CarianEventBus bus = new CarianEventBus();
        List<String> log = new ArrayList<>();
        bus.on(OtherEvent.class, EventPriority.HIGH, false, e -> {
            log.add("high");
            if (log.size() == 1) {
                bus.on(OtherEvent.class, EventPriority.LOW, false, x -> log.add("addedLow"));
            }
        });
        bus.post(new OtherEvent());
        assertEquals(List.of("high"), log, "正在进行的这次照旧用旧表");
        bus.post(new OtherEvent());
        assertEquals(List.of("high", "high", "addedLow"), log);
    }

    @Test
    void concurrentPostAndRegister() throws Exception {
        CarianEventBus bus = new CarianEventBus();
        java.util.concurrent.atomic.AtomicLong hits = new java.util.concurrent.atomic.AtomicLong();
        bus.on(OtherEvent.class, EventPriority.NORMAL, false, e -> hits.incrementAndGet());
        int threads = 6;
        int perThread = 20_000;
        Thread[] ts = new Thread[threads];
        java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    for (int k = 0; k < perThread; k++) {
                        bus.post(new OtherEvent());
                    }
                } catch (Throwable t) {
                    err.set(t);
                }
            });
            ts[i].start();
        }
        // 同时不停地挂、摘别的监听，逼着监听表反复重建
        for (int k = 0; k < 2000; k++) {
            bus.on(OtherEvent.class, EventPriority.LOW, false, e -> { }).run();
        }
        for (Thread t : ts) {
            t.join();
        }
        assertSame(null, err.get());
        assertEquals((long) threads * perThread, hits.get(), "常驻监听每次都收到，一次不漏不重");
    }

    // ---------- 父类型上的监听 ----------

    public static class ParentSubscriber {
        @SubscribeEvent
        public static void parent(OtherEvent e) {
            LOG.add("parent");
        }
    }

    public static class ChildSubscriber extends ParentSubscriber {
        @SubscribeEvent
        public static void child(OtherEvent e) {
            LOG.add("child");
        }
    }

    public interface ListeningInterface {
        @SubscribeEvent
        default void fromInterface(OtherEvent e) {
        }
    }

    public static class ImplementsListening implements ListeningInterface {
        @SubscribeEvent
        public void own(OtherEvent e) {
        }
    }

    @Test
    void supertypeListenersAreRejected() {
        // 和 NeoForge 的总线一样只看类自己声明的方法；父类型上有监听直接报错，免得父类的监听跟着子类再登记一遍
        CarianEventBus bus = new CarianEventBus();
        bus.register(ParentSubscriber.class);
        assertThrows(IllegalArgumentException.class, () -> bus.register(ChildSubscriber.class));
        assertThrows(IllegalArgumentException.class, () -> bus.register(new ImplementsListening()));
        bus.post(new OtherEvent());
        assertEquals(List.of("parent"), LOG, "父类的监听只跑一次，报错的登记什么都没挂上");
    }
}
