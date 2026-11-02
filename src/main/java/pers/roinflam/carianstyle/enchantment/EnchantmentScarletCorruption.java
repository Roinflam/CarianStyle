package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 *
 * 猩红腐败附魔
 * <p>
 * 武器附魔，攻击时施加腐败效果
 * 攻击时：
 * - 给目标施加猩红腐败效果（持续 = 等级 × 20秒）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "scarlet_rot",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class},
        baseEnchantability = 36,
        levelMultiplier = 20
)
@Deprecated
public class EnchantmentScarletCorruption extends EnchantmentBase {
    private static final String VALUE_ID = "scarlet_rot";
    private static final EnchantmentValues.Handle SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "seconds_per_level",
                    20, 1, 300);
    public EnchantmentScarletCorruption() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
}

