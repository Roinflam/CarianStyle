package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 黑焰刃附魔
 * <p>
 * 攻击时施加灭绝火焰燃烧效果
 * 持续伤害：伤害×等级×0.15/100 每tick，持续100tick
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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

    public EnchantmentBlackFlameBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 使用动态属性系统施加灭绝火焰效果（21tick，会自动同步客户端渲染白色火焰）
        DynamicAttributeManager.apply(
                victim,
                DynamicAttributes.DESTRUCTION_FIRE_BURNING.createInstance( 5 * 20 + 5, 0)
        );

        // 每tick伤害 = 原伤害×等级×0.15/100
        float damagePerTick = ctx.getDamage() * effectiveLevel * 0.15f / 100;

        // 持续伤害任务
        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > 100 || !victim.isAlive()) {
                    this.cancel();
                    return;
                }

                if (victim.getHealth() - damagePerTick * 2 > 0) {
                    EntityLivingUtil.damageHealthDirectly(victim, damagePerTick);
                } else {
                    EntityLivingUtil.kill(victim, ctx.getDamageSource());
                    this.cancel();
                }
            }
        }.start();
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