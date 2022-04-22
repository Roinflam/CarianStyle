package pers.roinflam.kaliastyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.enchantment.EnchantmentBloodCollection;
import pers.roinflam.kaliastyle.enchantment.EnchantmentBloodSlash;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentBlood extends Enchantment {
    private static final HashMap<UUID, Integer> INJURIES = new HashMap<>();

    public EnchantmentBlood(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "blood");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);

        new SynchronizationTask(6000, 6000) {

            @Override
            public void run() {
                INJURIES.clear();
            }

        }.start();
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.BLOOD;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        if (INJURIES.getOrDefault(hurter.getUniqueID(), 0) == 2) {
                            INJURIES.remove(hurter.getUniqueID());
                            float damage = (float) (hurter.getHealth() * 0.08);

                            attacker.heal((float) Math.min(damage, attacker.getMaxHealth() * 0.12));
                            hurter.setHealth(hurter.getHealth() - damage);

                            if (EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodSlash.getEnchantment(), attacker.getHeldItemMainhand()) > 0 && EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodCollection.getEnchantment(), attacker.getHeldItemMainhand()) > 0) {
                                evt.setAmount(evt.getAmount() + (attacker.getMaxHealth() - attacker.getHealth()));
                            } else {
                                evt.setAmount((float) (evt.getAmount() + (attacker.getMaxHealth() - attacker.getHealth()) * 0.2));
                            }
                        } else {
                            INJURIES.put(hurter.getUniqueID(), INJURIES.getOrDefault(hurter.getUniqueID(), 0) + 1);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return KaliaStyleEnchantments.RECOLLECT_ENCHANTABILITY;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

}
