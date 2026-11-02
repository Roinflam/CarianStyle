package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
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
@Deprecated
public class EnchantmentScarletCorruption extends EnchantmentBase {
    private static final String VALUE_ID = "scarlet_rot";
    private static final EnchantmentValues.Handle SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "seconds_per_level",
                    20, 1, 300);
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

