package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 双斩附魔
 *
 * 玩家攻击时有概率（等级×5+20%）触发二段斩
 * 触发时减少敌人无敌帧，额外造成魔法伤害（伤害×等级×0.2）
 */
@AutoRegisterEnchantment(
        id = "double_slash",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentDoubleSlash extends EnchantmentBase {

    public EnchantmentDoubleSlash() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        // 排除水鸟乱舞伤害（防止递归）
        if (ctx.getDamageSource() != null && "waterfowlDance".equals(ctx.getDamageSource().damageType)) {
            return;
        }

        // 只有玩家能触发
        if (!ctx.isHolderPlayer()) {
            return;
        }

        EntityPlayer player = ctx.getHolderAsPlayer();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 检查刚挥剑
        if (!isJustSwung(player)) {
            return;
        }

        // 重置攻击冷却
        player.resetCooldown();

        // 概率触发：等级×5 + 20%
        if (!RandomUtil.percentageChance(level * 5 + 20)) {
            return;
        }

        // 减少敌人无敌帧
        victim.hurtResistantTime = victim.maxHurtResistantTime / 2;

        // 修改伤害类型防止递归
        ctx.getDamageSource().damageType = "waterfowlDance";

        // 额外魔法伤害
        float extraDamage = ctx.getDamage() * level * 0.2f;
        victim.attackEntityFrom(DamageSource.MAGIC, extraDamage);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }
}