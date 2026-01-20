package pers.roinflam.carianstyle.enchantment;

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

/**
 * 波石魔法附魔
 * <p>
 * 武器附魔，魔法伤害增强
 * 造成魔法伤害时：
 * - 伤害增加50%
 * - 移除魔法标签（变为波石魔法）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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