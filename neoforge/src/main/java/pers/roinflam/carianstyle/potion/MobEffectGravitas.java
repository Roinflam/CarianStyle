package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.event.LivingTickEvent;

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
@EventBusSubscriber
public class MobEffectGravitas extends IconBase {

    public MobEffectGravitas(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                modifierId("gravitas", "attack_speed"),
                -0.01,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                modifierId("gravitas", "movement_speed"),
                -0.01,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        this.addAttributeModifier(
                Attributes.FLYING_SPEED,
                modifierId("gravitas", "flying_speed"),
                -0.01,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    /**
     * 重力状态下无法跳跃
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingTickEvent evt) {
        LivingEntity entityLiving = evt.getEntity();
        if (entityLiving.hasEffect(CarianStylePotion.GRAVITAS)) {
            EntityLivingUtil.setJumped(entityLiving);
        }
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "textures/mob_effect/gravitas.png");
    }
}