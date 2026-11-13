package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import net.neoforged.fml.common.EventBusSubscriber;

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
                modifierId("bad_omen", "attack_damage"),
                -0.2,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                modifierId("bad_omen", "attack_speed"),
                -0.2,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
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
        if (victim.hasEffect(CarianStylePotion.BAD_OMEN)) {
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
        if (healer.hasEffect(CarianStylePotion.BAD_OMEN)) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "textures/mob_effect/bad_omen.png");
    }
}