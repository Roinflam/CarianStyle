package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
import pers.roinflam.carianstyle.config.ConfigLoader;

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

    /**
     * 治疗间隔(tick)
     * 20 tick = 1秒
     */
    private static final int HEAL_INTERVAL = 20;

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
        if (tickCounter % HEAL_INTERVAL != 0) {
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

        // 计算总附魔等级
        int totalLevel = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(blessedDewTalisman, armor);
            }
        }

        // 应用等级上限
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        // 执行治疗
        if (totalLevel > 0) {
            // ⭐ 优化4: 因为改为每秒执行一次,所以不再除以20
            // 原公式: 最大血量 * 等级 * 0.002 / 20 每tick
            // 新公式: 最大血量 * 等级 * 0.002 每秒
            float healAmount = player.getMaxHealth() * totalLevel * 0.002f;
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