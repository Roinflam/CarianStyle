package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
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
 * PORT-STUB(E3)
 *
 * 野兽活力附魔
 * <p>
 * 胸甲附魔，延长药水效果持续时间
 * 获得药水效果时：持续时间增加 30% × 等级
 * 无限时长的效果不处理：时长 -1 按比例延长会变成 -2，效果当场消失
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@Deprecated
public class EnchantmentBeastVitality extends EnchantmentBase implements IEffectModifier {
    private static final String VALUE_ID = "beast_vitality";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DURATION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "duration_per_level",
                    0.3D, 0.0D, 5.0D);
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

