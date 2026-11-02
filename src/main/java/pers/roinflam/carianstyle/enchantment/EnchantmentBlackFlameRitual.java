package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E3)
 *
 * 黑焰仪式附魔
 * <p>v2.2：LivingHurt攻击者视角入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，无需检查。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "black_flame_ritual", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnchantTarget.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentShelterOfFire.class, EnchantmentHealingByFire.class}, baseEnchantability = 30, levelMultiplier = 0)
@Deprecated
public class EnchantmentBlackFlameRitual extends EnchantmentBase {
    private static final String VALUE_ID = "black_flame_ritual";
    private static final EnchantmentValues.Handle HARMFUL_BONUS = EnchantmentValues.define(VALUE_ID, "harmful_bonus",
                    0.2D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle BENEFICIAL_BONUS = EnchantmentValues.define(VALUE_ID, "beneficial_bonus",
                    0.1D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle TICK_INTERVAL = EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);
    private static final EnchantmentValues.Handle HEALTH_RETAIN_RATIO = EnchantmentValues.define(VALUE_ID, "health_retain_ratio",
                    0.95D, 0.5D, 1.0D);
    public EnchantmentBlackFlameRitual() {
        super(EnchantTarget.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench) && !ench.is(Enchantments.PROTECTION);
    }
}

