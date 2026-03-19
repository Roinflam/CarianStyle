// EnchantmentBloodBlade.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentBloodBlade.java
package pers.roinflam.carianstyle.enchantment.combatskill;

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

/**
 * 血刃附魔
 * <p>
 * 消耗自身15%最大生命值，造成额外伤害
 * 额外伤害 = (伤害×等级×0.33 + 目标当前生命值×等级×0.033) × 自身血量比例
 * 上限为目标最大生命值
 * 玩家需刚挥剑，非玩家可直接触发
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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