package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E3)
 *
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
        type = EnchantTarget.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        baseEnchantability = 5,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentBlessedDewTalisman extends EnchantmentBase {
    private static final String VALUE_ID = "blessed_dew_talisman";
    private static final EnchantmentValues.Handle HEAL_INTERVAL = EnchantmentValues.define(VALUE_ID, "heal_interval",
                    20, 1, 600);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.002D, 0.0D, 1.0D);
    private static final String COUNTER_ID = "blessed_dew_talisman_tick";
    public EnchantmentBlessedDewTalisman() {
        super(EnchantTarget.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        if (ench.is(Enchantments.PROTECTION)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }
}

