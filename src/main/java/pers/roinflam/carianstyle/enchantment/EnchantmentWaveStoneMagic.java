package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDoomedDeath;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.source.NewDamageSource;

import javax.annotation.Nonnull;

/**
 * 波石魔法附魔
 *
 * 武器附魔，魔法伤害增强
 * 造成魔法伤害时：
 * - 取消原伤害
 * - 改为造成波石魔法伤害（原伤害 × 1.5）
 */
@AutoRegisterEnchantment(
        id = "wave_stone_magic",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentWaveStoneMagic extends EnchantmentBase {

    public EnchantmentWaveStoneMagic() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 魔法伤害转为波石魔法伤害
     */
    @Override
    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        // 必须是魔法伤害
        if (ctx.getDamageSource() == null || !ctx.getDamageSource().isMagicDamage()) {
            return;
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        float originalDamage = ctx.getDamage();

        // 取消原伤害
        ctx.cancelEvent();

        // 重置无敌帧
        victim.hurtResistantTime = victim.maxHurtResistantTime / 2;

        // 造成波石魔法伤害（原伤害 × 1.5）
        victim.attackEntityFrom(NewDamageSource.WAVE_STONE_MAGIC, originalDamage + originalDamage * 0.5f);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentDoomedDeath.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentDeathBlade.class));
    }
}