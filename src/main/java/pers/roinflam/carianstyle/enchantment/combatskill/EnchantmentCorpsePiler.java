package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import java.util.UUID;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 尸山血海附魔
 * <p>v2.2：击杀计数器（killer视角）入口接入怪物附魔触发开关。
 * 死亡者计数衰减为清理逻辑，无需开关。
 * onDamageAsAttackerHighest 走中央分发器，已被 scanEntity 拦截。</p>
 * <p>v2.3：增伤与回血要求玩家满蓄力；击杀计数不受影响。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@Deprecated
public class EnchantmentCorpsePiler extends EnchantmentBase {
    private static final String VALUE_ID = "corpse_piler";
    private static final EnchantmentValues.Handle DAMAGE_PER_KILL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_kill_per_level",
                    0.01D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle HEAL_PER_KILL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_kill_per_level",
                    0.0005D, 0.0D, 0.1D);
    private static final EnchantmentValues.Handle MAX_KILL_COUNT = EnchantmentValues.define(VALUE_ID, "max_kill_count",
                    50, 1, 999);
    private static final EnchantmentValues.Handle COUNT_DURATION_TICKS = EnchantmentValues.define(VALUE_ID, "count_duration_ticks",
                    6000, 20, 72000);
    private static final String KILL_COUNT_KEY = "corpse_piler_kills";
    protected void onDamageAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

