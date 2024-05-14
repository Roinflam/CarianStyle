package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentGoldenLaw extends VeryRaryBase {
    private static final Set<UUID> GOLDEN_LAW = new HashSet<>();

    public EnchantmentGoldenLaw(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "golden_law");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.GOLDEN_LAW;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                    @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                    if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));

                if (bonusLevel > 0) {
                            float damage = evt.getAmount() * 0.15f + evt.getAmount() * 0.45f * (1 - attacker.getHealth() / attacker.getMaxHealth());
                            evt.setAmount(evt.getAmount() + damage);
                        }
                    }
                }
                if (!evt.getSource().canHarmInCreative()) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));

                if (bonusLevel > 0) {
                            evt.setAmount(evt.getAmount() - evt.getAmount() * 0.15f);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItemMainhand());
                    
                if (bonusLevel > 0) {
                        if (evt.getAmount() <= hurter.getHealth() * 0.15) {
                            evt.setCanceled(true);
                        } else if (!GOLDEN_LAW.contains(hurter.getUniqueID())) {
                            evt.setCanceled(true);
                            GOLDEN_LAW.add(hurter.getUniqueID());
                            new SynchronizationTask(100) {

                                @Override
                                public void run() {
                                    GOLDEN_LAW.remove(hurter.getUniqueID());
                                }

                            }.start();
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
        return !CarianStyleEnchantments.RECOLLECT.contains(ench) && !CarianStyleEnchantments.LAW.contains(ench);
    }
}
