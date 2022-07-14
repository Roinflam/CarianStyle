package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentLivingCorpse extends VeryRaryBase {
    private static final Set<UUID> DEAD = new HashSet<>();
    private static final Set<UUID> LOSE_BLOOD = new HashSet<>();

    public EnchantmentLivingCorpse(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "living_corpse");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.LIVING_CORPSE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            int bonusLevel = 0;
            for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            if (bonusLevel > 0) {
                if (!DEAD.contains(hurter.getUniqueID())) {
                    if (!hurter.isDead) {
                        evt.setCanceled(true);
                        hurter.setHealth(hurter.getMaxHealth());
                        DEAD.add(hurter.getUniqueID());
                        LOSE_BLOOD.add(hurter.getUniqueID());
                        new SynchronizationTask(1, 1) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (!hurter.isEntityAlive()) {
                                    this.cancel();
                                    return;
                                }
                                float damage = (float) (hurter.getMaxHealth() * 0.01 / 20);
                                damage = damage * 5 + damage * ++tick / 75;
                                if (hurter.getHealth() - damage * 1.1 > 0) {
                                    hurter.setHealth(hurter.getHealth() - damage);
                                } else {
                                    hurter.onDeath(evt.getSource().setDamageBypassesArmor());
                                    hurter.setDead();
                                    LOSE_BLOOD.remove(hurter.getUniqueID());
                                    this.cancel();
                                }
                            }

                        }.start();
                    }
                }
                new SynchronizationTask(4800) {

                    @Override
                    public void run() {
                        DEAD.remove(hurter.getUniqueID());
                    }

                }.start();
            }
        }
    }


    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getEntity() instanceof EntityLivingBase) {
                EntityLivingBase hurter = (EntityLivingBase) evt.getEntity();
                if (LOSE_BLOOD.contains(hurter.getUniqueID())) {
                    new SynchronizationTask(1, 1) {
                        private int tick = 0;

                        @Override
                        public void run() {
                            if (!hurter.isEntityAlive()) {
                                this.cancel();
                                return;
                            }
                            float damage = (float) (hurter.getMaxHealth() * 0.01 / 20);
                            damage = damage * 6 + damage * ++tick / 30;
                            if (hurter.getHealth() - damage * 1.1 > 0) {
                                hurter.setHealth(hurter.getHealth() - damage);
                            } else {
                                hurter.onDeath(DamageSource.causeMobDamage(hurter).setDamageBypassesArmor());
                                hurter.setDead();
                                LOSE_BLOOD.remove(hurter.getUniqueID());
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
        return CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.RECOLLECT.contains(ench) &&
                !CarianStyleEnchantments.DEAD.contains(ench);
    }
}
