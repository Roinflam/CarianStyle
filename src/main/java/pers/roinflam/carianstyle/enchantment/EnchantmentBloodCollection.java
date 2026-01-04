package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 血的收藏附魔
 *
 * 血量越低增伤越高，同时治疗自身
 * 与BloodSlash联动时治疗翻倍
 */
@AutoRegisterEnchantment(
        id = "blood_collection",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
public class EnchantmentBloodCollection extends EnchantmentBase {

    public EnchantmentBloodCollection() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        float lostHealthRatio = 1 - attacker.getHealth() / attacker.getMaxHealth();

        float bonusDamage = ctx.getDamage() * lostHealthRatio * effectiveLevel * 0.15f;
        ctx.addDamage(bonusDamage);

        // 检查是否有BloodSlash附魔
        float healMultiplier = 0.02f;
        Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
        if (bloodSlash != null) {
            if (EnchantmentHelper.getEnchantmentLevel(
                    bloodSlash,
                    attacker.getHeldItem(attacker.getActiveHand())) > 0) {
                healMultiplier = 0.04f;
            }
        }

        attacker.heal(attacker.getMaxHealth() * lostHealthRatio * effectiveLevel * healMultiplier);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}