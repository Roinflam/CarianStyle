package pers.roinflam.carianstyle.handler;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.api.IEffectModifier;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 药水效果修改处理器
 * <p>
 * 只负责找到所有实现了 IEffectModifier 的附魔并调用
 * 具体的等级获取和修改逻辑都在各个附魔类中
 * </p>
 *
 * @author RoinFlam
 */
public class EffectModifierHandler {

    /**
     * 处理药水效果修改
     * <p>
     * 找到实体装备上所有的 IEffectModifier 附魔，依次调用它们的修改方法
     * </p>
     *
     * @param entity 接受药水效果的实体
     * @param effectInstance 原始药水效果实例
     * @return 修改后的药水效果实例
     */
    public static MobEffectInstance handleEffectModification(@NotNull LivingEntity entity,
                                                             @NotNull MobEffectInstance effectInstance) {
        // 客户端不处理
        if (entity.level().isClientSide) {
            return effectInstance;
        }

        // 收集实体装备上所有实现了 IEffectModifier 的附魔（去重）
        Set<IEffectModifier> modifiers = collectModifiers(entity);

        if (modifiers.isEmpty()) {
            return effectInstance;
        }

        // 依次调用每个附魔的修改方法
        MobEffectInstance result = effectInstance;
        for (IEffectModifier modifier : modifiers) {
            // 让附魔自己获取等级
            int level = modifier.getEnchantmentLevel(entity);

            if (level > 0) {
                MobEffectInstance modified = modifier.modifyEffect(entity, result, level);
                if (modified != null) {
                    result = modified;
                }
            }
        }

        return result;
    }

    /**
     * 收集实体装备上的所有 IEffectModifier 附魔（去重）
     * <p>
     * 遍历护甲槽位和手持物品，找出所有实现了接口的附魔
     * </p>
     *
     * @param entity 目标实体
     * @return 实现了 IEffectModifier 的附魔集合
     */
    private static Set<IEffectModifier> collectModifiers(@NotNull LivingEntity entity) {
        Set<IEffectModifier> modifiers = new HashSet<>();

        // 遍历所有护甲槽位
        for (ItemStack armor : entity.getArmorSlots()) {
            addModifiersFromItem(armor, modifiers);
        }

        // 遍历手持物品（主手 + 副手）
        for (ItemStack hand : entity.getHandSlots()) {
            addModifiersFromItem(hand, modifiers);
        }

        return modifiers;
    }

    /**
     * 从物品中提取所有 IEffectModifier 附魔
     *
     * @param item 物品
     * @param modifiers 附魔集合（输出参数）
     */
    private static void addModifiersFromItem(@NotNull ItemStack item, @NotNull Set<IEffectModifier> modifiers) {
        if (item.isEmpty()) {
            return;
        }

        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(item);
        for (Enchantment enchantment : enchantments.keySet()) {
            if (enchantment instanceof IEffectModifier) {
                modifiers.add((IEffectModifier) enchantment);
            }
        }
    }
}