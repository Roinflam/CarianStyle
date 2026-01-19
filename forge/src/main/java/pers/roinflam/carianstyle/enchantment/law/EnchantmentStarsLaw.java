package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDarkAbandonedChild;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 星律附魔
 * <p>
 * 夜晚效果：
 * - 攻击时给敌人叠加冻伤，敌人有冻伤时魔法伤害增加
 * - 治疗量+50%
 * - 持续获得速度提升
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "stars_law",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.LAW,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
public class EnchantmentStarsLaw extends EnchantmentBase {

    /**
     * 追忆类附魔通用的附魔难度
     */
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentStarsLaw() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        if (victim.hasEffect(CarianStylePotion.FROSTBITE.get())) {
            if (ctx.isMagicDamage()) {
                int frostbiteLevel = victim.getEffect(CarianStylePotion.FROSTBITE.get()).getAmplifier();
                float bonusDamage = ctx.getDamage() * (frostbiteLevel + 1) * 0.075f;
                ctx.addDamage(bonusDamage);
            }
            int newLevel = Math.min(victim.getEffect(CarianStylePotion.FROSTBITE.get()).getAmplifier() + 1, 9);
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE.get(), 200, newLevel);
        } else {
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE.get(), 200, 0);
        }
    }

    @Override
    protected void onHeal(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        float currentHeal = ctx.getHealAmount();
        ctx.setHealAmount(currentHeal + currentHeal * 0.5f);
    }

    @Override
    protected void onPlayerTick(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        ctx.addPotionToHolder(CarianStylePotion.SPEED_BOOST.get(), 2, 25);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        // 与其他追忆类附魔冲突（暗弃子除外）
        if (isRecollectEnchantment(ench) && !isDarkAbandonedChild(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    /**
     * 判断是否是追忆类附魔
     *
     * @param ench 要检查的附魔
     * @return 是否为追忆类附魔
     */
    private boolean isRecollectEnchantment(Enchantment ench) {
        // 通过包名判断是否为追忆类或律法类附魔
        return ench.getClass().getPackage().getName().contains("recollect")
                || ench.getClass().getPackage().getName().contains("law");
    }

    /**
     * 判断是否是暗弃子附魔
     *
     * @param ench 要检查的附魔
     * @return 是否为暗弃子附魔
     */
    private boolean isDarkAbandonedChild(Enchantment ench) {
        return ench instanceof EnchantmentDarkAbandonedChild;
    }
}