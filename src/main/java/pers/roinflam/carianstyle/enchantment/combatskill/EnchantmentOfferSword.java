package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import javax.annotation.Nonnull;

/**
 * 奉剑附魔
 *
 * 满血时攻击增伤 +10% × 等级
 */
@AutoRegisterEnchantment(
        id = "offer_sword",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentOfferSword extends EnchantmentBase {

    public EnchantmentOfferSword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        // 满血时才触发
        if (attacker.getHealth() < attacker.getMaxHealth()) {
            return;
        }

        // 增伤 +10% × 等级
        ctx.addDamage(ctx.getDamage() * level * 0.1f);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}