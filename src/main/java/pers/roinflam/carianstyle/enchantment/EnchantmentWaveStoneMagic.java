package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDoomedDeath;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * PORT-STUB(E4)
 *
 * 波石魔法附魔
 * <p>
 * 武器附魔，魔法伤害增强
 * 造成魔法伤害时：
 * - 伤害增加50%
 * - 移除魔法标签（变为波石魔法）
 * </p>
 * <p>
 * v2.1：接入挥石魔法特效（辉石法阵 + 一块朴素的大石头 + 法阵碎成紫渣）。
 * 这个附魔改变的是伤害<b>类型</b>，而伤害类型此前完全不可见。
 * </p>
 * <p>
 * 特效画的是这个附魔的梗本身：法师魔力聚了半天，掏出来的是块石头，
 * 而且比法术好使。所以法阵做得一板一眼，石头做得灰扑扑毫无光效——
 * 全部效果都建立在这个反差上。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "wave_stone_magic",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentDoomedDeath.class, EnchantmentDeathBlade.class},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentWaveStoneMagic extends EnchantmentBase {
    private static final String VALUE_ID = "wave_stone_magic";
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    1.5D, 0.0D, 10.0D);
    public EnchantmentWaveStoneMagic() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
    }
}

