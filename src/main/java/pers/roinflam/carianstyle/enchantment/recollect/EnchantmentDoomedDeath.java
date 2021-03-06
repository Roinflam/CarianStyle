package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

@Mod.EventBusSubscriber
public class EnchantmentDoomedDeath extends VeryRaryBase {

    public EnchantmentDoomedDeath(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "doomed_death");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DOOMED_DEATH;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH_BURNING, 5 * 20 + 5, 0));
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH, 10 * 20, 0));
                        float hurtDamage = evt.getAmount();
                        new SynchronizationTask(5, 1) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 100 || hurter.isDead) {
                                    this.cancel();
                                    return;
                                }
                                float damage = (float) ((hurtDamage * 0.5 + hurter.getHealth() * 0.05) / 100);
                                damage = (float) (damage * 0.3 + damage * tick / 50 * 0.7);
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
