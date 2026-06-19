package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.List;

/**
 * 硬箭附魔
 * <p>v2.2：ProjectileImpact射手+LivingKnockBack受击者入口接入怪物附魔触发开关。
 * onHurtAsVictimHighest 走中央事件分发器，已被 scanEntity 拦截。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "hard_arrow", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.BOW, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentHardArrow extends EnchantmentBase {
    public EnchantmentHardArrow() { super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(hardArrow, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        arrow.setBaseDamage(arrow.getBaseDamage() + arrow.getBaseDamage() * level * 0.8);
    }

    @Override
    protected void onHurtAsVictimHighest(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.getAttacker() == null) return;
        LivingEntity victim = ctx.getHolder();
        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, victim, 12, entity -> !entity.equals(victim));
        if (entities.isEmpty()) return;
        ctx.addDamage(ctx.getDamage() * level * 0.8f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingKnockBack(@NotNull LivingKnockBackEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，被击退强化）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment hardArrow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHardArrow.class);
        if (hardArrow == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(hardArrow, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, victim, 12, entity -> !entity.equals(victim));
        if (entities.isEmpty()) return;
        evt.setStrength(evt.getStrength() + evt.getStrength() * level * 0.75f);
    }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
    @Override protected boolean checkCompatibility(@NotNull Enchantment ench) { return super.checkCompatibility(ench) && !ench.equals(Enchantments.POWER_ARROWS); }
}
