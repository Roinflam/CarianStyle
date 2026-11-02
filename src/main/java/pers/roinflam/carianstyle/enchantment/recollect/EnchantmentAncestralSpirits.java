package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E2)
 *
 * 先祖之魂附魔
 * <p>v2.1：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "ancestral_spirits",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentAncestralSpirits extends EnchantmentBase {
    private static final String VALUE_ID = "ancestral_spirits";
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.5D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle HEAL_DURATION_TICKS = EnchantmentValues.define(VALUE_ID, "heal_duration_ticks",
                    200, 20, 6000);
    private static final EnchantmentValues.Handle HEAL_RATIO_PER_SECOND = EnchantmentValues.define(VALUE_ID, "heal_ratio_per_second",
                    0.05D, 0.0D, 2.0D);
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentAncestralSpirits() {
        super(EnchantTarget.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }
}

