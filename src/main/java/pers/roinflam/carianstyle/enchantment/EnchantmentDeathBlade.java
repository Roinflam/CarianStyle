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
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.util.BossUtil;

import javax.annotation.Nonnull;

@AutoRegisterEnchantment(
        id = "death_blade",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentDeathBlade extends EnchantmentBase {

    /** 燃烧时长（tick） */
    private static final int DOT_DURATION = 100;
    /** 起始延迟（tick） */
    private static final int DOT_DELAY = 1;
    /** 转为燃烧的比例（按折半之前的本次伤害） */
    private static final float DOT_RATIO = 0.5F;
    /** 即时伤害倍率 */
    private static final float DIRECT_MULTIPLIER = 0.5F;
    /** 对 BOSS 的燃烧倍率 */
    private static final float BOSS_MULTIPLIER = 0.5F;
    /** 燃烧火焰显示秒数 */
    private static final int BURNING_SECONDS = 5;
    /** 最大生命降低持续秒数 */
    private static final int DOOM_SECONDS = 45;

    public EnchantmentDeathBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 即时伤害减半，被减掉的一半在 5 秒内烧完，同时 45 秒内最大生命 -10%；对 BOSS 燃烧减半
     * <p>
     * 本次伤害取护甲和抗性减免之前的值，燃烧剩不到两跳时直接处决，抗性提升 V 挡不住。
     * 最大生命降低和这一下打多少无关，连点能飞快给一群目标挂上，所以要求满蓄力；
     * 未满蓄力的一下拿不到燃烧和减益，但即时伤害折半照样付，否则连点反而比满蓄力划算。
     * </p>
     */
    @Override
    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();
        EntityLivingBase victim = ctx.getVictim();

        if (damageSource == null || victim == null || damageSource.canHarmInCreative()) {
            return;
        }

        if (!isFullyCharged(ctx.getHolder())) {
            ctx.multiplyDamage(DIRECT_MULTIPLIER);
            return;
        }

        float multiplier = BossUtil.isBoss(victim) ? BOSS_MULTIPLIER : 1.0F;
        float damagePerTick = ctx.getDamage() * DOT_RATIO * multiplier / DOT_DURATION;

        ctx.multiplyDamage(DIRECT_MULTIPLIER);

        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH_BURNING, BURNING_SECONDS * 20 + 5, 0));
        victim.addPotionEffect(new PotionEffect(CarianStylePotion.DOOMED_DEATH, DOOM_SECONDS * 20 + 5, 0));

        DamageOverTimeManager.applyLinear(victim, damagePerTick, DOT_DURATION, DOT_DELAY, damageSource, true);
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