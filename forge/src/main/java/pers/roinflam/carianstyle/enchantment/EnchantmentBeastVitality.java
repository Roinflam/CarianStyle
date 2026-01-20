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
 * 野兽活力附魔
 * <p>
 * 胸甲附魔，延长药水效果持续时间
 * 获得药水效果时：持续时间增加 30% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "beast_vitality",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        forceTreasure = true
)
public class EnchantmentBeastVitality extends EnchantmentBase implements IEffectModifier {

    public EnchantmentBeastVitality() {
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
        // 没有附魔等级，不处理
        if (enchantmentLevel <= 0) {
            return null;
        }

        MobEffect effect = effectInstance.getEffect();

        // 只对非瞬时、可见的效果生效
        if (effect.isInstantenous() || !effectInstance.isVisible()) {
            return null;
        }

        // 计算新的持续时间：原时间 + 原时间 × 等级 × 30%
        int originalDuration = effectInstance.getDuration();
        int addedDuration = (int) (originalDuration * enchantmentLevel * 0.3);
        int newDuration = originalDuration + addedDuration;

        // 创建延长时间的效果实例（其他属性不变）
        return new MobEffectInstance(
                effect,
                newDuration,
                effectInstance.getAmplifier(),
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon()
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}