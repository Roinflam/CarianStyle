package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
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

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 吸引半径上限（格），半径 = 等级 × 2 */
    private static final int MAX_ATTRACT_RADIUS = 12;
    /** 落雷半径上限（格），半径 = 等级 */
    private static final int MAX_LIGHTNING_RADIUS = 8;
    /** 吸引和落雷各自至多影响的目标数 */
    private static final int MAX_TARGETS = 20;
    /** 每级的吸引拉力强度 */
    private static final float ATTRACT_STRENGTH_PER_LEVEL = 0.35F;
    /** 每级的落雷伤害倍率（乘箭的基础伤害） */
    private static final double DAMAGE_PER_LEVEL = 0.3D;
    /** 夜晚的伤害倍率（白天为 1） */
    private static final double NIGHT_MULTIPLIER = 3.0D;

    public EnchantmentCallStar() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 按世界时钟判断夜晚：13000 ~ 23000
     * <p>以前用 {@code isDaytime()}，那是按天空亮度算的，下雨打雷时白天也会算成夜晚。</p>
     */
    private static boolean isNightTime(@Nonnull World world) {
        long dayTime = world.getWorldTime() % 24000L;
        return dayTime >= 13000L && dayTime < 23000L;
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
        // 射手不是生物（发射器等）时不触发，以前直接强转会崩
        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment callStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCallStar.class);
        if (callStar == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(callStar, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP);
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                arrow,
                Math.min(effectiveLevel * 2, MAX_ATTRACT_RADIUS),
                entity -> !entity.equals(attacker)
        );

        int attractHitCount = 0;
        for (EntityLivingBase entity : nearbyEntities) {
            if (attractHitCount >= MAX_TARGETS) {
                break;
            }
            double x = entity.posX - arrow.posX;
            double z = entity.posZ - arrow.posZ;
            float strength = (float) (effectiveLevel * ATTRACT_STRENGTH_PER_LEVEL * Math.max(Math.abs(x), Math.abs(z)) / 7);
            entity.knockBack(attacker, strength, x, z);
            attractHitCount++;
        }

        new SynchronizationTask(20) {
            @Override
            public void run() {
                List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        arrow,
                        Math.min(effectiveLevel, MAX_LIGHTNING_RADIUS),
                        entity -> !entity.equals(attacker)
                );

                if (!targets.isEmpty()) {
                    int hitCount = 0;
                    for (EntityLivingBase target : targets) {
                        if (hitCount >= MAX_TARGETS) {
                            break;
                        }
                        World world = target.world;

                        world.addWeatherEffect(new EntityLightningBolt(
                                world,
                                target.posX,
                                target.posY,
                                target.posZ,
                                true
                        ));

                        double magnification = isNightTime(world) ? NIGHT_MULTIPLIER : 1.0D;

                        float damage = (float) (arrow.getDamage() * effectiveLevel * DAMAGE_PER_LEVEL * magnification);
                        target.attackEntityFrom(DamageSource.LIGHTNING_BOLT, damage);

                        if (target.onGround) {
                            double x = RandomUtils.nextBoolean() ? arrow.posX - target.posX : target.posX - arrow.posX;
                            double z = RandomUtils.nextBoolean() ? arrow.posZ - target.posZ : target.posZ - arrow.posZ;
                            target.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) target.rotationYaw);
                            target.knockBack(attacker, 0.2f, x, z);
                        }
                        hitCount++;
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