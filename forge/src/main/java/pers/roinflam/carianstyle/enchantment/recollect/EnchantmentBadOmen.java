package pers.roinflam.carianstyle.enchantment.recollect;

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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 凶兆附魔
 * <p>
 * 攻击时给敌人施加凶兆效果
 * 敌人已有凶兆时，额外造成50%伤害（直接扣血），可触发斩杀
 * </p>
 * <p>
 * v2.1：玩家必须满蓄力才追加真伤、挂凶兆；未满蓄力的一下只付「目标已有凶兆时本体伤害折半」的代价、拿不到好处，
 * 怪物持有者不受影响
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "bad_omen",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentBadOmen extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.bad_omen.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "bad_omen";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 追加伤害占本次伤害的比例
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "extra_damage_ratio",
                    0.5D, 0.0D, 5.0D);

    /**
     * 本体伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.5D, 0.0D, 5.0D);

    /**
     * 施加不祥预感的持续时间（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_DURATION =
            EnchantmentValues.define(VALUE_ID, "effect_duration",
                    200, 20, 6000);

    public EnchantmentBadOmen() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
        // 排除无视创造模式的伤害
        if (ctx.getDamageSource() != null && ctx.getDamageSource().isCreativePlayer()) {
            return;
        }

        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 连点会让每一下都挂上凶兆（受伤 +25%、治疗减半），要求满蓄力。
        // 未满蓄力的一下拿不到追加真伤和凶兆，但目标已有凶兆时「本体伤害折半」这个代价照样付，
        // 否则连点反而比满蓄力多吃一份本体伤害
        if (!isFullyCharged(ctx.getHolder())) {
            if (victim.hasEffect(CarianStylePotion.BAD_OMEN.get())) {
                ctx.multiplyDamage((float) DAMAGE_MULTIPLIER.get());
            }
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        // 如果敌人已有凶兆效果，额外造成50%伤害
        if (victim.hasEffect(CarianStylePotion.BAD_OMEN.get())) {
            float damage = ctx.getDamage();
            float extraDamage = damage * (float) EXTRA_DAMAGE_RATIO.get();

            if (extraDamage >= victim.getHealth()) {
                EntityLivingUtil.kill(victim, ctx.getDamageSource());
            } else {
                // 使用真伤系统
                EntityLivingUtil.damageHealthDirectly(victim, extraDamage);
                ctx.multiplyDamage((float) DAMAGE_MULTIPLIER.get());
            }
        }

        // 施加凶兆效果
        victim.addEffect(new MobEffectInstance(CarianStylePotion.BAD_OMEN.get(), EFFECT_DURATION.getInt(), 0));
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
