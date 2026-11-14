package pers.roinflam.carianstyle.scan;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * 测试夹具：用 ASM 直接造类文件。
 * <p>
 * 注解只按描述符写进类文件，不需要注解类真的存在，所以测试不依赖总线包里的注解类。
 * 造出来的类都能被类加载器加载（有公开无参构造器）。
 * </p>
 */
public final class ClassFixtures {

    private ClassFixtures() {
    }

    /** 一个类的写法 */
    public static final class Spec {
        final String binaryName;
        String superName = "java/lang/Object";
        String[] interfaces = new String[0];
        boolean isInterface;
        boolean enchantment;
        boolean subscriber;
        String side;
        String listenerName;
        String listenerDescriptor;
        boolean listenerAnnotated = true;

        Spec(String binaryName) {
            this.binaryName = binaryName;
        }

        public Spec enchantment() {
            this.enchantment = true;
            return this;
        }

        /** side 为 null 表示注解上不写 side */
        public Spec subscriber(String side) {
            this.subscriber = true;
            this.side = side;
            return this;
        }

        /** 加一个静态监听方法，参数类型给内部名（如 pers/roinflam/carianstyle/event/client/RenderX） */
        public Spec listener(String name, String paramInternalName, boolean annotated) {
            return method(name, "(L" + paramInternalName + ";)V", annotated);
        }

        /** 加一个静态方法，直接给方法描述符 */
        public Spec method(String name, String descriptor, boolean annotated) {
            this.listenerName = name;
            this.listenerDescriptor = descriptor;
            this.listenerAnnotated = annotated;
            return this;
        }

        public Spec asInterface() {
            this.isInterface = true;
            return this;
        }

        public Spec implementing(String... internalNames) {
            this.interfaces = internalNames;
            return this;
        }
    }

    public static Spec spec(String binaryName) {
        return new Spec(binaryName);
    }

    public static byte[] bytes(Spec s) {
        String internal = s.binaryName.replace('.', '/');
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int access = s.isInterface
                ? Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT
                : Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        cw.visit(Opcodes.V17, access, internal, null, s.superName, s.interfaces);
        if (s.enchantment) {
            cw.visitAnnotation(ScanIndexScanner.AUTO_REGISTER_ENCHANTMENT, true).visitEnd();
        }
        if (s.subscriber) {
            AnnotationVisitor av = cw.visitAnnotation(ScanIndexScanner.EVENT_BUS_SUBSCRIBER, true);
            if (s.side != null) {
                av.visitEnum("side", "Lpers/roinflam/carianstyle/bus/EventBusSubscriber$Side;", s.side);
            }
            av.visitEnd();
        }
        if (!s.isInterface) {
            MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, s.superName, "<init>", "()V", false);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(0, 0);
            init.visitEnd();
        }
        if (s.listenerName != null) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, s.listenerName,
                    s.listenerDescriptor, null, null);
            if (s.listenerAnnotated) {
                mv.visitAnnotation(ScanIndexScanner.SUBSCRIBE_EVENT, true).visitEnd();
            }
            mv.visitCode();
            if (s.listenerDescriptor.endsWith(")V")) {
                mv.visitInsn(Opcodes.RETURN);
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** 把类写到 root 下对应的包目录里 */
    public static Path write(Path root, Spec s) throws IOException {
        Path file = root.resolve(s.binaryName.replace('.', '/') + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes(s));
        return file;
    }

    /** 往 root 下放一个任意文件 */
    public static void writeText(Path root, String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    /** 把 dir 下的全部文件打成 jar */
    public static void jar(Path dir, Path jar) throws IOException {
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out);
             var walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile).sorted()::iterator) {
                jos.putNextEntry(new JarEntry(dir.relativize(p).toString().replace('\\', '/')));
                jos.write(Files.readAllBytes(p));
                jos.closeEntry();
            }
        }
    }
}
