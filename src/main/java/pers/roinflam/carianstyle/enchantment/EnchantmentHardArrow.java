package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentHardArrow extends UncommonBase {

    public EnchantmentHardArrow(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "hard_arrow");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.HARD_ARROW;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityArrow entityArrow = evt.getArrow();
            if (entityArrow.shootingEntity instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) entityArrow.shootingEntity;
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        entityArrow.setDamage((float) (entityArrow.getDamage() + entityArrow.getDamage() * bonusLevel * 0.8));
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                    if (bonusLevel > 0) {
                        List<EntityLivingBase> entities = hurter.world.getEntitiesWithinAABB(
                                EntityLivingBase.class,
                                new AxisAlignedBB(hurter.getPosition()).expand(12, 12, 12),
                                entityLivingBase -> !entityLivingBase.equals(hurter)
                        );
                        if (!entities.isEmpty()) {
                            evt.setAmount((float) (evt.getAmount() + evt.getAmount() * bonusLevel * 0.8));
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(LivingKnockBackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                if (bonusLevel > 0) {
                    List<EntityLivingBase> entities = hurter.world.getEntitiesWithinAABB(
                            EntityLivingBase.class,
                            new AxisAlignedBB(hurter.getPosition()).expand(12, 12, 12),
                            entityLivingBase -> !entityLivingBase.equals(hurter)
                    );
                    if (!entities.isEmpty()) {
                        evt.setStrength((float) (evt.getStrength() + evt.getStrength() * bonusLevel * 0.75));
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 5 * 10;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.POWER);
    }
}
