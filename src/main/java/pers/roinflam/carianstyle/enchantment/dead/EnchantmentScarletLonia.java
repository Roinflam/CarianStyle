package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
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
        id = "scarlet_lonia",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentScarletLonia extends EnchantmentBase {

    public EnchantmentScarletLonia() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    protected void onDeath(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        EntityLivingBase hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("scarlet_lonia_cooldown", hurter.getUniqueID())) {
            Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUniqueID());
            if (isActive != null && isActive) {
                ctx.cancelEvent();
                hurter.setHealth(1);
            }
            return;
        }

        EnchantmentDataManager.setData("scarlet_lonia_active", hurter.getUniqueID(), true);
        EnchantmentDataManager.setCooldown("scarlet_lonia_cooldown", hurter.getUniqueID(), 1800);

        ctx.cancelEvent();
        hurter.setHealth(1);
        hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 6));

        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                hurter,
                level * 4,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        for (EntityLivingBase entityLivingBase : entities) {
            double x = entityLivingBase.posX - hurter.posX;
            double z = entityLivingBase.posZ - hurter.posZ;
            float stronge = (float) (level * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
            entityLivingBase.knockBack(hurter, stronge, x, z);
        }

        int finalLevel = level;
        new SynchronizationTask(30) {
            @Override
            public void run() {
                List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        hurter,
                        finalLevel * 2,
                        entityLivingBase -> !entityLivingBase.equals(hurter)
                );

                if (!nearbyEntities.isEmpty()) {
                    for (Entity entity : nearbyEntities) {
                        EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                        entityLivingBase.setHealth(entityLivingBase.getHealth() - entityLivingBase.getHealth() * finalLevel * 0.05f);
                        entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_ROT, finalLevel * 10 * 20, finalLevel - 1));

                        double x = hurter.posX - entityLivingBase.posX;
                        double z = hurter.posZ - entityLivingBase.posZ;
                        entityLivingBase.knockBack(hurter, finalLevel * 0.75f, x, z);
                    }
                }

                EnchantmentDataManager.removeData("scarlet_lonia_active", hurter.getUniqueID());
                EntityLivingUtil.kill(hurter, NewDamageSource.SCARLET_ROT);
            }
        }.start();
    }

    @Override
    protected void onDefendHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUniqueID());

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
                Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", entityLiving.getUniqueID());

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