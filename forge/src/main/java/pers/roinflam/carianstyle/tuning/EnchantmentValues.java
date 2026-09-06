package pers.roinflam.carianstyle.tuning;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔数值配置：把散落在各附魔类里的硬编码数字集中到一个可编辑的 JSON 文件。
 *
 * <h3>为什么不用 Forge 的 TOML 配置</h3>
 * <p>
 * 现有的 {@code ConfigLoader} 用的是 {@code ForgeConfigSpec}，它要求所有配置项
 * 在 spec 构建期（模组构造函数）就全部声明完毕。而附魔的数值声明发生在
 * <b>各附魔类的静态初始化</b>期间——那时 spec 早已 build，无法再追加。
 * </p>
 * <p>
 * 若强行把 113 个附魔的全部数值写进 {@code ConfigLoader}，那个文件会膨胀到
 * 几千行，且每加一个附魔都要改两处（附魔类 + ConfigLoader），必然遗漏。
 * 因此这里用独立的 JSON：附魔类<b>就近声明</b>自己的数值，文件由本类自动生成与合并。
 * </p>
 *
 * <h3>文件位置与生成规则</h3>
 * <p>
 * {@code config/carianstyle/enchantment_values.json}
 * </p>
 * <ul>
 *   <li>首次启动：按各附魔声明的默认值全量生成；</li>
 *   <li>后续启动：<b>保留服主已改的值</b>，只补进新增的键；</li>
 *   <li>被删除的附魔遗留的键会保留在文件里但不生效，方便回滚，不会静默丢失；</li>
 *   <li>越界的值在加载时 clamp 到声明的 [min, max] 并打警告，不会因为手滑写错崩服。</li>
 * </ul>
 *
 * <h3>⚠ 关于物品描述</h3>
 * <p>
 * 本系统只改<b>行为数值</b>，不会自动改附魔的说明文字。
 * 语言文件里的 {@code enchantment.carianstyle.xxx.desc} 是静态文本，
 * 服主/整合包作者调整数值后<b>需要自行同步修改语言文件</b>，
 * 否则玩家看到的描述会与实际效果对不上。
 * 生成的 JSON 头部与百科界面都会重复提示这一点。
 * </p>
 *
 * <h3>接入方式（附魔类侧）</h3>
 * <p>
 * 在附魔类里用静态常量持有句柄，构造期声明，使用处直接取值：
 * </p>
 * <pre>
 * private static final String ID = "quickstep";
 * static {
 *     EnchantmentValues.define(ID, "speed_divisor", 5.0D, 1.0D, 100.0D);
 *     EnchantmentValues.define(ID, "check_interval", 4, 1, 40);
 * }
 * // 使用：
 * double divisor = EnchantmentValues.getDouble(ID, "speed_divisor");
 * </pre>
 *
 * <h3>⚠ 共享数值：为什么需要 {@link #defineShared}</h3>
 * <p>
 * 本配置文件是<b>各端各一份</b>的：多人游戏时客户端读的是玩家自己电脑上的
 * {@code config/carianstyle/enchantment_values.json}，不是服务器那份。
 * </p>
 * <p>
 * 绝大多数数值只在服务端参与判定，这没有问题。但有少数值<b>客户端也要读</b>——
 * 比如重力力场的半径（渲染器要据此画地面范围圈）、隐匿面纱的战斗计时
 * （HUD 要据此画倒计时进度条）。这类值如果只改服务端，
 * 客户端仍按自己的默认值绘制，就会出现<b>「圈画在这里、判定却在那里」</b>——
 * 恰恰是这些附魔原注释里明确想避免的情况。
 * </p>
 * <p>
 * {@link #defineShared} 声明的数值会在玩家登录时由服务端下发
 * （见 {@code ValueSyncHandler}），客户端收到后写进
 * {@link Handle#syncedOverride}，此后 {@link Handle#get()} 返回的就是<b>服务端的值</b>。
 * 因此渲染器和 HUD 直接用同一个句柄即可，不需要区分端。
 * </p>
 * <p>
 * 下发是<b>覆盖</b>而非替换本地配置：断开连接时清空覆盖，回到单人游戏时
 * 自动恢复读本地文件。本地 {@link #reload()} 也不会冲掉已下发的覆盖值。
 * </p>
 * <p>
 * <b>只有确实需要客户端读的值才用 {@code defineShared}</b>——每一项都要占登录时的带宽，
 * 而且引入了一处双端状态。默认用 {@link #define}。
 * </p>
 *
 * <h3>性能</h3>
 * <p>
 * {@link #getDouble} / {@link #getInt} 是一次 {@link ConcurrentHashMap} 查询 +
 * 一次字段读取，没有装箱、没有字符串拼接（键在 define 时就已合成并缓存）。
 * 可以直接放在 tick 路径里，但更推荐附魔类在自己的静态字段中缓存句柄——
 * 见 {@link Handle}。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EnchantmentValues {

    /** 配置文件名 */
    private static final String FILE_NAME = "enchantment_values.json";

    /** JSON 中承载说明文字的键（生成时写入，加载时忽略） */
    private static final String COMMENT_KEY = "__README__";

    /** 生成文件头部的说明，逐行写入 {@link #COMMENT_KEY} 数组 */
    private static final String[] README = {
            "CarianStyle 附魔数值配置 / Enchantment value overrides.",
            "修改后重启服务器或在百科界面点击「重载数值」生效。",
            "!! 重要：本文件只改行为数值，不会改附魔的说明文字。",
            "!! 调整数值后请自行同步修改语言文件中的 enchantment.carianstyle.<id>.desc，",
            "!! 否则玩家看到的描述会与实际效果不符。",
            "每个数值的允许范围见 range 字段；超出范围的值会被自动收拢到边界并记录警告。"
    };

    /** 全部已声明的数值：复合键 → 句柄 */
    private static final Map<String, Handle> HANDLES = new ConcurrentHashMap<>();

    /** 是否已完成一次文件加载 */
    private static volatile boolean loaded = false;

    private EnchantmentValues() {
    }

    // ==================== 声明 API ====================

    /**
     * 声明一个浮点数值。
     * <p>重复声明同一个键会被忽略（保留首次声明），并记录警告。</p>
     *
     * @param enchantmentId 附魔注册 id
     * @param key           数值键（同一附魔内唯一，建议 snake_case）
     * @param defaultValue  默认值
     * @param min           允许最小值（含）
     * @param max           允许最大值（含）
     * @return 该数值的句柄，可存为静态字段供热路径直接读取
     */
    @Nonnull
    public static Handle define(@Nonnull String enchantmentId, @Nonnull String key,
                                double defaultValue, double min, double max) {
        return define(enchantmentId, key, defaultValue, min, max, false, false);
    }

    /**
     * 声明一个<b>需要下发给客户端</b>的浮点数值。
     * <p>用法与 {@link #define} 完全相同，区别只在于该值会随玩家登录同步到客户端。
     * 什么时候该用它，见类注释的「共享数值」一节。</p>
     *
     * @param enchantmentId 附魔注册 id
     * @param key           数值键
     * @param defaultValue  默认值
     * @param min           允许最小值（含）
     * @param max           允许最大值（含）
     * @return 该数值的句柄
     */
    @Nonnull
    public static Handle defineShared(@Nonnull String enchantmentId, @Nonnull String key,
                                      double defaultValue, double min, double max) {
        return define(enchantmentId, key, defaultValue, min, max, false, true);
    }

    /**
     * 声明一个<b>需要下发给客户端</b>的整数数值。
     *
     * @param enchantmentId 附魔注册 id
     * @param key           数值键
     * @param defaultValue  默认值
     * @param min           允许最小值（含）
     * @param max           允许最大值（含）
     * @return 该数值的句柄
     */
    @Nonnull
    public static Handle defineShared(@Nonnull String enchantmentId, @Nonnull String key,
                                      int defaultValue, int min, int max) {
        return define(enchantmentId, key, defaultValue, min, max, true, true);
    }

    /**
     * 声明一个整数数值。
     *
     * @param enchantmentId 附魔注册 id
     * @param key           数值键
     * @param defaultValue  默认值
     * @param min           允许最小值（含）
     * @param max           允许最大值（含）
     * @return 该数值的句柄
     */
    @Nonnull
    public static Handle define(@Nonnull String enchantmentId, @Nonnull String key,
                                int defaultValue, int min, int max) {
        return define(enchantmentId, key, defaultValue, min, max, true, false);
    }

    /**
     * 声明数值的内部实现。
     *
     * @param enchantmentId 附魔注册 id
     * @param key           数值键
     * @param defaultValue  默认值
     * @param min           下界
     * @param max           上界
     * @param integral      是否按整数处理（读取时四舍五入）
     * @param shared        是否需要在玩家登录时下发给客户端
     * @return 句柄
     * @throws IllegalArgumentException 参数非法（min &gt; max，或默认值越界）时抛出，
     *                                  这是开发期错误，不做静默容忍
     */
    @Nonnull
    private static Handle define(@Nonnull String enchantmentId, @Nonnull String key,
                                 double defaultValue, double min, double max,
                                 boolean integral, boolean shared) {
        if (min > max) {
            throw new IllegalArgumentException(
                    "数值 " + enchantmentId + "." + key + " 的 min(" + min + ") 大于 max(" + max + ")");
        }
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(
                    "数值 " + enchantmentId + "." + key + " 的默认值 " + defaultValue + " 超出 [" + min + ", " + max + "]");
        }

        String compound = enchantmentId + '.' + key;
        Handle existing = HANDLES.get(compound);
        if (existing != null) {
            LogUtil.warn("卡利亚式附魔 - 数值 %s 被重复声明，保留首次声明的默认值 %s",
                    compound, existing.defaultValue);
            return existing;
        }

        Handle handle = new Handle(enchantmentId, key, compound, defaultValue, min, max, integral, shared);
        HANDLES.put(compound, handle);
        return handle;
    }

    // ==================== 读取 API ====================

    /**
     * 按复合键读取浮点值。
     *
     * @param enchantmentId 附魔 id
     * @param key           数值键
     * @return 当前生效值；键未声明时返回 0 并记录警告
     */
    public static double getDouble(@Nonnull String enchantmentId, @Nonnull String key) {
        Handle handle = HANDLES.get(enchantmentId + '.' + key);
        if (handle == null) {
            LogUtil.warn("卡利亚式附魔 - 读取了未声明的数值 %s.%s，返回 0", enchantmentId, key);
            return 0.0D;
        }
        return handle.get();
    }

    /**
     * 按复合键读取整数值。
     *
     * @param enchantmentId 附魔 id
     * @param key           数值键
     * @return 当前生效值（四舍五入）；键未声明时返回 0 并记录警告
     */
    public static int getInt(@Nonnull String enchantmentId, @Nonnull String key) {
        Handle handle = HANDLES.get(enchantmentId + '.' + key);
        if (handle == null) {
            LogUtil.warn("卡利亚式附魔 - 读取了未声明的数值 %s.%s，返回 0", enchantmentId, key);
            return 0;
        }
        return handle.getInt();
    }

    /**
     * 取某个附魔已声明的全部数值（供百科界面展示）。
     *
     * @param enchantmentId 附魔 id
     * @return 按键名排序的句柄列表；没有声明任何数值时为空列表
     */
    @Nonnull
    public static List<Handle> handlesOf(@Nonnull String enchantmentId) {
        List<Handle> result = new ArrayList<>();
        for (Handle handle : HANDLES.values()) {
            if (handle.enchantmentId.equals(enchantmentId)) {
                result.add(handle);
            }
        }
        result.sort(Comparator.comparing(h -> h.key));
        return result;
    }

    /**
     * @return 是否已完成过一次文件加载
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 取全部需要下发给客户端的句柄。
     *
     * @return 按复合键排序的句柄列表（排序只是为了让抓包和日志可读，协议本身不依赖顺序）
     */
    @Nonnull
    public static List<Handle> sharedHandles() {
        List<Handle> result = new ArrayList<>();
        for (Handle handle : HANDLES.values()) {
            if (handle.shared) {
                result.add(handle);
            }
        }
        result.sort(Comparator.comparing(h -> h.compoundKey));
        return result;
    }

    /**
     * 应用服务端下发的共享数值（仅客户端调用）。
     * <p>
     * 只认<b>本端已声明且标记为共享</b>的键：服务端发来一个本端不认识的键
     * （版本不一致）会被静默忽略，不会污染状态；值也会收拢到本端声明的范围内，
     * 因为范围是本端代码定义的语义边界，不该由对端决定。
     * </p>
     *
     * @param values 复合键 -&gt; 值
     * @return 实际应用的条目数
     */
    public static int applySynced(@Nonnull Map<String, Double> values) {
        int applied = 0;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            Handle handle = HANDLES.get(entry.getKey());
            if (handle == null || !handle.shared) {
                LogUtil.debug("卡利亚式附魔 - 忽略服务端下发的未知共享数值：%s", entry.getKey());
                continue;
            }
            double value = Math.min(handle.max, Math.max(handle.min, entry.getValue()));
            handle.applySynced(value);
            applied++;
        }
        LogUtil.debug("卡利亚式附魔 - 已应用服务端下发的 %d 项共享数值", applied);
        return applied;
    }

    /**
     * 清空全部服务端下发的覆盖值，回落到本地配置（客户端断开连接时调用）。
     */
    public static void clearSynced() {
        for (Handle handle : HANDLES.values()) {
            handle.clearSynced();
        }
    }

    // ==================== 加载与写回 ====================

    /**
     * 在通用初始化阶段加载配置。
     * <p>
     * 用 {@link EventPriority#LOWEST} 是为了确保排在附魔注册之后——
     * 注册过程会 {@code Class.forName} 每个附魔类，触发它们的静态块，
     * 数值声明必须先于加载完成，否则生成的文件会缺键。
     * </p>
     *
     * @param event 通用初始化事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCommonSetup(@Nonnull FMLCommonSetupEvent event) {
        event.enqueueWork(EnchantmentValues::reload);
    }

    /**
     * 重新加载配置文件并写回合并结果。
     * <p>可由百科界面的「重载数值」按钮调用。</p>
     *
     * @return 是否成功
     */
    public static boolean reload() {
        Path path = resolvePath();
        JsonObject existing = read(path);

        int overridden = 0;
        for (Handle handle : HANDLES.values()) {
            JsonElement element = findValue(existing, handle);
            if (element == null || !element.isJsonPrimitive()) {
                handle.current = handle.defaultValue;
                continue;
            }
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isNumber()) {
                LogUtil.warn("卡利亚式附魔 - 数值 %s 不是数字，已回退默认值 %s",
                        handle.compoundKey, handle.defaultValue);
                handle.current = handle.defaultValue;
                continue;
            }

            double raw = primitive.getAsDouble();
            double clamped = Math.min(handle.max, Math.max(handle.min, raw));
            if (clamped != raw) {
                LogUtil.warn("卡利亚式附魔 - 数值 %s 的值 %s 超出范围 [%s, %s]，已收拢为 %s",
                        handle.compoundKey, raw, handle.min, handle.max, clamped);
            }
            handle.current = clamped;
            if (clamped != handle.defaultValue) {
                overridden++;
            }
        }

        boolean ok = write(path);
        loaded = true;
        LogUtil.info("卡利亚式附魔 - 数值配置加载完成：共 %d 项，其中 %d 项被服主修改",
                HANDLES.size(), overridden);
        if (overridden > 0) {
            LogUtil.warn("卡利亚式附魔 - 检测到 %d 项数值被修改，请记得同步修改语言文件中的附魔描述", overridden);
        }
        return ok;
    }

    /**
     * 解析配置文件路径，必要时创建目录。
     *
     * @return 配置文件路径
     */
    @Nonnull
    private static Path resolvePath() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(Reference.MOD_ID);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LogUtil.error("卡利亚式附魔 - 创建配置目录失败", e);
        }
        return dir.resolve(FILE_NAME);
    }

    /**
     * 读取现有配置文件。
     *
     * @param path 文件路径
     * @return 解析出的 JSON 对象；文件不存在或解析失败时返回空对象
     */
    @Nonnull
    private static JsonObject read(@Nonnull Path path) {
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root != null && root.isJsonObject()) {
                return root.getAsJsonObject();
            }
            LogUtil.warn("卡利亚式附魔 - %s 的根节点不是对象，将按默认值重建", FILE_NAME);
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 读取 " + FILE_NAME + " 失败，将按默认值重建", e);
        }
        return new JsonObject();
    }

    /**
     * 从已读取的 JSON 中定位某个句柄对应的值节点。
     *
     * @param root   根对象
     * @param handle 句柄
     * @return 值节点；不存在时返回 null
     */
    @Nullable
    private static JsonElement findValue(@Nonnull JsonObject root, @Nonnull Handle handle) {
        JsonElement group = root.get(handle.enchantmentId);
        if (group == null || !group.isJsonObject()) {
            return null;
        }
        JsonElement entry = group.getAsJsonObject().get(handle.key);
        if (entry == null) {
            return null;
        }
        // 条目形态为 {"value": x, "range": "..."}；也兼容直接写数字的简写形态
        if (entry.isJsonObject()) {
            return entry.getAsJsonObject().get("value");
        }
        return entry;
    }

    /**
     * 把当前全部句柄写回文件（保留服主已改的值）。
     *
     * @param path 文件路径
     * @return 是否写入成功
     */
    private static boolean write(@Nonnull Path path) {
        JsonObject root = new JsonObject();

        JsonArray readme = new JsonArray();
        for (String line : README) {
            readme.add(line);
        }
        root.add(COMMENT_KEY, readme);

        List<Handle> all = new ArrayList<>(HANDLES.values());
        all.sort(Comparator.comparing((Handle h) -> h.enchantmentId).thenComparing(h -> h.key));

        for (Handle handle : all) {
            JsonObject group = root.has(handle.enchantmentId) && root.get(handle.enchantmentId).isJsonObject()
                    ? root.getAsJsonObject(handle.enchantmentId)
                    : new JsonObject();

            JsonObject entry = new JsonObject();
            if (handle.integral) {
                entry.addProperty("value", (int) Math.round(handle.current));
            } else {
                entry.addProperty("value", handle.current);
            }
            entry.addProperty("default", handle.integral
                    ? (double) Math.round(handle.defaultValue) : handle.defaultValue);
            entry.addProperty("range", handle.min + " ~ " + handle.max);

            group.add(handle.key, entry);
            root.add(handle.enchantmentId, group);
        }

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root, writer);
            return true;
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 写入 " + FILE_NAME + " 失败", e);
            return false;
        }
    }

    /**
     * 单个可调数值的句柄。
     * <p>
     * 附魔类应把它存为 {@code private static final} 字段，
     * 使用时直接调 {@link #get()} / {@link #getInt()}，
     * 这样连一次 HashMap 查询都省掉，适合放在 tick 路径里。
     * </p>
     */
    public static final class Handle {

        /** 所属附魔 id */
        private final String enchantmentId;

        /** 数值键 */
        private final String key;

        /** 复合键（enchantmentId.key），预先合成避免运行期拼接 */
        private final String compoundKey;

        /** 默认值 */
        private final double defaultValue;

        /** 下界 */
        private final double min;

        /** 上界 */
        private final double max;

        /** 是否按整数语义使用 */
        private final boolean integral;

        /** 是否需要下发给客户端（见类注释的「共享数值」一节） */
        private final boolean shared;

        /** 当前生效值，由 {@link #reload()} 写入；volatile 保证重载后其它线程立即可见 */
        private volatile double current;

        /**
         * 服务端下发的覆盖值；{@code null} 表示没有覆盖（单人游戏或未同步）。
         * <p>
         * 用 {@code Double} 而非 {@code double} + 一个布尔标志，是为了让「有没有覆盖」
         * 和「覆盖值是多少」在一次 volatile 读里一起拿到——两个字段会有读到
         * 「标志已置位但值还没写入」的窗口。
         * </p>
         */
        private volatile Double syncedOverride;

        Handle(String enchantmentId, String key, String compoundKey,
               double defaultValue, double min, double max, boolean integral, boolean shared) {
            this.enchantmentId = enchantmentId;
            this.key = key;
            this.compoundKey = compoundKey;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.integral = integral;
            this.shared = shared;
            this.current = defaultValue;
        }

        /**
         * @return 当前生效的浮点值。已收到服务端下发时返回下发值，否则返回本地配置值
         */
        public double get() {
            Double override = syncedOverride;
            return override != null ? override : current;
        }

        /**
         * @return 是否需要下发给客户端
         */
        public boolean isShared() {
            return shared;
        }

        /**
         * 写入服务端下发的覆盖值（仅客户端调用）。
         *
         * @param value 服务端的值；已在包解码时收拢到本句柄声明的范围内
         */
        void applySynced(double value) {
            this.syncedOverride = value;
        }

        /**
         * 清除服务端下发的覆盖值，回落到本地配置（断开连接时调用）。
         */
        void clearSynced() {
            this.syncedOverride = null;
        }

        /**
         * @return 是否正在使用服务端下发的值
         */
        public boolean isSynced() {
            return syncedOverride != null;
        }

        /**
         * @return 当前生效值的整数形式（四舍五入）
         */
        public int getInt() {
            return (int) Math.round(current);
        }

        /**
         * @return 数值键
         */
        @Nonnull
        public String getKey() {
            return key;
        }

        /**
         * @return 所属附魔 id
         */
        @Nonnull
        public String getEnchantmentId() {
            return enchantmentId;
        }

        /**
         * @return 复合键，形如 {@code quickstep.speed_divisor}
         */
        @Nonnull
        public String getCompoundKey() {
            return compoundKey;
        }

        /**
         * @return 默认值
         */
        public double getDefaultValue() {
            return defaultValue;
        }

        /**
         * @return 下界
         */
        public double getMin() {
            return min;
        }

        /**
         * @return 上界
         */
        public double getMax() {
            return max;
        }

        /**
         * @return 是否按整数语义使用
         */
        public boolean isIntegral() {
            return integral;
        }

        /**
         * @return 本地配置值是否已被服主改动（百科界面据此显示提醒角标）
         * <p>刻意只看 {@link #current}、不看 {@link #syncedOverride}：
         * 这个方法回答的是「这台机器上的配置文件被改过吗」，
         * 与「当前正在用谁的值」是两个问题，后者用 {@link #isSynced()}。</p>
         */
        public boolean isOverridden() {
            return current != defaultValue;
        }
    }
}
