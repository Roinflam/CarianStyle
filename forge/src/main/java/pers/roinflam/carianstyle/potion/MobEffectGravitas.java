package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 重力药水效果
 * <p>
 * 效果：
 * - 攻击速度降低1%×等级
 * - 移动速度降低1%×等级
 * - 飞行速度降低1%×等级
 * - 无法跳跃
 * </p>
 */
public class MobEffectGravitas extends IconBase {

    public MobEffectGravitas(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                "64dd94d7-8d83-122b-82be-0c52223463ca",
                -0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                "53878b9c-1134-c379-59c4-391599537f5e",
                -0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.FLYING_SPEED,
                "710bd865-953f-ed1a-facf-eba7de0ce330",
                -0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    /**
     * 重力状态下无法跳跃
     */
    @SubscribeEvent
    public void onLivingUpdate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entityLiving = evt.getEntity();
        if (entityLiving.hasEffect(this)) {
            EntityLivingUtil.setJumped(entityLiving);
        }
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/gravitas.png");
    }
}