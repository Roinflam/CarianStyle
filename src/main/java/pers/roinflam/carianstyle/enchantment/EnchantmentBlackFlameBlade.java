package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

@Mod.EventBusSubscriber
public class EnchantmentBlackFlameBlade extends RaryBase {

    public EnchantmentBlackFlameBlade(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "black_flame_blade");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLACK_FLAME_BLADE;
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
                        float damage = (float) (evt.getAmount() * bonusLevel * 0.15 / 100);
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DESTRUCTION_FIRE_BURNING, 5 * 20 + 5, 0));
                        new SynchronizationTask(5, 1) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 100 || !hurter.isEntityAlive()) {
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
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25 + (enchantmentLevel - 1) * 15;
    }


    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(CarianStyleEnchantments.INVISIBLE_WEAPON);
    }

}
