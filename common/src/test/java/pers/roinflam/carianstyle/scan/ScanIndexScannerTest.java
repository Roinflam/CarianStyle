package pers.roinflam.carianstyle.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static pers.roinflam.carianstyle.scan.ClassFixtures.spec;
import static pers.roinflam.carianstyle.scan.ClassFixtures.write;

/**
 * 生成清单：带 / 不带注解、内部类、仅客户端、排序；违规写法报错；第三道保新（重扫比对）认得出加类、删类、改端。
 */
class ScanIndexScannerTest {

    private static final String CLIENT_EVENT = "pers/roinflam/carianstyle/event/client/RenderLevelStageEvent";
    private static final String COMMON_EVENT = "pers/roinflam/carianstyle/event/LivingTickEvent";
    private static final String MC_CLIENT_TYPE = "net/minecraft/client/Minecraft";

    /** 一套典型夹具：文件系统顺序和类名顺序故意不一致 */
    private static void standardFixtures(Path root) throws IOException {
        write(root, spec("fix.b.OuterA").enchantment());
        write(root, spec("fix.b.Outer2").enchantment());
        write(root, spec("fix.b.Outer$Inner").enchantment());   // 内部类
        write(root, spec("fix.b.Outer"));                       // 外部类本身没注解
        write(root, spec("fix.a.Plain"));                       // 什么注解都没有
        write(root, spec("fix.Z").enchantment());
        write(root, spec("fix.c.SubDefault").subscriber(null)); // 不写 side = 两端
        write(root, spec("fix.c.SubBoth").subscriber("BOTH").listener("onTick", COMMON_EVENT, true));
        write(root, spec("fix.c.SubClient").subscriber("CLIENT").listener("onRender", CLIENT_EVENT, true));
        write(root, spec("fix.c.Holder$ClientOnly").subscriber("CLIENT"));
        write(root, spec("fix.c.Both").enchantment().subscriber(null)); // 两种注解都有，两张表都进
        // 只在客户端登记的订阅类，普通方法的签名带原版客户端类：允许
        write(root, spec("fix.c.SubHelper").subscriber("CLIENT").listener("helper", MC_CLIENT_TYPE, false));
        ClassFixtures.writeText(root, "fix/package-info.class", "不是类文件，按文件名跳过");
        ClassFixtures.writeText(root, "module-info.class", "不是类文件，按文件名跳过");
        ClassFixtures.writeText(root, "fix/readme.txt", "不是类文件");
    }

    @Test
    void generatesSortedListsWithInnerAndClientClasses(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        standardFixtures(root);
        ScanIndexScanner.Result r = ScanIndexScanner.scan(List.of(root));

        assertEquals(List.of("fix.Z", "fix.b.Outer$Inner", "fix.b.Outer2", "fix.b.OuterA", "fix.c.Both"), r.enchantments());
        assertEquals(List.of(
                new ScanIndex.Subscriber(SubscriberSide.BOTH, "fix.c.Both"),
                new ScanIndex.Subscriber(SubscriberSide.CLIENT, "fix.c.Holder$ClientOnly"),
                new ScanIndex.Subscriber(SubscriberSide.BOTH, "fix.c.SubBoth"),
                new ScanIndex.Subscriber(SubscriberSide.CLIENT, "fix.c.SubClient"),
                new ScanIndex.Subscriber(SubscriberSide.BOTH, "fix.c.SubDefault"),
                new ScanIndex.Subscriber(SubscriberSide.CLIENT, "fix.c.SubHelper")), r.subscribers());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());

