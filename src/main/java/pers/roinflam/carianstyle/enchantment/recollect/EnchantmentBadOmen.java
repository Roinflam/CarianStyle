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
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 凶兆附魔
 *
 * 攻击时给敌人施加凶兆效果
 * 敌人已有凶兆时，额外造成50%伤害（直接扣血），可触发斩杀
 */
@AutoRegisterEnchantment(
        id = "bad_omen",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentBadOmen extends EnchantmentBase {

    public EnchantmentBadOmen() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        // 排除无视创造模式的伤害
        if (ctx.getDamageSource() != null && ctx.getDamageSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 如果敌人已有凶兆效果，额外造成50%伤害
        if (victim.isPotionActive(CarianStylePotion.BAD_OMEN)) {
            float damage = ctx.getDamage();
            float extraDamage = damage * 0.5f;

            if (extraDamage >= victim.getHealth()) {
                // 足以致死，直接击杀
                EntityLivingUtil.kill(victim, ctx.getDamageSource().setDamageAllowedInCreativeMode());
            } else {
                // 直接扣血（无视护甲）
                victim.setHealth(victim.getHealth() - extraDamage);
                ctx.multiplyDamage(0.5f);
            }
        }

        // 施加凶兆效果
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.BAD_OMEN, 200, 0));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}