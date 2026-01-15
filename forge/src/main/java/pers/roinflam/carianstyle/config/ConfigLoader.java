package pers.roinflam.carianstyle.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 卡利亚风格模组配置加载器
 * <p>
 * 本配置文件控制模组的各项功能和平衡性设置
 * </p>
 */
@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID, category = "general")
public final class ConfigLoader {

    // ==================== 日志系统配置 ====================

    @Config.Name("Enable Detailed Logging | 启用详细日志")
    @Config.Comment({
            "Enable detailed logging for debugging purposes.",
            "When enabled, the mod will output additional debug information to help diagnose issues.",
            "This may slightly impact performance and should be disabled in production environments.",
            "启用详细日志以便调试。",
            "启用后，模组会输出额外的调试信息以帮助诊断问题。",
            "这可能会轻微影响性能，在生产环境中应该关闭。",
            "Default | 默认值: false"
    })
    @Config.LangKey("config.carianstyle.enableDetailedLogging")
    public static boolean enableDetailedLogging = false;

    // ==================== 附魔系统配置 ====================

    @Config.Name("Uninstall Enchantments | 禁用的附魔")
    @Config.Comment({
            "Fill in the IDs of enchantments you want to disable registration here.",
            "You can find the IDs of relevant enchantments in en_us.lang file.",
            "Example: {\"scarlet_rot\", \"doomed_death\"}",
            "在此填入你想要禁用注册的附魔ID。",
            "你可以在 en_us.lang 文件中找到相关附魔的ID。",
            "示例: {\"scarlet_rot\", \"doomed_death\"}",
            "Default | 默认值: {}"
    })
    @Config.LangKey("config.carianstyle.uninstallEnchantment")
    public static String[] uninstallEnchantment = {};

    @Config.Name("Rock Blaster Max Range | 碎岩者最大范围")
    @Config.Comment({
            "The maximum mining radius of the Rock Blaster Enchantment.",
            "This determines how many blocks in each direction can be mined at once.",
            "Higher values allow faster mining but may cause lag.",
            "碎岩者附魔的最大挖掘半径。",
            "这决定了一次可以挖掘每个方向上多少个方块。",
            "更高的值允许更快的挖掘，但可能导致卡顿。",
            "Range | 范围: 0 - 20",
            "Default | 默认值: 10"
    })
    @Config.RangeInt(min = 0, max = 20)
    @Config.LangKey("config.carianstyle.rockBlasterMaxRange")
    public static int rockBlasterMaxRange = 10;

    @Config.Name("Prayerful Strike Max Health | 祈祷打击最大生命值")
    @Config.Comment({
            "Set the maximum stackable life limit for the Prayer Strike Enchantment.",
            "This caps how much extra health can be gained from kills.",
            "设置祈祷打击附魔的最大可叠加生命值上限。",
            "这限制了从击杀中获得的额外生命值上限。",
            "Range | 范围: 0 - 1000000",
            "Default | 默认值: 1000"
    })
    @Config.RangeInt(min = 0, max = 1000000)
    @Config.LangKey("config.carianstyle.prayerfulStrikeMaxHealth")
    public static int prayerfulStrikeMaxHealth = 1000;

    // ==================== 宝藏附魔配置 ====================

    @Config.Name("Very Rare to Treasure | 超稀有附魔变为宝藏")
    @Config.Comment({
            "Convert all Very Rare enchantments to Treasure enchantments.",
            "When enabled, all mod enchantments with Very Rare rarity will become treasure enchantments.",
            "Treasure enchantments cannot be obtained from enchanting tables, only from loot.",
            "将所有超稀有(Very Rare)级别的附魔变为宝藏附魔。",
            "启用后，本模组所有超稀有级别的附魔都将成为宝藏附魔。",
            "宝藏附魔无法从附魔台获得，只能从战利品中获取。",
            "Default | 默认值: false"
    })
    @Config.LangKey("config.carianstyle.isTreasureVeryRaryEnchantment")
    public static boolean isTreasureVeryRaryEnchantment = false;

    @Config.Name("Rare to Treasure | 稀有附魔变为宝藏")
    @Config.Comment({
            "Convert all Rare enchantments to Treasure enchantments.",
            "When enabled, all mod enchantments with Rare rarity will become treasure enchantments.",
            "将所有稀有(Rare)级别的附魔变为宝藏附魔。",
            "启用后，本模组所有稀有级别的附魔都将成为宝藏附魔。",
            "Default | 默认值: false"
    })
    @Config.LangKey("config.carianstyle.isTreasureRaryEnchantment")
    public static boolean isTreasureRaryEnchantment = false;

    @Config.Name("Uncommon to Treasure | 罕见附魔变为宝藏")
    @Config.Comment({
            "Convert all Uncommon enchantments to Treasure enchantments.",
            "When enabled, all mod enchantments with Uncommon rarity will become treasure enchantments.",
            "将所有罕见(Uncommon)级别的附魔变为宝藏附魔。",
            "启用后，本模组所有罕见级别的附魔都将成为宝藏附魔。",
            "Default | 默认值: false"
    })
    @Config.LangKey("config.carianstyle.isTreasureUncommonEnchantment")
    public static boolean isTreasureUncommonEnchantment = false;

    // ==================== 游戏平衡配置 ====================

    @Config.Name("Enchantment Level Limit | 附魔等级限制")
    @Config.Comment({
            "Setting a limit on some enchantments after exceeding the level cap to prevent too much invincibility.",
            "When enabled, enchantments will have their effects capped even if the level is higher.",
            "This helps maintain game balance in situations with very high enchantment levels.",
            "为某些附魔在超过等级上限后设置限制，以防止过于无敌。",
            "启用后，即使等级更高，附魔效果也会被限制。",
            "这有助于在附魔等级非常高的情况下保持游戏平衡。",
            "Default | 默认值: false"
    })
    @Config.LangKey("config.carianstyle.levelLimit")
    public static boolean levelLimit = false;

    @Config.Name("Enchanting Difficulty Multiplier | 附魔难度倍率")
    @Config.Comment({
            "Set the difficulty multiplier for enchantments.",
            "1.0 is the default difficulty.",
            "Higher values increase the enchantability cost, making enchantments harder to obtain.",
            "Lower values decrease the cost, making enchantments easier to obtain.",
            "设置附魔的难度倍率。",
            "1.0 是默认难度。",
            "更高的值会增加附魔能力消耗，使附魔更难获得。",
            "更低的值会降低消耗，使附魔更容易获得。",
            "Range | 范围: 0.1 - 10.0",
            "Default | 默认值: 1.0"
    })
    @Config.RangeDouble(min = 0.1, max = 10.0)
    @Config.LangKey("config.carianstyle.enchantingDifficulty")
    public static double enchantingDifficulty = 1.0;

    // ==================== 事件处理 ====================

    /**
     * 配置文件修改事件处理
     * <p>
     * 当配置文件被修改时，自动同步到内存中
     * </p>
     *
     * @param evt 配置修改事件
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onConfigChanged(@Nonnull ConfigChangedEvent.OnConfigChangedEvent evt) {
        if (evt.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }
}