package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 硬箭附魔
 * <p>
 * 弓箭附魔，高伤害但有代价
 * 正面效果：
 * - 箭矢伤害增加 80% × 等级
 * 负面效果（周围12格有敌人时）：
 * - 受到伤害增加 80% × 等级
 * - 受到击退增加 75% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "hard_arrow",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentHardArrow extends EnchantmentBase {

    public EnchantmentHardArrow() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) arrow.getOwner();

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(hardArrow, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        arrow.setBaseDamage(arrow.getBaseDamage() + arrow.getBaseDamage() * level * 0.8);
    }

    @Override
    protected void onHurtAsVictimHighest(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.getAttacker() == null) {
            return;
        }

        LivingEntity victim = ctx.getHolder();

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                12,
                entity -> !entity.equals(victim)
        );

        if (entities.isEmpty()) {
            return;
        }

        float bonusDamage = ctx.getDamage() * level * 0.8f;
        ctx.addDamage(bonusDamage);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(@NotNull LivingKnockBackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        ItemStack heldItem = victim.getItemInHand(victim.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(hardArrow, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                12,
                entity -> !entity.equals(victim)
        );

        if (entities.isEmpty()) {
            return;
        }

        evt.setStrength(evt.getStrength() + evt.getStrength() * level * 0.75f);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.POWER_ARROWS);
    }
}