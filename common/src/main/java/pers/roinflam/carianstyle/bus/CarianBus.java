package pers.roinflam.carianstyle.bus;

import java.util.List;
import java.util.function.Consumer;

/**
 * 本模组的事件总线（全局唯一的那一个）。业务代码里原来写 {@code NeoForge.EVENT_BUS.xxx} 的地方改成 {@code CarianBus.xxx}。
 * <p>
 * 怎么用：
 * </p>
 * <ul>
 *     <li>订阅类：类上标 {@link EventBusSubscriber}，方法标 {@link SubscribeEvent}（public static，一个事件参数）；
 *     平台入口按构建时的订阅清单逐个 {@link #register(Class)}。</li>
 *     <li>发事件：{@link #post}。Fabric 侧和模组自己发的事件用这个，一次跑完五档。</li>
 *     <li>NeoForge / Forge 的转发层在平台事件的每一档里调 {@link #postPhase}，只跑那一档。</li>
 *     <li>平台转发层启动时 {@link #provide} 声明自己转发 / 发出的事件类，再 {@link #verifyProvided()}；
 *     之后登记的监听如果没有平台在发，当场抛异常。</li>
 *     <li>测试台、运行期临时挂监听用 {@link #on}，返回的句柄 {@code run()} 即摘掉；{@link #dump} 按执行顺序列出监听。</li>
 * </ul>
 * <p>
 * 实现和语义细节见 {@link CarianEventBus}。
 * </p>
 */
public final class CarianBus {

    private static final CarianEventBus BUS = new CarianEventBus();

    private CarianBus() {
    }

    /** 背后的总线实例，给转发层和测试用。 */
    public static CarianEventBus get() {
        return BUS;
    }

    public static void register(Class<?> type) {
        BUS.register(type);
    }

    public static void register(Object instance) {
        BUS.register(instance);
    }

    public static void unregister(Object target) {
        BUS.unregister(target);
    }

    public static <T extends Event> CarianEventBus.Handle on(Class<T> eventType, EventPriority priority,
                                                             boolean receiveCanceled, Consumer<? super T> handler) {
        return BUS.on(eventType, priority, receiveCanceled, handler);
    }

    public static <T extends Event> T post(T event) {
        return BUS.post(event);
    }

    public static void postPhase(Event event, EventPriority priority) {
        BUS.postPhase(event, priority);
    }

    public static boolean hasListeners(Class<? extends Event> eventClass) {
        return BUS.hasListeners(eventClass);
    }

    public static void provide(Class<?>... eventTypes) {
        BUS.provide(eventTypes);
    }

    public static void verifyProvided() {
        BUS.verifyProvided();
    }

    public static List<String> dump(Class<?> eventClass) {
        return BUS.dump(eventClass);
    }
}
