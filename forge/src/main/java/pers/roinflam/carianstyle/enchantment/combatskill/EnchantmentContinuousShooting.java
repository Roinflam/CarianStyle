package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 连射附魔
 * <p>
 * 拉弓速度×5，但箭矢伤害-50%
 * </p>
 * <p>
 * 性能优化记录：
 * - 在LivingTickEvent中增加弓/弩类物品快速检查，避免不相关物品触发NBT遍历
 * - 吃食物、喝药水、举盾等非弓类使用都会被快速过滤
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "continuous_shooting",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentContinuousShooting extends EnchantmentBase {

    /** 缓存附魔实例，避免每次事件触发都查询注册表 */
    private static volatile Enchantment cachedEnchantment = null;
    /** 标记是否已尝试过缓存 */
    private static volatile boolean cacheAttempted = false;

    public EnchantmentContinuousShooting() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 获取缓存的附魔实例
     *
     * @return 附魔实例，注册表未就绪时返回 null
     */
    private static Enchantment getCachedEnchantment() {
        if (!cacheAttempted) {
            cachedEnchantment = EnchantmentRegistry.getEnchantmentByClass(EnchantmentContinuousShooting.class);
            cacheAttempted = true;
        }
        return cachedEnchantment;
    }

    /**
     * 箭矢命中时伤害-50%
     *
     * @param evt 弹射物碰撞事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(@NotNull ProjectileImpactEvent evt) {
        // 仅处理原版AbstractArrow
        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        if (arrow.level().isClientSide) {
            return;
        }

        // 只处理实体命中
        HitResult hitResult = evt.getRayTraceResult();
        if (!(hitResult instanceof EntityHitResult)) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity shooter)) {
            return;
        }

        Enchantment continuousShooting = getCachedEnchantment();
        if (continuousShooting == null) {
            return;
        }

        ItemStack heldItem = shooter.getMainHandItem();
        if (heldItem.isEmpty() || !heldItem.isEnchanted()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(continuousShooting, heldItem);

        if (level > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() * 0.5);
        }
    }

    /**
     * 加速拉弓：每tick额外更新4次
     * <p>
     * 性能优化：增加弓/弩类物品快速检查
     * 原代码：任何正在使用物品的实体都会继续执行到附魔检查（吃食物、喝药水、举盾等）
     * 优化后：非弓/弩类物品立即返回，避免后续的附魔查询和NBT遍历
     * </p>
     *
     * @param evt 生物tick事件
     */
    @SubscribeEvent
    public static void onLivingUpdate(@NotNull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();

        // 快速退出：未在使用物品
        if (!entity.isUsingItem()) {
            return;
        }

        ItemStack heldItem = entity.getItemInHand(entity.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        // 性能优化：快速检查物品类型，非弓/弩直接退出
        // 这避免了吃食物、喝药水、举盾等场景触发附魔NBT遍历
        if (!(heldItem.getItem() instanceof BowItem) && !(heldItem.getItem() instanceof CrossbowItem)) {
            return;
        }

        // 快速检查：没附魔直接跳过
        if (!heldItem.isEnchanted()) {
            return;
        }

        Enchantment continuousShooting = getCachedEnchantment();
        if (continuousShooting == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(continuousShooting, heldItem);

        if (level > 0) {
            // 额外更新4次使用进度
            for (int i = 0; i < 4; i++) {
                EntityLivingUtil.updateHeld(entity);
            }
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.INFINITY_ARROWS) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
