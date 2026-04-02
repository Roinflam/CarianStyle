package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;

/**
 * 注定死亡附魔
 * <p>
 * 攻击时施加诅咒效果，并造成持续递增伤害
 * 持续100tick，伤害随时间递增，足以致死时直接击杀
 * </p>
 * <p>
 * 性能优化 v3.0：使用 DamageOverTimeManager 替代 SynchronizationTask(5, 1)
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "doomed_death",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentDoomedDeath extends EnchantmentBase {

    /** 持续伤害总时长（tick） */
    private static final int DOT_DURATION = 100;
    /** 初始延迟（tick） */
    private static final int DOT_DELAY = 5;

    public EnchantmentDoomedDeath() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null || victim.level().isClientSide) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 玩家需要刚挥剑
        if (ctx.isHolderPlayer() && !isJustSwung(ctx.getHolderAsPlayer())) {
            return;
        }

        // 应用注定死亡燃烧效果（猩红色火焰视觉）
        DynamicAttributeManager.apply(
                victim,
                DynamicAttributes.DOOMED_DEATH_BURNING.createInstance(5 * 20 + 5, 0)
        );

        // 应用注定死亡效果（最大生命值-25%）
        DynamicAttributeManager.apply(
                victim,
                DynamicAttributes.DOOMED_DEATH.createInstance(10 * 20 + 5, 0)
        );

        // 递增伤害：baseDamage * 0.3 + baseDamage * elapsed / 50 * 0.7
        float originalDamage = ctx.getDamage();
        float baseDamagePerTick = (originalDamage * 0.5f + victim.getHealth() * 0.1f) / DOT_DURATION;

        DamageOverTimeManager.applyScaling(
                victim,
                baseDamagePerTick,
                DOT_DURATION,
                DOT_DELAY,
                ctx.getDamageSource(),
                true,
                (base, elapsed) -> base * 0.3f + base * elapsed / 50f * 0.7f
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
