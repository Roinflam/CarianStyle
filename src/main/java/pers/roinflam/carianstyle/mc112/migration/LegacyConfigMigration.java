package pers.roinflam.carianstyle.mc112.migration;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Loader;
import pers.roinflam.carianstyle.config.ConfigSpec;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * 旧版配置文件迁移
 * <p>
 * 2.3.0 以前的 1.12.2 版本把配置写在 {@code config/carianstyle.cfg} 的 {@code general} 分类里，
 * 键名是「English Name | 中文名」这种形式；2.3.0 换成和 1.20.1 对齐的 {@code config/carianstyle-common.cfg}，
 * 分类、键名都变了。这里在模组构造器里把旧文件里有的项拷进 {@link ConfigSpec}，
 * 最后把旧文件改名为 {@code carianstyle.cfg.migrated}，以后不再迁移。
 * </p>
 * <p>
 * 必须在构造器里做：FML 构造完模组就同步一次 {@code @Config}，新文件不存在时按字段当前值建文件，
 * 字段这时已经是旧值，就直接写进新文件了。等到 preInit 再改字段、再调 {@code ConfigManager.sync}，
 * 会被第一次同步留下的「已改动」标记挡住，字段反而被文件里的默认值盖回去。
 * 新文件已经存在（以前装过 2.3.0）时以新文件为准，旧文件只改名不迁移。
 * </p>
 * <p>
 * 旧文件里没有的项（老版本没有怪物触发开关）保持新文件的值。旧文件读不了就跳过、打日志，不影响启动。
 * </p>
 */
public final class LegacyConfigMigration {

    private static final String LEGACY_FILE = Reference.MOD_ID + ".cfg";
    private static final String LEGACY_CATEGORY = "general";
    /** 和 {@link ConfigSpec} 上 {@code @Config(name = ...)} 一致 */
    private static final String CURRENT_FILE = "carianstyle-common.cfg";

    private LegacyConfigMigration() {
    }

    /**
     * 有旧配置就迁移（模组构造器里调用，见类注释）
     */
    public static void run() {
        File dir = Loader.instance().getConfigDir();
        File legacy = new File(dir, LEGACY_FILE);
        if (!legacy.isFile()) {
            return;
        }
        File current = new File(dir, CURRENT_FILE);
        int count;
        if (current.exists()) {
            LogUtil.info("卡利亚式附魔 - 已有新配置 %s，旧配置 %s 不迁移，只改名", current.getName(), LEGACY_FILE);
            count = -1;
        } else try {
            Configuration old = new Configuration(legacy);
            if (!old.hasCategory(LEGACY_CATEGORY)) {
                LogUtil.info("卡利亚式附魔 - 旧配置 %s 里没有 general 分类，不迁移，直接改名", LEGACY_FILE);
                count = 0;
            } else {
                count = copy(old.getCategory(LEGACY_CATEGORY));
            }
        } catch (RuntimeException e) {
            LogUtil.error("卡利亚式附魔 - 读取旧配置 " + LEGACY_FILE + " 失败，跳过迁移", e);
            return;
        }

        File renamed = new File(dir, LEGACY_FILE + ".migrated");
        if (renamed.exists() && !renamed.delete()) {
            renamed = new File(dir, LEGACY_FILE + ".migrated-" + System.currentTimeMillis());
        }
        if (!legacy.renameTo(renamed)) {
            LogUtil.warn("卡利亚式附魔 - 旧配置 %s 改名失败，下次启动会再迁移一次", LEGACY_FILE);
        }
        if (count >= 0) {
            LogUtil.info("卡利亚式附魔 - 已从旧配置迁移 %d 项（旧文件改名为 %s）", count, renamed.getName());
        }
    }

    /**
     * 按旧键名把值拷进新配置，只拷文件里存在的项
     *
     * @return 拷了多少项
     */
    private static int copy(@Nonnull ConfigCategory c) {
        int n = 0;
        Property p;
        if ((p = c.get("Enable Detailed Logging | 启用详细日志")) != null) {
            ConfigSpec.logging.enableDetailedLogging = p.getBoolean(ConfigSpec.logging.enableDetailedLogging);
            n++;
        }
        if ((p = c.get("Uninstall Enchantments | 禁用的附魔")) != null) {
            ConfigSpec.enchantment.uninstallEnchantment = p.getStringList();
            n++;
        }
        if ((p = c.get("Rock Blaster Max Range | 碎岩者最大范围")) != null) {
            ConfigSpec.enchantment.rockBlasterMaxRange = clamp(p.getInt(ConfigSpec.enchantment.rockBlasterMaxRange), 0, 20);
            n++;
        }
        if ((p = c.get("Prayerful Strike Max Health | 祈祷打击最大生命值")) != null) {
            ConfigSpec.enchantment.prayerfulStrikeMaxHealth = clamp(p.getInt(ConfigSpec.enchantment.prayerfulStrikeMaxHealth), 0, 1000000);
            n++;
        }
        if ((p = c.get("Very Rare to Treasure | 超稀有附魔变为宝藏")) != null) {
            ConfigSpec.treasure.isTreasureVeryRareEnchantment = p.getBoolean(ConfigSpec.treasure.isTreasureVeryRareEnchantment);
            n++;
        }
        if ((p = c.get("Rare to Treasure | 稀有附魔变为宝藏")) != null) {
            ConfigSpec.treasure.isTreasureRareEnchantment = p.getBoolean(ConfigSpec.treasure.isTreasureRareEnchantment);
            n++;
        }
        if ((p = c.get("Uncommon to Treasure | 罕见附魔变为宝藏")) != null) {
            ConfigSpec.treasure.isTreasureUncommonEnchantment = p.getBoolean(ConfigSpec.treasure.isTreasureUncommonEnchantment);
            n++;
        }
        if ((p = c.get("Enchantment Level Limit | 附魔等级限制")) != null) {
            ConfigSpec.balance.levelLimit = p.getBoolean(ConfigSpec.balance.levelLimit);
            n++;
        }
        if ((p = c.get("Enchanting Difficulty Multiplier | 附魔难度倍率")) != null) {
            double v = p.getDouble(ConfigSpec.balance.enchantingDifficulty);
            ConfigSpec.balance.enchantingDifficulty = Math.max(0.1, Math.min(10.0, v));
            n++;
        }
        if ((p = c.get("Allow Mob Trigger Enchantments | 允许怪物触发附魔")) != null) {
            ConfigSpec.mobTrigger.allowMobTriggerEnchantments = p.getBoolean(ConfigSpec.mobTrigger.allowMobTriggerEnchantments);
            n++;
        }
        if ((p = c.get("Allow Mob Trigger Death Enchantments | 允许怪物触发死亡类附魔")) != null) {
            ConfigSpec.mobTrigger.allowMobTriggerDeathEnchantments = p.getBoolean(ConfigSpec.mobTrigger.allowMobTriggerDeathEnchantments);
            n++;
        }
        return n;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
