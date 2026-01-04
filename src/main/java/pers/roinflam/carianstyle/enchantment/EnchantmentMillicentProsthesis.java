package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 米莉森义肢附魔
 *
 * 武器附魔，连续攻击增强
 * 攻击时：
 * - 叠加攻击增益效果（最多叠到等级×7层）
 * - 满层后刷新为等级×16层，持续60tick
 * 伤害加成：
 * - 满层时增伤10%×等级
 * - 未满层时增伤5%×等级
 */
@AutoRegisterEnchantment(
        id = "millicent_prosthesis",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentMillicentProsthesis extends EnchantmentBase {

    public EnchantmentMillicentProsthesis() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时叠加攻击增益效果
     * 注意：原代码没有等级上限检查
     */
    @Override
    protected void onAttackLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        // 玩家必须是刚挥动武器
        if (attacker instanceof EntityPlayer) {
            if (!isJustSwung((EntityPlayer) attacker)) {
                return;
            }
        }

        @Nullable PotionEffect currentEffect = attacker.getActivePotionEffect(CarianStylePotion.ATTACK_BOOST);

        if (currentEffect == null) {
            // 没有效果，初始化
            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.ATTACK_BOOST, 30, level - 1));
        } else if (currentEffect.getAmplifier() < level * 7 - 1) {
            // 未满层，叠加
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.ATTACK_BOOST,
                    30,
                    currentEffect.getAmplifier() + level
            ));
        } else {
            // 满层，刷新为更高层
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.ATTACK_BOOST,
                    60,
                    level * 16 - 1
            ));
        }
    }

    /**
     * 根据攻击增益层数增加伤害
     * 注意：原代码没有等级上限检查
     */
    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        @Nullable PotionEffect currentEffect = attacker.getActivePotionEffect(CarianStylePotion.ATTACK_BOOST);

        if (currentEffect != null && currentEffect.getAmplifier() >= level * 7 - 1) {
            // 满层：增伤10%×等级
            ctx.addDamage(ctx.getDamage() * level * 0.1f);
        } else {
            // 未满层：增伤5%×等级
            ctx.addDamage(ctx.getDamage() * level * 0.05f);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScarletCorruption.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentFireGivesPower.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentFireDevoured.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentVicDragonThunder.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentRedFeatheredBranchsword.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlueFeatheredBranchsword.class));
    }
}