package pers.roinflam.carianstyle.enchantment.combatskill;

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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 对空射击附魔
 * <p>
 * 射击比自己高至少5格的目标时触发
 * 额外造成 100% × 等级 的伤害
 * 额外造成目标当前生命值 × 10% 的伤害
 * 触发后自身获得减速II效果，持续5秒
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - getUsedItemHand() → InteractionHand.MAIN_HAND
 *   箭矢命中时玩家可能已经放开弓，不再处于"使用物品"状态
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
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

    /**
     * 高度差阈值（格数）
     */
    private static final double HEIGHT_THRESHOLD = 5.0;

    public EnchantmentSkyShot() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时触发：检查高度差并造成额外伤害
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // 必须是箭矢
        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        // 必须命中实体
        if (evt.getRayTraceResult().getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) evt.getRayTraceResult();

        // 必须有射手
        if (arrow.getOwner() == null) {
            return;
        }

        // 射手必须是生物
        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        // 被击中的必须是生物
        if (!(entityHit.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity shooter = (LivingEntity) arrow.getOwner();
        LivingEntity target = (LivingEntity) entityHit.getEntity();

        // v2.1修复：使用主手而非 getUsedItemHand()
        // 箭矢命中时弓可能已放下
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

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 检查高度差：目标必须比射手高至少5格
        double heightDifference = target.getY() - shooter.getY();
        if (heightDifference < HEIGHT_THRESHOLD) {
            return;
        }

        // 计算原始箭矢伤害
        double baseDamage = arrow.getBaseDamage();

        // 额外伤害1：100% × 等级
        double bonusDamage1 = baseDamage * effectiveLevel;

        // 额外伤害2：目标当前生命值 × 10%
        double bonusDamage2 = target.getHealth() * 0.1;

        // 设置新的箭矢伤害
        arrow.setBaseDamage(baseDamage + bonusDamage1 + bonusDamage2);

        // 给射手施加减速II效果，持续5秒（100 tick）
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