        // 写出来的文件逐字核对，再读回来
        Path out = tmp.resolve("out");
        ScanIndexScanner.write(r, out);
        String ench = Files.readString(out.resolve("META-INF/carianstyle/enchantments.list"), StandardCharsets.UTF_8);
        assertEquals(ScanIndex.HEADER + "\nfix.Z\nfix.b.Outer$Inner\nfix.b.Outer2\nfix.b.OuterA\nfix.c.Both\n", ench);
        String subs = Files.readString(out.resolve("META-INF/carianstyle/subscribers.list"), StandardCharsets.UTF_8);
        assertEquals(ScanIndex.HEADER + "\nBOTH\tfix.c.Both\nCLIENT\tfix.c.Holder$ClientOnly\nBOTH\tfix.c.SubBoth\n"
                + "CLIENT\tfix.c.SubClient\nBOTH\tfix.c.SubDefault\nCLIENT\tfix.c.SubHelper\n", subs);
        assertEquals(r.enchantments(), ScanIndex.parseEnchantments("t", List.of(ench.split("\n"))));
        assertEquals(r.subscribers(), ScanIndex.parseSubscribers("t", List.of(subs.split("\n"))));
    }

    @Test
    void mainWritesBothFiles(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        standardFixtures(root);
        Path out = tmp.resolve("out");
        ScanIndexScanner.main(new String[]{out.toString(), root.toString(), tmp.resolve("missing").toString()});
        assertTrue(Files.isRegularFile(out.resolve("META-INF/carianstyle/enchantments.list")));
        assertTrue(Files.isRegularFile(out.resolve("META-INF/carianstyle/subscribers.list")));
    }

    @Test
    void noAnnotatedClassesGivesHeaderOnly(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        write(root, spec("fix.a.Plain"));
        ScanIndexScanner.Result r = ScanIndexScanner.scan(List.of(root));
        assertTrue(r.enchantments().isEmpty());
        assertTrue(r.subscribers().isEmpty());
        assertEquals(ScanIndex.HEADER + "\n", ScanIndex.renderSubscribers(r.subscribers()));
    }

    @Test
    void sameClassInTwoRootsCountsOnce(@TempDir Path tmp) throws IOException {
        Path a = tmp.resolve("a");
        Path b = tmp.resolve("b");
        write(a, spec("fix.E").enchantment());
        write(b, spec("fix.E").enchantment());
        write(b, spec("fix.F").enchantment());
        assertEquals(List.of("fix.E", "fix.F"), ScanIndexScanner.scan(List.of(a, b)).enchantments());
    }

    @Test
    void bothSideListenerOnClientEventFailsBuild(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        write(root, spec("fix.Bad").subscriber(null).listener("onRender", CLIENT_EVENT, true));
        write(root, spec("fix.Bad2").subscriber("BOTH").listener("onRender", CLIENT_EVENT, true));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ScanIndexScanner.scan(List.of(root)));
        assertTrue(e.getMessage().contains("fix.Bad#onRender"), e.getMessage());
        assertTrue(e.getMessage().contains("fix.Bad2#onRender"), e.getMessage());
    }

    @Test
    void bothSideMethodSignatureWithVanillaClientTypeFailsBuild(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        write(root, spec("fix.Helper").subscriber(null).listener("helper", MC_CLIENT_TYPE, false));
        write(root, spec("fix.Ret").subscriber("BOTH").method("make", "()L" + MC_CLIENT_TYPE + ";", false));
        write(root, spec("fix.ClientOk").subscriber("CLIENT").listener("helper", MC_CLIENT_TYPE, false));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ScanIndexScanner.scan(List.of(root)));
        assertTrue(e.getMessage().contains("fix.Helper#helper(" + MC_CLIENT_TYPE + ")"), e.getMessage());
        assertTrue(e.getMessage().contains("fix.Ret#make"), e.getMessage());
        assertFalse(e.getMessage().contains("fix.ClientOk"), e.getMessage());
    }

    @Test
    void bothSideAnyMethodSignatureWithClientEventFailsBuild(@TempDir Path tmp) throws IOException {
        // 总线反射取全部方法，不带监听注解的方法、返回值、数组照样会解析到
        Path root = tmp.resolve("classes");
        write(root, spec("fix.Helper").subscriber(null).listener("helper", CLIENT_EVENT, false));
        write(root, spec("fix.Ret").subscriber(null).method("make", "()L" + CLIENT_EVENT + ";", false));
        write(root, spec("fix.Arr").subscriber(null).method("many", "([L" + CLIENT_EVENT + ";)V", false));
        // 只在客户端登记的订阅类随便用
        write(root, spec("fix.ClientOk").subscriber("CLIENT").method("make", "()L" + CLIENT_EVENT + ";", false));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ScanIndexScanner.scan(List.of(root)));
        assertTrue(e.getMessage().contains("fix.Helper#helper"), e.getMessage());
        assertTrue(e.getMessage().contains("fix.Ret#make"), e.getMessage());
        assertTrue(e.getMessage().contains("fix.Arr#many"), e.getMessage());
        assertFalse(e.getMessage().contains("fix.ClientOk"), e.getMessage());
    }

    @Test
    void unknownSideFailsBuild(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        write(root, spec("fix.Odd").subscriber("SERVER"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ScanIndexScanner.scan(List.of(root)));
        assertTrue(e.getMessage().contains("SERVER"), e.getMessage());
    }

    // ==================== 第三道保新 ====================

    private static ScanIndexScanner.Result scanned(Path root) throws IOException {
        return ScanIndexScanner.scan(List.of(root));
    }

    @Test
    void freshIndexPasses(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        standardFixtures(root);
        ScanIndexScanner.Result r = scanned(root);
        assertDoesNotThrow(() -> ScanIndex.verifyFresh(root, r.enchantments(), r.subscribers()));
    }

    @Test
    void addedClassIsDetected(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        standardFixtures(root);
        ScanIndexScanner.Result r = scanned(root);
        write(root, spec("fix.New").enchantment());
        write(root, spec("fix.NewSub").subscriber("CLIENT"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ScanIndex.verifyFresh(root, r.enchantments(), r.subscribers()));
        assertTrue(e.getMessage().contains("enchantments.list 少了：fix.New"), e.getMessage());
        assertTrue(e.getMessage().contains("subscribers.list 少了：CLIENT\tfix.NewSub"), e.getMessage());
    }

    @Test
    void deletedOrRenamedClassIsDetected(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        standardFixtures(root);
        ScanIndexScanner.Result r = scanned(root);
        Files.delete(root.resolve("fix/b/Outer$Inner.class"));
        Files.move(root.resolve("fix/Z.class"), root.resolve("fix/Y.class"));
        write(root, spec("fix.Y").enchantment()); // 改名后类文件内容跟着改
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ScanIndex.verifyFresh(root, r.enchantments(), r.subscribers()));
        assertTrue(e.getMessage().contains("多了：fix.b.Outer$Inner"), e.getMessage());
        assertTrue(e.getMessage().contains("多了：fix.Z"), e.getMessage());
        assertTrue(e.getMessage().contains("少了：fix.Y"), e.getMessage());
    }

    @Test
    void changedSideIsDetected(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        standardFixtures(root);
        ScanIndexScanner.Result r = scanned(root);
        write(root, spec("fix.c.SubDefault").subscriber("CLIENT"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ScanIndex.verifyFresh(root, r.enchantments(), r.subscribers()));
        assertTrue(e.getMessage().contains("少了：CLIENT\tfix.c.SubDefault"), e.getMessage());
        assertTrue(e.getMessage().contains("多了：BOTH\tfix.c.SubDefault"), e.getMessage());
    }

    @Test
    void rawLinesCheckAllowsEmptySubscriberListAndCatchesReorder(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("classes");
        write(root, spec("fix.A").enchantment());
        write(root, spec("fix.B").enchantment());
        String h = ScanIndex.HEADER;
        // 还没有订阅类时订阅清单只有首行：比对照样通过（解析才会因为空清单报错）
        assertDoesNotThrow(() -> ScanIndex.verifyFreshLines(root, List.of(h, "fix.A", "fix.B"), List.of(h)));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ScanIndex.verifyFreshLines(root, List.of(h, "fix.B", "fix.A"), List.of(h)));
        assertTrue(e.getMessage().contains("顺序"), e.getMessage());
        e = assertThrows(IllegalStateException.class,
                () -> ScanIndex.verifyFreshLines(root, List.of("fix.A", "fix.B"), List.of(h)));
        assertTrue(e.getMessage().contains("少了：" + h), e.getMessage());
    }

    @Test
    void locatesRootOfDirectoryAndJar(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("classes");
        standardFixtures(root);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            Class<?> anchor = loader.loadClass("fix.b.Outer$Inner");
            assertEquals(root.toRealPath(), ScanIndex.locateRoot(anchor).toRealPath());
        }

        Path jar = tmp.resolve("mod.jar");
        ClassFixtures.jar(root, jar);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            Class<?> anchor = loader.loadClass("fix.c.SubClient");
            Path jarRoot = ScanIndex.locateRoot(anchor);
            try {
                assertEquals("/", jarRoot.toString());
                ScanIndexScanner.Result r = scanned(root);
                assertDoesNotThrow(() -> ScanIndex.verifyFresh(jarRoot, r.enchantments(), r.subscribers()));
            } finally {
                jarRoot.getFileSystem().close();
            }
        }
    }

    @Test
    void jarRootAlreadyOpenIsReused(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("classes");
        write(root, spec("fix.E").enchantment());
        Path jar = tmp.resolve("open.jar");
        ClassFixtures.jar(root, jar);
        URI uri = URI.create("jar:" + jar.toUri());
        try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of());
             URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            Path found = ScanIndex.locateRoot(loader.loadClass("fix.E"));
            assertSame(fs, found.getFileSystem());
        }
    }
}
