package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentGiantFlame extends VeryRaryBase {
    private static final Set<UUID> FLAME = new HashSet<>();

    public EnchantmentGiantFlame(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "giant_flame");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.GIANT_FLAME;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                    if (EntityUtil.getFire(hurter) > 0) {
                        int bonusLevel = 0;
                        for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                            if (!itemStack.isEmpty()) {
                                bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                            }
                        }
                        if (bonusLevel > 0) {
                            attacker.attackEntityFrom(DamageSource.IN_FIRE, (float) (evt.getAmount() * 0.5));
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    evt.setAmount(evt.getAmount() - (float) (evt.getAmount() * (hurter.getHealth() / hurter.getMaxHealth()) * 0.25));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getEntityLiving().isEntityAlive()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (!evt.getSource().canHarmInCreative()) {
                    int bonusLevel = 0;
                    for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    if (bonusLevel > 0) {
                        DamageSource damageSource = evt.getSource();
                        if (damageSource.isFireDamage()) {
                            evt.setCanceled(true);
                            if (!FLAME.contains(hurter.getUniqueID())) {
                                hurter.heal(evt.getAmount());
                                FLAME.add(hurter.getUniqueID());
                                new SynchronizationTask(10) {

                                    @Override
                                    public void run() {
                                        FLAME.remove(hurter.getUniqueID());
                                    }

                                }.start();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.RECOLLECT.contains(ench);
    }
}
