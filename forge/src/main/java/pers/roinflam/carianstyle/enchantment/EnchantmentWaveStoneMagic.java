package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDoomedDeath;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
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
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentDoomedDeath.class, EnchantmentDeathBlade.class}
)
public class EnchantmentWaveStoneMagic extends EnchantmentBase {

    public EnchantmentWaveStoneMagic() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 修复：改用 High 优先级，在 Normal 之后执行
     */
    @Override
    protected void onHurtAsAttackerHigh(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.getDamageSource() == null || !DamageSourceUtil.isMagicDamage(ctx.getDamageSource())) {
            return;
        }

        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 伤害提升50%
        ctx.multiplyDamage(1.5f);

        // 移除魔法标签（改为波石魔法，不再是普通魔法）
        DamageSourceUtil.removeTag(ctx.getDamageSource(), net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO);

        // ⭐ v2.1：挥石魔法特效。
        // 位置取受击者、朝向取攻击者 —— 碎石是从被砸的那一方身上崩出来的。
        // 只在魔法伤害判定通过之后播，普通物理攻击不该有这个演出
        LivingEntity attacker = ctx.getHolder();
        if (attacker != null && attacker.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.waveStone(serverLevel, attacker, victim);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
