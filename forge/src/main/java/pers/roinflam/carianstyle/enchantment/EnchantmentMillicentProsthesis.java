package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 米莉森义肢附魔
 * <p>
 * 武器附魔，连续攻击增强
 * 攻击时：
 * - 叠加攻击增益效果（最多叠到等级×7层）
 * - 满层后刷新为等级×16层，持续60tick
 * 伤害加成：
 * - 满层时增伤10%×等级
 * - 未满层时增伤5%×等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "millicent_prosthesis",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class,
                EnchantmentRedFeatheredBranchsword.class,
                EnchantmentBlueFeatheredBranchsword.class
        }
)
public class EnchantmentMillicentProsthesis extends EnchantmentBase {

    public EnchantmentMillicentProsthesis() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onAttackLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        // 玩家必须是刚挥动武器
        if (attacker instanceof Player) {
            if (!isJustSwung((Player) attacker)) {
                return;
            }
        }

        @Nullable MobEffectInstance currentEffect = attacker.getEffect(CarianStylePotion.ATTACK_BOOST.get());

        if (currentEffect == null) {
            // 没有效果，初始化
            attacker.addEffect(new MobEffectInstance(CarianStylePotion.ATTACK_BOOST.get(), 30, level - 1));
        } else if (currentEffect.getAmplifier() < level * 7 - 1) {
            // 未满层，叠加
            attacker.addEffect(new MobEffectInstance(
                    CarianStylePotion.ATTACK_BOOST.get(),
                    30,
                    currentEffect.getAmplifier() + level
            ));
        } else {
            // 满层，刷新为更高层
            attacker.addEffect(new MobEffectInstance(
                    CarianStylePotion.ATTACK_BOOST.get(),
                    60,
                    level * 16 - 1
            ));
        }
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        @Nullable MobEffectInstance currentEffect = attacker.getEffect(CarianStylePotion.ATTACK_BOOST.get());

        if (currentEffect != null && currentEffect.getAmplifier() >= level * 7 - 1) {
            // 满层：增伤10%×等级
            ctx.addDamage(ctx.getDamage() * level * 0.1f);
        } else {
            // 未满层：增伤5%×等级
            ctx.addDamage(ctx.getDamage() * level * 0.05f);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}