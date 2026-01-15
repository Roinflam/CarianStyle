package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@AutoRegisterEnchantment(
        id = "epilepsy_spread",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentEpilepsySpread extends EnchantmentBase {

    public EnchantmentEpilepsySpread() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    protected void onDamageAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        EntityLivingBase hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("epilepsy_spread_cooldown", hurter.getUniqueID())) {
            return;
        }

        if (hurter.getHealth() - ctx.getDamage() <= hurter.getMaxHealth() * 0.3) {
            EnchantmentDataManager.setData("epilepsy_spread_active", hurter.getUniqueID(), true);
            EnchantmentDataManager.setCooldown("epilepsy_spread_cooldown", hurter.getUniqueID(), 1800);

            ctx.cancelEvent();
            hurter.setHealth(hurter.getMaxHealth() * 0.3f);
            hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 6));

            List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    hurter,
                    level * 4
            );

            for (EntityLivingBase entityLivingBase : entities) {
                entityLivingBase.playSound(SoundEvents.ENTITY_GHAST_HURT, 1, 1);
                if (!entityLivingBase.equals(hurter)) {
                    double x = entityLivingBase.posX - hurter.posX;
                    double z = entityLivingBase.posZ - hurter.posZ;
                    float stronge = (float) (level * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
                    entityLivingBase.knockBack(hurter, stronge, x, z);
                }
            }

            int finalLevel = level;
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
                                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUniqueID());
                                            EntityLivingUtil.kill(hurter, NewDamageSource.EPILEPSY_FIRE);
                                            this.cancel();
                                        }
                                    } else {
                                        float damage = hurter.getMaxHealth() * finalLevel * 0.3f * 2 / 60;
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
                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUniqueID());
                        }
                    }.start();
                }
            }.start();
        }
    }

    @Override
    protected void onDefendHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", hurter.getUniqueID());

        if (isActive != null && isActive) {
            ctx.cancelEvent();
        }
    }

    @Mod.EventBusSubscriber
    public static class ClientEventHandler {

        @SubscribeEvent
        public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
            if (evt.getEntity().world.isRemote) {
                EntityLivingBase entityLiving = evt.getEntityLiving();
                Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", entityLiving.getUniqueID());

                if (isActive != null && isActive) {
                    EntityLivingUtil.setJumped(entityLiving);
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

}