package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.api.IEffectModifier;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;

/**
 * 野兽强健附魔
 * <p>
 * 胸甲附魔，缩短药水效果时间但大幅增强等级
 * 获得药水效果时：
 * - 持续时间变为 40%（不受附魔等级影响）
 * - 效果等级变为 原等级 × 2 + 1（不受附魔等级影响），最高不超过 100
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "beast_robust",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        forceTreasure = true
)
public class EnchantmentBeastRobust extends EnchantmentBase implements IEffectModifier {

    /**
     * 药水效果等级的最大上限
     */
    private static final int MAX_AMPLIFIER = 100;

    public EnchantmentBeastRobust() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getEnchantmentLevel(@NotNull LivingEntity entity) {
        // 只从胸甲获取附魔等级
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(this, chest);

        // 应用等级限制
        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        return level;
    }

    @Nullable
    @Override
    public MobEffectInstance modifyEffect(@NotNull LivingEntity entity,
                                          @NotNull MobEffectInstance effectInstance,
                                          int enchantmentLevel) {
        // 只要有附魔就生效（等级不影响效果强度）
        if (enchantmentLevel <= 0) {
            return null;
        }

        MobEffect effect = effectInstance.getEffect();

        // 只对非瞬时、可见的效果生效
        if (effect.isInstantenous() || !effectInstance.isVisible()) {
            return null;
        }

        // 计算新属性
        int newDuration = (int) (effectInstance.getDuration() * 0.4);  // 时间缩短到 40%
        int newAmplifier = Math.min(effectInstance.getAmplifier() * 2 + 1, MAX_AMPLIFIER);  // 等级翻倍+1，上限100

        // 创建修改后的效果实例
        return new MobEffectInstance(
                effect,
                newDuration,
                newAmplifier,
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon()
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}