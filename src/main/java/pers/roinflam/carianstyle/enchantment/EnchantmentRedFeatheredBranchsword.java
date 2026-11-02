package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 *
 * 红羽枝剑附魔
 * <p>
 * 武器附魔，低血量增伤
 * 攻击时：
 * - 如果自身血量 <= 20%，伤害增加 20% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "red_feathered_branchsword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentCorruptedWingSword.class},
        baseEnchantability = 15,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentRedFeatheredBranchsword extends EnchantmentBase {
    private static final String VALUE_ID = "red_feathered_branchsword";
    private static final EnchantmentValues.Handle HEALTH_THRESHOLD = EnchantmentValues.define(VALUE_ID, "health_threshold",
                    0.2D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.2D, 0.0D, 5.0D);
    public EnchantmentRedFeatheredBranchsword() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

