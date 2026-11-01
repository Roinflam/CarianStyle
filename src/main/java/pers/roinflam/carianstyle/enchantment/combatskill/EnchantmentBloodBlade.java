package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 血刃附魔
 *
 * 消耗15%最大生命值，造成额外伤害
 * 额外伤害 = (伤害×等级×0.33 + 目标当前生命值×等级×0.033) × 自身血量比例，至多为目标最大生命值
 * 玩家需满蓄力，非玩家可直接触发
 * <p>
 * 代价改为 15% 最大生命值、走 {@link EntityLivingUtil#payHealthCost}：血量不够付时正常死亡（不死图腾挡得住），
 * 时间回溯期间不付。以前扣的是 10% 当前生命值，永远扣不死；额外伤害以前也没有上限。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "blood_blade",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
public class EnchantmentBloodBlade extends EnchantmentBase {

    // 每级额外伤害：本次伤害的比例
    private static final float DAMAGE_PER_LEVEL = 0.33F;
    // 每级额外伤害：目标当前生命值的比例
    private static final float VICTIM_HEALTH_PER_LEVEL = 0.033F;
    // 每次触发消耗的最大生命值比例
    private static final float SELF_DAMAGE_RATIO = 0.15F;

    public EnchantmentBloodBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 玩家需要满蓄力，非玩家可直接触发
        if (!isFullyCharged(attacker)) {
            return;
        }

        // 额外伤害 = (当前伤害×等级×0.33 + 目标当前生命值×等级×0.033) × 自身血量比例，上限为目标最大生命值
        float healthRatio = attacker.getHealth() / attacker.getMaxHealth();
        float bonusDamage = Math.min(
                (ctx.getDamage() * level * DAMAGE_PER_LEVEL + victim.getHealth() * level * VICTIM_HEALTH_PER_LEVEL) * healthRatio,
                victim.getMaxHealth()
        );

        // 消耗自身15%最大生命值，血量不够付时正常死亡（不死图腾挡得住）
        EntityLivingUtil.payHealthCost(attacker, attacker.getMaxHealth() * SELF_DAMAGE_RATIO);

        // 增加伤害
        ctx.addDamage(bonusDamage);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}