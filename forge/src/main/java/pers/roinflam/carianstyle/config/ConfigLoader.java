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
 * <p>
 * v2.3新增：怪物附魔触发开关
 * <ul>
 *   <li>{@link #allowMobTriggerEnchantments}：是否允许非玩家生物（怪物等）触发卡利亚式附魔，默认开启。
 *       关闭后可大幅降低存在大量附魔生物时的服务器开销。</li>
 *   <li>{@link #allowMobTriggerDeathEnchantments}：是否允许非玩家生物触发死亡/濒死类附魔
 *       （包括 DEAD 分类的全部附魔，以及 RECOLLECT 中的 满月/死诞者/时间逆转），默认关闭。
 *       建议保持禁用，避免精英怪/Boss获得不公平的复活和范围反伤能力。</li>
 * </ul>
 * </p>
 *
 * <p>
 * v2.4新增：附魔获取途径平衡开关
 * <ul>
 *   <li>{@link #useVanillaRarityWeight}：是否把模组稀有度映射回原版权重体系，默认开启（即修复生效）。</li>
 *   <li>{@link #allowVillagerBookTrade}：是否允许模组附魔进入村民附魔书交易池，默认关闭（即修复生效）。</li>
 * </ul>
 * 两项的背景与影响详见 {@code EnchantmentBase} 的类注释。
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

        // ==================== 附魔获取途径平衡配置（v2.4新增） ====================

        public final ForgeConfigSpec.BooleanValue useVanillaRarityWeight;
        public final ForgeConfigSpec.BooleanValue allowVillagerBookTrade;

        // ==================== 怪物附魔触发配置（v2.3新增） ====================

        public final ForgeConfigSpec.BooleanValue allowMobTriggerEnchantments;
        public final ForgeConfigSpec.BooleanValue allowMobTriggerDeathEnchantments;

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

            // ---------- v2.4新增：附魔台候选权重 ----------
            useVanillaRarityWeight = builder
                    .comment("Map CarianStyle enchantment rarities onto the vanilla weight system.")
                    .comment("将卡利亚式附魔的稀有度映射到原版的权重体系。")
                    .comment("Vanilla picks enchanting-table candidates by weight:")
                    .comment("原版附魔台按权重加权抽取候选附魔：")
                    .comment("COMMON=10, UNCOMMON=5, RARE=2, VERY_RARE=1")
                    .comment("Without this, every CarianStyle enchantment registers as COMMON (weight 10),")
                    .comment("关闭时所有卡利亚式附魔都以 COMMON（权重 10）注册，")
                    .comment("i.e. as common as Efficiency / Protection / Sharpness, which floods the table")
                    .comment("即与「效率 / 保护 / 锋利」同档，会把原版附魔挤出附魔台候选池。")
                    .comment("and crowds vanilla enchantments out of the candidate pool.")
                    .comment("SIDE EFFECT: anvil combine cost also reads rarity (1x/2x/4x/8x),")
                    .comment("副作用：铁砧合成花费同样读取稀有度（1/2/4/8 倍率），")
                    .comment("so Rare and Very Rare enchantments become more expensive on the anvil.")
                    .comment("因此 Rare 与 Very Rare 档的附魔在铁砧上会变贵。")
                    .comment("Set to false to restore the old behaviour (everything weight 10).")
                    .comment("设为 false 可恢复旧行为（全部权重 10）。")
                    .comment("Default: true")
                    .comment("默认值：true")
                    .define("useVanillaRarityWeight", true);

            // ---------- v2.4新增：村民附魔书交易池 ----------
            allowVillagerBookTrade = builder
                    .comment("Allow CarianStyle enchantments to appear in librarian enchanted book trades.")
                    .comment("是否允许卡利亚式附魔出现在图书管理员的附魔书交易中。")
                    .comment("WARNING: vanilla picks trade books UNIFORMLY at random from every tradeable")
                    .comment("警告：原版是从所有可交易附魔里【等概率】随机抽一本，完全不看稀有度权重。")
                    .comment("enchantment, ignoring rarity weights entirely. Enabling this adds 100+ entries")
                    .comment("启用后会往原版约 40 个可交易附魔的池子里塞进一百多个条目，")
                    .comment("to a pool of roughly 40, cutting the odds of rolling Mending to about a quarter.")
                    .comment("「经验修补」的出现概率会掉到原来的四分之一左右。")
                    .comment("This does NOT affect enchanting tables or loot chests.")
                    .comment("此选项不影响附魔台与战利品箱的获取途径。")
                    .comment("Default: false (block)")
                    .comment("默认值：false（拦截）")
                    .define("allowVillagerBookTrade", false);

            builder.pop();

            // ========== 怪物附魔触发配置（v2.3新增） ==========
            builder.comment("")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("Mob Enchantment Trigger Configuration")
                    .comment("怪物附魔触发配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("mobTrigger");

            allowMobTriggerEnchantments = builder
                    .comment("Allow non-player entities (mobs) to trigger CarianStyle enchantments.")
                    .comment("是否允许非玩家生物（如怪物）触发卡利亚式附魔。")
                    .comment("Disabling this can significantly reduce server load when many enchanted mobs are present.")
                    .comment("禁用后可大幅降低存在大量附魔生物时的服务器开销，因为会跳过对怪物装备槽位的附魔扫描。")
                    .comment("Note: This only blocks enchantments routed through the central event handler.")
                    .comment("注意：仅拦截走中央事件处理器（EnchantmentEventHandler）路径的附魔触发。")
                    .comment("Enchantments using their own @SubscribeEvent listeners may still trigger on mobs.")
                    .comment("使用独立 @SubscribeEvent 监听器的附魔（部分附魔）仍可能在怪物身上触发。")
                    .comment("Default: true (allow)")
                    .comment("默认值：true（允许）")
                    .define("allowMobTriggerEnchantments", true);

            allowMobTriggerDeathEnchantments = builder
                    .comment("Allow non-player entities to trigger death / last-stand enchantments.")
                    .comment("是否允许非玩家生物触发死亡/濒死类附魔。")
                    .comment("Covers all DEAD-category enchantments (Scarlet Aeonia, Frenzied Spread, Greatblade Phalanx,")
                    .comment("Ancient Dragon Lightning) and the death-triggered RECOLLECT-category enchantments")
                    .comment("(Full Moon, Living Corpse, Time Reversal).")
                    .comment("覆盖所有 DEAD 分类附魔（猩红艾奥尼亚、发狂扩散、巨剑阵、古龙雷击）")
                    .comment("以及 RECOLLECT 分类中的濒死触发型附魔（满月、死诞者、时间逆转）。")
                    .comment("Recommended to keep disabled to prevent boss/elite mobs from gaining unfair revival/AOE abilities.")
                    .comment("建议保持禁用，避免精英怪/Boss获得不公平的复活和范围反伤能力，防止玩家被打破防。")
                    .comment("Note: This only applies when allowMobTriggerEnchantments is enabled.")
                    .comment("注意：仅当 allowMobTriggerEnchantments 启用时此选项才生效。")
                    .comment("Default: false (block)")
                    .comment("默认值：false（拦截）")
                    .define("allowMobTriggerDeathEnchantments", false);

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
     * 是否把模组稀有度映射到原版权重体系（v2.4新增）
     * <p>
     * 默认 true。原版附魔台按 {@code getRarity().getWeight()} 加权抽取候选
     * （COMMON=10、UNCOMMON=5、RARE=2、VERY_RARE=1），
     * 而 {@code EnchantmentBase} 的主构造函数只能在 super() 阶段传占位值 COMMON，
     * 若不做映射，全部模组附魔都是权重 10，会把原版附魔挤出候选池。
     * </p>
     * <p>
     * 副作用：铁砧合成花费同样读取稀有度，Rare / Very Rare 档会变贵。
     * 设为 false 可恢复旧行为。
     * </p>
     */
    public static boolean useVanillaRarityWeight = true;

    /**
     * 是否允许模组附魔进入村民附魔书交易池（v2.4新增）
     * <p>
     * 默认 false。1.20.1 的 {@code VillagerTrades.EnchantBookForEmeralds}
     * 是从所有 {@code isTradeable()} 为 true 的附魔里<b>等概率</b>抽取，不看权重。
     * 一百多个模组附魔全部可交易会把原版约 40 个的池子撑到 150+，
     * 导致「经验修补」等原版关键附魔的出现概率掉到原来的四分之一左右。
     * </p>
     * <p>
     * 不影响附魔台与战利品箱的获取途径。
     * </p>
     */
    public static boolean allowVillagerBookTrade = false;

    /**
     * 是否允许非玩家生物触发卡利亚式附魔
     * <p>
     * 默认 true（允许）。关闭后可大幅降低存在大量附魔怪物时的服务器开销，
     * 因为 EnchantmentEventHandler 会在伤害事件扫描入口直接跳过非玩家持有者。
     * </p>
     * <p>
     * 注意：仅作用于走中央事件处理器路径的附魔。部分使用独立 @SubscribeEvent
     * 监听器的附魔不受此开关影响。
     * </p>
     */
    public static boolean allowMobTriggerEnchantments = true;

    /**
     * 是否允许非玩家生物触发死亡/濒死类附魔
     * <p>
     * 默认 false（拦截）。覆盖范围：
     * <ul>
     *   <li>所有 DEAD 分类附魔（猩红艾奥尼亚、发狂扩散、巨剑阵、古龙雷击）</li>
     *   <li>RECOLLECT 分类中的濒死触发型附魔（满月、死诞者、时间逆转）</li>
     * </ul>
     * </p>
     * <p>
     * 建议保持禁用，避免精英怪/Boss获得复活、范围反伤等过强能力，防止玩家"被打破防"。
     * </p>
     * <p>
     * 仅当 {@link #allowMobTriggerEnchantments} 为 true 时才会被检查；
     * 若通用开关为 false，则死亡类附魔自然也不会触发。
     * </p>
     */
    public static boolean allowMobTriggerDeathEnchantments = false;

    /**
     * 从配置规范同步到静态字段
     * <p>
     * 在配置加载或修改后调用。
     * v2.2修复：末尾清除所有附魔的禁用状态缓存，确保黑名单变更立即生效。
     * v2.3新增：同步两个怪物附魔触发开关。
     * v2.4新增：同步权重映射与村民交易两个开关。
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

        // v2.4新增：同步附魔获取途径平衡开关
        useVanillaRarityWeight = COMMON.useVanillaRarityWeight.get();
        allowVillagerBookTrade = COMMON.allowVillagerBookTrade.get();

        // v2.3新增：同步怪物附魔触发开关
        allowMobTriggerEnchantments = COMMON.allowMobTriggerEnchantments.get();
        allowMobTriggerDeathEnchantments = COMMON.allowMobTriggerDeathEnchantments.get();

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
