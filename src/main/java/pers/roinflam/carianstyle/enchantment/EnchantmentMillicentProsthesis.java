package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
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
 * 伤害加成（只在增幅期间）：
 * - 满层时增伤10%×等级
 * - 未满层时增伤5%×等级
 * <p>
 * 以前身上没有增幅时也照加 5%×等级，等于只要拿着就永久增伤。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "millicent_prosthesis",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentMillicentProsthesis extends EnchantmentBase {

    /** 满层时每级增伤比例 */
    private static final float DAMAGE_PER_LEVEL_FULL = 0.1F;
    /** 增幅未满层时每级增伤比例 */
    private static final float DAMAGE_PER_LEVEL_PARTIAL = 0.05F;

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

        // 满蓄力挥击才叠层
        if (!isFullyCharged(attacker)) {
            return;
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
     * 根据攻击增益层数增加伤害，没有增幅时不加
     */
    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        @Nullable PotionEffect currentEffect = attacker.getActivePotionEffect(CarianStylePotion.ATTACK_BOOST);

        if (currentEffect == null) {
            return;
        }
        if (currentEffect.getAmplifier() >= level * 7 - 1) {
            // 满层：增伤10%×等级
            ctx.addDamage(ctx.getDamage() * level * DAMAGE_PER_LEVEL_FULL);
        } else {
            // 未满层：增伤5%×等级
            ctx.addDamage(ctx.getDamage() * level * DAMAGE_PER_LEVEL_PARTIAL);
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