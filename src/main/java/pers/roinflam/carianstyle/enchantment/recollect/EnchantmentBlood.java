package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodCollection;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodSlash;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentBlood extends VeryRaryBase {
    private static final HashMap<UUID, Integer> INJURIES = new HashMap<>();

    public EnchantmentBlood(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "blood");

        new SynchronizationTask(6000, 6000) {

            @Override
            public void run() {
                INJURIES.clear();
            }

        }.start();
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLOOD;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    
                if (bonusLevel > 0) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        if (INJURIES.getOrDefault(attacker.getUniqueID(), 0) == 2) {
                            INJURIES.remove(attacker.getUniqueID());
                            float damage = hurter.getHealth() * 0.12f;

                            attacker.heal(Math.min(damage, attacker.getMaxHealth() * 0.18f));
                            hurter.setHealth(hurter.getHealth() - damage);

                            if (EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodSlash.getEnchantment(), attacker.getHeldItem(attacker.getActiveHand())) > 0 && EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodCollection.getEnchantment(), attacker.getHeldItem(attacker.getActiveHand())) > 0) {
                                hurter.addPotionEffect(new PotionEffect(CarianStylePotion.HEMORRHAGE, 30, 7));
                            } else {
                                hurter.addPotionEffect(new PotionEffect(CarianStylePotion.HEMORRHAGE, 30, 0));
                            }
                        } else {
                            INJURIES.put(attacker.getUniqueID(), INJURIES.getOrDefault(attacker.getUniqueID(), 0) + 1);
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
        return !CarianStyleEnchantments.RECOLLECT.contains(ench);
    }
}
