package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentStarsLaw extends VeryRaryBase {

    public EnchantmentStarsLaw(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "stars_law");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.STARS_LAW;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!evt.getSource().isMagicDamage()) {
                    if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            if (attacker.isPotionActive(CarianStylePotion.FROSTBITE)) {
                                int level = Math.min(attacker.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier() + 1, 5);
                                attacker.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, level));
                            } else {
                                attacker.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, 0));
                            }
                        }
                    }
                }
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
                        if (hurter.isPotionActive(CarianStylePotion.FROSTBITE)) {
                            if (evt.getSource().isMagicDamage()) {
                                evt.setAmount(evt.getAmount() + evt.getAmount() * (hurter.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier() + 1) * 0.075f);
                            }
                            int level = Math.min(hurter.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier() + 1, 9);
                            hurter.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, level));
                        } else {
                            hurter.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, 0));
                        }
                    }
                }
            } else if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        if (!evt.getSource().isMagicDamage()) {
                            if (attacker.isPotionActive(CarianStylePotion.FROSTBITE)) {
                                int level = Math.min(attacker.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier() + 1, 9);
                                attacker.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, level));
                            } else {
                                attacker.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, 0));
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (!healer.getHeldItem(healer.getActiveHand()).isEmpty()) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), healer.getHeldItem(healer.getActiveHand()));
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    evt.setAmount(evt.getAmount() + evt.getAmount() * 0.5f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote && !evt.player.world.isDaytime()) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                @Nonnull EntityPlayer entityPlayer = evt.player;
                if (entityPlayer.isEntityAlive()) {
                    if (!entityPlayer.getHeldItem(entityPlayer.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), entityPlayer.getHeldItem(entityPlayer.getActiveHand()));
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            entityPlayer.addPotionEffect(new PotionEffect(CarianStylePotion.SPEED_BOOST, 2, 25));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !CarianStyleEnchantments.LAW.contains(ench) &&
                (ench.equals(CarianStyleEnchantments.DARK_ABANDONED_CHILD) || !CarianStyleEnchantments.RECOLLECT.contains(ench)) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(CarianStyleEnchantments.VIC_DRAGON_THUNDER);
    }

}
