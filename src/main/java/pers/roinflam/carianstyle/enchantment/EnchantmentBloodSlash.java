package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentBloodSlash extends RaryBase {

    public EnchantmentBloodSlash(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "blood_slash");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLOOD_SLASH;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        evt.setAmount(evt.getAmount() + Math.min(hurter.getHealth() * bonusLevel * 0.05f, hurter.getMaxHealth()));
                        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
                            if (attacker.getHealth() > attacker.getMaxHealth() * 0.1) {
                                attacker.setHealth(attacker.getHealth() - attacker.getMaxHealth() * 0.1f);
                            } else {
                                EntityLivingUtil.kill(attacker, NewDamageSource.HEMORRHAGE);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (killer.isEntityAlive() && !evt.getEntityLiving().equals(killer)) {
                    if (!killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), killer.getHeldItem(killer.getActiveHand()));
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            if (EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodSlash.getEnchantment(), killer.getHeldItem(killer.getActiveHand())) > 0) {
                                killer.heal(killer.getMaxHealth() * bonusLevel * 0.05f);
                            } else {
                                killer.heal(killer.getMaxHealth() * bonusLevel * 0.025f);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
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
