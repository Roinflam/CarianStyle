package pers.roinflam.carianstyle.scan;

import pers.roinflam.carianstyle.platform.Services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 构建时生成的类清单：哪些类是附魔、哪些类是事件订阅类。
 * <p>
 * <b>为什么要清单：</b>NeoForge / Forge 能在运行期拿到加载器扫好的注解表，Fabric 没有这种东西。
 * 两边都改成构建时用 ASM 扫一遍编译输出，把结果写成资源文件打进 jar，运行期只读文件、按类名加载，
 * 两个平台发现的类和先后就完全一样了。
 * </p>
 * <p>
 * <b>格式</b>（{@code META-INF/carianstyle/} 下两个 UTF-8 文件，换行一律 {@code \n}）：
 * </p>
 * <ul>
 *   <li>第一行固定是 {@link #HEADER}；格式变了就改版本号，旧代码读新格式会直接报错，不会读错。</li>
 *   <li>{@code enchantments.list}：之后每行一个附魔类的二进制名（内部类带 {@code $}）。</li>
 *   <li>{@code subscribers.list}：之后每行 {@code BOTH} 或 {@code CLIENT}，一个制表符，再是订阅类的二进制名。</li>
 *   <li>按类名的 {@link String#compareTo} 排序、不重复。登记顺序就是清单顺序，所以排序是格式的一部分，读的时候也核。</li>
 * </ul>
 * <p>
 * <b>不会用到过期清单</b>，三道：
 * </p>
 * <ol>
 *   <li>清单由 Gradle 任务从编译输出生成，平台模块处理资源时依赖它；开发运行和测试台都经 Gradle，
 *       类一改清单就跟着重新生成（删掉的类不会残留：生成前先清空输出目录）。</li>
 *   <li>开发运行的准备任务也依赖资源处理，交给 Gradle 起的运行同样拿到新清单。IDE 自己编译、不交给 Gradle 时，
 *       IDE 的输出里根本没有清单，读的时候直接报「找不到类清单」，不会读到旧的。</li>
 *   <li>开发环境里第一次读清单时，用同一套扫描代码把模组根目录再扫一遍、和清单逐行比，不一致直接抛异常
 *       （绕过 Gradle 直接编译、清单没跟上时在这里拦住）。正式包不扫。</li>
 * </ol>
 * <p>
 * 读到的清单缺失、为空、首行不对、行格式不对、没排序都抛异常：少登记一个附魔，存档里带它的物品就会丢附魔，
 * 宁可开不起来。
 * </p>
 */
public final class ScanIndex {

    /** 两个清单的首行 */
    public static final String HEADER = "# carianstyle scan index v1";
    /** 清单所在目录（资源路径，不带开头的斜杠） */
    public static final String DIR = "META-INF/carianstyle/";
    public static final String ENCHANTMENTS = "enchantments.list";
    public static final String SUBSCRIBERS = "subscribers.list";

    private static final Pattern BINARY_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    /** 订阅清单的一行 */
    public record Subscriber(SubscriberSide side, String className) {
        public Subscriber {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(className, "className");
        }
    }

    private static List<String> enchantments;
    private static List<Subscriber> subscribers;
    private static boolean devChecked;

    private ScanIndex() {
    }

    /** 附魔类名，按清单顺序。第一次调用时读清单（开发环境顺带做第三道保新）。 */
    public static synchronized List<String> enchantmentClasses() {
        if (enchantments == null) {
            List<String> parsed = parseEnchantments(ENCHANTMENTS, readResource(ENCHANTMENTS));
            // 自检通过才缓存：调用方吞掉异常再来取，也拿不到没核过的清单
            checkFreshInDevelopment();
            enchantments = parsed;
        }
        return enchantments;
    }

    /** 全部订阅类（两端的都在），按清单顺序。 */
    public static synchronized List<Subscriber> subscribers() {
        if (subscribers == null) {
            List<Subscriber> parsed = parseSubscribers(SUBSCRIBERS, readResource(SUBSCRIBERS));
            checkFreshInDevelopment();
            subscribers = parsed;
        }
        return subscribers;
    }

    // ==================== 读 ====================

    private static List<String> readResource(String name) {
        // 用类相对的绝对路径。NeoForge 上模组在命名模块里，会先在本模组里找；Fabric 上是整个类路径里第一个，
        // 靠目录名 META-INF/carianstyle/ 只有本模组用来避免撞名
        try (InputStream in = ScanIndex.class.getResourceAsStream("/" + DIR + name)) {
            if (in == null) {
                throw new IllegalStateException("找不到类清单 " + DIR + name + "：构建没有生成，或者打包漏了");
            }
            return readLines(in);
        } catch (IOException e) {
            throw new IllegalStateException("读类清单 " + DIR + name + " 出错", e);
        }
    }

    static List<String> readLines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** 解析附魔清单；{@code source} 只用在报错信息里。 */
    static List<String> parseEnchantments(String source, List<String> lines) {
        List<String> body = body(source, lines);
        List<String> names = new ArrayList<>(body.size());
        for (int i = 0; i < body.size(); i++) {
            names.add(checkName(source, i + 2, body.get(i)));
        }
        checkSorted(source, names);
        return Collections.unmodifiableList(names);
    }

    /** 解析订阅清单。 */
    static List<Subscriber> parseSubscribers(String source, List<String> lines) {
        List<String> body = body(source, lines);
        List<Subscriber> result = new ArrayList<>(body.size());
        List<String> names = new ArrayList<>(body.size());
        for (int i = 0; i < body.size(); i++) {
            int lineNo = i + 2;
            String line = body.get(i);
            int tab = line.indexOf('\t');
            if (tab < 0 || line.indexOf('\t', tab + 1) >= 0) {
                throw bad(source, lineNo, "应为「端<TAB>类名」", line);
            }
            SubscriberSide side;
            try {
                side = SubscriberSide.valueOf(line.substring(0, tab));
            } catch (IllegalArgumentException e) {
                throw bad(source, lineNo, "端只能是 BOTH 或 CLIENT", line);
            }
            String name = checkName(source, lineNo, line.substring(tab + 1));
            result.add(new Subscriber(side, name));
            names.add(name);
        }
        checkSorted(source, names);
        return Collections.unmodifiableList(result);
    }

    /** 核首行、去掉首行，返回内容行；空清单也算错。 */
    private static List<String> body(String source, List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalStateException("类清单 " + source + " 是空文件");
        }
        if (!HEADER.equals(stripCr(lines.get(0)))) {
            throw bad(source, 1, "首行应为「" + HEADER + "」", lines.get(0));
        }
        if (lines.size() == 1) {
            throw new IllegalStateException("类清单 " + source + " 只有首行、一个类都没有");
        }
        List<String> body = new ArrayList<>(lines.size() - 1);
        for (int i = 1; i < lines.size(); i++) {
            String line = stripCr(lines.get(i));
            if (line.isEmpty()) {
                throw bad(source, i + 1, "不许有空行", line);
            }
            body.add(line);
        }
        return body;
    }

    private static String checkName(String source, int lineNo, String name) {
        if (!BINARY_NAME.matcher(name).matches()) {
            throw bad(source, lineNo, "不是合法的类名", name);
        }
        return name;
    }

    private static void checkSorted(String source, List<String> names) {
        for (int i = 1; i < names.size(); i++) {
            int c = names.get(i - 1).compareTo(names.get(i));
            if (c >= 0) {
                throw bad(source, i + 2, c == 0 ? "类名重复" : "没有按类名排序", names.get(i));
            }
        }
    }

    private static String stripCr(String line) {
        // 被人在 Windows 上手改过、换行变成 CRLF 时照样认
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static IllegalStateException bad(String source, int lineNo, String why, String line) {
        return new IllegalStateException("类清单 " + source + " 第 " + lineNo + " 行" + why + "：「" + line + "」");
    }

    // ==================== 写（构建时用） ====================

    /** 附魔清单的文件内容（会排序、去重）。 */
    static String renderEnchantments(List<String> classNames) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        classNames.stream().distinct().sorted().forEach(n -> sb.append(n).append('\n'));
        return sb.toString();
    }

    /** 订阅清单的文件内容（按类名排序）。 */
    static String renderSubscribers(List<Subscriber> entries) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        entries.stream()
                .sorted((a, b) -> a.className().compareTo(b.className()))
                .forEach(s -> sb.append(s.side().name()).append('\t').append(s.className()).append('\n'));
        return sb.toString();
    }

    // ==================== 第三道保新：开发环境重扫 ====================

    private static void checkFreshInDevelopment() {
        if (devChecked || !Services.platform().isDevelopment()) {
            return;
        }
        // 两个文件都按原始行比，不经解析：一个清单本来就该是空的时候（还没有订阅类），不妨碍核另一个。
        // 通过了才记下，没通过的下次再取还会再核、再抛
        verifyFreshLines(locateRoot(ScanIndex.class), readResource(ENCHANTMENTS), readResource(SUBSCRIBERS));
        devChecked = true;
    }

    /**
     * 把 {@code root} 下的类重扫一遍，和给定的清单内容比，不一致就抛异常并列出差在哪。
     */
    static void verifyFresh(Path root, List<String> enchantments, List<Subscriber> subscribers) {
        verifyFreshLines(root, renderEnchantments(enchantments).lines().toList(),
                renderSubscribers(subscribers).lines().toList());
    }

    /** 同上，给的是清单文件的原始行（含首行）。 */
    static void verifyFreshLines(Path root, List<String> enchantmentLines, List<String> subscriberLines) {
        ScanIndexScanner.Result actual;
        try {
            actual = ScanIndexScanner.scan(List.of(root));
        } catch (IOException e) {
            throw new IllegalStateException("开发环境重扫模组目录 " + root.toUri() + " 出错", e);
        }
        List<String> problems = new ArrayList<>();
        diff(problems, ENCHANTMENTS, enchantmentLines, renderEnchantments(actual.enchantments()).lines().toList());
        diff(problems, SUBSCRIBERS, subscriberLines, renderSubscribers(actual.subscribers()).lines().toList());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("类清单过期（和 " + root.toUri() + " 下的类对不上），重新经 Gradle 构建：\n  "
                    + String.join("\n  ", problems));
        }
    }

    private static void diff(List<String> out, String name, List<String> fileLines, List<String> expected) {
        List<String> listed = fileLines.stream().map(ScanIndex::stripCr).toList();
        if (listed.equals(expected)) {
            return;
        }
        int before = out.size();
        for (String s : expected) {
            if (!listed.contains(s)) {
                out.add(name + " 少了：" + s);
            }
        }
        for (String s : listed) {
            if (!expected.contains(s)) {
                out.add(name + " 多了：" + s);
            }
        }
        if (out.size() == before) {
            out.add(name + " 内容相同但顺序或重复行不对");
        }
    }

    /**
     * 由一个模组类所在的位置倒推模组根目录（开发环境里是编译输出目录；NeoForge 上是把类和资源目录合起来的那个虚拟根）。
     */
    static Path locateRoot(Class<?> anchor) {
        String entry = anchor.getName().replace('.', '/') + ".class";
        URL url = anchor.getResource("/" + entry);
        if (url == null) {
            throw new IllegalStateException("开发环境自检找不到 " + entry + " 所在位置");
        }
        Path classFile;
        try {
            URI uri = url.toURI();
            try {
                classFile = Path.of(uri);
            } catch (FileSystemNotFoundException e) {
                // jar: 这类要先开文件系统；开发环境一般是目录，走不到这里。开出来的不关：返回的路径要用它，
                // 而且只在开发环境开一次
                classFile = FileSystems.newFileSystem(uri, Map.of()).provider().getPath(uri);
            }
        } catch (URISyntaxException | IOException | RuntimeException e) {
            throw new IllegalStateException("开发环境自检认不出 " + url + " 这种位置", e);
        }
        Path root = classFile;
        for (int i = entry.split("/").length; i > 0; i--) {
            root = root.getParent();
            if (root == null) {
                throw new IllegalStateException("开发环境自检从 " + classFile + " 倒推不出模组根目录");
            }
        }
        return root;
    }

    /** 测试用：清掉缓存，下次重新读。 */
    static synchronized void resetForTest() {
        enchantments = null;
        subscribers = null;
        devChecked = false;
    }
}
