package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 黑焰刃附魔
 *
 * 攻击时施加灭绝火焰燃烧效果
 * 持续伤害：伤害×等级×0.15/100 每tick，持续100tick
 */
@AutoRegisterEnchantment(
        id = "black_flame_blade",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        forceTreasure = true,
        conflictsWith = {
                EnchantmentInvisibleWeapon.class
        }
)
public class EnchantmentBlackFlameBlade extends EnchantmentBase {

    public EnchantmentBlackFlameBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 施加灭绝火焰燃烧效果
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DESTRUCTION_FIRE_BURNING, 5 * 20 + 5, 0));

        // 每tick伤害 = 原伤害×等级×0.15/100
        float damagePerTick = ctx.getDamage() * effectiveLevel * 0.15f / 100;

        // 持续伤害任务
        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > 100 || !victim.isEntityAlive()) {
                    this.cancel();
                    return;
                }

                if (victim.getHealth() - damagePerTick * 2 > 0) {
                    victim.setHealth(victim.getHealth() - damagePerTick);
                } else {
                    EntityLivingUtil.kill(victim, ctx.getDamageSource());
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}