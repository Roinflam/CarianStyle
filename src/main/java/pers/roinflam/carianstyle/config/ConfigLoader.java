package pers.roinflam.carianstyle.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

public class ConfigLoader {
    public static List<String> uninstall_enchantment;
    public static int rockblaster_maxrange;
    public static int prayerfulStrike_maxHealth;
    public static boolean isTreasureEnchantment;

    public static boolean levelLimit;
    private static Configuration config;
    private static Logger logger;

    public ConfigLoader(@Nonnull FMLPreInitializationEvent evt) {
        logger = evt.getModLog();
        config = new Configuration(evt.getSuggestedConfigurationFile());

        config.load();
        load();
    }

    public static void load() {
        logger.info("Started loading config.");
        @Nonnull String comment = "Fill in the id of the enchantment you want to disable registration here, you can find the id of the relevant enchantment in en_us.lang.";

        uninstall_enchantment = Arrays.asList(config.get(Configuration.CATEGORY_GENERAL, "uninstall", new String[]{}, comment).getStringList());

        comment = "The maximum mining radius of the Rock Blaster Enchantment.";
        rockblaster_maxrange = config.get(Configuration.CATEGORY_GENERAL, "rockBlasterMaxRange", 10, comment).getInt();

        comment = "Set the maximum stackable life limit for the Prayer Strike Enchantment.";
        prayerfulStrike_maxHealth = config.get(Configuration.CATEGORY_GENERAL, "prayerfulStrike_maxHealth", 1000, comment).getInt();

        comment = "Set all enchantments to Treasure Enchantments.";
        isTreasureEnchantment = config.get(Configuration.CATEGORY_GENERAL, "isTreasureEnchantment", false, comment).getBoolean();

        comment = "Set the level cap for the GoldenVow Limit and Protection of The Erdtree Enchantment to level 5.";
        levelLimit = config.get(Configuration.CATEGORY_GENERAL, "levelLimit", false, comment).getBoolean();

        config.save();
        logger.info("Finished loading config.");
    }

    public static Logger getLogger() {
        return logger;
    }
}
