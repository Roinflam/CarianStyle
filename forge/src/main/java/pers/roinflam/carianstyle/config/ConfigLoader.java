package pers.roinflam.carianstyle.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 卡利亚风格模组配置类
 * Carian Style Mod Configuration Class
 *
 * 统一管理所有配置项
 * Unified management of all configuration items
 * 配置文件位置:config/carianstyle-common.toml
 * Config file location: config/carianstyle-common.toml
 *
 * <p>
 * v2.2修复：bake() 末尾调用 EnchantmentBase.invalidateAllDisabledCaches()，
 * 确保配置热重载后 uninstallEnchantment 黑名单立即生效。
 * </p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigLoader {

    // ==================== 配置规范 ====================

    public static final ForgeConfigSpec COMMON_CONFIG;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

        COMMON = new Common(COMMON_BUILDER);

        COMMON_CONFIG = COMMON_BUILDER.build();
    }

    /**
     * 通用配置类
     */
    public static class Common {

        // ==================== 日志系统配置 ====================

        public final ForgeConfigSpec.BooleanValue enableDetailedLogging;

        // ==================== 附魔系统配置 ====================

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> uninstallEnchantment;
        public final ForgeConfigSpec.IntValue rockBlasterMaxRange;
        public final ForgeConfigSpec.BooleanValue rockBlasterSuppressCommonDrops;
        public final ForgeConfigSpec.IntValue prayerfulStrikeMaxHealth;

        // ==================== 真伤系统配置 ====================

        public final ForgeConfigSpec.BooleanValue enableTrueDamage;

        // ==================== 宝藏附魔配置 ====================

        public final ForgeConfigSpec.BooleanValue isTreasureVeryRareEnchantment;
        public final ForgeConfigSpec.BooleanValue isTreasureRareEnchantment;
        public final ForgeConfigSpec.BooleanValue isTreasureUncommonEnchantment;

        // ==================== 游戏平衡配置 ====================

        public final ForgeConfigSpec.BooleanValue levelLimit;
        public final ForgeConfigSpec.DoubleValue enchantingDifficulty;

        /**
         * 构造配置
         *
         * @param builder 配置构建器
         */
        public Common(ForgeConfigSpec.Builder builder) {

            // ========== 日志系统 ==========
            builder.comment("═══════════════════════════════════════════════════════════════")
                    .comment("Logging System Configuration")
                    .comment("日志系统配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("logging");

            enableDetailedLogging = builder
                    .comment("Enable detailed logging for debugging purposes.")
                    .comment("启用详细日志以便调试。")
                    .comment("When enabled, the mod will output additional debug information.")
                    .comment("启用后，模组会输出额外的调试信息以帮助诊断问题。")
                    .comment("This may slightly impact performance and should be disabled in production.")
                    .comment("这可能会轻微影响性能，在生产环境中应该关闭。")
                    .define("enableDetailedLogging", false);

            builder.pop();

            // ========== 附魔系统 ==========
            builder.comment("")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("Enchantment System Configuration")
                    .comment("附魔系统配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("enchantment");

            uninstallEnchantment = builder
                    .comment("Fill in the IDs of enchantments you want to disable.")
                    .comment("在此填入你想要禁用注册的附魔ID。")
                    .comment("You can find enchantment IDs in the en_us.json file.")
                    .comment("你可以在 en_us.json 文件中找到相关附魔的ID。")
                    .comment("Example: [\"scarlet_rot\", \"doomed_death\"]")
                    .comment("示例: [\"scarlet_rot\", \"doomed_death\"]")
                    .defineList("uninstallEnchantment", Collections.emptyList(), obj -> obj instanceof String);

            rockBlasterMaxRange = builder
                    .comment("The maximum mining radius of the Rock Blaster Enchantment.")
                    .comment("碎岩者附魔的最大挖掘半径。")
                    .comment("Higher values allow faster mining but may cause lag.")
                    .comment("更高的值允许更快的挖掘，但可能导致卡顿。")
                    .defineInRange("rockBlasterMaxRange", 10, 0, 20);

            rockBlasterSuppressCommonDrops = builder
                    .comment("When enabled, Rock Blaster will not drop items for common stone-type blocks.")
                    .comment("启用后，碎岩者破坏常见石头类方块时不会掉落物品，以减少掉落物造成的卡顿。")
                    .comment("Affected blocks: stone, cobblestone, deepslate, cobbled_deepslate, granite, diorite,")
                    .comment("andesite, tuff, calcite, smooth_basalt, gravel, netherrack, basalt, blackstone, end_stone")
                    .comment("受影响方块：石头、圆石、深板岩、花岗岩、闪长岩、安山岩、凝灰岩、方解石、")
                    .comment("平滑玄武岩、沙砾、地狱岩、玄武岩、黑石、末地石")
                    .define("rockBlasterSuppressCommonDrops", false);

            prayerfulStrikeMaxHealth = builder
                    .comment("Set the maximum stackable life limit for the Prayer Strike Enchantment.")
                    .comment("设置祈祷打击附魔的最大可叠加生命值上限。")
                    .defineInRange("prayerfulStrikeMaxHealth", 1000, 0, 1000000);

            builder.pop();

            // ========== 真伤系统 ==========
            builder.comment("")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("True Damage System Configuration")
                    .comment("真伤系统配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("trueDamage");

            enableTrueDamage = builder
                    .comment("Enable the advanced true damage system.")
                    .comment("启用高级真伤系统。")
                    .comment("When enabled, the mod will use complex field detection to bypass armor and protection.")
                    .comment("启用后，模组会使用复杂的字段检测来绕过护甲和保护。")
                    .comment("When disabled, the mod will use simple setHealth method (may not work on some modded entities).")
                    .comment("禁用后，模组会使用简单的setHealth方法（可能对某些模组生物无效）。")
                    .comment("Recommended: true (default)")
                    .comment("推荐: true（默认）")
                    .define("enableTrueDamage", true);

            builder.pop();

            // ========== 宝藏附魔配置 ==========
            builder.comment("")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("Treasure Enchantment Configuration")
                    .comment("宝藏附魔配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("treasure");

            isTreasureVeryRareEnchantment = builder
                    .comment("Convert all Very Rare enchantments to Treasure enchantments.")
                    .comment("将所有超稀有(Very Rare)级别的附魔变为宝藏附魔。")
                    .comment("Treasure enchantments cannot be obtained from enchanting tables.")
                    .comment("宝藏附魔无法从附魔台获得，只能从战利品中获取。")
                    .define("isTreasureVeryRareEnchantment", false);

            isTreasureRareEnchantment = builder
                    .comment("Convert all Rare enchantments to Treasure enchantments.")
                    .comment("将所有稀有(Rare)级别的附魔变为宝藏附魔。")
                    .define("isTreasureRareEnchantment", false);

            isTreasureUncommonEnchantment = builder
                    .comment("Convert all Uncommon enchantments to Treasure enchantments.")
                    .comment("将所有罕见(Uncommon)级别的附魔变为宝藏附魔。")
                    .define("isTreasureUncommonEnchantment", false);

            builder.pop();

            // ========== 游戏平衡配置 ==========
            builder.comment("")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("Game Balance Configuration")
                    .comment("游戏平衡配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("balance");

            levelLimit = builder
                    .comment("Limit some enchantments to prevent overpowered effects.")
                    .comment("为某些附魔在超过等级上限后设置限制，以防止过于无敌。")
                    .define("levelLimit", false);

            enchantingDifficulty = builder
                    .comment("Set the difficulty multiplier for enchantments.")
                    .comment("设置附魔的难度倍率。")
                    .comment("1.0 is the default difficulty.")
                    .comment("1.0 是默认难度。")
                    .defineInRange("enchantingDifficulty", 1.0, 0.1, 10.0);

            builder.pop();
        }
    }

    // ==================== 便捷访问属性 ====================

    /** 是否启用详细日志 */
    public static boolean enableDetailedLogging = false;

    /** 禁用的附魔ID列表 */
    public static String[] uninstallEnchantment = new String[0];

    /** 碎岩者最大范围 */
    public static int rockBlasterMaxRange = 10;

    /** 碎岩者是否抑制常见石头掉落物 */
    public static boolean rockBlasterSuppressCommonDrops = false;

    /** 祈祷打击最大生命值 */
    public static int prayerfulStrikeMaxHealth = 1000;

    /** 是否启用高级真伤系统 */
    public static boolean enableTrueDamage = true;

    /** 超稀有附魔是否为宝藏 */
    public static boolean isTreasureVeryRaryEnchantment = false;

    /** 稀有附魔是否为宝藏 */
    public static boolean isTreasureRaryEnchantment = false;

    /** 罕见附魔是否为宝藏 */
    public static boolean isTreasureUncommonEnchantment = false;

    /** 是否启用等级限制 */
    public static boolean levelLimit = false;

    /** 附魔难度倍率 */
    public static double enchantingDifficulty = 1.0;

    /**
     * 从配置规范同步到静态字段
     * <p>
     * 在配置加载或修改后调用。
     * v2.2修复：末尾清除所有附魔的禁用状态缓存，确保黑名单变更立即生效。
     * </p>
     */
    public static void bake() {
        enableDetailedLogging = COMMON.enableDetailedLogging.get();

        List<? extends String> enchList = COMMON.uninstallEnchantment.get();
        uninstallEnchantment = enchList.toArray(new String[0]);

        rockBlasterMaxRange = COMMON.rockBlasterMaxRange.get();
        rockBlasterSuppressCommonDrops = COMMON.rockBlasterSuppressCommonDrops.get();
        prayerfulStrikeMaxHealth = COMMON.prayerfulStrikeMaxHealth.get();

        enableTrueDamage = COMMON.enableTrueDamage.get();

        isTreasureVeryRaryEnchantment = COMMON.isTreasureVeryRareEnchantment.get();
        isTreasureRaryEnchantment = COMMON.isTreasureRareEnchantment.get();
        isTreasureUncommonEnchantment = COMMON.isTreasureUncommonEnchantment.get();

        levelLimit = COMMON.levelLimit.get();
        enchantingDifficulty = COMMON.enchantingDifficulty.get();

        // v2.2修复：清除所有附魔的禁用状态缓存
        // 确保 uninstallEnchantment 配置变更后，isDisabled() 会重新计算
        EnchantmentBase.invalidateAllDisabledCaches();
    }

    /**
     * 配置加载事件监听器
     * Config loading event listener
     */
    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            bake();
        }
    }

    /**
     * 配置重载事件监听器
     * Config reloading event listener
     */
    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            bake();
        }
    }

    // 私有构造函数，防止实例化
    private ConfigLoader() {
    }
}