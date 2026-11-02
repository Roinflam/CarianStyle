package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import javax.annotation.Nonnull;

/**
 * 双斩附魔
 * <p>
 * 玩家攻击时有概率（等级×5+20%）触发二段斩
 * 触发时减少敌人无敌帧，额外造成魔法伤害（伤害×等级×0.2）
 * </p>
 * <p>v2.1：触发时播放「两道交叉刀光」自绘特效</p>
 *
 * <h3>v2.1 关于插入位置</h3>
 * <p>
 * 特效调用放在 {@code victim.attackEntityFrom(...)} <b>之后</b>——那时二段斩已经真正打出去了。
 * 放在概率判定与 {@code attackEntityFrom} 之间也能跑，但若将来在这两行之间插入任何提前返回的分支，
 * 就会出现「播了特效却没打伤害」的不一致。放在最后是最不容易被后续改动破坏的位置。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "double_slash",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentDoubleSlash extends EnchantmentBase {

    public EnchantmentDoubleSlash() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        // 排除水鸟乱舞伤害（防止递归）
        if (ctx.getDamageSource() != null && "waterfowlDance".equals(ctx.getDamageSource().getDamageType())) {
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

        // 消耗这一下挥击：冷却延后到下一 tick 开头清零（直接清零会让同一下挥击里后面的蓄力判断，如命定之死，全部失效），
        // 同一 tick 内只结算一次——横扫副目标、范围伤害的其它目标、主副手都带本附魔的重复分发都不再各结算一遍
        if (!AttackCooldownHelper.spendSwing(player)) {
            return;
        }

        // 概率触发：等级×5 + 20%
        if (!RandomUtil.percentageChance(level * 5 + 20)) {
            return;
        }

        // 减少敌人无敌帧：降到最大无敌时间（20）的一半，嵌套的这一下按满额结算，不被当成无敌帧内的补差
        victim.hurtResistantTime = 10;

        // 额外魔法伤害（原版魔法伤害源）
        float extraDamage = ctx.getDamage() * level * 0.2f;
        victim.attackEntityFrom(DamageSource.MAGIC, extraDamage);

        // v2.1：播放「两道交叉刀光」自绘特效。
        // 位置取受击者、朝向取攻击者（与狮子斩同理）
        if (player.world instanceof WorldServer) {
            CarianStyleCombatArtEffects.doubleSlash((WorldServer) player.world, player, victim);
        }
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
