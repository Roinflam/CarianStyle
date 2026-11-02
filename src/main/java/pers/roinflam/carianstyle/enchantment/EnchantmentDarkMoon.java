package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentFullMoon;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * PORT-STUB(E3)
 *
 * 暗月附魔
 * <p>v2.2：LivingHurt双向+LivingHeal入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，无需检查。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "dark_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantTarget.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class}, baseEnchantability = 35, levelMultiplier = 0)
@Deprecated
public class EnchantmentDarkMoon extends EnchantmentBase {
    private static final String VALUE_ID = "dark_moon";
    private static final EnchantmentValues.Handle REDUCTION = EnchantmentValues.define(VALUE_ID, "reduction",
                    0.25D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle REDUCTION_WITH_FULL_MOON = EnchantmentValues.define(VALUE_ID, "reduction_with_full_moon",
                    0.375D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle EXTRA_DAMAGE = EnchantmentValues.define(VALUE_ID, "extra_damage",
                    0.05D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle EXTRA_DAMAGE_WITH_FULL_MOON = EnchantmentValues.define(VALUE_ID, "extra_damage_with_full_moon",
                    0.075D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle NIGHT_VISION_TICKS = EnchantmentValues.define(VALUE_ID, "night_vision_ticks",
                    210, 20, 6000);
    public EnchantmentDarkMoon() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
}

