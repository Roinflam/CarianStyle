package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentSacredOrder extends VeryRaryBase {

    public EnchantmentSacredOrder(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "sacred_order");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.SACRED_ORDER;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                DamageSource damageSource = evt.getSource();
                EntityLivingBase killer = (EntityLivingBase) damageSource.getTrueSource();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : killer.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                
                if (bonusLevel > 0) {
                    if (killer.getAbsorptionAmount() < killer.getMaxHealth() * 3) {
                        float damageAbsorption = Math.min(killer.getMaxHealth() * 3, killer.getAbsorptionAmount() + killer.getMaxHealth() * 0.1f);
                        killer.setAbsorptionAmount(damageAbsorption);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            EntityLivingBase hurter = evt.getEntityLiving();
            if (hurter.getAbsorptionAmount() > 0) {
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                
                if (bonusLevel > 0) {
                    evt.setAmount(evt.getAmount() * 0.75f);
                    if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                        attacker.attackEntityFrom(DamageSource.MAGIC, hurter.getAbsorptionAmount() * 0.05f);
                    }
                }
            }
            if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                if (attacker.getAbsorptionAmount() > 0) {
                    int bonusLevel = 0;
                    for (@Nonnull ItemStack itemStack : attacker.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    
                if (bonusLevel > 0) {
                        evt.setAmount(evt.getAmount() * 1.5f);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent evt) {
        if (!evt.getWorld().isRemote) {
            if (evt.getEntity() instanceof EntityLivingBase) {
                EntityLivingBase entityLivingBase = (EntityLivingBase) evt.getEntity();
                if (entityLivingBase.getAbsorptionAmount() <= 0) {
                    int bonusLevel = 0;
                    for (@Nonnull ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    
                if (bonusLevel > 0) {
                        entityLivingBase.setAbsorptionAmount(entityLivingBase.getMaxHealth());
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            int bonusLevel = 0;
            for (@Nonnull ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            
                if (bonusLevel > 0) {
                evt.setCanceled(true);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(Enchantments.PROTECTION);
    }

}
