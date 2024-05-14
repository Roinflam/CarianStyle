package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentTimeReversal extends VeryRaryBase {
    private static final HashMap<UUID, Float> REVERSAL = new HashMap<>();
    private static final Set<UUID> REVERSAL_COOLDING = new HashSet<>();

    public EnchantmentTimeReversal(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "time_reversal");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.TIME_REVERSAL;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            int bonusLevel = 0;
            for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            
                if (bonusLevel > 0) {
                if (!REVERSAL_COOLDING.contains(hurter.getUniqueID())) {
                    if (!hurter.isDead) {
                        evt.setCanceled(true);
                        hurter.setHealth(1);

                        REVERSAL_COOLDING.add(hurter.getUniqueID());
                        REVERSAL.put(hurter.getUniqueID(), 0f);

                        new SynchronizationTask(100) {

                            @Override
                            public void run() {
                                if (hurter.isEntityAlive()) {
                                    hurter.heal(REVERSAL.get(hurter.getUniqueID()) * 0.25f);
                                }
                                REVERSAL.remove(hurter.getUniqueID());
                            }

                        }.start();
                        new SynchronizationTask(6000) {

                            @Override
                            public void run() {
                                REVERSAL_COOLDING.remove(hurter.getUniqueID());
                            }

                        }.start();
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            int bonusLevel = 0;
            for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            
                if (bonusLevel > 0) {
                if (REVERSAL.containsKey(hurter.getUniqueID())) {
                    if (!evt.getEntity().equals(evt.getSource().getTrueSource())) {
                        evt.setCanceled(true);
                        REVERSAL.put(hurter.getUniqueID(), REVERSAL.get(hurter.getUniqueID()) + evt.getAmount());
                        if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                            @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                            attacker.attackEntityFrom(evt.getSource(), evt.getAmount());
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
        return !CarianStyleEnchantments.RECOLLECT.contains(ench) &&
                !CarianStyleEnchantments.DEAD.contains(ench);
    }
}
