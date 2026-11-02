package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E1)
 *
 * 连射附魔
 * <p>v2.2：ProjectileImpact射手+LivingTickEvent入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "continuous_shooting",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.BOW,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentContinuousShooting extends EnchantmentBase {
    private static final String VALUE_ID = "continuous_shooting";
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    0.5D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle EXTRA_ARROWS = EnchantmentValues.define(VALUE_ID, "extra_arrows",
                    4, 1, 32);
    public EnchantmentContinuousShooting() {
        super(EnchantTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        if (ench.is(Enchantments.INFINITY)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }
}

