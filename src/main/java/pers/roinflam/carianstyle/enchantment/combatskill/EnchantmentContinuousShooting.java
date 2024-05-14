package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentContinuousShooting extends VeryRaryBase {

    public EnchantmentContinuousShooting(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "continuous_shooting");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CONTINUOUS_SHOOTING;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getArrow().shootingEntity != null && evt.getRayTraceResult().entityHit != null) {
                EntityArrow entityArrow = evt.getArrow();
                EntityLivingBase attacker = (EntityLivingBase) evt.getArrow().shootingEntity;
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                
                if (bonusLevel > 0) {
                    entityArrow.setDamage(entityArrow.getDamage() - entityArrow.getDamage() * 0.5);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        if (entityLivingBase.isHandActive()) {
            if (!entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()).isEmpty()) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()));
                
                if (bonusLevel > 0) {
                    for (int i = 0; i < 4; i++) {
                        EntityLivingUtil.updateHeld(entityLivingBase);
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench) && !ench.equals(Enchantments.INFINITY);
    }
}
