package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * 黑焰刃附魔
 * <p>
 * 攻击时施加灭绝火焰燃烧效果
 * 持续伤害：伤害×等级×0.15/100 每tick，持续100tick
 * </p>
 * <p>
 * 性能优化 v3.0：使用 DamageOverTimeManager 替代 SynchronizationTask(5, 1)
 * 黑焰刃是RARE级非宝藏附魔，使用频率最高的DoT附魔之一，优化效果显著
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "black_flame_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {
                EnchantmentInvisibleWeapon.class
        }
)
public class EnchantmentBlackFlameBlade extends EnchantmentBase {

    /** 持续伤害总时长（tick） */
    private static final int DOT_DURATION = 100;
    /** 初始延迟（tick） */
    private static final int DOT_DELAY = 5;

    public EnchantmentBlackFlameBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 施加灭绝火焰效果（白色火焰视觉）
        DynamicAttributeManager.apply(
                victim,
                DynamicAttributes.DESTRUCTION_FIRE_BURNING.createInstance(5 * 20 + 5, 0)
        );

        // 每tick伤害 = 原伤害×等级×0.15/100
        float damagePerTick = ctx.getDamage() * effectiveLevel * 0.15f / DOT_DURATION;

        DamageOverTimeManager.applyLinear(
                victim,
                damagePerTick,
                DOT_DURATION,
                DOT_DELAY,
                ctx.getDamageSource(),
                true
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
