package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentHardArrow extends UncommonBase {

    public EnchantmentHardArrow(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "hard_arrow");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.HARD_ARROW;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityArrow entityArrow = evt.getArrow();
            if (entityArrow.shootingEntity instanceof EntityLivingBase) {
                @Nonnull EntityLivingBase attacker = (EntityLivingBase) entityArrow.shootingEntity;
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        entityArrow.setDamage((float) (entityArrow.getDamage() + entityArrow.getDamage() * bonusLevel * 0.8));
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                hurter,
                                12,
                                entityLivingBase -> !entityLivingBase.equals(hurter)
                        );
                        if (!entities.isEmpty()) {
                            evt.setAmount(evt.getAmount() + evt.getAmount() * bonusLevel * 0.8f);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(@Nonnull LivingKnockBackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                            EntityLivingBase.class,
                            hurter,
                            12,
                            entityLivingBase -> !entityLivingBase.equals(hurter)
                    );
                    if (!entities.isEmpty()) {
                        evt.setStrength(evt.getStrength() + evt.getStrength() * bonusLevel * 0.75f);
                    }
                }
            }
        }
    }

    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.POWER);
    }
}
