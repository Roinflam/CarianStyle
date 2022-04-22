package pers.roinflam.kaliastyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentDoomedDeath extends Enchantment {
    private static final List<UUID> DOOMED_DEATH = new ArrayList<>();

    public EnchantmentDoomedDeath(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "doomed_death");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.DOOMED_DEATH;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        float hurtDamage = evt.getAmount();
                        DOOMED_DEATH.add(hurter.getUniqueID());
                        new SynchronizationTask(5, 1) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 100 || !hurter.isEntityAlive()) {
                                    this.cancel();
                                    return;
                                }
                                float damage = (float) ((hurtDamage * 0.75 + hurter.getHealth() * 0.075) / 100);
                                damage = (float) (damage * 0.3 + damage * tick / 50 * 0.7);
                                if (hurter.getHealth() - damage * 1.1 > 0) {
                                    hurter.setHealth(hurter.getHealth() - damage);
                                } else {
                                    hurter.hurtResistantTime = 0;
                                    hurter.attackEntityFrom(evt.getSource().setDamageBypassesArmor().setDamageAllowedInCreativeMode(), (float) (damage * 1.1));
                                    this.cancel();
                                }
                            }

                        }.start();
                        new SynchronizationTask(30) {

                            @Override
                            public void run() {
                                DOOMED_DEATH.remove(hurter.getUniqueID());
                            }

                        }.start();
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (DOOMED_DEATH.contains(healer.getUniqueID())) {
                evt.setAmount((float) (evt.getAmount() - evt.getAmount() * 0.99));
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

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !KaliaStyleEnchantments.RECOLLECT.contains(ench);
    }
}
