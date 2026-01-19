// 文件：EnchantmentDoubleSlash.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentDoubleSlash.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

/**
 * 双斩附魔
 * <p>
 * 玩家攻击时有概率（等级×5+20%）触发二段斩
 * 触发时减少敌人无敌帧，额外造成魔法伤害（伤害×等级×0.2）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "double_slash",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentDoubleSlash extends EnchantmentBase {

    public EnchantmentDoubleSlash() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        // 排除水鸟乱舞伤害（防止递归）
        if (ctx.getDamageSource() != null && "waterfowlDance".equals(ctx.getDamageSource().getMsgId())) {
            return;
        }

        // 只有玩家能触发
        if (!ctx.isHolderPlayer()) {
            return;
        }

        Player player = ctx.getHolderAsPlayer();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 检查刚挥剑
        if (!isJustSwung(player)) {
            return;
        }

        // 重置攻击冷却
        player.resetAttackStrengthTicker();

        // 概率触发：等级×5 + 20%
        if (!RandomUtil.percentageChance(level * 5 + 20)) {
            return;
        }

        // 减少敌人无敌帧
        // 1.20.1: hurtResistantTime → invulnerableTime, maxHurtResistantTime → 默认20
        victim.invulnerableTime = 10;

        // 额外魔法伤害
        // 1.20.1: 使用原版魔法伤害源
        float extraDamage = ctx.getDamage() * level * 0.2f;
        victim.hurt(victim.damageSources().magic(), extraDamage);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}