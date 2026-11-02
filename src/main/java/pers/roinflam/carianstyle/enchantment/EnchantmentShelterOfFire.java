package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E4)
 *
 * 火焰庇护附魔
 * <p>
 * 护甲附魔，着火时减伤并回血
 * 着火时：
 * - 受到伤害减少 2% × 等级（50级时完全免疫）
 * - 每秒恢复 0.1% × 等级 最大生命值
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "shelter_of_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 25,
        levelMultiplier = 5
)
@Deprecated
public class EnchantmentShelterOfFire extends EnchantmentBase {
    private static final String VALUE_ID = "shelter_of_fire";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.02D, 0.0D, 0.2D);
    private static final EnchantmentValues.Handle HEAL_INTERVAL_TICKS = EnchantmentValues.define(VALUE_ID, "heal_interval_ticks",
                    20, 1, 600);
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.001D, 0.0D, 0.1D);
    public EnchantmentShelterOfFire() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }
    protected void onDamageAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onPlayerTick(@NotNull EnchantmentContext ctx, int level) {
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench) && !ench.is(Enchantments.PROTECTION);
    }
}

