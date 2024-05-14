package pers.roinflam.carianstyle.enchantment.dead;

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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentScarletLonia extends RaryBase {
    private static final Set<UUID> SCARLET_LONIA = new HashSet<>();
    private static final Set<UUID> SCARLET_LONIA_COOLDING = new HashSet<>();

    public EnchantmentScarletLonia(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "scarlet_lonia");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.SCARLET_LONIA;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    if (!SCARLET_LONIA_COOLDING.contains(hurter.getUniqueID())) {
                        SCARLET_LONIA.add(hurter.getUniqueID());
                        SCARLET_LONIA_COOLDING.add(hurter.getUniqueID());

                        evt.setCanceled(true);
                        hurter.setHealth(1);
                        hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 6));

                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                hurter,
                                bonusLevel * 4,
                                entityLivingBase -> !entityLivingBase.equals(hurter)
                        );
                        for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                            double x = entityLivingBase.posX - hurter.posX;
                            double z = entityLivingBase.posZ - hurter.posZ;
                            float stronge = (float) (bonusLevel * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
                            entityLivingBase.knockBack(hurter, stronge, x, z);
                        }
                        int finalBonusLevel = bonusLevel;
                        new SynchronizationTask(30) {

                            @Override
                            public void run() {
                                @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                        EntityLivingBase.class,
                                        hurter,
                                        finalBonusLevel * 2,
                                        entityLivingBase -> !entityLivingBase.equals(hurter)
                                );
                                if (!entities.isEmpty()) {
                                    for (Entity entity : entities) {
                                        EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                                        entityLivingBase.setHealth(entityLivingBase.getHealth() - entityLivingBase.getHealth() * finalBonusLevel * 0.05f);
                                        entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_ROT, finalBonusLevel * 10 * 20, finalBonusLevel - 1));
                                        double x = hurter.posX - entityLivingBase.posX;
                                        double z = hurter.posZ - entityLivingBase.posZ;
                                        entityLivingBase.knockBack(hurter, finalBonusLevel * 0.75f, x, z);
                                    }
                                }
                                SCARLET_LONIA.remove(hurter.getUniqueID());

                                EntityLivingUtil.kill(hurter, NewDamageSource.SCARLET_ROT);
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
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (SCARLET_LONIA.contains(hurter.getUniqueID())) {
                evt.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            EntityLivingBase entityLiving = evt.getEntityLiving();
            if (SCARLET_LONIA.contains(entityLiving.getUniqueID())) {
                EntityLivingUtil.setJumped(entityLiving);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.DEAD.contains(ench);
    }
}
