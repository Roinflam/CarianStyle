package pers.roinflam.carianstyle.enchantment.recollect;

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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 注定死亡附魔
 *
 * 攻击时施加诅咒效果，并造成持续递增伤害
 * 持续100tick，伤害随时间递增，足以致死时直接击杀
 */
@AutoRegisterEnchantment(
        id = "doomed_death",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentDoomedDeath extends EnchantmentBase {

    public EnchantmentDoomedDeath() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 玩家需要刚挥剑，非玩家直接触发
        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        // 施加诅咒效果
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH_BURNING, 5 * 20 + 5, 0));
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH, 10 * 20 + 5, 0));

        // 记录原伤害用于持续伤害计算
        float originalDamage = ctx.getDamage();

        // 持续伤害任务
        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > 100 || victim.isDead) {
                    this.cancel();
                    return;
                }

                // 基础伤害 = (原伤害×0.5 + 目标当前血量×0.1) / 100
                float baseDamage = (originalDamage * 0.5f + victim.getHealth() * 0.1f) / 100;

                // 实际伤害递增：基础×0.3 + 基础×tick/50×0.7
                float actualDamage = baseDamage * 0.3f + baseDamage * tick / 50 * 0.7f;

                // 判断是否致死
                if (victim.getHealth() - actualDamage * 2 > 0) {
                    victim.setHealth(victim.getHealth() - actualDamage);
                } else {
                    EntityLivingUtil.kill(victim, ctx.getDamageSource());
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}