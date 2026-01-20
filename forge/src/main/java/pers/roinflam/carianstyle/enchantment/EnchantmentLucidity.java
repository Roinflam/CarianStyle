package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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
@AutoRegisterEnchantment(
        id = "lucidity",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
public class EnchantmentLucidity extends EnchantmentBase implements IEffectModifier {

    public EnchantmentLucidity() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @Override
    public int getEnchantmentLevel(@NotNull LivingEntity entity) {
        // 从所有护甲槽位累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(this, armor);
            }
        }

        // 应用等级限制
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        return totalLevel;
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

        // 只对非瞬时、可见的负面效果生效
        if (effect.isInstantenous()
                || !effectInstance.isVisible()
                || !effect.getCategory().equals(MobEffectCategory.HARMFUL)) {
            return null;
        }

        // 计算新的持续时间（减少 15% × 等级）
        int originalDuration = effectInstance.getDuration();
        int newDuration = (int) (originalDuration * (1.0 - enchantmentLevel * 0.15));

        // 确保持续时间至少为 1 tick
        newDuration = Math.max(newDuration, 1);

        // 创建修改后的效果实例（持续时间减少，等级+1）
        return new MobEffectInstance(
                effect,
                newDuration,
                effectInstance.getAmplifier() + 1,  // 等级+1，效果更强
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon()
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}