package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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

/** 血的收藏附魔 - 修复: BloodSlash联动检查getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "blood_collection", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class, EnchantmentDarkMoon.class})
public class EnchantmentBloodCollection extends EnchantmentBase {
    public EnchantmentBloodCollection() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) effectiveLevel = Math.min(effectiveLevel, 10);
        if (ctx.isHolderPlayer() && ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) return;
        float lostHealthRatio = 1 - attacker.getHealth() / attacker.getMaxHealth();
        float bonusDamage = ctx.getDamage() * lostHealthRatio * effectiveLevel * 0.15f;
        ctx.addDamage(bonusDamage);
        // 修复：使用主手检查BloodSlash联动
        float healMultiplier = 0.02f;
        Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
        if (bloodSlash != null) {
            ItemStack mainHand = attacker.getItemInHand(InteractionHand.MAIN_HAND);
            if (!mainHand.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(bloodSlash, mainHand) > 0) {
                healMultiplier = 0.04f;
            }
        }
        attacker.heal(attacker.getMaxHealth() * lostHealthRatio * effectiveLevel * healMultiplier);
    }

    @Override public int getMinCost(int l) { return (int)((25 + (l - 1) * 15) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
