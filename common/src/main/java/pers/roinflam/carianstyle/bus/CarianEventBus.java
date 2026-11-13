package pers.roinflam.carianstyle.bus;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 自带事件总线的实现。正式代码只用 {@link CarianBus} 上的那一个实例，单测各自 new。
 * <p>
 * 语义照 NeoForge / Forge 的总线做，换成自带总线之后监听的行为不变：
 * </p>
 * <ul>
 *     <li>五档优先级；同一档内先按 {@link SubscribeEvent#order()}，再按登记先后（全局递增的序号）。
 *     运行期才登记的监听序号更大，同档里排在已登记的静态监听之后。</li>
 *     <li>取消：某个监听取消后，后面 {@code receiveCanceled = false} 的跳过，检查在每个监听调用前做。</li>
 *     <li>父类上的监听收得到子类事件。</li>
 *     <li>同一个登记目标（类对象或实例，按身份）第二次 {@code register} 什么都不做。NeoForge 的总线也是这样，
 *     {@code @EventBusSubscriber} 加手动 {@code register} 的类每 tick 只跑一次，换总线后不能变成两次。</li>
 * </ul>
 * <p>
 * 并发：登记 / 注销在锁里改，监听表写时复制；分发不加锁，读的是不可变的数组。分发途中登记的监听
 * 只影响之后的分发，正在进行的这次照旧用旧表（和平台总线一样）。事件里再发事件可以任意嵌套。
 * </p>
 * <p>
 * 转发登记：每个平台用 {@link #provide} 声明自己转发（或自己发出）了哪些事件类。{@link #verifyProvided()} 之后
 * 进入严格模式，此后每登记一个监听都检查它的事件类有没有平台在发，没有就当场抛异常——
 * 否则这个监听在这个平台上永远不会被调用，而且不会有任何提示。
 * </p>
 */
public final class CarianEventBus {

    private static final Listener[][] EMPTY = new Listener[EventPriority.values().length][0];
    private static final String CLIENT_EVENT_PACKAGE = "pers.roinflam.carianstyle.event.client.";

    private final Object lock = new Object();
    /** 全部监听，写时复制；只在锁里替换。 */
    private volatile Listener[] all = new Listener[0];
    /** 已登记的目标（类对象、实例、{@link #on} 返回的句柄），按身份去重。只在锁里读写。 */
    private final Map<Object, Boolean> targets = new IdentityHashMap<>();
    /** 按事件的实际类缓存的分档监听表；监听有变化就整张换掉。 */
    private volatile Map<Class<?>, Listener[][]> tables = new ConcurrentHashMap<>();
    private long nextSeq;

    private final Set<Class<?>> provided = new LinkedHashSet<>();
    private volatile boolean strict;

    // ==================== 登记 ====================

    /** 登记一个类里全部带 {@link SubscribeEvent} 的 public static 方法。同一个类第二次登记什么都不做。 */
    public void register(Class<?> type) {
        Objects.requireNonNull(type, "type");
        synchronized (lock) {
            if (targets.containsKey(type)) {
                return;
            }
            List<Listener> found = scan(type, null);
            commit(type, found);
        }
    }

    /**
     * 登记一个实例上全部带 {@link SubscribeEvent} 的 public 实例方法（只看实例的类自己声明的，父类型上有监听直接报错）。
     * 传进来的是 {@link Class} 时按 {@link #register(Class)} 处理。同一个实例第二次登记什么都不做。
     */
    public void register(Object instance) {
        Objects.requireNonNull(instance, "instance");
        if (instance instanceof Class<?> c) {
            register(c);
            return;
        }
        synchronized (lock) {
            if (targets.containsKey(instance)) {
                return;
            }
            List<Listener> found = scan(instance.getClass(), instance);
            commit(instance, found);
        }
    }

    /**
     * 按事件类挂一个监听，不用写订阅类。返回的句柄传给 {@link #unregister} 或直接 {@code run()} 就摘掉。
     * 测试台、运行期临时挂的监听用这个。
     */
    public <T extends Event> Handle on(Class<T> eventType, EventPriority priority, boolean receiveCanceled,
                                       Consumer<? super T> handler) {
        return on(eventType, priority, receiveCanceled, 0, handler);
    }

    /** 同 {@link #on(Class, EventPriority, boolean, Consumer)}，多一个同档排序值。 */
    @SuppressWarnings("unchecked")
    public <T extends Event> Handle on(Class<T> eventType, EventPriority priority, boolean receiveCanceled, int order,
                                       Consumer<? super T> handler) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(handler, "handler");
        if (!Event.class.isAssignableFrom(eventType)) {
            throw new IllegalArgumentException("不是本模组总线的事件类：" + eventType.getName());
        }
        Handle handle = new Handle(this);
        synchronized (lock) {
            Listener l = new Listener(eventType, priority, receiveCanceled, order, nextSeq++,
                    (Consumer<Object>) handler, handle, "on:" + handler.getClass().getName());
            commit(handle, List.of(l));
        }
        return handle;
    }

    /** 摘掉某个登记目标（类对象、实例、{@link #on} 的句柄）下的全部监听；没登记过的什么都不做。 */
    public void unregister(Object target) {
        Objects.requireNonNull(target, "target");
        synchronized (lock) {
            if (targets.remove(target) == null) {
                return;
            }
            List<Listener> keep = new ArrayList<>(all.length);
            for (Listener l : all) {
                if (l.target != target) {
                    keep.add(l);
                }
            }
            all = keep.toArray(new Listener[0]);
            tables = new ConcurrentHashMap<>();
        }
    }

    private void commit(Object target, List<Listener> found) {
        if (strict) {
            List<String> missing = new ArrayList<>();
            for (Listener l : found) {
                if (!isProvided(l.eventType)) {
                    missing.add(describeMissing(l));
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(missingMessage(missing));
            }
        }
        targets.put(target, Boolean.TRUE);
        if (found.isEmpty()) {
            return;
        }
        Listener[] next = Arrays.copyOf(all, all.length + found.size());
        for (int i = 0; i < found.size(); i++) {
            next[all.length + i] = found.get(i);
        }
        all = next;
        tables = new ConcurrentHashMap<>();
    }

    private List<Listener> scan(Class<?> type, Object instance) {
        boolean wantStatic = instance == null;
        EventBusSubscriber sub = type.getAnnotation(EventBusSubscriber.class);
        boolean bothSides = sub == null || sub.side() == EventBusSubscriber.Side.BOTH;

        // 和 NeoForge 的总线一样只看这个类自己声明的方法，父类、接口上不许有监听：
        // 否则父类的 public static 监听会跟着每个子类再登记一遍，同一个方法一次事件跑两次
        checkSupertypes(type, type.getSuperclass());
        for (Class<?> i : type.getInterfaces()) {
            checkSupertypes(type, i);
        }
        Method[] methods = type.getDeclaredMethods();
        for (Method m : methods) {
            if (m.isAnnotationPresent(SubscribeEvent.class) && !Modifier.isPublic(m.getModifiers())) {
                throw new IllegalArgumentException("监听方法必须是 public：" + m);
            }
        }
        // getDeclaredMethods() 的顺序 JVM 不保证；按方法名、再按描述符排，登记顺序才是确定的
        Arrays.sort(methods, Comparator.comparing(Method::getName).thenComparing(CarianEventBus::descriptor));
        List<Listener> out = new ArrayList<>();
        for (Method m : methods) {
            SubscribeEvent s = m.getAnnotation(SubscribeEvent.class);
            if (s == null) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            if (wantStatic && !isStatic) {
                throw new IllegalArgumentException("用类对象登记时监听方法必须是 static：" + m
                        + "（要么改成 static，要么用实例登记）");
            }
            if (!wantStatic && isStatic) {
                throw new IllegalArgumentException("用实例登记时监听方法不能是 static：" + m
                        + "（要么改成实例方法，要么用 register(" + type.getSimpleName() + ".class)）");
            }
            if (m.getParameterCount() != 1 || !Event.class.isAssignableFrom(m.getParameterTypes()[0])) {
                throw new IllegalArgumentException("监听方法必须只有一个参数、类型是本模组总线的事件：" + m);
            }
            Class<?> eventType = m.getParameterTypes()[0];
            if (bothSides && eventType.getName().startsWith(CLIENT_EVENT_PACKAGE)) {
                // 客户端事件类的成员是客户端类型，双端订阅类引用它，在 Fabric 专用服上取签名就 NoClassDefFoundError
                throw new IllegalArgumentException("双端订阅类不能监听客户端事件，改成 @EventBusSubscriber(side = CLIENT) 的类：" + m);
            }
            out.add(new Listener(eventType, s.priority(), s.receiveCanceled(), s.order(), nextSeq++,
                    makeInvoker(m, instance), instance == null ? type : instance,
                    m.getDeclaringClass().getName() + "#" + m.getName() + descriptor(m)));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException(type.getName() + " 没有带 @SubscribeEvent 的"
                    + (wantStatic ? " static " : "实例") + "方法，却被登记到了总线上");
        }
        return out;
    }

    private static void checkSupertypes(Class<?> registered, Class<?> type) {
        if (type == null || type == Object.class) {
            return;
        }
        for (Method m : type.getDeclaredMethods()) {
            if (m.isAnnotationPresent(SubscribeEvent.class)) {
                throw new IllegalArgumentException("登记的是 " + registered.getName() + "，但它的父类型 " + type.getName()
                        + " 上有监听方法 " + m + "；监听只能写在被登记的类自己身上");
            }
        }
        checkSupertypes(registered, type.getSuperclass());
        for (Class<?> i : type.getInterfaces()) {
            checkSupertypes(registered, i);
        }
    }

    private static String descriptor(Method m) {
        return MethodType.methodType(m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString();
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> makeInvoker(Method m, Object instance) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(m.getDeclaringClass(), MethodHandles.lookup());
            MethodHandle mh = lookup.unreflect(m);
            MethodType erased = MethodType.methodType(void.class, Object.class);
            MethodType actual = MethodType.methodType(void.class, m.getParameterTypes()[0]);
            if (instance == null) {
                CallSite cs = LambdaMetafactory.metafactory(lookup, "accept",
                        MethodType.methodType(Consumer.class), erased, mh, actual);
                return (Consumer<Object>) cs.getTarget().invoke();
            }
            CallSite cs = LambdaMetafactory.metafactory(lookup, "accept",
                    MethodType.methodType(Consumer.class, m.getDeclaringClass()), erased, mh, actual);
            return (Consumer<Object>) cs.getTarget().invoke(instance);
        } catch (Throwable t) {
            // 生成不了调用点（类加载器、模块可见性之类）就退回反射：慢，但行为一样；监听抛的异常原样往外抛
            m.setAccessible(true);
            return e -> {
                try {
                    m.invoke(instance, e);
                } catch (InvocationTargetException ex) {
                    throw sneaky(ex.getCause());
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException(ex);
                }
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static <X extends Throwable> RuntimeException sneaky(Throwable t) throws X {
        throw (X) t;
    }

    // ==================== 转发登记 ====================

    /**
     * 平台声明：这些事件类在这个平台上有人转发或发出。可以多次调用、重复声明。
     * 监听某个已声明事件类本身或它的父类都算有人发；只监听某个子类的不算（平台发的是声明的那个类）。
     */
    public void provide(Class<?>... eventTypes) {
        synchronized (lock) {
            for (Class<?> c : eventTypes) {
                if (!Event.class.isAssignableFrom(c)) {
                    throw new IllegalArgumentException("不是本模组总线的事件类：" + c.getName());
                }
                provided.add(c);
            }
        }
    }

    /**
     * 检查现有的全部监听都有平台在发，有缺的一次列全抛 {@link IllegalStateException}。
     * 调过之后（不论有没有抛）进入严格模式，之后登记的监听当场检查。
     */
    public void verifyProvided() {
        synchronized (lock) {
            strict = true;
            List<String> missing = new ArrayList<>();
            for (Listener l : all) {
                if (!isProvided(l.eventType)) {
                    missing.add(describeMissing(l));
                }
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(missingMessage(missing));
            }
        }
    }

    /** 这个事件类有没有平台在发：监听的参数类型是某个已声明事件类本身或它的父类。 */
    public boolean isProvided(Class<?> listenedType) {
        synchronized (lock) {
            for (Class<?> p : provided) {
                if (listenedType.isAssignableFrom(p)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String describeMissing(Listener l) {
        return l.name + " 监听的 " + l.eventType.getName();
    }

    private static String missingMessage(List<String> missing) {
        return "这些监听的事件在当前平台上没有转发，监听永远不会被调用（平台转发层漏登记了，或这个事件这个平台没有）：\n  "
                + String.join("\n  ", missing);
    }

    // ==================== 分发 ====================

    /** 五档依次分发。返回传进来的事件，方便链式读结果。 */
    public <T extends Event> T post(T event) {
        Listener[][] t = table(event.getClass());
        if (t == EMPTY) {
            return event;
        }
        ICancellableEvent c = event instanceof ICancellableEvent cc ? cc : null;
        for (Listener[] ls : t) {
            dispatch(ls, event, c);
        }
        return event;
    }

    /**
     * 只分发一档。NeoForge / Forge 的转发层在平台事件的同一档里调它，
     * 本模组的监听和平台上别的模组的监听之间的先后、改值、取消就和直接挂在平台总线上一样。
     */
    public void postPhase(Event event, EventPriority priority) {
        Listener[] ls = table(event.getClass())[priority.ordinal()];
        if (ls.length != 0) {
            dispatch(ls, event, event instanceof ICancellableEvent cc ? cc : null);
        }
    }

    /**
     * 取某个事件类（实际分发时的类）此刻的分档监听表。之后的登记 / 注销不影响拿到的这份。
     * 转发层在最高档取一次、五档都按它分发，和平台总线「一次分发用同一份监听表」一致；
     * 不然分发途中挂上的监听会从中途某一档开始收到这次事件。
     */
    public Snapshot snapshot(Class<? extends Event> eventClass) {
        Listener[][] t = table(eventClass);
        return t == EMPTY ? Snapshot.NONE : new Snapshot(t);
    }

    /** 按 {@link #snapshot} 取到的那份表分发一档。 */
    public void postPhase(Event event, EventPriority priority, Snapshot snapshot) {
        Listener[] ls = snapshot.table[priority.ordinal()];
        if (ls.length != 0) {
            dispatch(ls, event, event instanceof ICancellableEvent cc ? cc : null);
        }
    }

    /** 有没有任何监听会收到这个类的事件。转发层用它在没人听的时候连包装对象都不建。 */
    public boolean hasListeners(Class<? extends Event> eventClass) {
        return table(eventClass) != EMPTY;
    }

    private static void dispatch(Listener[] ls, Event event, ICancellableEvent c) {
        for (Listener l : ls) {
            if (c != null && !l.receiveCanceled && c.isCanceled()) {
                continue;
            }
            l.invoker.accept(event);
        }
    }

    private Listener[][] table(Class<?> eventClass) {
        Listener[][] t = tables.get(eventClass);
        if (t != null) {
            return t;
        }
        synchronized (lock) {
            // 在锁里建表并放进当前这张缓存：登记也在锁里换缓存，所以不会把旧表放进新缓存
            Map<Class<?>, Listener[][]> cache = tables;
            t = cache.get(eventClass);
            if (t != null) {
                return t;
            }
            int n = EventPriority.values().length;
            List<List<Listener>> buckets = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                buckets.add(new ArrayList<>());
            }
            boolean any = false;
            for (Listener l : all) {
                if (l.eventType.isAssignableFrom(eventClass)) {
                    buckets.get(l.priority.ordinal()).add(l);
                    any = true;
                }
            }
            if (!any) {
                t = EMPTY;
            } else {
                t = new Listener[n][];
                for (int i = 0; i < n; i++) {
                    List<Listener> b = buckets.get(i);
                    b.sort(ORDER);
                    t[i] = b.toArray(new Listener[0]);
                }
            }
            cache.put(eventClass, t);
            return t;
        }
    }

    private static final Comparator<Listener> ORDER =
            Comparator.comparingInt((Listener l) -> l.order).thenComparingLong(l -> l.seq);

    // ==================== 导出 ====================

    /**
     * 按执行顺序列出某个事件类（实际分发时的类）会经过的监听，每行
     * {@code 档位序号-档位名 \t 同档内序号(三位) \t 类全名#方法(描述符) \t receiveCanceled \t order}。
     * 前几列和重构前从 NeoForge 总线导出的监听顺序表一致，可以直接逐档对比。
     */
    public List<String> dump(Class<?> eventClass) {
        List<String> out = new ArrayList<>();
        Listener[][] t = table(eventClass);
        EventPriority[] ps = EventPriority.values();
        for (int i = 0; i < t.length; i++) {
            for (int j = 0; j < t[i].length; j++) {
                Listener l = t[i][j];
                out.add(i + "-" + ps[i] + "\t" + String.format("%03d", j) + "\t" + l.name + "\t" + l.receiveCanceled
                        + "\t" + l.order);
            }
        }
        return out;
    }

    // ==================== 内部类型 ====================

    private record Listener(Class<?> eventType, EventPriority priority, boolean receiveCanceled, int order, long seq,
                            Consumer<Object> invoker, Object target, String name) {
    }

    /** {@link #snapshot} 取到的分档监听表，不可变；只能交回给 {@link #postPhase(Event, EventPriority, Snapshot)}。 */
    public static final class Snapshot {
        static final Snapshot NONE = new Snapshot(EMPTY);
        private final Listener[][] table;

        private Snapshot(Listener[][] table) {
            this.table = table;
        }

        /** 取的那一刻有没有任何监听。 */
        public boolean isEmpty() {
            return this == NONE;
        }
    }

    /** {@link #on} 返回的句柄：{@code run()} 或 {@code unregister(handle)} 都能摘掉那个监听，摘多次无害。 */
    public static final class Handle implements Runnable {
        private final CarianEventBus bus;

        private Handle(CarianEventBus bus) {
            this.bus = bus;
        }

        @Override
        public void run() {
            bus.unregister(this);
        }
    }
}
