package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 连射附魔
 *
 * 拉弓速度×5，但箭矢伤害-50%
 */
@AutoRegisterEnchantment(
        id = "continuous_shooting",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentContinuousShooting extends EnchantmentBase {

    public EnchantmentContinuousShooting() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时伤害-50%
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        if (arrow.shootingEntity == null || evt.getRayTraceResult().entityHit == null) {
            return;
        }

        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase shooter = (EntityLivingBase) arrow.shootingEntity;

        if (shooter.getHeldItem(shooter.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment continuousShooting = EnchantmentRegistry.getEnchantmentByClass(EnchantmentContinuousShooting.class);
        if (continuousShooting == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                continuousShooting,
                shooter.getHeldItem(shooter.getActiveHand()));

        if (level > 0) {
            arrow.setDamage(arrow.getDamage() * 0.5);
        }
    }

    /**
     * 加速拉弓：每tick额外更新4次
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entity = evt.getEntityLiving();

        if (!entity.isHandActive()) {
            return;
        }

        if (entity.getHeldItem(entity.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment continuousShooting = EnchantmentRegistry.getEnchantmentByClass(EnchantmentContinuousShooting.class);
        if (continuousShooting == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                continuousShooting,
                entity.getHeldItem(entity.getActiveHand()));

        if (level > 0) {
            // 额外更新4次使用进度
            for (int i = 0; i < 4; i++) {
                EntityLivingUtil.updateHeld(entity);
            }
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与无限冲突
        if (ench == Enchantments.INFINITY) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}