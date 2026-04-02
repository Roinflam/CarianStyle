package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * 死亡之刃附魔
 * <p>
 * 武器附魔
 * 初始伤害降为50%，但施加死亡烙印
 * 持续5秒造成累计伤害（总伤害=原伤害×75%）
 * </p>
 * <p>
 * 性能优化 v3.0：使用 DamageOverTimeManager 替代 SynchronizationTask(1, 1)
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "death_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentDeathBlade extends EnchantmentBase {

    /** 持续伤害总时长（tick） */
    private static final int DOT_DURATION = 100;
    /** 初始延迟（tick） */
    private static final int DOT_DELAY = 1;

    public EnchantmentDeathBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        if (damageSource == null || "deathBlade".equals(damageSource.getMsgId()) ||
                damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (ctx.getVictim() == null) {
            return;
        }

        // 计算每tick伤害
        float damagePerTick = ctx.getDamage() * 0.75f / DOT_DURATION;

        // 降低即时伤害为50%
        ctx.multiplyDamage(0.5f);

        // 应用火焰燃烧效果（同步客户端渲染）
        DynamicAttributeManager.apply(ctx.getVictim(),
                DynamicAttributes.DOOMED_DEATH_BURNING.createInstance(5 * 20 + 5, 0));
        ClientSyncEffectHelper.onAttributeApplied(ctx.getVictim(), DynamicAttributes.DOOMED_DEATH_BURNING);

        // 应用注定死亡效果
        DynamicAttributeManager.apply(ctx.getVictim(),
                DynamicAttributes.DOOMED_DEATH.createInstance(10 * 20 + 5, 0));

        // 持续伤害
        DamageOverTimeManager.applyLinear(
                ctx.getVictim(),
                damagePerTick,
                DOT_DURATION,
                DOT_DELAY,
                damageSource,
                true
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (50 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
