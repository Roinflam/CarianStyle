package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.api.IEffectModifier;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;

/**
 * PORT-STUB(E3)
 *
 * 野兽强健附魔
 * <p>
 * 胸甲附魔，缩短药水效果时间但大幅增强等级
 * 获得药水效果时：
 * - 持续时间变为 40%（不受附魔等级影响）
 * - 效果等级变为 原等级 × 2 + 1（不受附魔等级影响），最高不超过 100
 * 无限时长的效果不处理：时长 -1 按比例缩放会取整成 0，效果当场消失
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "beast_robust",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        forceTreasure = true,
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentBeastRobust extends EnchantmentBase implements IEffectModifier {
    private static final String VALUE_ID = "beast_robust";
    private static final EnchantmentValues.Handle MAX_AMPLIFIER = EnchantmentValues.define(VALUE_ID, "max_amplifier",
                    100, 0, 127);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DURATION_RATIO = EnchantmentValues.define(VALUE_ID, "duration_ratio",
                    0.4D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle AMPLIFIER_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "amplifier_multiplier",
                    2, 1, 20);
    public EnchantmentBeastRobust() {
        super(EnchantTarget.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }
    public int getEnchantmentLevel(@NotNull LivingEntity entity) {
        return 0;
    }
    @Nullable public MobEffectInstance modifyEffect(@NotNull LivingEntity entity, @NotNull MobEffectInstance effectInstance, int enchantmentLevel) {
        return effectInstance;
    }
}

