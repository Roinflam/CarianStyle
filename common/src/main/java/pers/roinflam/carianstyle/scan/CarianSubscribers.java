package pers.roinflam.carianstyle.scan;

import pers.roinflam.carianstyle.platform.Services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 照订阅清单把订阅类交给事件总线登记。
 * <p>
 * 两端的订阅类由平台的通用入口登记，仅客户端的由客户端入口登记：先按端过滤、再加载类，
 * 专用服上仅客户端的订阅类连加载都不会发生（加载就会因为找不到客户端类出错）。
 * 登记顺序就是清单顺序（按类名排序），两个平台上同一档监听的先后因此一致。
 * </p>
 * <p>
 * 总线由调用方传进来（{@code CarianBus::register}），这个类本身不依赖总线，单测可以直接收集结果。
 * 每一端只许登记一次，第二次调用说明入口写重了，直接抛异常。
 * </p>
 */
public final class CarianSubscribers {

    private static final Set<SubscriberSide> DONE = EnumSet.noneOf(SubscriberSide.class);

    private CarianSubscribers() {
    }

    /**
     * 登记一端的订阅类。
     *
     * @param side      哪一端
     * @param registrar 收订阅类的方法，一般是总线的 {@code register(Class)}
     * @return 按登记顺序交出去的类
     */
    public static synchronized List<Class<?>> register(SubscriberSide side, Consumer<Class<?>> registrar) {
        if (side == SubscriberSide.CLIENT && !Services.platform().isClient()) {
            throw new IllegalStateException("专用服上不登记仅客户端的订阅类");
        }
        return register(side, ScanIndex.subscribers(), registrar);
    }

    /** 测试和自检用：照给定的清单内容登记，不读资源、不判断进程。 */
    static synchronized List<Class<?>> register(SubscriberSide side, List<ScanIndex.Subscriber> entries,
                                                Consumer<Class<?>> registrar) {
        if (DONE.contains(side)) {
            throw new IllegalStateException(side + " 端的订阅类已经登记过了");
        }
        ClassLoader loader = CarianSubscribers.class.getClassLoader();
        List<Class<?>> registered = new ArrayList<>();
        for (ScanIndex.Subscriber entry : entries) {
            if (entry.side() != side) {
                continue;
            }
            Class<?> type;
            try {
                // 和平台自动登记订阅类时一样先初始化类：订阅类的静态块里可能有登记以外的准备工作
                type = Class.forName(entry.className(), true, loader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("订阅清单里的类加载不到：" + entry.className(), e);
            }
            registrar.accept(type);
            registered.add(type);
        }
        // 全部登记完才记下：中途失败时重来会再报真正的原因，不会只报「已经登记过」（总线对同一个类重复登记什么都不做）
        DONE.add(side);
        return Collections.unmodifiableList(registered);
    }

    /** 测试用 */
    static synchronized void resetForTest() {
        DONE.clear();
    }
}
