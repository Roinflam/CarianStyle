package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.entity.ArrowDamage;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * 连射附魔
 * <p>v2.2：ProjectileImpact射手+LivingTickEvent入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "continuous_shooting",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.BOW,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@EventBusSubscriber
public class EnchantmentContinuousShooting extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.continuous_shooting.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "continuous_shooting";

    /**
     * 连射箭矢的伤害倍率
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    0.5D, 0.0D, 5.0D);

    /**
     * 每次触发额外射出的箭矢数
     * <p>默认 4，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_ARROWS =
            EnchantmentValues.define(VALUE_ID, "extra_arrows",
                    4, 1, 32);

    private static volatile EnchantmentBase cachedEnchantment = null;
    private static volatile boolean cacheAttempted = false;

    public EnchantmentContinuousShooting() {
        super(EnchantTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    private static EnchantmentBase getCachedEnchantment() {
        if (!cacheAttempted) {
            cachedEnchantment = EnchantmentRegistry.getEnchantmentByClass(EnchantmentContinuousShooting.class);
            cacheAttempted = true;
        }
        return cachedEnchantment;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(@NotNull ProjectileImpactEvent evt) {
        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        if (arrow.level().isClientSide) {
            return;
        }

        HitResult hitResult = evt.getRayTraceResult();
        if (!(hitResult instanceof EntityHitResult entityHit)) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity shooter)) {
            return;
        }

        // v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(shooter, false)) return;

        EnchantmentBase continuousShooting = getCachedEnchantment();
        if (continuousShooting == null) {
            return;
        }

        ItemStack heldItem = shooter.getMainHandItem();
        if (heldItem.isEmpty() || !heldItem.isEnchanted()) {
            return;
        }

        int level = EnchantmentLevels.get(continuousShooting, heldItem);

        if (level > 0) {
            // 1.21.1 的力量在命中时才加，经 ArrowDamage 换算成「对含力量的箭伤乘倍率」，和 1.20.1 一致
            ArrowDamage.scale(arrow, entityHit.getEntity(), base -> base * ARROW_DAMAGE_MULTIPLIER.get());
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(@NotNull LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();

        if (!entity.isUsingItem()) {
            return;
        }

        // v2.2：怪物附魔触发开关（持有者视角，加速拉弓）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        ItemStack heldItem = entity.getItemInHand(entity.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        if (!(heldItem.getItem() instanceof BowItem) && !(heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }

        if (!heldItem.isEnchanted()) {
            return;
        }

        EnchantmentBase continuousShooting = getCachedEnchantment();
        if (continuousShooting == null) {
            return;
        }

        int level = EnchantmentLevels.get(continuousShooting, heldItem);

        if (level > 0) {
            for (int i = 0; i < EXTRA_ARROWS.getInt(); i++) {
                EntityLivingUtil.updateHeld(entity);
            }
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        if (ench.is(Enchantments.INFINITY)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

}
