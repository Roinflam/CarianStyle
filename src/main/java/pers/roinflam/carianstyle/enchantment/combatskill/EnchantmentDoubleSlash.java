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
import pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 双斩附魔
 *
 * 玩家攻击时有概率（等级×5+20%）触发二段斩
 * 触发时减少敌人无敌帧，额外造成魔法伤害（伤害×等级×0.2，无视护甲）
 * <p>
 * 要求满蓄力（蓄力超过 90%），每一下挥击只结算一次：以前横扫打到几个目标就各掷一次概率、各追加一次伤害。
 * 不再把这一下的伤害类型名改成 waterfowlDance：以前靠它防递归，打死目标时的死亡信息会变成水鸟乱舞那条，
 * 同一把武器带水鸟乱舞时连首段都触发不了。追加的魔法伤害没有攻击者，本来就不会再进到这里。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "double_slash",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentDoubleSlash extends EnchantmentBase {

    // 基础触发概率（%）
    private static final int BASE_CHANCE = 20;
    // 每级触发概率（%）
    private static final int CHANCE_PER_LEVEL = 5;
    // 每级追加伤害（本次伤害的比例）
    private static final float DAMAGE_PER_LEVEL = 0.2F;
    // 追加前目标的无敌时间（tick）
    private static final int EXTRA_HIT_INVULNERABLE_TIME = 10;

    public EnchantmentDoubleSlash() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        // 只有玩家能触发
        if (!ctx.isHolderPlayer()) {
            return;
        }

        EntityPlayer player = ctx.getHolderAsPlayer();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 满蓄力
        if (!isFullyCharged(player)) {
            return;
        }

        // 消耗这一下挥击：同一 tick 只结算一次（横扫副目标、主副手都带本附魔的重复分发不再各结算一遍），
        // 冷却推迟到下一 tick 开头清零
        if (!AttackCooldownHelper.spendSwing(player)) {
            return;
        }

        // 概率触发：等级×5 + 20%
        if (!RandomUtil.percentageChance(level * CHANCE_PER_LEVEL + BASE_CHANCE)) {
            return;
        }

        // 减少敌人无敌帧
        victim.hurtResistantTime = EXTRA_HIT_INVULNERABLE_TIME;

        // 额外魔法伤害
        float extraDamage = ctx.getDamage() * level * DAMAGE_PER_LEVEL;
        victim.attackEntityFrom(DamageSource.MAGIC, extraDamage);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }
}