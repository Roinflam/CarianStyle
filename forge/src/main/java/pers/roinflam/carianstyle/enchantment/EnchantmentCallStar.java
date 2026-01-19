package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 唤星附魔
 * <p>
 * 箭矢落地时吸引周围敌人，延迟后召唤闪电造成伤害
 * 夜晚伤害×3
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "call_star",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentLorettaBigBow.class,
                EnchantmentLorettaTrick.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCallStar extends EnchantmentBase {

    public EnchantmentCallStar() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // 必须是箭矢
        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        // 必须有射击者且未击中实体（落地）
        if (arrow.getOwner() == null || evt.getRayTraceResult().getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) arrow.getOwner();

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment callStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCallStar.class);
        if (callStar == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(callStar, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                arrow,
                effectiveLevel * 2,
                entity -> !entity.equals(attacker)
        );

        for (LivingEntity entity : nearbyEntities) {
            double x = entity.getX() - arrow.getX();
            double z = entity.getZ() - arrow.getZ();
            float strength = (float) (effectiveLevel * 0.35f * Math.max(Math.abs(x), Math.abs(z)) / 7);
            entity.knockback(strength, x, z);
        }

        new SynchronizationTask(20) {
            @Override
            public void run() {
                List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        arrow,
                        effectiveLevel,
                        entity -> !entity.equals(attacker)
                );

                if (!targets.isEmpty()) {
                    for (LivingEntity target : targets) {
                        Level world = target.level();

                        if (world instanceof ServerLevel serverLevel) {
                            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                            if (lightning != null) {
                                lightning.moveTo(target.getX(), target.getY(), target.getZ());
                                lightning.setVisualOnly(true);
                                serverLevel.addFreshEntity(lightning);
                            }
                        }

                        int magnification = 1;
                        if (!world.isDay()) {
                            magnification = 3;
                        }

                        float damage = (float) (arrow.getBaseDamage() * effectiveLevel * 0.3 * magnification);
                        target.hurt(target.damageSources().lightningBolt(), damage);

                        if (target.onGround()) {
                            double x = RandomUtils.nextBoolean() ? arrow.getX() - target.getX() : target.getX() - arrow.getX();
                            double z = RandomUtils.nextBoolean() ? arrow.getZ() - target.getZ() : target.getZ() - arrow.getZ();
                            target.knockback(0.2f, x, z);
                        }
                    }
                } else {
                    Level world = arrow.level();
                    if (world instanceof ServerLevel serverLevel) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                        if (lightning != null) {
                            lightning.moveTo(arrow.getX(), arrow.getY(), arrow.getZ());
                            lightning.setVisualOnly(true);
                            serverLevel.addFreshEntity(lightning);
                        }
                    }
                }
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}