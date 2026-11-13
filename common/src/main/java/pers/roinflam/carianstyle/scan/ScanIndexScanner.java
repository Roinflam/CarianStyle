package pers.roinflam.carianstyle.scan;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 用 ASM 扫编译输出、生成类清单（格式见 {@link ScanIndex}）。
 * <p>
 * 构建时由 Gradle 任务 {@code generateScanIndex} 调 {@link #main}；开发环境第一次读清单时也用同一份代码重扫一遍比对，
 * 两处判定规则不会分叉。只读类级注解和方法签名（{@code SKIP_CODE}），不加载类、不跑静态块。
 * </p>
 * <p>
 * 注解按描述符认，不引用注解类本身：订阅注解所在的总线包和这里可以各自演进，注解挪包时改这里的常量。
 * 订阅注解的 {@code side} 只认枚举常量名 {@code BOTH} / {@code CLIENT}，不写就是 {@code BOTH}。
 * </p>
 * <p>
 * 扫的时候顺带查两端都登记的订阅类的方法签名（参数、返回值）。总线登记时反射取全部声明的方法，签名里的类型都会被解析，
 * Fabric 专用服上没有客户端类，解析到就 {@code NoClassDefFoundError}：
 * </p>
 * <ul>
 *   <li>出现 {@code event.client} 包下的事件：构建失败。这些事件是模组自己的客户端事件；</li>
 *   <li>出现原版客户端类（{@code net.minecraft.client}、{@code com.mojang.blaze3d}）：同样构建失败。</li>
 * </ul>
 * <p>
 * {@link Result#warnings()} 目前没有会产生警告的检查，留着给以后只提示、不拦构建的项。
 * </p>
 * <p>
 * 覆盖范围只到「订阅类自己声明的方法的参数、返回值描述符」，下面几处会在专用服上同样出错、但这里查不到，
 * 写两端订阅类时自己注意：父类和接口上声明的方法（总线登记会逐级反射）、方法 {@code throws} 里的异常类型、
 * 静态块和方法体（第一次分发时初始化、校验）。字段不查：总线登记不碰它们。
 * </p>
 */
public final class ScanIndexScanner {

    static final String AUTO_REGISTER_ENCHANTMENT = "Lpers/roinflam/carianstyle/annotation/AutoRegisterEnchantment;";
    static final String EVENT_BUS_SUBSCRIBER = "Lpers/roinflam/carianstyle/bus/EventBusSubscriber;";
    static final String SUBSCRIBE_EVENT = "Lpers/roinflam/carianstyle/bus/SubscribeEvent;";
    static final String CLIENT_EVENT_PREFIX = "pers/roinflam/carianstyle/event/client/";
    static final String[] CLIENT_TYPE_PREFIXES = {"net/minecraft/client/", "com/mojang/blaze3d/"};

    /** 扫描结果，两张表都已按类名排序 */
    public record Result(List<String> enchantments, List<ScanIndex.Subscriber> subscribers, List<String> warnings) {
    }

    private ScanIndexScanner() {
    }

    /**
     * 扫若干个根目录下的全部 {@code .class}（同一个类在几个根里都有只算一次）。
     *
     * @throws IllegalStateException 发现上面说的违规写法，或注解的 {@code side} 写了认不出的值
     */
    public static Result scan(Collection<Path> roots) throws IOException {
        Map<String, String> enchantments = new TreeMap<>();
        Map<String, ScanIndex.Subscriber> subscribers = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".class"))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                if (fileName.equals("module-info.class") || fileName.equals("package-info.class")) {
                    continue;
                }
                byte[] bytes;
                try (InputStream in = Files.newInputStream(file)) {
                    bytes = in.readAllBytes();
                }
                ClassInfo info = read(bytes);
                if (info.enchantment) {
                    enchantments.put(info.name, info.name);
                }
                if (info.subscriber) {
                    if (info.side == null) {
                        errors.add(info.name + "：订阅注解的 side 写了认不出的值「" + info.rawSide + "」");
                        continue;
                    }
                    if (info.side == SubscriberSide.BOTH) {
                        for (String bad : info.clientEventMethods) {
                            errors.add(info.name + "#" + bad + "：两端都登记的订阅类里，方法签名不许出现 event.client 包下的事件，"
                                    + "把订阅类改成只在客户端（side = CLIENT），或把这个方法挪进客户端订阅类");
                        }
                        for (String bad : info.clientTypeMethods) {
                            errors.add(info.name + "#" + bad + "：两端都登记的订阅类里，方法签名不许带客户端类（专用服上没有这些类，"
                                    + "登记时反射取签名就出错），把订阅类改成只在客户端，或把这个方法挪进客户端的类");
                        }
                    }
                    subscribers.put(info.name, new ScanIndex.Subscriber(info.side, info.name));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("类清单生成失败：\n  " + String.join("\n  ", errors));
        }
        return new Result(List.copyOf(enchantments.keySet()), List.copyOf(subscribers.values()), List.copyOf(warnings));
    }

    /** 把扫描结果写成两个清单文件，放到 {@code outDir/META-INF/carianstyle/} 下。 */
    public static void write(Result result, Path outDir) throws IOException {
        Path dir = outDir.resolve(ScanIndex.DIR);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ScanIndex.ENCHANTMENTS), ScanIndex.renderEnchantments(result.enchantments()),
                StandardCharsets.UTF_8);
        Files.writeString(dir.resolve(ScanIndex.SUBSCRIBERS), ScanIndex.renderSubscribers(result.subscribers()),
                StandardCharsets.UTF_8);
    }

    /**
     * 构建时入口。参数：输出目录，之后是要扫的编译输出目录（一个或多个）。
     * 输出目录由调用方先清空，这里只管写。
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("用法：ScanIndexScanner <输出目录> <编译输出目录>...");
        }
        List<Path> roots = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            roots.add(Path.of(args[i]));
        }
        Result result = scan(roots);
        write(result, Path.of(args[0]));
        System.out.println("类清单：附魔 " + result.enchantments().size() + " 个，订阅类 " + result.subscribers().size()
                + " 个（其中仅客户端 " + result.subscribers().stream().filter(s -> s.side() == SubscriberSide.CLIENT).count()
                + " 个）");
        for (String warning : result.warnings()) {
            System.out.println("警告：" + warning);
        }
    }

    // ==================== 读一个类 ====================

    private static final class ClassInfo {
        String name;
        boolean enchantment;
        boolean subscriber;
        /** 认出来的端；写了认不出的值时为 null */
        SubscriberSide side = SubscriberSide.BOTH;
        String rawSide;
        final List<String> clientEventMethods = new ArrayList<>();
        final List<String> clientTypeMethods = new ArrayList<>();
    }

    static ClassInfo read(byte[] bytes) {
        ClassInfo info = new ClassInfo();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                info.name = name.replace('/', '.');
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (AUTO_REGISTER_ENCHANTMENT.equals(descriptor)) {
                    info.enchantment = true;
                } else if (EVENT_BUS_SUBSCRIBER.equals(descriptor)) {
                    info.subscriber = true;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitEnum(String name, String enumDescriptor, String value) {
                            if ("side".equals(name)) {
                                info.rawSide = value;
                                info.side = switch (value) {
                                    case "BOTH" -> SubscriberSide.BOTH;
                                    case "CLIENT" -> SubscriberSide.CLIENT;
                                    default -> null;
                                };
                            }
                        }
                    };
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                // 总线登记订阅类时反射取全部声明的方法，参数和返回值里的类型都会被解析，
                // 所以看的是每个方法的整个签名，不只是带监听注解的
                List<Type> types = new ArrayList<>(List.of(Type.getArgumentTypes(descriptor)));
                types.add(Type.getReturnType(descriptor));
                for (Type type : types) {
                    if (type.getSort() == Type.ARRAY) {
                        type = type.getElementType();
                    }
                    if (type.getSort() != Type.OBJECT) {
                        continue;
                    }
                    String internal = type.getInternalName();
                    if (internal.startsWith(CLIENT_EVENT_PREFIX)) {
                        info.clientEventMethods.add(name);
                        break;
                    }
                    for (String prefix : CLIENT_TYPE_PREFIXES) {
                        if (internal.startsWith(prefix)) {
                            info.clientTypeMethods.add(name + "(" + internal + ")");
                            break;
                        }
                    }
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return info;
    }
}
