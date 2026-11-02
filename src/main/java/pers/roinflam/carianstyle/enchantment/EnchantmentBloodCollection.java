package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * 血的收藏附魔 - 修复: BloodSlash联动检查getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@Deprecated
public class EnchantmentBloodCollection extends EnchantmentBase {
    private static final String VALUE_ID = "blood_collection";
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.15D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "heal_multiplier",
                    0.02D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER_WITH_SLASH = EnchantmentValues.define(VALUE_ID, "heal_multiplier_with_slash",
                    0.04D, 0.0D, 1.0D);
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

