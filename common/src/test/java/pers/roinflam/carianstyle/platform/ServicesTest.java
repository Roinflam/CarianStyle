package pers.roinflam.carianstyle.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pers.roinflam.carianstyle.scan.ClassFixtures;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Services#load}：用接口自己的类加载器找实现，正好一个才给；在别的线程（上下文类加载器不同）上照样找得到。
 * <p>
 * 接口、实现、{@code META-INF/services} 都放在临时目录里、由单独的类加载器加载，
 * 模拟「接口和实现由模组的类加载器加载、测试线程的上下文类加载器看不见它们」。
 * </p>
 */
class ServicesTest {

    private static URLClassLoader fixtureLoader(Path root, String services) throws Exception {
        ClassFixtures.write(root, ClassFixtures.spec("fx.Svc").asInterface());
        ClassFixtures.write(root, ClassFixtures.spec("fx.SvcImpl").implementing("fx/Svc"));
        ClassFixtures.write(root, ClassFixtures.spec("fx.SvcImpl2").implementing("fx/Svc"));
        if (services != null) {
            ClassFixtures.writeText(root, "META-INF/services/fx.Svc", services);
        }
        // 父加载器为空：测试自己的类加载器看不见这些类
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
    }

    @Test
    void findsSingleImplementationOnAnyThread(@TempDir Path tmp) throws Exception {
        try (URLClassLoader loader = fixtureLoader(tmp, "fx.SvcImpl\n")) {
            Class<?> svc = loader.loadClass("fx.Svc");
            Object here = Services.load(svc);
            assertEquals("fx.SvcImpl", here.getClass().getName());

            // 另一个线程，上下文类加载器换成什么都看不见的，照样找得到
            Object there = CompletableFuture.supplyAsync(() -> {
                Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
                return Services.load(svc);
            }).get();
            assertEquals("fx.SvcImpl", there.getClass().getName());
        }
    }

    @Test
    void noImplementationThrows(@TempDir Path tmp) throws Exception {
        try (URLClassLoader loader = fixtureLoader(tmp, null)) {
            Class<?> svc = loader.loadClass("fx.Svc");
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> Services.load(svc));
            assertTrue(e.getMessage().contains("找不到平台实现"), e.getMessage());
        }
    }

    @Test
    void twoImplementationsThrow(@TempDir Path tmp) throws Exception {
        try (URLClassLoader loader = fixtureLoader(tmp, "fx.SvcImpl\nfx.SvcImpl2\n")) {
            Class<?> svc = loader.loadClass("fx.Svc");
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> Services.load(svc));
            assertTrue(e.getMessage().contains("不止一个"), e.getMessage());
        }
    }

    @Test
    void brokenRegistrationThrows(@TempDir Path tmp) throws Exception {
        try (URLClassLoader loader = fixtureLoader(tmp, "fx.Missing\n")) {
            Class<?> svc = loader.loadClass("fx.Svc");
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> Services.load(svc));
            assertTrue(e.getMessage().contains("建不出来"), e.getMessage());
        }
    }
}
