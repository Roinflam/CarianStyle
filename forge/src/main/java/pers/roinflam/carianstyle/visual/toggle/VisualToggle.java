package pers.roinflam.carianstyle.visual.toggle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 客户端特效开关（纯客户端，默认全开）。
 *
 * <h3>为什么用普通 JSON 而不是 Forge 的 CLIENT 配置</h3>
 * <p>
 * 这个开关会在<b>渲染热路径</b>上每帧被查询 20 多次
 * （每个渲染器一次）。{@code ForgeConfigSpec.BooleanValue#get()} 内部有
 * 缓存失效检查与 {@code Supplier} 间接调用，虽然单次开销不大，
 * 但没有理由在每帧固定调用的位置上付这个成本。
 * </p>
 * <p>
 * 本类改用 {@code boolean[]}，按枚举 ordinal 直接下标访问——
 * {@link #isEnabled} 编译后就是一次数组读取，可以放心写在渲染器的第一行。
 * </p>
 *
 * <h3>存储</h3>
 * <p>
 * {@code config/carianstyle/visual_toggle.json}。所有项默认 true；
 * 文件缺失或损坏时全部回落到 true，不会因为配置问题让玩家看不到特效。
 * 新增的特效类型在旧文件中找不到键时同样默认 true，玩家不会「更新后少了特效」。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>
 * 写入只发生在界面操作（客户端主线程），读取发生在渲染线程——在原版客户端
 * 二者是同一个线程。{@code enabled} 数组声明为 {@code volatile} 引用，
 * 整体替换而非逐元素修改，保证读取方要么看到旧的完整状态、要么看到新的完整状态。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class VisualToggle {

    /** 配置文件名 */
    private static final String FILE_NAME = "visual_toggle.json";

    /**
     * 开关状态，下标为 {@link VisualEffectType#ordinal()}。
     * <p>整体替换而非原地修改，见类注释的线程安全说明。</p>
     */
    private static volatile boolean[] enabled = defaults();

    /** 是否已从磁盘加载过 */
    private static boolean loaded = false;

    private VisualToggle() {
    }

    /**
     * 查询某个特效是否启用。
     * <p>渲染热路径专用：一次数组读取，无分支之外的开销。</p>
     *
     * @param type 特效类型
     * @return 是否启用
     */
    public static boolean isEnabled(@Nonnull VisualEffectType type) {
        return enabled[type.ordinal()];
    }

    /**
     * 设置某个特效的开关并立即写盘。
     *
     * @param type  特效类型
     * @param value 是否启用
     */
    public static void set(@Nonnull VisualEffectType type, boolean value) {
        boolean[] copy = enabled.clone();
        copy[type.ordinal()] = value;
        enabled = copy;
        save();
    }

    /**
     * 批量设置某个分组下的全部特效。
     *
     * @param category 分组
     * @param value    是否启用
     */
    public static void setCategory(@Nonnull VisualEffectType.Category category, boolean value) {
        boolean[] copy = enabled.clone();
        for (VisualEffectType type : VisualEffectType.values()) {
            if (type.getCategory() == category) {
                copy[type.ordinal()] = value;
            }
        }
        enabled = copy;
        save();
    }

    /**
     * 全部恢复为默认（全开）并写盘。
     */
    public static void resetAll() {
        enabled = defaults();
        save();
    }

    /**
     * 统计某分组下已启用的数量。
     *
     * @param category 分组
     * @return 已启用数量
     */
    public static int enabledCountOf(@Nonnull VisualEffectType.Category category) {
        int count = 0;
        boolean[] snapshot = enabled;
        for (VisualEffectType type : VisualEffectType.values()) {
            if (type.getCategory() == category && snapshot[type.ordinal()]) {
                count++;
            }
        }
        return count;
    }

    /**
     * 确保已从磁盘加载（惰性，首次打开界面或首次渲染时触发）。
     */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    /**
     * @return 全部为 true 的默认数组
     */
    @Nonnull
    private static boolean[] defaults() {
        boolean[] array = new boolean[VisualEffectType.values().length];
        Arrays.fill(array, true);
        return array;
    }

    /**
     * 解析配置文件路径，必要时创建目录。
     *
     * @return 文件路径
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
     * 从磁盘加载开关状态。
     * <p>任何异常都回落到「全开」，不让配置问题影响游戏体验。</p>
     */
    private static void load() {
        Path path = resolvePath();
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }

        boolean[] result = defaults();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root == null || !root.isJsonObject()) {
                LogUtil.warn("卡利亚式附魔 - %s 格式不正确，已回落到全部启用", FILE_NAME);
                enabled = result;
                return;
            }
            JsonObject object = root.getAsJsonObject();
            for (VisualEffectType type : VisualEffectType.values()) {
                JsonElement element = object.get(type.getKey());
                // 找不到键 = 新增的特效类型，保持默认 true
                if (element != null && element.isJsonPrimitive()
                        && element.getAsJsonPrimitive().isBoolean()) {
                    result[type.ordinal()] = element.getAsBoolean();
                }
            }
            enabled = result;
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 读取 " + FILE_NAME + " 失败，已回落到全部启用", e);
            enabled = defaults();
        }
    }

    /**
     * 把当前开关状态写盘。
     */
    private static void save() {
        Path path = resolvePath();
        JsonObject root = new JsonObject();

        boolean[] snapshot = enabled;
        for (VisualEffectType type : VisualEffectType.values()) {
            root.addProperty(type.getKey(), snapshot[type.ordinal()]);
        }

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root, writer);
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 写入 " + FILE_NAME + " 失败", e);
        }
    }
}
