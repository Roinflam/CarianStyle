package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * PORT-STUB(E1)
 *
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
@Deprecated
public class EnchantmentBloodBlade extends EnchantmentBase {
    private static final String VALUE_ID = "blood_blade";
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.33D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle VICTIM_HEALTH_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "victim_health_per_level",
                    0.033D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO = EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.15D, 0.0D, 0.9D);
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

