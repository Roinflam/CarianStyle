package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 硬箭附魔
 *
 * 弓箭附魔，高伤害但有代价
 * 正面效果：
 * - 箭矢伤害增加 80% × 等级
 * 负面效果（周围12格有敌人时）：
 * - 受到伤害增加 80% × 等级
 * - 受到击退增加 75% × 等级
 */
@AutoRegisterEnchantment(
        id = "hard_arrow",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentHardArrow extends EnchantmentBase {

    public EnchantmentHardArrow() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时增加伤害
     * 由于 ProjectileImpactEvent.Arrow 没有模板方法，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;
        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                hardArrow,
                attacker.getHeldItem(attacker.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 箭矢伤害增加 80% × 等级
        arrow.setDamage(arrow.getDamage() + arrow.getDamage() * level * 0.8);
    }

    /**
     * 受击时：周围有敌人则受到更多伤害（代价）
     */
    @Override
    protected void onHurtAsVictimHighest(@Nonnull EnchantmentContext ctx, int level) {
        // 必须有攻击来源
        if (ctx.getAttacker() == null) {
            return;
        }

        EntityLivingBase victim = ctx.getHolder();

        // 检查周围12格是否有其他实体
        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                12,
                entity -> !entity.equals(victim)
        );

        if (entities.isEmpty()) {
            return;
        }

        // 受到伤害增加 80% × 等级
        float bonusDamage = ctx.getDamage() * level * 0.8f;
        ctx.addDamage(bonusDamage);
    }

    /**
     * 受击时：周围有敌人则受到更多击退（代价）
     * 由于 LivingKnockBackEvent 没有模板方法，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(@Nonnull LivingKnockBackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                hardArrow,
                victim.getHeldItem(victim.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 检查周围12格是否有其他实体
        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                12,
                entity -> !entity.equals(victim)
        );

        if (entities.isEmpty()) {
            return;
        }

        // 击退增加 75% × 等级
        evt.setStrength(evt.getStrength() + evt.getStrength() * level * 0.75f);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.POWER);
    }
}