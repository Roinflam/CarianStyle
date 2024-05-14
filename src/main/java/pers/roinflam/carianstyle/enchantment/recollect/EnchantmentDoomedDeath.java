package pers.roinflam.carianstyle.enchantment.recollect;

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
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentDoomedDeath extends VeryRaryBase {

    public EnchantmentDoomedDeath(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "doomed_death");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DOOMED_DEATH;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
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
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH_BURNING, 5 * 20 + 5, 0));
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH, 10 * 20 + 5, 0));
                        float hurtDamage = evt.getAmount();
                        new SynchronizationTask(5, 1) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 100 || hurter.isDead) {
                                    this.cancel();
                                    return;
                                }
                                float damage = (hurtDamage * 0.5f + hurter.getHealth() * 0.1f) / 100;
                                damage = damage * 0.3f + damage * tick / 50 * 0.7f;
                                if (hurter.getHealth() - damage * 2 > 0) {
                                    hurter.setHealth(hurter.getHealth() - damage);
                                } else {
                                    EntityLivingUtil.kill(hurter, evt.getSource());
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
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.RECOLLECT.contains(ench);
    }
}
