package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * 对空射击附魔
 * <p>v2.2：射手视角接入怪物附魔触发开关</p>
 * <p>
 * v2.3：接入对空射击特效（自更高处竖直贯下的箭光 + <b>目标高度处</b>的空爆环）。
 * 特效<b>只在高度差判定通过之后</b>才播——普通命中不该有这个演出。
 * </p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "sky_shot",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentSkyShot extends EnchantmentBase {

    private static final double HEIGHT_THRESHOLD = 5.0;

    public EnchantmentSkyShot() {
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

        if (evt.getRayTraceResult().getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) evt.getRayTraceResult();

        if (arrow.getOwner() == null) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        if (!(entityHit.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity shooter = (LivingEntity) arrow.getOwner();

        // ⭐ v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(shooter, false)) return;

        LivingEntity target = (LivingEntity) entityHit.getEntity();

        ItemStack heldItem = shooter.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment skyShot = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSkyShot.class);
        if (skyShot == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(skyShot, heldItem);
        if (level <= 0) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        double heightDifference = target.getY() - shooter.getY();
        if (heightDifference < HEIGHT_THRESHOLD) {
            return;
        }

        double baseDamage = arrow.getBaseDamage();
        double bonusDamage1 = baseDamage * effectiveLevel;
        double bonusDamage2 = target.getHealth() * 0.1;

        arrow.setBaseDamage(baseDamage + bonusDamage1 + bonusDamage2);

        // ⭐ v2.3：对空射击特效。
        // 必须放在高度差判定之后 —— 这个演出的全部语义就是「在空中把它打下来」。
        // 传入的是 target 本身（它此刻在空中），空爆环会画在它所处的高度而非地面
        if (shooter.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.skyShot(serverLevel, shooter, target);
        }

        shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
