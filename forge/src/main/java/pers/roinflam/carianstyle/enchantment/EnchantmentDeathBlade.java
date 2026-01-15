package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
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

@AutoRegisterEnchantment(
        id = "death_blade",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentDeathBlade extends EnchantmentBase {

    public EnchantmentDeathBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        // 防止重复触发和创造模式检查
        if (damageSource.damageType.equals("deathBlade") || damageSource.canHarmInCreative()) {
            return;
        }

        EntityLivingBase victim = ctx.getVictim();
        float damage = ctx.getDamage() * 0.75f / 100;

        // 减少初始伤害到50%
        ctx.multiplyDamage(0.5f);

        // 标记伤害来源
        damageSource.damageType = "deathBlade";

        // 施加药水效果
        ctx.addPotionToOpponent(CarianStylePotion.DOOMED_DEATH_BURNING, 5 * 20 + 5, 0);
        ctx.addPotionToOpponent(CarianStylePotion.DOOMED_DEATH, 10 * 20 + 5, 0);

        // 持续伤害效果
        new SynchronizationTask(1, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > 100 || !victim.isEntityAlive()) {
                    this.cancel();
                    return;
                }

                if (victim.getHealth() - damage * 2 > 0) {
                    victim.setHealth(victim.getHealth() - damage);
                } else {
                    EntityLivingUtil.kill(victim, damageSource);
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (50 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}