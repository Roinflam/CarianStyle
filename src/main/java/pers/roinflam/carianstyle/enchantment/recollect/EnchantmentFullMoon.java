package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentFullMoon extends VeryRaryBase {
    private static final Set<UUID> FULL_MOON = new HashSet<>();
    private static final Set<UUID> FULL_MOON_COOLDING = new HashSet<>();

    public EnchantmentFullMoon(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "full_moon");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.FULL_MOON;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    if (!FULL_MOON_COOLDING.contains(hurter.getUniqueID())) {
                        if (!hurter.isDead) {
                            evt.setCanceled(true);
                            hurter.setHealth(hurter.getMaxHealth() * 0.0075f);

                            FULL_MOON_COOLDING.add(hurter.getUniqueID());
                            FULL_MOON.add(hurter.getUniqueID());

                            if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                                if (EnchantmentHelper.getEnchantmentLevel(EnchantmentDarkMoon.getEnchantment(), hurter.getHeldItem(hurter.getActiveHand())) > 0) {
                                    new SynchronizationTask(1, 1) {
                                        private int tick = 1;

                                        @Override
                                        public void run() {
                                            if (++tick > 400 || !hurter.isEntityAlive()) {
                                                this.cancel();
                                                FULL_MOON.remove(hurter.getUniqueID());
                                                return;
                                            }
                                            hurter.heal(hurter.getMaxHealth() / 200);
                                        }

                                    }.start();
                                    return;
                                }
                            }
                            new SynchronizationTask(1, 1) {
                                private int tick = 1;

                                @Override
                                public void run() {
                                    if (++tick > 200 || !hurter.isEntityAlive()) {
                                        this.cancel();
                                        FULL_MOON.remove(hurter.getUniqueID());
                                        return;
                                    }
                                    hurter.heal(hurter.getMaxHealth() / 200);
                                }

                            }.start();
                        }
                    }
                    new SynchronizationTask(3600 / (!hurter.world.isDaytime() ? 2 : 1)) {

                        @Override
                        public void run() {
                            FULL_MOON_COOLDING.remove(hurter.getUniqueID());
                        }

                    }.start();
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                
                if (bonusLevel > 0) {
                    if (FULL_MOON.contains(hurter.getUniqueID())) {
                        evt.setAmount(evt.getAmount() * 0.5f);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            EntityLivingBase healer = evt.getEntityLiving();
            int bonusLevel = 0;
            for (@Nonnull ItemStack itemStack : healer.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            
                if (bonusLevel > 0) {
                evt.setAmount(evt.getAmount() + evt.getAmount() * 0.25f);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.RECOLLECT.contains(ench) &&
                !CarianStyleEnchantments.DEAD.contains(ench) &&
                !ench.equals(CarianStyleEnchantments.HEALING_BY_FIRE) &&
                !ench.equals(CarianStyleEnchantments.SHELTER_OF_FIRE);
    }
}
