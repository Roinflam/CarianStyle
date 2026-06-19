package pers.roinflam.carianstyle.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡利亚式附魔 Cloth Config 配置界面
 * Carian Style Cloth Config Screen
 *
 * <p>v2.3新增：怪物附魔触发分类（两个开关：通用 / 死亡类）。</p>
 *
 * @author RoinFlam
 */
public class ClothConfigScreen {

    /**
     * 创建配置界面
     * Create configuration screen
     *
     * @param parent 父屏幕 / Parent screen
     * @return 配置屏幕 / Configuration screen
     */
    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.carianstyle.title"))
                .setSavingRunnable(() -> {
                    // 保存配置
                    // Save configuration
                    ConfigLoader.COMMON_CONFIG.save();
                    // 同步配置到静态字段
                    // Sync config to static fields
                    ConfigLoader.bake();
                });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ═══════════════════════════════════════════════════════════════
        // 调试设置 / Debug Settings
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory debugCategory = builder.getOrCreateCategory(
                Component.translatable("config.carianstyle.category.debug"));

        debugCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.enableDetailedLogging"),
                        ConfigLoader.COMMON.enableDetailedLogging.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.carianstyle.enableDetailedLogging.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.enableDetailedLogging::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 附魔系统 / Enchantment System
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory enchantmentCategory = builder.getOrCreateCategory(
                Component.translatable("config.carianstyle.category.enchantment"));

        enchantmentCategory.addEntry(entryBuilder.startStrList(
                        Component.translatable("config.carianstyle.uninstallEnchantment"),
                        new ArrayList<>(ConfigLoader.COMMON.uninstallEnchantment.get()))
                .setDefaultValue(List.of())
                .setTooltip(Component.translatable("config.carianstyle.uninstallEnchantment.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.uninstallEnchantment::set)
                .build());

        enchantmentCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.carianstyle.rockBlasterMaxRange"),
                        ConfigLoader.COMMON.rockBlasterMaxRange.get())
                .setDefaultValue(10)
                .setMin(0)
                .setMax(20)
                .setTooltip(Component.translatable("config.carianstyle.rockBlasterMaxRange.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.rockBlasterMaxRange::set)
                .build());

        enchantmentCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.rockBlasterSuppressCommonDrops"),
                        ConfigLoader.COMMON.rockBlasterSuppressCommonDrops.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.carianstyle.rockBlasterSuppressCommonDrops.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.rockBlasterSuppressCommonDrops::set)
                .build());

        enchantmentCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.carianstyle.prayerfulStrikeMaxHealth"),
                        ConfigLoader.COMMON.prayerfulStrikeMaxHealth.get())
                .setDefaultValue(1000)
                .setMin(0)
                .setMax(1000000)
                .setTooltip(Component.translatable("config.carianstyle.prayerfulStrikeMaxHealth.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.prayerfulStrikeMaxHealth::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 真伤系统 / True Damage System
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory trueDamageCategory = builder.getOrCreateCategory(
                Component.translatable("config.carianstyle.category.trueDamage"));

        trueDamageCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.enableTrueDamage"),
                        ConfigLoader.COMMON.enableTrueDamage.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.carianstyle.enableTrueDamage.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.enableTrueDamage::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 宝藏附魔配置 / Treasure Enchantment Configuration
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory treasureCategory = builder.getOrCreateCategory(
                Component.translatable("config.carianstyle.category.treasure"));

        treasureCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.isTreasureVeryRareEnchantment"),
                        ConfigLoader.COMMON.isTreasureVeryRareEnchantment.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.carianstyle.isTreasureVeryRareEnchantment.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.isTreasureVeryRareEnchantment::set)
                .build());

        treasureCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.isTreasureRareEnchantment"),
                        ConfigLoader.COMMON.isTreasureRareEnchantment.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.carianstyle.isTreasureRareEnchantment.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.isTreasureRareEnchantment::set)
                .build());

        treasureCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.isTreasureUncommonEnchantment"),
                        ConfigLoader.COMMON.isTreasureUncommonEnchantment.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.carianstyle.isTreasureUncommonEnchantment.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.isTreasureUncommonEnchantment::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 游戏平衡配置 / Game Balance Configuration
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory balanceCategory = builder.getOrCreateCategory(
                Component.translatable("config.carianstyle.category.balance"));

        balanceCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.levelLimit"),
                        ConfigLoader.COMMON.levelLimit.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.carianstyle.levelLimit.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.levelLimit::set)
                .build());

        balanceCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.carianstyle.enchantingDifficulty"),
                        ConfigLoader.COMMON.enchantingDifficulty.get())
                .setDefaultValue(1.0)
                .setMin(0.1)
                .setMax(10.0)
                .setTooltip(Component.translatable("config.carianstyle.enchantingDifficulty.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.enchantingDifficulty::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 怪物附魔触发配置 / Mob Enchantment Trigger Configuration（v2.3新增）
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory mobTriggerCategory = builder.getOrCreateCategory(
                Component.translatable("config.carianstyle.category.mobTrigger"));

        mobTriggerCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.allowMobTriggerEnchantments"),
                        ConfigLoader.COMMON.allowMobTriggerEnchantments.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.carianstyle.allowMobTriggerEnchantments.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.allowMobTriggerEnchantments::set)
                .build());

        mobTriggerCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.carianstyle.allowMobTriggerDeathEnchantments"),
                        ConfigLoader.COMMON.allowMobTriggerDeathEnchantments.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.carianstyle.allowMobTriggerDeathEnchantments.tooltip"))
                .setSaveConsumer(ConfigLoader.COMMON.allowMobTriggerDeathEnchantments::set)
                .build());

        return builder.build();
    }
}
