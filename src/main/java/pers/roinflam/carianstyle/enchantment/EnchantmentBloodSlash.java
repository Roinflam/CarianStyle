package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentBloodSlash extends RaryBase {

    public EnchantmentBloodSlash(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "blood_slash");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLOOD_SLASH;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        evt.setAmount((float) (evt.getAmount() + hurter.getHealth() * bonusLevel * 0.05));
                        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
                            if (attacker.getHealth() > attacker.getMaxHealth() * 0.075) {
                                attacker.setHealth((float) (attacker.getHealth() - attacker.getMaxHealth() * 0.075));
                            } else {
                                attacker.setHealth(0);
                                attacker.onDeath(DamageSource.causeMobDamage(attacker));
                                attacker.setDead();
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (killer.isEntityAlive() && !evt.getEntityLiving().equals(killer)) {
                    if (!killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), killer.getHeldItem(killer.getActiveHand()));
                        if (bonusLevel > 0) {
                            if (EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodSlash.getEnchantment(), killer.getHeldItem(killer.getActiveHand())) > 0) {
                                killer.heal((float) (killer.getMaxHealth() * 0.05 * bonusLevel));
                            } else {
                                killer.heal((float) (killer.getMaxHealth() * 0.025 * bonusLevel));
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 20 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(CarianStyleEnchantments.VIC_DRAGON_THUNDER) &&
                !ench.equals(CarianStyleEnchantments.DARK_MOON);
    }
}
