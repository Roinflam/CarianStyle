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
public class EnchantmentEpilepsySpread extends Enchantment {
    private static final Set<UUID> EPILEPSY_SPREAD = new HashSet<>();
    private static final Set<UUID> EPILEPSY_SPREAD_COOLDING = new HashSet<>();

    public EnchantmentEpilepsySpread(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "epilepsy_spread");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.EPILEPSY_SPREAD;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
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
                    if (!EPILEPSY_SPREAD_COOLDING.contains(hurter.getUniqueID())) {
                        if (hurter.getHealth() - evt.getAmount() <= hurter.getMaxHealth() * 0.3) {
                            EPILEPSY_SPREAD.add(hurter.getUniqueID());
                            EPILEPSY_SPREAD_COOLDING.add(hurter.getUniqueID());

                            evt.setCanceled(true);
                            hurter.setHealth((float) (hurter.getMaxHealth() * 0.3));
                            hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 6));

                            List<Entity> entities = EntityUtil.getNearbyEntities(
                                    hurter,
                                    bonusLevel * 4,
                                    bonusLevel * 4,
                                    entity -> entity instanceof EntityLivingBase
                            );
                            for (Entity entity : entities) {
                                EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
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
                                                        float damage = (float) (hurter.getMaxHealth() * 0.3 / 60);
                                                        if (hurter.getHealth() - damage * 1.1 > 0) {
                                                            hurter.setHealth(hurter.getHealth() - damage);
                                                        } else {
                                                            EPILEPSY_SPREAD.remove(hurter.getUniqueID());
                                                            if (hurter.isEntityAlive()) {
                                                                hurter.attackEntityFrom(NewDamageSource.EPILEPSY_FIRE, (float) (damage * 1.1));
                                                            }
                                                            this.cancel();
                                                        }
                                                    } else {
                                                        float damage = (float) (hurter.getMaxHealth() * finalBonusLevel * 0.3 * 2 / 60);
                                                        if (entityLivingBase.getHealth() - damage * 1.1 > 0) {
                                                            entityLivingBase.setHealth(entityLivingBase.getHealth() - damage);
                                                        } else {
                                                            if (entityLivingBase.isEntityAlive()) {
                                                                entityLivingBase.attackEntityFrom(NewDamageSource.EPILEPSY_FIRE, (float) (damage * 1.1));
                                                            }
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
                                            if (hurter.isEntityAlive()) {
                                                hurter.setHealth(0);
                                                hurter.onDeath(NewDamageSource.EPILEPSY_FIRE);
                                            }
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
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (EPILEPSY_SPREAD.contains(hurter.getUniqueID())) {
                evt.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            EntityLivingBase entityLiving = evt.getEntityLiving();
            if (EPILEPSY_SPREAD.contains(entityLiving.getUniqueID())) {
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
        return !CarianStyleEnchantments.DEAD.contains(ench);
    }
}
