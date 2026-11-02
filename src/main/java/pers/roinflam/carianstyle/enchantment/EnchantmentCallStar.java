package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.RandomUtils;
import javax.annotation.Nonnull;
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
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentLorettaBigBow.class,
                EnchantmentLorettaTrick.class
        }
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentCallStar extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.call_star.desc，
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
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    private static boolean isNightTime(@Nonnull World level) {
        long dayTime = level.getWorldTime() % 24000;
        return dayTime >= 13000 && dayTime < 23000;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        EntityArrow arrow = evt.getArrow();
        if (arrow.world.isRemote) {
            return;
        }

        // 1.12.2 版：箭的主人是 shootingEntity 字段；射中实体时 typeOfHit 为 ENTITY
        if (arrow.shootingEntity == null || evt.getRayTraceResult().typeOfHit == RayTraceResult.Type.ENTITY) {
            return;
        }

        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        // v2.3：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment callStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCallStar.class);
        if (callStar == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(callStar, heldItem);

        level = applyLevelLimit(callStar, level, LEVEL_CAP.getInt());

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        int attractRadius = Math.min(effectiveLevel * 2, MAX_ATTRACT_RADIUS.getInt());

        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                arrow,
                attractRadius,
                entity -> !entity.equals(attacker)
        );

        int attractHitCount = 0;
        for (EntityLivingBase entity : nearbyEntities) {
            if (attractHitCount >= MAX_TARGETS.getInt()) {
                break;
            }
            double x = entity.posX - arrow.posX;
            double z = entity.posZ - arrow.posZ;
            float strength = (float) (effectiveLevel * (float) ATTRACT_STRENGTH_PER_LEVEL.get()
                * Math.max(Math.abs(x), Math.abs(z)) / 7);
            entity.knockBack(attacker, strength, x, z);
            attractHitCount++;
        }

        new SynchronizationTask(20) {
            @Override
            public void run() {
                int lightningRadius = Math.min(effectiveLevel, MAX_LIGHTNING_RADIUS.getInt());

                List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        arrow,
                        lightningRadius,
                        entity -> !entity.equals(attacker)
                );

                if (!targets.isEmpty()) {
                    int hitCount = 0;
                    for (EntityLivingBase target : targets) {
                        if (hitCount >= MAX_TARGETS.getInt()) {
                            break;
                        }

                        World world = target.world;

                        // 只有画面和声音的闪电（effectOnly），伤害在下面单独算
                        world.addWeatherEffect(new EntityLightningBolt(world, target.posX, target.posY, target.posZ, true));

                        double magnification = isNightTime(world) ? NIGHT_MULTIPLIER.get() : 1.0D;

                        float baseDamage = (float) arrow.getDamage();
                        float damage = (float) (baseDamage * effectiveLevel * DAMAGE_PER_LEVEL.get() * magnification);

                        target.attackEntityFrom(DamageSource.LIGHTNING_BOLT, damage);

                        if (target.onGround) {
                            double x = RandomUtils.nextBoolean() ? arrow.posX - target.posX : target.posX - arrow.posX;
                            double z = RandomUtils.nextBoolean() ? arrow.posZ - target.posZ : target.posZ - arrow.posZ;
                            target.knockBack(attacker, 0.2f, x, z);
                        }
                        hitCount++;
                    }
                } else {
                    World world = arrow.world;
                    world.addWeatherEffect(new EntityLightningBolt(world, arrow.posX, arrow.posY, arrow.posZ, true));
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
