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

@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID)
public final class ConfigLoader {
    @Config.Comment("Fill in the id of the enchantment you want to disable registration here, you can find the id of the relevant enchantment in en_us.lang.")
    public static String[] uninstallEnchantment = {};

    @Config.Comment("The maximum mining radius of the Rock Blaster Enchantment.")
    @Config.RangeInt(min = 0)
    public static int rockBlasterMaxRange = 10;

    @Config.Comment("Set the maximum stackable life limit for the Prayer Strike Enchantment.")
    @Config.RangeInt(min = 0)
    public static int prayerfulStrikeMaxHealth = 1000;

    @Config.Comment("Set all enchantments to Treasure Very Rary Enchantments.")
    public static boolean isTreasureVeryRaryEnchantment = false;

    @Config.Comment("Set all enchantments to Treasure Rary Enchantments.")
    public static boolean isTreasureRaryEnchantment = false;

    @Config.Comment("Set all enchantments to Treasure Uncommon Enchantments.")
    public static boolean isTreasureUncommonEnchantment = false;

    @Config.Comment("Setting a limit on some enchantments after exceeding the level cap to prevent too much invincibility.")
    public static boolean levelLimit = false;

    @Config.Comment("Set the difficulty of enchantment. 1 is the default difficulty. The higher the value, the greater the difficulty.")
    public static double enchantingDifficulty = 1;

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onConfigChanged(@Nonnull ConfigChangedEvent.OnConfigChangedEvent evt) {
        if (evt.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }
}
