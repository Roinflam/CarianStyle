package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 血的收藏附魔
 * <p>
 * 血量越低增伤越高，同时治疗自身
 * 与BloodSlash联动时治疗翻倍
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "blood_collection",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
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
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        if (ctx.isHolderPlayer()) {
            if (ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) {
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
            if (EnchantmentHelper.getItemEnchantmentLevel(
                    bloodSlash,
                    attacker.getItemInHand(attacker.getUsedItemHand())) > 0) {
                healMultiplier = 0.04f;
            }
        }

        attacker.heal(attacker.getMaxHealth() * lostHealthRatio * effectiveLevel * healMultiplier);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}