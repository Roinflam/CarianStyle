package pers.roinflam.carianstyle.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端专属视觉布局配置；只在客户端初始化时读写，渲染时直接读取基本类型。
 * 配置不联网同步，不改变伤害、实际范围或服务端存档；修改文件后重启客户端生效。
 * 缺失项使用默认值，非法项单独回退，损坏文件保留原样以便排查。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientVisualConfig {
    /** 客户端特效总开关，关闭后隐藏本配置管理的所有特效及 HUD。 */
    public static boolean enabled = true;

    /** HUD 距屏幕左侧的距离，单位为 GUI 像素；超出屏幕时自动限制。 */
    public static int hudX = 6;

    /** HUD 距屏幕顶部的距离，单位为 GUI 像素；超出屏幕时自动限制。 */
    public static int hudY = 6;

    /** HUD 整体大小倍率，文字与卡片一起缩放。 */
    public static float hudScale = 1f;

    /** 自动缩放下限，实际不会超过 hudScale。 */
    public static float hudMinScale = 0.62f;

    /** 行数过多时自动缩小；关闭后仍保留分列和折叠提示。 */
    public static boolean hudAutoScale = true;

    /** HUD 卡片行间距，缩放前的 GUI 像素。 */
    public static int hudRowGap = 3;

    /** HUD 列间步长，缩放前的 GUI 像素；名称较长时可加大。 */
    public static int hudColumnWidth = 104;

    /** HUD 最多显示列数，同时受屏幕可用宽度限制。 */
    public static int hudMaxColumns = 3;

    /** HUD 整体不透明度倍率。 */
    public static float hudOpacity = 1f;

    /** HUD 装饰动画开关：扫光、呼吸光、增层闪光和满层火焰。 */
    public static boolean hudDecorations = true;

    /** 自定义实体火焰与第一人称火焰总开关。 */
    public static boolean flamesEnabled = true;

    /** 第一人称火焰开关；白焰仍保持原有第三人称专属行为。 */
    public static boolean firstPersonFlames = true;

    /** 第一人称火焰大小倍率。 */
    public static float firstPersonFlameScale = 1f;

    /** 第一人称火焰水平偏移，渲染单位，正数向右。 */
    public static float firstPersonFlameX = 0f;

    /** 第一人称火焰垂直偏移，渲染单位，正数向上。 */
    public static float firstPersonFlameY = 0f;

    /** 禁止实例化配置容器。 */
    private ClientVisualConfig() {
    }

    /**
     * 客户端启动时加载并补全配置说明；异常时保留文件并使用默认配置。
     */
    public static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(Reference.MOD_ID).resolve("client_visual.json");
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) {
                        throw new IllegalArgumentException("Expected a JSON object");
                    }
                    root = parsed.getAsJsonObject();
                }
            }
            root.addProperty("_comment_en", "CLIENT ONLY. Edit values, then restart the client. No server sync. Per-effect switches: visual_toggle.json. Defaults preserve the original appearance.");
            root.addProperty("_comment_zh", "客户端专属。修改数值后重启客户端，不与服务端同步。各类特效开关见 visual_toggle.json，默认保留原有外观。");
            enabled = readBoolean(root, "enabled", true);
            root.addProperty("enabled", enabled);
            root.addProperty("_enabled_en", "Enable client visual effects (master switch).");
            root.addProperty("_enabled_zh", "客户端特效总开关，关闭后隐藏本配置管理的所有特效及 HUD。");
            hudX = (int) readNumber(root, "hudX", 6, 0, 16384);
            root.addProperty("hudX", hudX);
            root.addProperty("_hudX_en", "HUD left offset in GUI pixels; clamped to the current screen. Range: 0 ~ 16384.");
            root.addProperty("_hudX_zh", "HUD 距屏幕左侧的距离，单位为 GUI 像素；超出屏幕时自动限制。");
            hudY = (int) readNumber(root, "hudY", 6, 0, 16384);
            root.addProperty("hudY", hudY);
            root.addProperty("_hudY_en", "HUD top offset in GUI pixels; clamped to the current screen. Range: 0 ~ 16384.");
            root.addProperty("_hudY_zh", "HUD 距屏幕顶部的距离，单位为 GUI 像素；超出屏幕时自动限制。");
            hudScale = (float) readNumber(root, "hudScale", 1f, 0.25, 3);
            root.addProperty("hudScale", hudScale);
            root.addProperty("_hudScale_en", "HUD size multiplier, including text and cards. Range: 0.25 ~ 3.");
            root.addProperty("_hudScale_zh", "HUD 整体大小倍率，文字与卡片一起缩放。");
            hudMinScale = (float) readNumber(root, "hudMinScale", 0.62f, 0.25, 3);
            root.addProperty("hudMinScale", hudMinScale);
            root.addProperty("_hudMinScale_en", "Minimum automatic scale; never exceeds hudScale. Range: 0.25 ~ 3.");
            root.addProperty("_hudMinScale_zh", "自动缩放下限，实际不会超过 hudScale。");
            hudAutoScale = readBoolean(root, "hudAutoScale", true);
            root.addProperty("hudAutoScale", hudAutoScale);
            root.addProperty("_hudAutoScale_en", "Shrink HUD when rows do not fit; columns and overflow remain enabled.");
            root.addProperty("_hudAutoScale_zh", "行数过多时自动缩小；关闭后仍保留分列和折叠提示。");
            hudRowGap = (int) readNumber(root, "hudRowGap", 3, 0, 40);
            root.addProperty("hudRowGap", hudRowGap);
            root.addProperty("_hudRowGap_en", "Gap between HUD cards before scaling. Range: 0 ~ 40.");
            root.addProperty("_hudRowGap_zh", "HUD 卡片行间距，缩放前的 GUI 像素。");
            hudColumnWidth = (int) readNumber(root, "hudColumnWidth", 104, 80, 1000);
            root.addProperty("hudColumnWidth", hudColumnWidth);
            root.addProperty("_hudColumnWidth_en", "Column stride before scaling; increase for long translated names. Range: 80 ~ 1000.");
            root.addProperty("_hudColumnWidth_zh", "HUD 列间步长，缩放前的 GUI 像素；名称较长时可加大。");
            hudMaxColumns = (int) readNumber(root, "hudMaxColumns", 3, 1, 8);
            root.addProperty("hudMaxColumns", hudMaxColumns);
            root.addProperty("_hudMaxColumns_en", "Maximum HUD columns; also limited by screen width. Range: 1 ~ 8.");
            root.addProperty("_hudMaxColumns_zh", "HUD 最多显示列数，同时受屏幕可用宽度限制。");
            hudOpacity = (float) readNumber(root, "hudOpacity", 1f, 0.1, 1);
            root.addProperty("hudOpacity", hudOpacity);
            root.addProperty("_hudOpacity_en", "Opacity multiplier for the entire HUD. Range: 0.1 ~ 1.");
            root.addProperty("_hudOpacity_zh", "HUD 整体不透明度倍率。");
            hudDecorations = readBoolean(root, "hudDecorations", true);
            root.addProperty("hudDecorations", hudDecorations);
            root.addProperty("_hudDecorations_en", "Enable HUD shimmer, pulsing glow, stack flashes and full-stack flames.");
            root.addProperty("_hudDecorations_zh", "HUD 装饰动画开关：扫光、呼吸光、增层闪光和满层火焰。");
            flamesEnabled = readBoolean(root, "flamesEnabled", true);
            root.addProperty("flamesEnabled", flamesEnabled);
            root.addProperty("_flamesEnabled_en", "Enable custom entity and first-person flames.");
            root.addProperty("_flamesEnabled_zh", "自定义实体火焰与第一人称火焰总开关。");
            firstPersonFlames = readBoolean(root, "firstPersonFlames", true);
            root.addProperty("firstPersonFlames", firstPersonFlames);
            root.addProperty("_firstPersonFlames_en", "Enable first-person flames; white flames remain third-person only.");
            root.addProperty("_firstPersonFlames_zh", "第一人称火焰开关；白焰仍保持原有第三人称专属行为。");
            firstPersonFlameScale = (float) readNumber(root, "firstPersonFlameScale", 1f, 0.25, 2);
            root.addProperty("firstPersonFlameScale", firstPersonFlameScale);
            root.addProperty("_firstPersonFlameScale_en", "First-person flame size multiplier. Range: 0.25 ~ 2.");
            root.addProperty("_firstPersonFlameScale_zh", "第一人称火焰大小倍率。");
            firstPersonFlameX = (float) readNumber(root, "firstPersonFlameX", 0f, -2, 2);
            root.addProperty("firstPersonFlameX", firstPersonFlameX);
            root.addProperty("_firstPersonFlameX_en", "First-person flame horizontal offset in render units; positive moves right. Range: -2 ~ 2.");
            root.addProperty("_firstPersonFlameX_zh", "第一人称火焰水平偏移，渲染单位，正数向右。");
            firstPersonFlameY = (float) readNumber(root, "firstPersonFlameY", 0f, -2, 2);
            root.addProperty("firstPersonFlameY", firstPersonFlameY);
            root.addProperty("_firstPersonFlameY_en", "First-person flame vertical offset in render units; positive moves up. Range: -2 ~ 2.");
            root.addProperty("_firstPersonFlameY_zh", "第一人称火焰垂直偏移，渲染单位，正数向上。");
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root, writer);
            }
            LogUtil.debug("Client visual configuration loaded: %s", path);
        } catch (Exception exception) {
            LogUtil.error("Failed to load or save client visual configuration: " + path, exception);
        }
    }

    /**
     * 校验布尔值，禁止将字符串或数字隐式转换为开关。
     * @param root 配置对象
     * @param key 配置键
     * @param fallback 默认值
     * @return 有效布尔值或默认值
     */
    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        LogUtil.warn("Invalid client visual option %s; using default", key);
        return fallback;
    }

    /**
     * 校验有限数值及范围，非法配置单项回退，不影响其它设置。
     * @param root 配置对象
     * @param key 配置键
     * @param fallback 默认值
     * @param minimum 最小值
     * @param maximum 最大值
     * @return 范围内数值或默认值；整数字段由调用方截断小数
     */
    private static double readNumber(JsonObject root, String key, double fallback,
                                     double minimum, double maximum) {
        JsonElement value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number = value.getAsDouble();
            if (Double.isFinite(number) && number >= minimum && number <= maximum) {
                return number;
            }
        }
        LogUtil.warn("Invalid client visual option %s; using default", key);
        return fallback;
    }
}
