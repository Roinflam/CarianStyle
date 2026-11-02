package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * PORT-STUB(E4)
 *
 * 魔力蝎符附魔
 * <p>
 * 效果：
 * - 造成的魔法伤害增加 10% × 等级
 * - 造成魔法伤害时恢复自身已损失生命值 1% × 等级
 * - 受到物理伤害时伤害增加 10% × 等级
 * - 最大等级：5
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "magic_scorpion_charm",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        baseEnchantability = 5,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentMagicScorpionCharm extends EnchantmentBase {
    private static final String VALUE_ID = "magic_scorpion_charm";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.1D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.01D, 0.0D, 1.0D);
    public EnchantmentMagicScorpionCharm() {
        super(EnchantTarget.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onHurtAsVictimHighest(@NotNull EnchantmentContext ctx, int level) {
    }
}

