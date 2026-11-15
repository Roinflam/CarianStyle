package pers.roinflam.carianstyle.potion;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.carianstyle.event.LivingHealEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.bus.EventPriority;
import pers.roinflam.carianstyle.bus.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.bus.EventBusSubscriber;

/**
 * 不祥预感药水效果
 * <p>
 * 效果：
 * - 攻击伤害降低20%
 * - 攻击速度降低20%
 * - 受到伤害增加25%
 * - 治疗量减少50%
 * </p>
 */
@EventBusSubscriber
public class MobEffectBadOmen extends IconBase {

    public MobEffectBadOmen(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                "5154bf3b-743a-cee6-cf4f-2a62cf832d25",
                -0.2,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                "d545b2e6-98a0-c8c7-a7f3-345df7ec14dc",
                -0.2,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    /**
     * 受到伤害增加25%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        if (victim.hasEffect(CarianStylePotion.BAD_OMEN.get())) {
            evt.setAmount(evt.getAmount() * 1.25f);
        }
    }

    /**
     * 治疗量减少50%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity healer = evt.getEntity();
        if (healer.hasEffect(CarianStylePotion.BAD_OMEN.get())) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}