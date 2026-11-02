package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;

/**
 * PORT-STUB(E3)
 * 血的收藏附魔 - 修复: BloodSlash联动检查getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "blood_collection", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantTarget.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class, EnchantmentDarkMoon.class}, baseEnchantability = 25, levelMultiplier = 15)
@Deprecated
public class EnchantmentBloodCollection extends EnchantmentBase {
    private static final String VALUE_ID = "blood_collection";
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.15D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "heal_multiplier",
                    0.02D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER_WITH_SLASH = EnchantmentValues.define(VALUE_ID, "heal_multiplier_with_slash",
                    0.04D, 0.0D, 1.0D);
    public EnchantmentBloodCollection() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

