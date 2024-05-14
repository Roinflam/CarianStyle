package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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
public class EnchantmentEpilepsySpread extends RaryBase {
    private static final Set<UUID> EPILEPSY_SPREAD = new HashSet<>();
    private static final Set<UUID> EPILEPSY_SPREAD_COOLDING = new HashSet<>();

    public EnchantmentEpilepsySpread(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "epilepsy_spread");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.EPILEPSY_SPREAD;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
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
                    if (!EPILEPSY_SPREAD_COOLDING.contains(hurter.getUniqueID())) {
                        if (hurter.getHealth() - evt.getAmount() <= hurter.getMaxHealth() * 0.3) {
                            EPILEPSY_SPREAD.add(hurter.getUniqueID());
                            EPILEPSY_SPREAD_COOLDING.add(hurter.getUniqueID());

                            evt.setCanceled(true);
                            hurter.setHealth(hurter.getMaxHealth() * 0.3f);
                            hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 6));

                            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                    EntityLivingBase.class,
                                    hurter,
                                    bonusLevel * 4
                            );
                            for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                                entityLivingBase.playSound(SoundEvents.ENTITY_GHAST_HURT, 1, 1);
                                if (!entityLivingBase.equals(hurter)) {
                                    double x = entityLivingBase.posX - hurter.posX;
                                    double z = entityLivingBase.posZ - hurter.posZ;
                                    float stronge = (float) (bonusLevel * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
                                    entityLivingBase.knockBack(hurter, stronge, x, z);
                                }
                            }
                            int finalBonusLevel = bonusLevel;
                            new SynchronizationTask(30) {

                                @Override
                                public void run() {
                                    if (!entities.isEmpty()) {
                                        for (Entity entity : entities) {
                                            EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                                            entityLivingBase.playSound(SoundEvents.ENTITY_GHAST_HURT, 1, 1);
                                            entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.EPILEPSY_FIRE_BURNING, 3 * 20 + 5, 0));
                                            new SynchronizationTask(5, 1) {
                                                private int tick = 0;

                                                @Override
                                                public void run() {
                                                    if (++tick > 60 || !entityLivingBase.isEntityAlive()) {
                                                        this.cancel();
                                                        return;
                                                    }
                                                    if (entityLivingBase.equals(hurter)) {
                                                        float damage = hurter.getMaxHealth() * 0.3f / 60;
                                                        if (hurter.getHealth() - damage * 2 > 0) {
                                                            hurter.setHealth(hurter.getHealth() - damage);
                                                        } else {
                                                            EPILEPSY_SPREAD.remove(hurter.getUniqueID());
                                                            EntityLivingUtil.kill(hurter, NewDamageSource.EPILEPSY_FIRE);
                                                            this.cancel();
                                                        }
                                                    } else {
                                                        float damage = hurter.getMaxHealth() * finalBonusLevel * 0.3f * 2 / 60;
                                                        if (entityLivingBase.getHealth() - damage * 2 > 0) {
                                                            entityLivingBase.setHealth(entityLivingBase.getHealth() - damage);
                                                        } else {
                                                            EntityLivingUtil.kill(entityLivingBase, NewDamageSource.EPILEPSY_FIRE);
                                                            this.cancel();
                                                        }
                                                    }
                                                }

                                            }.start();
                                        }
                                    }

                                    new SynchronizationTask(66) {

                                        @Override
                                        public void run() {
                                            EntityLivingUtil.kill(hurter, NewDamageSource.EPILEPSY_FIRE);
                                            EPILEPSY_SPREAD.remove(hurter.getUniqueID());
                                        }

                                    }.start();
                                }

                            }.start();

                            new SynchronizationTask(1800) {

                                @Override
                                public void run() {
                                    EPILEPSY_SPREAD_COOLDING.remove(hurter.getUniqueID());
                                }

                            }.start();
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (EPILEPSY_SPREAD.contains(hurter.getUniqueID())) {
                evt.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            EntityLivingBase entityLiving = evt.getEntityLiving();
            if (EPILEPSY_SPREAD.contains(entityLiving.getUniqueID())) {
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
