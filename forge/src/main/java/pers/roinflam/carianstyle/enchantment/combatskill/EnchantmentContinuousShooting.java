// 文件：EnchantmentContinuousShooting.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentContinuousShooting.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 连射附魔
 * <p>
 * 拉弓速度×5，但箭矢伤害-50%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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

    public EnchantmentContinuousShooting() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时伤害-50%
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        // 1.20.1: HitResult需要先检查类型
        HitResult hitResult = evt.getRayTraceResult();
        if (!(hitResult instanceof EntityHitResult entityHit)) {
            return;
        }

        // 1.20.1: shootingEntity → getOwner()
        if (arrow.getOwner() == null || entityHit.getEntity() == null) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        LivingEntity shooter = (LivingEntity) arrow.getOwner();

        ItemStack heldItem = shooter.getItemInHand(shooter.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment continuousShooting = EnchantmentRegistry.getEnchantmentByClass(EnchantmentContinuousShooting.class);
        if (continuousShooting == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(continuousShooting, heldItem);

        if (level > 0) {
            // 1.20.1: setDamage → setBaseDamage
            arrow.setBaseDamage(arrow.getBaseDamage() * 0.5);
        }
    }

    /**
     * 加速拉弓：每tick额外更新4次
     */
    @SubscribeEvent
    public static void onLivingUpdate(@NotNull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();

        if (!entity.isUsingItem()) {
            return;
        }

        ItemStack heldItem = entity.getItemInHand(entity.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment continuousShooting = EnchantmentRegistry.getEnchantmentByClass(EnchantmentContinuousShooting.class);
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
        // 与无限冲突
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