package pers.roinflam.carianstyle.enchantment.combatskill;

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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentCorpsePiler extends RaryBase {
    public final static HashMap<UUID, Integer> KILL_NUMBER = new HashMap<>();

    public EnchantmentCorpsePiler(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "corpse_piler");

        new SynchronizationTask(6000, 6000) {

            @Override
            public void run() {
                KILL_NUMBER.clear();
            }

        }.start();
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CORPSE_PILER;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase deader = evt.getEntityLiving();
                @Nullable EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), killer.getHeldItem(killer.getActiveHand()));
                    
                if (bonusLevel > 0) {
                        if (killer.world.rand.nextBoolean()) {
                            KILL_NUMBER.put(killer.getUniqueID(), Math.min(KILL_NUMBER.getOrDefault(killer.getUniqueID(), 0) + 1, 50));
                        }
                    }
                }
                if (KILL_NUMBER.containsKey(deader.getUniqueID())) {
                    KILL_NUMBER.put(deader.getUniqueID(), KILL_NUMBER.get(deader.getUniqueID()) / 2);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    
                if (bonusLevel > 0) {
                        int number = KILL_NUMBER.getOrDefault(attacker.getUniqueID(), 0);
                        if (number > 0) {
                            evt.setAmount(evt.getAmount() + evt.getAmount() * number * bonusLevel * 0.01f);
                            attacker.heal(attacker.getMaxHealth() * number * bonusLevel * 0.0005f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }


    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(CarianStyleEnchantments.VIC_DRAGON_THUNDER) &&
                !ench.equals(CarianStyleEnchantments.DARK_MOON);
    }

}
