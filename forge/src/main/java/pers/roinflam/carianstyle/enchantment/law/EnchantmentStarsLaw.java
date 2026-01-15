package pers.roinflam.carianstyle.enchantment.law;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
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

import javax.annotation.Nonnull;

/**
 * 星律附魔
 *
 * 夜晚效果：
 * - 攻击时给敌人叠加冻伤，敌人有冻伤时魔法伤害增加
 * - 治疗量+50%
 * - 持续获得速度提升
 */
@AutoRegisterEnchantment(
        id = "stars_law",
        category = EnchantmentCategory.LAW,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
public class EnchantmentStarsLaw extends EnchantmentBase {

    // 追忆类附魔通用的附魔难度
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentStarsLaw() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        if (victim.isPotionActive(CarianStylePotion.FROSTBITE)) {
            if (ctx.isMagicDamage()) {
                int frostbiteLevel = victim.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier();
                float bonusDamage = ctx.getDamage() * (frostbiteLevel + 1) * 0.075f;
                ctx.addDamage(bonusDamage);
            }
            int newLevel = Math.min(victim.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier() + 1, 9);
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE, 200, newLevel);
        } else {
            ctx.addPotionToOpponent(CarianStylePotion.FROSTBITE, 200, 0);
        }
    }

    @Override
    protected void onHeal(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        float currentHeal = ctx.getHealAmount();
        ctx.setHealAmount(currentHeal + currentHeal * 0.5f);
    }

    @Override
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isDaytime()) {
            return;
        }

        ctx.addPotionToHolder(CarianStylePotion.SPEED_BOOST, 2, 25);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        // 与其他追忆类附魔冲突（暗弃子除外）
        if (isRecollectEnchantment(ench) &&
                !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class))) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    /**
     * 判断是否是追忆类附魔
     */
    private boolean isRecollectEnchantment(Enchantment ench) {
        // 这里需要根据实际的追忆类附魔列表来判断
        // 简化处理：通过包名或注解来判断
        return ench.getClass().getPackage().getName().contains("recollect")
                || ench.getClass().getPackage().getName().contains("law");
    }
}