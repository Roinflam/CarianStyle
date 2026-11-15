package pers.roinflam.carianstyle.api;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 药水效果修改接口
 * <p>
 * 实现此接口的附魔可以在药水效果添加到实体前修改其属性
 * 附魔自己决定从哪里获取等级（护甲、手持、主手等）
 * </p>
 *
 * @author RoinFlam
 */
public interface IEffectModifier {

    /**
     * 获取附魔等级
     * <p>
     * 由附魔自己决定从哪里获取等级：
     * - 护甲槽位累加
     * - 手持物品
     * - 主手武器
     * - 等等
     * </p>
     *
     * @param entity 目标实体
     * @return 附魔等级（0 表示实体没有该附魔）
     */
    int getEnchantmentLevel(@NotNull LivingEntity entity);

    /**
     * 修改药水效果
     * <p>
     * 当实体即将获得药水效果时调用此方法
     * </p>
     *
     * @param entity 接受药水效果的实体
     * @param effectInstance 原始药水效果实例
     * @param enchantmentLevel 当前附魔的等级（已由 getEnchantmentLevel 获取）
     * @return 修改后的药水效果实例，如果不需要修改则返回 null
     */
    @Nullable
    MobEffectInstance modifyEffect(@NotNull LivingEntity entity,
                                   @NotNull MobEffectInstance effectInstance,
                                   int enchantmentLevel);
}