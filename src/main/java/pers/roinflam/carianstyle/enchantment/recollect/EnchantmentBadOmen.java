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
/**
 * 噩兆附魔
 * <p>
 * 满蓄力攻击时给目标施加 10 秒噩兆；目标已有噩兆时，额外直接扣除本次伤害 50% 的生命
 * （足以致命时直接击杀），本体伤害 -50%。未满蓄力攻击噩兆目标时只有本体伤害 -50%。
 * </p>
 * <p>
 * 以前没有蓄力要求，连点每下都挂噩兆、都追加扣血；致死时还会改动原伤害源的标志。
 * 未满蓄力时代价照付、收益不给，否则连点反而比满蓄力多吃一份本体伤害。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "bad_omen",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentBadOmen extends EnchantmentBase {

    /** 追加扣血占本次伤害的比例 */
    private static final float EXTRA_DAMAGE_RATIO = 0.5F;
    /** 目标已有噩兆时本体伤害倍率 */
    private static final float DAMAGE_MULTIPLIER = 0.5F;
    /** 噩兆持续时间（tick） */
    private static final int EFFECT_DURATION = 200;

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

        if (!isFullyCharged(ctx.getHolder())) {
            if (victim.isPotionActive(CarianStylePotion.BAD_OMEN)) {
                ctx.multiplyDamage(DAMAGE_MULTIPLIER);
            }
            return;
        }

        if (victim.isPotionActive(CarianStylePotion.BAD_OMEN)) {
            float extraDamage = ctx.getDamage() * EXTRA_DAMAGE_RATIO;

            if (extraDamage >= victim.getHealth()) {
                // 足以致死，直接击杀
                EntityLivingUtil.kill(victim, ctx.getDamageSource());
            } else {
                // 直接扣血（无视护甲）
                EntityLivingUtil.damageHealthDirectly(victim, extraDamage);
                ctx.multiplyDamage(DAMAGE_MULTIPLIER);
            }
        }

        victim.addPotionEffect(new PotionEffect(CarianStylePotion.BAD_OMEN, EFFECT_DURATION, 0));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}