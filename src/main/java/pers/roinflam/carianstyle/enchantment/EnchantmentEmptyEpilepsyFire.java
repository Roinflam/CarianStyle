package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

@Mod.EventBusSubscriber
public class EnchantmentEmptyEpilepsyFire extends RaryBase {

    public EnchantmentEmptyEpilepsyFire(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "empty_epilepsy_fire");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.EMPTY_EPILEPSY_FIRE;
    }

    @SubscribeEvent
    public static void onProjectileImpact_Arrow(ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getArrow().shootingEntity != null && evt.getRayTraceResult().entityHit instanceof EntityLivingBase) {
                EntityArrow entityArrow = evt.getArrow();
                EntityLivingBase attacker = (EntityLivingBase) evt.getArrow().shootingEntity;
                EntityLivingBase hurter = (EntityLivingBase) evt.getRayTraceResult().entityHit;
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                if (bonusLevel > 0) {
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
                                float damage = (float) (attacker.getMaxHealth() * 0.15 / 60);
                                if (attacker.getHealth() - damage * 2 > 0) {
                                    attacker.setHealth(attacker.getHealth() - damage);
                                } else {
                                    EntityLivingUtil.kill(attacker, NewDamageSource.EPILEPSY_FIRE);
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
                            float damage = (float) (attacker.getMaxHealth() * 0.15 * bonusLevel / 60);
                            if (hurter.getHealth() - damage * 2 > 0) {
                                hurter.setHealth(hurter.getHealth() - damage);
                            } else {
                                EntityLivingUtil.kill(hurter, NewDamageSource.EPILEPSY_FIRE);
                                this.cancel();
                            }
                        }

                    }.start();
                }
            }
        }

    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 10 * 10;
    }

}
