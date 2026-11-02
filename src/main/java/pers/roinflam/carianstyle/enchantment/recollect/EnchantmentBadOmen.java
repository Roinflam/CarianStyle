package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * PORT-STUB(E2)
 *
 * 凶兆附魔
 * <p>
 * 攻击时给敌人施加凶兆效果
 * 敌人已有凶兆时，额外造成50%伤害（直接扣血），可触发斩杀
 * </p>
 * <p>
 * v2.1：玩家必须满蓄力才追加真伤、挂凶兆；未满蓄力的一下只付「目标已有凶兆时本体伤害折半」的代价、拿不到好处，
 * 怪物持有者不受影响
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "bad_omen",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 38,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentBadOmen extends EnchantmentBase {
    private static final String VALUE_ID = "bad_omen";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle EXTRA_DAMAGE_RATIO = EnchantmentValues.define(VALUE_ID, "extra_damage_ratio",
                    0.5D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.5D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle EFFECT_DURATION = EnchantmentValues.define(VALUE_ID, "effect_duration",
                    200, 20, 6000);
    public EnchantmentBadOmen() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
    }
}

