package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

import javax.annotation.Nonnull;

/**
 * 沙布里里的嚎叫药水效果（隐藏）
 * <p>
 * 效果：
 * - 护甲-15%×等级
 * - 韧性-15%×等级
 * - 治疗量-10%×(等级+1)
 * </p>
 */
public class MobEffectHowlShabriri extends HideBase {

    public MobEffectHowlShabriri(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ARMOR,
                "55fb160e-958d-4962-9dcf-086634ca0699",
                -0.15,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                "915e48f6-3049-a15e-e892-035e2d5a7ca1",
                -0.15,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    /**
     * 治疗量减少
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity healer = evt.getEntity();
        if (healer.hasEffect(this)) {
            int amplifier = healer.getEffect(this).getAmplifier();
            evt.setAmount(evt.getAmount() * (1 - (amplifier + 1) * 0.1f));
        }
    }
}