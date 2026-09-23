package pers.roinflam.carianstyle.visual.toggle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.config.ClientVisualConfig;
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
        return ClientVisualConfig.enabled && enabled[type.ordinal()];
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
            return;
        }
        // 旧文件成功解析后补齐双语说明，保留原有开关值。
        save();
    }

    /**
     * 把当前开关状态写盘。
     */
    private static void save() {
        Path path = resolvePath();
        JsonObject root = new JsonObject();
        root.addProperty("_comment_en", "CLIENT ONLY. Local visual switches; no damage or server changes. HUD layout and master switch: client_visual.json. Restart after manual edits.");
        root.addProperty("_comment_zh", "客户端专属。仅改变本机显示，不影响伤害和服务端。HUD 布局及总开关见 client_visual.json；手动修改后重启客户端。");
        root.addProperty("_hemorrhage_en", "Hemorrhage — Blood spray and pools on afflicted entities");
        root.addProperty("_hemorrhage_zh", "出血 — 患者身上的飙血与血泊");
        root.addProperty("_scarlet_rot_en", "Scarlet Rot — Rot spore mist");
        root.addProperty("_scarlet_rot_zh", "猩红腐败 — 猩红腐败的孢子雾");
        root.addProperty("_frostbite_en", "Frostbite — Frost mist and ice crystals");
        root.addProperty("_frostbite_zh", "冻伤 — 冻伤的冰雾与霜晶");
        root.addProperty("_incision_en", "Incision — Laceration wound effect");
        root.addProperty("_incision_zh", "切割 — 切割的伤口特效");
        root.addProperty("_sleep_en", "Sleep — Floating sleep symbols");
        root.addProperty("_sleep_zh", "睡眠 — 睡眠状态的漂浮符号");
        root.addProperty("_bad_omen_en", "Bad Omen — Ominous dark haze");
        root.addProperty("_bad_omen_zh", "不祥预感 — 不祥预感的黑雾");
        root.addProperty("_gravitas_en", "Gravitas — Gravity field distortion and range ring");
        root.addProperty("_gravitas_zh", "重力压制 — 重力力场的空间扭曲与范围圈");
        root.addProperty("_golden_tree_en", "Erdtree Blessing — Golden radiance of the Erdtree");
        root.addProperty("_golden_tree_zh", "黄金树祝福 — 黄金树祝福的金色光辉");
        root.addProperty("_combat_art_burst_en", "Combat Art Burst — Burst effect on combat art release");
        root.addProperty("_combat_art_burst_zh", "战技爆发 — 战技释放瞬间的爆发特效");
        root.addProperty("_combat_art_effect_en", "Combat Art Effect — Generic combat art trails and impacts");
        root.addProperty("_combat_art_effect_zh", "战技特效 — 战技的通用轨迹与冲击特效");
        root.addProperty("_combat_art_extra_en", "Combat Art Extras — Additional decorative combat art visuals");
        root.addProperty("_combat_art_extra_zh", "战技附加特效 — 战技的额外装饰性特效");
        root.addProperty("_hard_arrow_range_en", "Hard Arrow Range — Range indicator for the hard arrow art");
        root.addProperty("_hard_arrow_range_zh", "硬箭范围 — 硬箭战技的射程指示");
        root.addProperty("_waterfowl_flurry_en", "Waterfowl Dance — Blade trails of the waterfowl dance");
        root.addProperty("_waterfowl_flurry_zh", "猎犬连击 — 猎犬连击的刀光");
        root.addProperty("_carian_retaliation_en", "Carian Retaliation — Magic barrier of Carian retaliation");
        root.addProperty("_carian_retaliation_zh", "卡利亚反击 — 卡利亚反击的魔法屏障");
        root.addProperty("_shield_ward_en", "Shield Ward — Protective ward around the shield");
        root.addProperty("_shield_ward_zh", "盾墙 — 盾牌防护的护壁");
        root.addProperty("_aoe_en", "Area Effects — Ground visuals for fixed and follow AOE");
        root.addProperty("_aoe_zh", "范围特效 — 定点与跟随型范围伤害的地面特效");
        root.addProperty("_aura_ground_en", "Aura Circles — Ground rune circles for aura enchantments");
        root.addProperty("_aura_ground_zh", "地面法阵 — 光环类附魔的地面符文法阵");
        root.addProperty("_glintblades_en", "Glintblades — Trails and glow of glintblade projectiles");
        root.addProperty("_glintblades_zh", "辉剑 — 辉剑投射物的拖尾与光效");
        root.addProperty("_calamity_en", "Calamity — Full-screen calamity spectacle");
        root.addProperty("_calamity_zh", "灾祸 — 灾祸大招的全屏演出");
        root.addProperty("_dark_moon_en", "Dark Moon — Dark moon orb spectacle");
        root.addProperty("_dark_moon_zh", "暗月 — 暗月的月轮演出");
        root.addProperty("_daedicar_woe_en", "Daedicar's Woe — Wailing effect of Daedicar's Woe");
        root.addProperty("_daedicar_woe_zh", "妲德凯尔之殇 — 妲德凯尔之殇的哀嚎特效");
        root.addProperty("_howl_shabriri_en", "Howl of Shabriri — Madness effect of Shabriri's howl");
        root.addProperty("_howl_shabriri_zh", "夏布利利之嚎 — 夏布利利之嚎的疯狂特效");
        root.addProperty("_time_reversal_en", "Time Reversal — Rewind afterimages");
        root.addProperty("_time_reversal_zh", "时间逆转 — 时间逆转的回溯残影");
        root.addProperty("_stack_hud_en", "Stack HUD — On-screen stacks and cooldown timers");
        root.addProperty("_stack_hud_zh", "叠层 HUD — 屏幕上的叠层与冷却倒计时");


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
