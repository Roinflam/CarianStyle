package pers.roinflam.carianstyle.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * 取平台实现的唯一入口。
 * <p>
 * 用 {@link ServiceLoader} 加接口自己的类加载器取：NeoForge（模组在模块层里）、Forge 1.20.1
 * （{@code TransformingClassLoader}）、Fabric（{@code KnotClassLoader}）上，接口和实现都由加载模组的那个类加载器载入，
 * 用它找一定找得到；线程上下文类加载器在不同线程上不一样（服务端线程、渲染线程、工作线程），不用它。
 * </p>
 * <p>
 * 每个接口第一次取时建实例并缓存，之后各线程拿到的是同一个对象。实现必须正好一个：一个都没有说明平台模块没登记
 * （或打包漏了 {@code META-INF/services}），多于一个说明两个平台的实现混进了同一个 jar，都直接抛异常，不猜。
 * </p>
 * <p>
 * {@link ClientPlatform} 只在客户端进程里给：它的实现类引用客户端类，专用服上一加载就出错，
 * 而且错误发生在第一次调用某个方法时，离真正的调用点很远。这里在取之前先判断进程，专用服上直接抛出说明清楚的异常。
 * </p>
 */
public final class Services {

    private static volatile Platform platform;
    private static volatile PlatformRegistry registry;
    private static volatile ClientPlatform client;

    private Services() {
    }

    public static Platform platform() {
        Platform p = platform;
        if (p == null) {
            synchronized (Services.class) {
                p = platform;
                if (p == null) {
                    platform = p = load(Platform.class);
                }
            }
        }
        return p;
    }

    public static PlatformRegistry registry() {
        PlatformRegistry r = registry;
        if (r == null) {
            synchronized (Services.class) {
                r = registry;
                if (r == null) {
                    registry = r = load(PlatformRegistry.class);
                }
            }
        }
        return r;
    }

    /**
     * 客户端平台能力。专用服上调用会抛 {@link IllegalStateException}。
     */
    public static ClientPlatform client() {
        ClientPlatform c = client;
        if (c == null) {
            if (!platform().isClient()) {
                throw new IllegalStateException("专用服上不能取客户端平台实现：调用方是只该在客户端运行的代码");
            }
            synchronized (Services.class) {
                c = client;
                if (c == null) {
                    client = c = load(ClientPlatform.class);
                }
            }
        }
        return c;
    }

    /**
     * 新取一次实现，不经缓存，只给本类和测试用。不公开：注册器这类有状态的实现，另建一个实例在上面声明的内容不会挂上总线。
     *
     * @throws IllegalStateException 没有实现、多于一个实现，或实现建不出来
     */
    static <T> T load(Class<T> type) {
        List<T> found = new ArrayList<>(1);
        try {
            for (T impl : ServiceLoader.load(type, type.getClassLoader())) {
                found.add(impl);
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            // LinkageError：实现类引用的类在这个进程里不存在（比如客户端实现被拿到专用服上）
            throw new IllegalStateException("平台实现建不出来：" + type.getName(), e);
        }
        if (found.isEmpty()) {
            throw new IllegalStateException("找不到平台实现：" + type.getName()
                    + "（平台模块的 META-INF/services 里没有登记）");
        }
        if (found.size() > 1) {
            List<String> names = new ArrayList<>(found.size());
            for (T impl : found) {
                names.add(impl.getClass().getName());
            }
            throw new IllegalStateException("平台实现不止一个：" + type.getName() + " -> " + names);
        }
        return found.get(0);
    }
}
