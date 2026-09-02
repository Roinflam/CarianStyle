package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * 血刃附魔
 * <p>
 * 消耗自身15%最大生命值，造成额外伤害
 * 额外伤害 = (伤害×等级×0.33 + 目标当前生命值×等级×0.033) × 自身血量比例
 * 上限为目标最大生命值
 * 玩家需刚挥剑，非玩家可直接触发
 * </p>
 * <p>
 * v2.1：接入血刃打击反馈（自伤血溅 + 朝正前方射出的细长血色新月）。
 * 这是「自伤换伤」的附魔，扣了 15% 血却毫无提示最容易导致误判血线，
 * 特效因此画在<b>自己</b>身上而非目标身上。
 * </p>
 * <p>
 * 视觉原型取自《艾尔登法环》的战技<b>《血刃》</b>（割破自身、射出一道新月），
 * 而非《鲜血斩击》——后者是身周爆发，在本模组里对应另一个附魔
 * {@code EnchantmentBloodSlash}，那套语汇留给它。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "blood_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
public class EnchantmentBloodBlade extends EnchantmentBase {

    public EnchantmentBloodBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时触发血刃效果
     * 根据自身血量比例和附魔等级计算额外伤害，同时消耗自身15%最大生命值
     *
     * @param ctx   附魔上下文
     * @param level 附魔等级
     */
    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 玩家需要刚挥剑，非玩家可直接触发
        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        // 额外伤害 = (当前伤害×等级×0.33 + 目标当前生命值×等级×0.033) × 自身血量比例，上限为目标最大生命值
        float healthRatio = attacker.getHealth() / attacker.getMaxHealth();
        float bonusDamage = Math.min(
                (ctx.getDamage() * level * 0.33f + victim.getHealth() * level * 0.033f) * healthRatio,
                victim.getMaxHealth()
        );

        // 消耗自身15%最大生命值
        attacker.setHealth(attacker.getHealth() - attacker.getMaxHealth() * 0.15f);

        // ⭐ v2.1：血刃打击反馈。
        // 位置刻意放在 setHealth 之后 —— 只有代价真正付出去了才播，
        // 上面任何一个 return 都不该看到特效
        if (attacker.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.bloodBlade(serverLevel, attacker);
        }

        // 增加伤害
        ctx.addDamage(bonusDamage);
    }

    /**
     * 获取最低附魔能力需求
     *
     * @param enchantmentLevel 附魔等级
     * @return 最低附魔能力值
     */
    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    /**
     * 获取最高附魔能力需求
     *
     * @param enchantmentLevel 附魔等级
     * @return 最高附魔能力值
     */
    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
