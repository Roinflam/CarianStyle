package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 唤星附魔
 * <p>v2.3：ProjectileImpact射手视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.3
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

    private static final int MAX_ATTRACT_RADIUS = 12;
    private static final int MAX_LIGHTNING_RADIUS = 8;
    private static final int MAX_TARGETS = 20;

    public EnchantmentCallStar() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    private static boolean isNightTime(@NotNull Level level) {
        long dayTime = level.getDayTime() % 24000;
        return dayTime >= 13000 && dayTime < 23000;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        if (arrow.getOwner() == null || evt.getRayTraceResult().getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) arrow.getOwner();

        // ⭐ v2.3：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
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

        int attractRadius = Math.min(effectiveLevel * 2, MAX_ATTRACT_RADIUS);

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                arrow,
                attractRadius,
                entity -> !entity.equals(attacker)
        );

        int attractHitCount = 0;
        for (LivingEntity entity : nearbyEntities) {
            if (attractHitCount >= MAX_TARGETS) {
                break;
            }
            double x = entity.getX() - arrow.getX();
            double z = entity.getZ() - arrow.getZ();
            float strength = (float) (effectiveLevel * 0.35f * Math.max(Math.abs(x), Math.abs(z)) / 7);
            entity.knockback(strength, x, z);
            attractHitCount++;
        }

        new SynchronizationTask(20) {
            @Override
            public void run() {
                int lightningRadius = Math.min(effectiveLevel, MAX_LIGHTNING_RADIUS);

                List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        arrow,
                        lightningRadius,
                        entity -> !entity.equals(attacker)
                );

                if (!targets.isEmpty()) {
                    int hitCount = 0;
                    for (LivingEntity target : targets) {
                        if (hitCount >= MAX_TARGETS) {
                            break;
                        }

                        Level world = target.level();

                        if (world instanceof ServerLevel serverLevel) {
                            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                            if (lightning != null) {
                                lightning.moveTo(target.getX(), target.getY(), target.getZ());
                                lightning.setVisualOnly(true);
                                serverLevel.addFreshEntity(lightning);
                            }
                        }

                        int magnification = isNightTime(world) ? 3 : 1;

                        float baseDamage = (float) arrow.getBaseDamage();
                        float damage = baseDamage * effectiveLevel * 0.3f * magnification;

                        target.hurt(target.damageSources().lightningBolt(), damage);

                        if (target.onGround()) {
                            double x = RandomUtils.nextBoolean() ? arrow.getX() - target.getX() : target.getX() - arrow.getX();
                            double z = RandomUtils.nextBoolean() ? arrow.getZ() - target.getZ() : target.getZ() - arrow.getZ();
                            target.knockback(0.2f, x, z);
                        }
                        hitCount++;
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
