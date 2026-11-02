package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.api.IEffectModifier;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 *
 * 清醒附魔
 * <p>
 * 护甲附魔，缩短负面效果持续时间但增强效果
 * 获得负面效果时：
 * - 持续时间减少 15% × 等级
 * - 效果等级+1（效果更强但更短）
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@Deprecated
public class EnchantmentLucidity extends EnchantmentBase implements IEffectModifier {
    private static final String VALUE_ID = "lucidity";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DURATION_REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "duration_reduction_per_level",
                    0.15D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle AMPLIFIER_BONUS = EnchantmentValues.define(VALUE_ID, "amplifier_bonus",
                    1, 0, 20);
    public int getEnchantmentLevel(@NotNull LivingEntity entity) {
        return 0;
    }
    @Nullable public MobEffectInstance modifyEffect(@NotNull LivingEntity entity, @NotNull MobEffectInstance effectInstance, int enchantmentLevel) {
        return effectInstance;
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

