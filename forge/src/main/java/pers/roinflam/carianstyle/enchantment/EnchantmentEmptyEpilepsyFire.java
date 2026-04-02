package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * 空癫火附魔
 * <p>
 * 弓箭附魔，双刃剑效果
 * 箭矢命中敌人时：
 * - 对敌人造成持续3秒的癫火伤害（攻击者最大生命值 × 10% × 等级 × 20% / 60tick）
 * - 对自己造成持续3秒的癫火伤害（自己最大生命值 × 10% / 60tick）
 * - 创造模式玩家免疫自损
 * </p>
 * <p>
 * 性能优化 v3.0：两个SynchronizationTask(5, 1)全部改用 DamageOverTimeManager
 * 修复保留 v2.1：getUsedItemHand() → InteractionHand.MAIN_HAND
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "empty_epilepsy_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentEmptyEpilepsyFire extends EnchantmentBase {

    /** 燃烧视觉效果持续时间（tick） */
    private static final int BURN_VISUAL_DURATION = 65;
    /** 持续伤害时长（tick） */
    private static final int DAMAGE_TICKS = 60;
    /** 初始延迟（tick） */
    private static final int DOT_DELAY = 5;

    public EnchantmentEmptyEpilepsyFire() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        if (arrow.getOwner() == null) {
            return;
        }

        if (evt.getRayTraceResult().getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        if (!(((net.minecraft.world.phys.EntityHitResult) evt.getRayTraceResult()).getEntity() instanceof LivingEntity)) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) arrow.getOwner();
        LivingEntity victim = (LivingEntity) ((net.minecraft.world.phys.EntityHitResult) evt.getRayTraceResult()).getEntity();

        Enchantment emptyEpilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class);
        if (emptyEpilepsyFire == null) {
            return;
        }

        // v2.1修复保留：使用主手
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(emptyEpilepsyFire, heldItem);
        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }
        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        // ========== 攻击者自损 ==========
        DynamicAttributeManager.apply(attacker,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION, 0));
        ClientSyncEffectHelper.onAttributeApplied(attacker, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        // 自损：最大生命值 × 10% / 60tick
        float attackerDmgPerTick = attacker.getMaxHealth() * 0.1f / DAMAGE_TICKS;
        DamageOverTimeManager.applyLinear(
                attacker, attackerDmgPerTick, DAMAGE_TICKS, DOT_DELAY,
                NewDamageSource.epilepsyFire(attacker.level()), true
        );

        // ========== 受击者伤害 ==========
        DynamicAttributeManager.apply(victim,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION, 0));
        ClientSyncEffectHelper.onAttributeApplied(victim, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        // 受害者：攻击者最大生命值 × 10% × 等级 × 20% / 60tick
        float victimDmgPerTick = attacker.getMaxHealth() * 0.1f * effectiveLevel * 0.2f / DAMAGE_TICKS;
        DamageOverTimeManager.applyLinear(
                victim, victimDmgPerTick, DAMAGE_TICKS, DOT_DELAY,
                NewDamageSource.epilepsyFire(victim.level()), true
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
