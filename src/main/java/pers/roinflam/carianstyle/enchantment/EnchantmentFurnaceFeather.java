package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import pers.roinflam.carianstyle.utils.util.EquipmentSlots;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E3)
 *
 * 熔炉之羽附魔
 * <p>
 * 护甲附魔，以受到更多伤害换取机动性增益
 * 受击时：
 * - 受到的伤害增加50% * 全身等级之和（只算一次，四件 Ⅲ 级 = 12 级，×7）
 * - 大幅增加无敌帧（invulnerableTime = max + max/2 × 等级 × 1.5）
 * - 获得速度效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * - 获得跳跃提升效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * 后三项按等级最高的一件算，不叠加
 * </p>
 * <p>
 * 分发器按件回调，以前每件各乘一次，四件 Ⅲ 级约 ×39。现在只在第一件上结算一次。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "furnace_feather",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true,
        baseEnchantability = 10,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentFurnaceFeather extends EnchantmentBase {
    private static final String VALUE_ID = "furnace_feather";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle INVULNERABLE_FACTOR = EnchantmentValues.define(VALUE_ID, "invulnerable_factor",
                    1.5D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle EFFECT_TICKS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "effect_ticks_per_level",
                    40, 1, 1200);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.5D, 0.0D, 5.0D);
    public EnchantmentFurnaceFeather() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }
    protected void onDamageAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench) && !ench.is(Enchantments.PROTECTION);
    }
}

