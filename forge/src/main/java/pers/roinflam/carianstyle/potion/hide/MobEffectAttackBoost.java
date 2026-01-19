package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

/**
 * 攻击提升药水效果（隐藏）
 * <p>
 * 效果：
 * - 攻击伤害+1%×等级
 * - 攻击速度+2%×等级
 * </p>
 */
public class MobEffectAttackBoost extends HideBase {

    public MobEffectAttackBoost(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                "74817132-1c8f-0594-1350-1a7734e34205",
                0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                "bb938acd-fd3f-a0e5-d625-0352b8f23fd9",
                0.02,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }
}