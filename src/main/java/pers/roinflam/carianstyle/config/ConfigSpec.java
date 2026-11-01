package pers.roinflam.carianstyle.config;

import net.minecraftforge.common.config.Config;
import pers.roinflam.carianstyle.base.enchantment.LevelDecay;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 通用配置的规格：Forge {@code @Config}，生成 {@code config/carianstyle-common.cfg}
 * <p>
 * 对应 1.20.1 {@code ConfigLoader.Common}（ForgeConfigSpec）。分类和键名和 1.20.1 的 toml 一一对应，
 * 界面上显示的文字直接复用 1.20.1 的 {@code config.carianstyle.*} 翻译（{@code GuiConfig} 会自动找
 * {@code <键>.tooltip} 当提示）。{@code @Config.Comment} 写在文件里给手改配置的人看，照 1.20.1 的英文原文。
 * </p>
 * <p>
 * 1.12.2 版的写法限制：{@code category = ""} 时根下只能放代表分类的对象字段，所以每个分类是一个
 * {@code public static final} 字段，类型是直接继承 Object 的静态内部类，键是内部类的 public 实例字段。
 * 分类名在文件里会被 Forge 转成小写（{@code truedamage}、{@code leveldecay}、{@code mobtrigger}）。
 * </p>
 * <p>
 * 这里只是规格；附魔代码读的是 {@link ConfigLoader} 里烘焙好的静态字段，改值后要调 {@link ConfigLoader#bake()}。
 * 1.12.2 版：只有游戏内配置界面改动会触发重载；专用服手改 {@code .cfg} 要重启。
 * </p>
 */
@Config(modid = Reference.MOD_ID, name = "carianstyle-common", category = "")
public final class ConfigSpec {

    @Config.LangKey("config.carianstyle.category.debug")
    @Config.Comment({
            "Logging System Configuration",
            "Client-only HUD / visual settings: config/carianstyle/client_visual.json and visual_toggle.json. Edit them on each client."
    })
    public static final Logging logging = new Logging();

    @Config.LangKey("config.carianstyle.category.enchantment")
    @Config.Comment("Enchantment System Configuration")
    public static final Enchantment enchantment = new Enchantment();

    @Config.LangKey("config.carianstyle.category.trueDamage")
    @Config.Comment("True Damage System Configuration")
    public static final TrueDamage trueDamage = new TrueDamage();

    @Config.LangKey("config.carianstyle.category.treasure")
    @Config.Comment("Treasure Enchantment Configuration")
    public static final Treasure treasure = new Treasure();

    @Config.LangKey("config.carianstyle.category.balance")
    @Config.Comment("Game Balance Configuration")
    public static final Balance balance = new Balance();

    @Config.LangKey("config.carianstyle.category.levelDecay")
    @Config.Comment({
            "High Level Decay",
            "Only affects CarianStyle enchantments. Levels at or below the threshold are untouched,",
            "and one more level never makes an enchantment weaker."
    })
    public static final LevelDecayCategory levelDecay = new LevelDecayCategory();

    @Config.LangKey("config.carianstyle.category.mobTrigger")
    @Config.Comment("Mob Enchantment Trigger Configuration")
    public static final MobTrigger mobTrigger = new MobTrigger();

    @Config.LangKey("config.carianstyle.category.stealth")
    @Config.Comment("Stealth Configuration")
    public static final Stealth stealth = new Stealth();

    private ConfigSpec() {
    }

    /** 日志 */
    public static class Logging {
        @Config.LangKey("config.carianstyle.enableDetailedLogging")
        @Config.Comment({
                "Enable detailed logging for debugging purposes.",
                "When enabled, the mod will output additional debug information.",
                "This may slightly impact performance and should be disabled in production."
        })
        public boolean enableDetailedLogging = false;
    }

    /** 附魔系统 */
    public static class Enchantment {
        @Config.LangKey("config.carianstyle.uninstallEnchantment")
        @Config.Comment({
                "Fill in the IDs of enchantments you want to disable.",
                "You can find enchantment IDs in the en_us.lang file.",
                "Example: scarlet_rot, doomed_death (one per line)"
        })
        public String[] uninstallEnchantment = new String[0];

        @Config.LangKey("config.carianstyle.rockBlasterMaxRange")
        @Config.Comment({
                "The maximum mining radius of the Rock Blaster Enchantment.",
                "Higher values allow faster mining but may cause lag."
        })
        @Config.RangeInt(min = 0, max = 20)
        public int rockBlasterMaxRange = 10;

        @Config.LangKey("config.carianstyle.rockBlasterSuppressCommonDrops")
        @Config.Comment({
                "When enabled, Rock Blaster will not drop items for common stone-type blocks.",
                "Affected blocks: stone, cobblestone, deepslate, cobbled_deepslate, granite, diorite,",
                "andesite, tuff, calcite, smooth_basalt, gravel, netherrack, basalt, blackstone, end_stone"
        })
        public boolean rockBlasterSuppressCommonDrops = false;

        @Config.LangKey("config.carianstyle.prayerfulStrikeMaxHealth")
        @Config.Comment("Set the maximum stackable life limit for the Prayer Strike Enchantment.")
        @Config.RangeInt(min = 0, max = 1000000)
        public int prayerfulStrikeMaxHealth = 1000;
    }

    /** 真伤 */
    public static class TrueDamage {
        @Config.LangKey("config.carianstyle.enableTrueDamage")
        @Config.Comment({
                "Enable the advanced true damage system.",
                "When enabled, the mod will use complex field detection to bypass armor and protection.",
                "When disabled, the mod will use simple setHealth method (may not work on some modded entities).",
                "Recommended: true (default)"
        })
        public boolean enableTrueDamage = true;
    }

    /** 宝藏附魔 */
    public static class Treasure {
        @Config.LangKey("config.carianstyle.isTreasureVeryRareEnchantment")
        @Config.Comment({
                "Convert all Very Rare enchantments to Treasure enchantments.",
                "Treasure enchantments cannot be obtained from enchanting tables."
        })
        public boolean isTreasureVeryRareEnchantment = false;

        @Config.LangKey("config.carianstyle.isTreasureRareEnchantment")
        @Config.Comment("Convert all Rare enchantments to Treasure enchantments.")
        public boolean isTreasureRareEnchantment = false;

        @Config.LangKey("config.carianstyle.isTreasureUncommonEnchantment")
        @Config.Comment("Convert all Uncommon enchantments to Treasure enchantments.")
        public boolean isTreasureUncommonEnchantment = false;
    }

    /** 游戏平衡 */
    public static class Balance {
        @Config.LangKey("config.carianstyle.levelLimit")
        @Config.Comment("Limit some enchantments to prevent overpowered effects.")
        public boolean levelLimit = false;

        @Config.LangKey("config.carianstyle.enchantingDifficulty")
        @Config.Comment({
                "Set the difficulty multiplier for enchantments.",
                "1.0 is the default difficulty."
        })
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double enchantingDifficulty = 1.0;

        @Config.LangKey("config.carianstyle.useVanillaRarityWeight")
        @Config.Comment({
                "Map CarianStyle enchantment rarities onto the vanilla weight system.",
                "Vanilla picks enchanting-table candidates by weight:",
                "COMMON=10, UNCOMMON=5, RARE=2, VERY_RARE=1",
                "Without this, every CarianStyle enchantment registers as COMMON (weight 10),",
                "i.e. as common as Efficiency / Protection / Sharpness, which floods the table",
                "and crowds vanilla enchantments out of the candidate pool.",
                "SIDE EFFECT: anvil combine cost also reads rarity (1x/2x/4x/8x),",
                "so Rare and Very Rare enchantments become more expensive on the anvil.",
                "Set to false to restore the old behaviour (everything weight 10).",
                "Default: true"
        })
        public boolean useVanillaRarityWeight = true;

        @Config.LangKey("config.carianstyle.allowVillagerBookTrade")
        @Config.Comment({
                "Allow CarianStyle enchantments to appear in librarian enchanted book trades.",
                "WARNING: vanilla picks trade books UNIFORMLY at random from every tradeable",
                "enchantment, ignoring rarity weights entirely. Enabling this adds 100+ entries",
                "to a pool of roughly 40, cutting the odds of rolling Mending to about a quarter.",
                "This does NOT affect enchanting tables or loot chests.",
                "Default: false (block)"
        })
        public boolean allowVillagerBookTrade = false;
    }

    /** 高等级衰减 */
    public static class LevelDecayCategory {
        @Config.LangKey("config.carianstyle.levelDecay.enabled")
        @Config.Comment("Enable high level decay. Default: false")
        public boolean enabled = false;

        @Config.LangKey("config.carianstyle.levelDecay.threshold")
        @Config.Comment({
                "Decay starts above this level. 0 = each enchantment's own max level (1 / 3 / 5).",
                "a positive number applies the same threshold to every enchantment."
        })
        @Config.RangeInt(min = 0, max = 10000)
        public int threshold = 0;

        @Config.LangKey("config.carianstyle.levelDecay.byMultiple")
        @Config.Comment({
                "true (default): curve points are written as multiples of the threshold, so every enchantment",
                "is reduced by the same proportion whatever its max level (1 / 3 / 5).",
                "false: curve points are actual levels shared by every enchantment; enchantments with a",
                "lower max level are then reduced less (level 10 is 9x the max of a level-1 enchantment)."
        })
        public boolean byMultiple = true;

        @Config.LangKey("config.carianstyle.levelDecay.curve")
        @Config.Comment({
                "Curve points as \"multiple:percent\" (or \"level:percent\" when byMultiple = false):",
                "once the level reaches threshold x multiple, effective level = level x percent.",
                "Between points the effective level is interpolated linearly; past the last point it keeps",
                "growing at the slope of the last segment. Points at or below the threshold are ignored.",
                "Default, for a max level 5 enchantment: 10->9, 20->15, 30->20, 50->25, 75->30, 100->33, 200->40",
                "for a max level 3 enchantment: 6->5, 12->9, 30->15, 60->20; max level 1: 2->2, 10->5, 100->12",
                "Another option, harsher early and looser late: 2:80, 5:70, 10:60, 15:50, 20:40"
        })
        public String[] curve = LevelDecay.DEFAULT_CURVE.toArray(new String[0]);

        @Config.LangKey("config.carianstyle.levelDecay.maxLevel")
        @Config.Comment("Hard cap on the effective level after decay. 0 = no cap.")
        @Config.RangeInt(min = 0, max = 100000)
        public int maxLevel = 0;

        @Config.LangKey("config.carianstyle.levelDecay.showInName")
        @Config.Comment("Append the effective level to enchantment names, e.g. \"(effective 25)\". Default: true")
        public boolean showInName = true;
    }

    /** 怪物触发附魔 */
    public static class MobTrigger {
        @Config.LangKey("config.carianstyle.allowMobTriggerEnchantments")
        @Config.Comment({
                "Allow non-player entities (mobs) to trigger CarianStyle enchantments.",
                "Disabling this can significantly reduce server load when many enchanted mobs are present.",
                "Note: This only blocks enchantments routed through the central event handler.",
                "Enchantments using their own @SubscribeEvent listeners may still trigger on mobs.",
                "Default: true (allow)"
        })
        public boolean allowMobTriggerEnchantments = true;

        @Config.LangKey("config.carianstyle.allowMobTriggerDeathEnchantments")
        @Config.Comment({
                "Allow non-player entities to trigger death / last-stand enchantments.",
                "Covers all DEAD-category enchantments (Scarlet Aeonia, Frenzied Spread, Greatblade Phalanx,",
                "Ancient Dragon Lightning) and the death-triggered RECOLLECT-category enchantments",
                "(Full Moon, Living Corpse, Time Reversal).",
                "Recommended to keep disabled to prevent boss/elite mobs from gaining unfair revival/AOE abilities.",
                "Note: This only applies when allowMobTriggerEnchantments is enabled.",
                "Default: false (block)"
        })
        public boolean allowMobTriggerDeathEnchantments = false;
    }

    /** 隐身 */
    public static class Stealth {
        @Config.LangKey("config.carianstyle.hideStealthHitbox")
        @Config.Comment({
                "Hide the hitbox (F3+B) of players under CarianStyle stealth on every client.",
                "Stealth only hides the player model; vanilla F3+B still draws the hitbox,",
                "so anyone can see through stealth in PvP just by pressing F3+B.",
                "Decided by the server and synced to clients; in multiplayer a client's own value has no effect.",
                "Default: true (hidden)"
        })
        public boolean hideStealthHitbox = true;
    }
}
