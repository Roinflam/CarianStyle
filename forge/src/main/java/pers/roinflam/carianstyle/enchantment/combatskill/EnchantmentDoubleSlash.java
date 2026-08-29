// 文件：EnchantmentDoubleSlash.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentDoubleSlash.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

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
 * 特效调用放在 {@code victim.hurt(...)} <b>之后</b>——那时二段斩已经真正打出去了。
 * 放在概率判定与 {@code hurt} 之间也能跑，但若将来在这两行之间插入任何提前返回的分支，
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

        // ⭐ v2.1：播放「两道交叉刀光」自绘特效。
        // 位置取受击者、朝向取攻击者（与狮子斩同理）
        if (player.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.doubleSlash(serverLevel, player, victim);
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
