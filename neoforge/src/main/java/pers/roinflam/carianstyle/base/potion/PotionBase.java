package pers.roinflam.carianstyle.base.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 药水效果基类
 * <p>
 * 所有模组药水效果的基础类
 * </p>
 */
public abstract class PotionBase extends MobEffect {

    /**
     * 构造函数
     *
     * @param category 效果类型（有益/有害）
     * @param liquidColor 液体颜色
     */
    protected PotionBase(@Nonnull MobEffectCategory category, int liquidColor) {
        super(category, liquidColor);
    }

    /**
     * 药水属性修饰符的 id：{@code carianstyle:effect/<药水>/<属性>}
     * <p>
     * 1.21.1 的修饰符按 ResourceLocation 区分，不再用 UUID。1.20.1 各药水写死的 UUID
     * 在旧存档里会被原版修复器改成 {@code minecraft:<uuid>}，由
     * {@code dynamicattr.LegacyModifierMigration} 在实体进世界时清掉、换成这里的新 id。
     * </p>
     *
     * @param effect    药水的注册名（不带命名空间）
     * @param attribute 属性名（{@code attack_damage} 这种，不带 {@code generic.}）
     */
    @Nonnull
    protected static ResourceLocation modifierId(@Nonnull String effect, @Nonnull String attribute) {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "effect/" + effect + "/" + attribute);
    }

    /**
     * 每次到点的效果逻辑，子类重写
     * <p>
     * 1.21.1 起返回值表示效果是否继续：返回 false 会让原版立刻把效果移除（客户端也会跟着移除）。
     * 本模组的效果都不靠这个提前结束，一律返回 true。
     * </p>
     */
    @Override
    public boolean applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        // 子类重写
        return true;
    }

    /**
     * 这一 tick 要不要调 {@link #applyEffectTick}（1.20.1 的 {@code isDurationEffectTick}，只改了名字）
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}