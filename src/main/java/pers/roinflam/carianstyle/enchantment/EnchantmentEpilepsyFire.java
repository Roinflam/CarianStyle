package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

@Mod.EventBusSubscriber
public class EnchantmentEpilepsyFire extends RaryBase {

    public EnchantmentEpilepsyFire(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "epilepsy_fire");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.EPILEPSY_FIRE;
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
                        float hurtDamage = evt.getAmount();
                        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
                            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.EPILEPSY_FIRE_BURNING, 3 * 20 + 5, 0));
                            new SynchronizationTask(5, 1) {
                                private int tick = 0;

                                @Override
                                public void run() {
                                    if (++tick > 60 || !attacker.isEntityAlive()) {
                                        this.cancel();
                                        return;
                                    }
                                    float damage = (float) (attacker.getMaxHealth() * 0.2 / 60);
                                    if (attacker.getHealth() - damage * 1.1 > 0) {
                                        attacker.setHealth(attacker.getHealth() - damage);
                                    } else {
                                        attacker.onDeath(NewDamageSource.EPILEPSY_FIRE);
                                        attacker.setDead();
                                        this.cancel();
                                    }
                                }

                            }.start();
                        }
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.EPILEPSY_FIRE_BURNING, 3 * 20 + 5, 0));
                        new SynchronizationTask(5, 1) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 60 || !hurter.isEntityAlive()) {
                                    this.cancel();
                                    return;
                                }
                                float damage = (float) (attacker.getMaxHealth() * 0.2 * bonusLevel / 60);
                                if (hurter.getHealth() - damage * 1.1 > 0) {
                                    hurter.setHealth(hurter.getHealth() - damage);
                                } else {
                                    hurter.onDeath(NewDamageSource.EPILEPSY_FIRE);
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
        return 5 + (enchantmentLevel - 1) * 25;
    }

}
