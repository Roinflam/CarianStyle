package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

@Mod.EventBusSubscriber
public class EnchantmentWarrior extends VeryRaryBase {

    public EnchantmentWarrior(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "warrior");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.WARRIOR;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage_attack(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.25));
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage_hurter(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                if (bonusLevel > 0) {
                    evt.setAmount((float) (evt.getAmount() * 0.5));
                    float damage = evt.getAmount() / 60;
                    new SynchronizationTask(5, 1) {
                        private int tick = 0;

                        @Override
                        public void run() {
                            if (++tick > 60 || !hurter.isEntityAlive()) {
                                this.cancel();
                                return;
                            }
                            if (hurter.getHealth() - damage * 1.1 > 0) {
                                hurter.setHealth(hurter.getHealth() - damage);
                            } else {
                                hurter.onDeath(evt.getSource().setDamageBypassesArmor());
                                hurter.setDead();
                                this.cancel();
                            }
                        }

                    }.start();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), killer.getHeldItem(killer.getActiveHand()));
                    if (bonusLevel > 0) {
                        killer.heal((float) ((killer.getMaxHealth() - killer.getHealth()) * 0.25));
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
