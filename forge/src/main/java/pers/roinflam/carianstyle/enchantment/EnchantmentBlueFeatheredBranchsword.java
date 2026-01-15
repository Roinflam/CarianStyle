package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import javax.annotation.Nonnull;

/**
 * 蓝羽枝剑附魔
 *
 * 血量<=20%时受击减伤（等级×10%）
 */
@AutoRegisterEnchantment(
        id = "blue_feathered_branchsword",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        conflictsWith = {
                EnchantmentCorruptedWingSword.class
        }
)
public class EnchantmentBlueFeatheredBranchsword extends EnchantmentBase {

    public EnchantmentBlueFeatheredBranchsword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsVictim(@Nonnull EnchantmentContext ctx, int level) {
        // 只处理有攻击者的伤害
        if (ctx.getAttacker() == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 血量 <= 20%时触发
        if (ctx.getHolder().getHealth() <= ctx.getHolder().getMaxHealth() * 0.2) {
            // 减伤 = 伤害 × 等级 × 10%
            ctx.multiplyDamage(1 - effectiveLevel * 0.1f);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}