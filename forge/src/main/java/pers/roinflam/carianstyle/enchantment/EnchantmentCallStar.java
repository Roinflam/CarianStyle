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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
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

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.call_star.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "call_star";

    /**
     * 吸引效果的搜索半径上限（格）
     * <p>默认 12，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_ATTRACT_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_attract_radius",
                    12, 1, 64);

    /**
     * 落雷效果的搜索半径上限（格）
     * <p>默认 8，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_LIGHTNING_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_lightning_radius",
                    8, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的吸引拉力强度
     * <p>默认 0.35，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle ATTRACT_STRENGTH_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "attract_strength_per_level",
                    0.35D, 0.0D, 5.0D);

    /**
     * 每级的落雷伤害倍率
     * <p>默认 0.3，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.3D, 0.0D, 5.0D);

    /**
     * 夜间的伤害倍率（白天恒为 1）
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle NIGHT_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "night_multiplier",
                    3.0D, 0.0D, 20.0D);

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
            level = Math.min(level, LEVEL_CAP.getInt());
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        int attractRadius = Math.min(effectiveLevel * 2, MAX_ATTRACT_RADIUS.getInt());

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                arrow,
                attractRadius,
                entity -> !entity.equals(attacker)
        );

        int attractHitCount = 0;
        for (LivingEntity entity : nearbyEntities) {
            if (attractHitCount >= MAX_TARGETS.getInt()) {
                break;
            }
            double x = entity.getX() - arrow.getX();
            double z = entity.getZ() - arrow.getZ();
            float strength = (float) (effectiveLevel * (float) ATTRACT_STRENGTH_PER_LEVEL.get()
                * Math.max(Math.abs(x), Math.abs(z)) / 7);
            entity.knockback(strength, x, z);
            attractHitCount++;
        }

        new SynchronizationTask(20) {
            @Override
            public void run() {
                int lightningRadius = Math.min(effectiveLevel, MAX_LIGHTNING_RADIUS.getInt());

                List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        arrow,
                        lightningRadius,
                        entity -> !entity.equals(attacker)
                );

                if (!targets.isEmpty()) {
                    int hitCount = 0;
                    for (LivingEntity target : targets) {
                        if (hitCount >= MAX_TARGETS.getInt()) {
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

                        double magnification = isNightTime(world) ? NIGHT_MULTIPLIER.get() : 1.0D;

                        float baseDamage = (float) arrow.getBaseDamage();
                        float damage = (float) (baseDamage * effectiveLevel * DAMAGE_PER_LEVEL.get() * magnification);

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
