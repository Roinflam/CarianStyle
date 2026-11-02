package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E4)
 *
 * 不动之盾附魔
 * <p>
 * 修复记录：构造函数 EnchantmentCategory.BREAKABLE → CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD")
 * 原bug：所有可损坏物品都能附上此盾牌专属附魔
 * </p>
 *
 * @version 2.1
 */
@AutoRegisterEnchantment(id = "immutable_shield", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantTarget.SHIELD, slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}, baseEnchantability = 5, levelMultiplier = 10)
@Deprecated
public class EnchantmentImmutableShield extends EnchantmentBase {
    private static final String VALUE_ID = "immutable_shield";
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.01D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.1D, 0.0D, 1.0D);
    public EnchantmentImmutableShield() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        });
    }
    protected void onHurtAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench)
                && !ench.isOf(EnchantmentScholarShield.class);
    }
}

