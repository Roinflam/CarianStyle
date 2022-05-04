package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentScarletLonia extends Enchantment {
    private static final Set<UUID> SCARLET_LONIA = new HashSet<>();
    private static final Set<UUID> SCARLET_LONIA_COOLDING = new HashSet<>();

    public EnchantmentScarletLonia(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "scarlet_lonia");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.SCARLET_LONIA;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (itemStack != null) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    if (!SCARLET_LONIA_COOLDING.contains(hurter.getUniqueID())) {
                        SCARLET_LONIA.add(hurter.getUniqueID());
                        SCARLET_LONIA_COOLDING.add(hurter.getUniqueID());

                        evt.setCanceled(true);
                        hurter.setHealth(1);
                        hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 6));

                        List<Entity> entities = EntityUtil.getNearbyEntities(
                                hurter,
                                bonusLevel * 4,
                                bonusLevel * 4,
                                entity -> entity instanceof EntityLivingBase && !entity.equals(hurter)
                        );
                        for (Entity entity : entities) {
                            EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                            double x = entityLivingBase.posX - hurter.posX;
                            double z = entityLivingBase.posZ - hurter.posZ;
                            float stronge = (float) (bonusLevel * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
                            entityLivingBase.knockBack(hurter, stronge, x, z);
                        }
                        int finalBonusLevel = bonusLevel;
                        new SynchronizationTask(30) {

                            @Override
                            public void run() {
                                List<Entity> entities = EntityUtil.getNearbyEntities(
                                        hurter,
                                        finalBonusLevel * 2,
                                        finalBonusLevel * 2,
                                        entity -> entity instanceof EntityLivingBase && !entity.equals(hurter)
                                );
                                if (!entities.isEmpty()) {
                                    for (Entity entity : entities) {
                                        EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                                        entityLivingBase.setHealth((float) (entityLivingBase.getHealth() - entityLivingBase.getHealth() * finalBonusLevel * 0.05));
                                        entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_CORRUPTION, finalBonusLevel * 10 * 20, finalBonusLevel - 1));
                                        double x = hurter.posX - entityLivingBase.posX;
                                        double z = hurter.posZ - entityLivingBase.posZ;
                                        entityLivingBase.knockBack(hurter, (float) (finalBonusLevel * 0.75), x, z);
                                    }
                                }
                                SCARLET_LONIA.remove(hurter.getUniqueID());

                                hurter.setHealth(0);
                                hurter.onDeath(NewDamageSource.SCARLET_CORRUPTION);
                            }

                        }.start();

                        new SynchronizationTask(1800) {

                            @Override
                            public void run() {
                                SCARLET_LONIA_COOLDING.remove(hurter.getUniqueID());
                            }

                        }.start();
                    } else if (SCARLET_LONIA.contains(hurter.getUniqueID())) {
                        evt.setCanceled(true);
                        hurter.setHealth(1);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (SCARLET_LONIA.contains(hurter.getUniqueID())) {
                evt.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            EntityLivingBase entityLiving = evt.getEntityLiving();
            if (SCARLET_LONIA.contains(entityLiving.getUniqueID())) {
                EntityLivingUtil.setJumped(entityLiving);
            }
        }
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 36 + (enchantmentLevel - 1) * 20;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.FULL_MOON) &&
                !ench.equals(CarianStyleEnchantments.LIVING_CORPSE) &&
                !ench.equals(CarianStyleEnchantments.TIME_REVERSAL) &&
                !ench.equals(CarianStyleEnchantments.ANCIENT_DRAGON_LIGHTNING);
    }
}
