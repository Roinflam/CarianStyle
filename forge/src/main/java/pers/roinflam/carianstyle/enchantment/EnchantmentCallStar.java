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
 * <p>
 * 修复记录 v2.1：
 * - getUsedItemHand() → InteractionHand.MAIN_HAND
 *   箭矢命中时玩家可能已经不在"使用"状态（弓已放开），
 *   getUsedItemHand()可能返回错误的手，应直接检查主手
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
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

    /**
     * 判断是否为夜晚
     *
     * @param level 世界
     * @return 如果是夜晚返回true
     */
    private static boolean isNightTime(@NotNull Level level) {
        // 获取世界时间（0-24000循环）
        long dayTime = level.getDayTime() % 24000;

        // 夜晚时间：13000-23000
        // 12000是日落开始，13000完全黑暗
        // 23000是日出开始，0是完全白天
        return dayTime >= 13000 && dayTime < 23000;
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

        // v2.1修复：使用主手而非getUsedItemHand()
        // 箭矢飞行/命中时玩家可能已经不在"使用"状态
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

        // 第一阶段：吸引周围敌人（向箭矢位置拉近）
        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                arrow,
                effectiveLevel * 2,
                entity -> !entity.equals(attacker)
        );

        for (LivingEntity entity : nearbyEntities) {
            // 吸引方向：从entity指向arrow（内部取反后推向arrow方向→即拉近）
            // 这里是有意为之的吸引效果，不是击退
            double x = entity.getX() - arrow.getX();
            double z = entity.getZ() - arrow.getZ();
            float strength = (float) (effectiveLevel * 0.35f * Math.max(Math.abs(x), Math.abs(z)) / 7);
            entity.knockback(strength, x, z);
        }

        // 第二阶段：延迟后召唤闪电
        new SynchronizationTask(20) {
            @Override
            public void run() {
                // 重新获取目标列表（因为延迟了20tick，目标可能已移动）
                List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        arrow,
                        effectiveLevel,
                        entity -> !entity.equals(attacker)
                );

                if (!targets.isEmpty()) {
                    for (LivingEntity target : targets) {
                        Level world = target.level();

                        // 召唤视觉闪电效果
                        if (world instanceof ServerLevel serverLevel) {
                            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                            if (lightning != null) {
                                lightning.moveTo(target.getX(), target.getY(), target.getZ());
                                lightning.setVisualOnly(true);
                                serverLevel.addFreshEntity(lightning);
                            }
                        }

                        // 计算伤害倍率（夜晚×3）
                        int magnification = isNightTime(world) ? 3 : 1;

                        // 计算伤害：箭基础伤害 × 等级 × 0.3 × 倍率
                        float baseDamage = (float) arrow.getBaseDamage();
                        float damage = baseDamage * effectiveLevel * 0.3f * magnification;

                        target.hurt(target.damageSources().lightningBolt(), damage);

                        // 如果目标在地面上，施加小幅随机击退
                        if (target.onGround()) {
                            double x = RandomUtils.nextBoolean() ? arrow.getX() - target.getX() : target.getX() - arrow.getX();
                            double z = RandomUtils.nextBoolean() ? arrow.getZ() - target.getZ() : target.getZ() - arrow.getZ();
                            target.knockback(0.2f, x, z);
                        }
                    }
                } else {
                    // 如果没有目标，在箭矢位置召唤闪电效果
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
