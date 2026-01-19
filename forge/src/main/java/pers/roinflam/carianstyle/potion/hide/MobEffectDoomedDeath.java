package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

/**
 * 注定死亡药水效果（隐藏）
 * <p>
 * 效果：
 * - 最大生命值-25%×等级
 * </p>
 */
public class MobEffectDoomedDeath extends HideBase {

    public MobEffectDoomedDeath(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.MAX_HEALTH,
                "58993fe2-d11c-2b97-4958-6a8304ff8ad8",
                -0.25,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }
}