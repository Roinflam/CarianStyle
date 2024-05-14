package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
public class EnchantmentDeathBlade extends VeryRaryBase {

    public EnchantmentDeathBlade(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "death_blade");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DEATH_BLADE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
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
                        DamageSource damageSource = evt.getSource();
                        if (!damageSource.damageType.equals("deathBlade") && !damageSource.canHarmInCreative()) {
                            float damage = evt.getAmount() * 0.75f / 100;
                            evt.setAmount(evt.getAmount() * 0.5f);
                            damageSource.damageType = "deathBlade";
                            hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH_BURNING, 5 * 20 + 5, 0));
                            hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH, 10 * 20 + 5, 0));
                            new SynchronizationTask(1, 1) {
                                private int tick = 0;

                                @Override
                                public void run() {
                                    if (++tick > 100 || !hurter.isEntityAlive()) {
                                        this.cancel();
                                        return;
                                    }
                                    if (hurter.getHealth() - damage * 2 > 0) {
                                        hurter.setHealth(hurter.getHealth() - damage);
                                    } else {
                                        EntityLivingUtil.kill(hurter, damageSource);
                                        this.cancel();
                                    }
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
        return (int) (50 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
