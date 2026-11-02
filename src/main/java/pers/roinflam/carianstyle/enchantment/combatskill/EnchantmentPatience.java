package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import java.util.UUID;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 忍耐附魔
 * <p>v2.2：受击累积监听器接入怪物附魔触发开关。
 * onHurtAsAttackerLowest 走中央事件分发器，已被 scanEntity 入口拦截。
 * onLivingDeath 是清理类，不影响行为，无需检查。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "patience",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 10,
        levelMultiplier = 5
)
@Deprecated
public class EnchantmentPatience extends EnchantmentBase {
    private static final String VALUE_ID = "patience";
    private static final EnchantmentValues.Handle MAX_ACCUMULATED_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "max_accumulated_per_level",
                    0.4D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle ACCUMULATE_RATE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "accumulate_rate_per_level",
                    0.1D, 0.0D, 2.0D);
    private static final String PATIENCE_DATA_KEY = "patience_accumulated";
    public EnchantmentPatience() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
}

