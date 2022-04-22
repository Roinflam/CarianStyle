package pers.roinflam.kaliastyle.enchantment;

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
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentBloodSlash extends Enchantment {

    public EnchantmentBloodSlash(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "blood_slash");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.BLOOD_SLASH;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        evt.setAmount((float) (evt.getAmount() + hurter.getHealth() * bonusLevel * 0.05));
                        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
                            if (attacker.getHealth() > attacker.getMaxHealth() * 0.075) {
                                attacker.setHealth((float) (attacker.getHealth() - attacker.getMaxHealth() * 0.075));
                            } else {
                                attacker.setHealth(0);
                                attacker.onDeath(DamageSource.causeMobDamage(attacker));
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
                    if (killer.getHeldItemMainhand() != null) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), killer.getHeldItemMainhand());
                        if (bonusLevel > 0) {
                            if (EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodSlash.getEnchantment(), killer.getHeldItemMainhand()) > 0) {
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
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 20 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(KaliaStyleEnchantments.SCARLET_CORRUPTION) &&
                !ench.equals(KaliaStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(KaliaStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(KaliaStyleEnchantments.VIC_DRAGON_THUNDER) &&
                !ench.equals(KaliaStyleEnchantments.DARK_MOON);
    }
}
