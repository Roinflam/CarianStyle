package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.RandomUtils;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 唤星附魔
 *
 * 箭矢落地时吸引周围敌人，延迟后召唤闪电造成伤害
 * 夜晚伤害×3
 */
@AutoRegisterEnchantment(
        id = "call_star",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentLorettaBigBow.class,
                EnchantmentLorettaTrick.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCallStar extends EnchantmentBase {

    public EnchantmentCallStar() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getArrow().shootingEntity == null || evt.getRayTraceResult().entityHit != null) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment callStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCallStar.class);
        if (callStar == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                callStar,
                attacker.getHeldItem(attacker.getActiveHand()));

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                arrow,
                effectiveLevel * 2,
                entity -> !entity.equals(attacker)
        );

        for (EntityLivingBase entity : nearbyEntities) {
            double x = entity.posX - arrow.posX;
            double z = entity.posZ - arrow.posZ;
            float strength = (float) (effectiveLevel * 0.35f * Math.max(Math.abs(x), Math.abs(z)) / 7);
            entity.knockBack(attacker, strength, x, z);
        }

        new SynchronizationTask(20) {
            @Override
            public void run() {
                List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        arrow,
                        effectiveLevel,
                        entity -> !entity.equals(attacker)
                );

                if (!targets.isEmpty()) {
                    for (EntityLivingBase target : targets) {
                        World world = target.world;

                        world.addWeatherEffect(new EntityLightningBolt(
                                world,
                                target.posX,
                                target.posY,
                                target.posZ,
                                true
                        ));

                        int magnification = 1;
                        if (!world.isDaytime()) {
                            magnification = 3;
                        }

                        float damage = (float) (arrow.getDamage() * effectiveLevel * 0.3 * magnification);
                        target.attackEntityFrom(DamageSource.LIGHTNING_BOLT, damage);

                        if (target.onGround) {
                            double x = RandomUtils.nextBoolean() ? arrow.posX - target.posX : target.posX - arrow.posX;
                            double z = RandomUtils.nextBoolean() ? arrow.posZ - target.posZ : target.posZ - arrow.posZ;
                            target.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) target.rotationYaw);
                            target.knockBack(attacker, 0.2f, x, z);
                        }
                    }
                } else {
                    World world = arrow.world;
                    world.addWeatherEffect(new EntityLightningBolt(
                            world,
                            arrow.posX,
                            arrow.posY,
                            arrow.posZ,
                            true
                    ));
                }
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}