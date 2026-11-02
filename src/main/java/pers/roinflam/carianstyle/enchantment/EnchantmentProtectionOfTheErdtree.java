package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E4)
 *
 * 黄金树的庇佑附魔
 * <p>v2.2：LivingAttack双向监听器入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "protection_of_the_erdtree",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true,
        baseEnchantability = 35,
        levelMultiplier = 25
)
@Deprecated
public class EnchantmentProtectionOfTheErdtree extends EnchantmentBase {
    private static final String VALUE_ID = "protection_of_the_erdtree";
    private static final EnchantmentValues.Handle VICTIM_LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "victim_level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle ATTACKER_LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "attacker_level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle ATTACKER_AMPLIFIER_CAP = EnchantmentValues.define(VALUE_ID, "attacker_amplifier_cap",
                    5, 1, 100);
    private static final EnchantmentValues.Handle VICTIM_AMPLIFIER_CAP = EnchantmentValues.define(VALUE_ID, "victim_amplifier_cap",
                    5, 1, 100);
    private static final EnchantmentValues.Handle DURATION_SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "duration_seconds_per_level",
                    2.5D, 0.1D, 120.0D);
    public EnchantmentProtectionOfTheErdtree() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench) && !ench.is(Enchantments.PROTECTION);
    }
}

