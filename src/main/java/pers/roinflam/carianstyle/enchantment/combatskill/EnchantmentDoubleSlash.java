package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * PORT-STUB(E1)
 *
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
@Deprecated
public class EnchantmentDoubleSlash extends EnchantmentBase {
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

