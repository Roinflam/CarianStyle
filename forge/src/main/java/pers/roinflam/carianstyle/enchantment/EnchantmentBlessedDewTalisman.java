package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 祝福露水护符附魔
 * <p>
 * 玩家饱食度满时持续回血
 * 回血量 = 最大血量 × 等级 × 0.002 每秒(原版每tick,已优化为每秒)
 * </p>
 *
 * @author RoinFlam
 * @version 2.1 - 性能优化版
 */
@AutoRegisterEnchantment(
        id = "blessed_dew_talisman",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber
public class EnchantmentBlessedDewTalisman extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.blessed_dew_talisman.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blessed_dew_talisman";

    /**
     * 回血结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle HEAL_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "heal_interval",
                    20, 1, 600);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每次结算每级回复的最大生命占比
     * <p>默认 0.002，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.002D, 0.0D, 1.0D);


    /**
     * 治疗间隔(tick)
     * 20 tick = 1秒
     */

    /**
     * 计数器ID前缀
     */
    private static final String COUNTER_ID = "blessed_dew_talisman_tick";

    public EnchantmentBlessedDewTalisman() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        // 只在服务端执行
        if (evt.player.level().isClientSide) {
            return;
        }

        // 只在START阶段执行
        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        Player player = evt.player;

        // ⭐ 优化1: 使用计数器控制执行频率,每20tick(1秒)执行一次
        int tickCounter = EnchantmentDataManager.incrementCounter(COUNTER_ID, player.getUUID());
        if (tickCounter % HEAL_INTERVAL.getInt() != 0) {
            return;
        }

        // ⭐ 优化2: 提前检查是否满血,满血直接跳过
        if (player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        // ⭐ 优化3: 提前检查饱食度,不满时直接跳过
        // 1.20.1: getFoodStats().needFood() → getFoodData().needsFood()
        if (player.getFoodData().needsFood()) {
            return;
        }

        // 获取附魔实例
        Enchantment blessedDewTalisman = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlessedDewTalisman.class);
        if (blessedDewTalisman == null) {
            return;
        }

        // 计算总附魔等级（v-cache：走中央装备缓存）
        int totalLevel = EnchantmentEventHandler.armorTotal(player, blessedDewTalisman);

        // 应用等级上限
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, LEVEL_CAP.getInt());
        }

        // 执行治疗
        if (totalLevel > 0) {
            // ⭐ 优化4: 因为改为每秒执行一次,所以不再除以20
            // 原公式: 最大血量 * 等级 * 0.002 / 20 每tick
            // 新公式: 最大血量 * 等级 * 0.002 每秒
            float healAmount = player.getMaxHealth() * totalLevel * (float) HEAL_PER_LEVEL.get();
            player.heal(healAmount);
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.ALL_DAMAGE_PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
