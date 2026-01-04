package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 狮子斩附魔
 *
 * 20%概率触发：伤害无视护甲 + 增伤+15%×等级
 */
@AutoRegisterEnchantment(
        id = "lion_claw",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentLionClaw extends EnchantmentBase {

    public EnchantmentLionClaw() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        // 20%概率触发
        if (!RandomUtil.percentageChance(20)) {
            return;
        }

        // 伤害无视护甲
        if (ctx.getDamageSource() != null) {
            ctx.getDamageSource().setDamageBypassesArmor();
        }

        // 增伤 +15% × 等级
        ctx.addDamage(ctx.getDamage() * level * 0.15f);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}