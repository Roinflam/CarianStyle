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


        config.save();
        logger.info("Finished loading config.");
    }

    public static Logger getLogger() {
        return logger;
    }
}
