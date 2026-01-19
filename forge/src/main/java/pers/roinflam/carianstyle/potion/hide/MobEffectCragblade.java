package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 岩石剑药水效果（隐藏）
 *
 * 效果：
 * - 攻击力提高 10% × 等级
 * - 击退抗性 +10% × 等级
 * - 护甲提高 10% × 等级
 * - 韧性提高 10% × 等级
 * - 无法跳跃
 */
public class MobEffectCragblade extends HideBase {

    public MobEffectCragblade(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        // 攻击力提高 10% × 等级
        this.addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                "1288f431-abde-d1dc-0daa-6f3e8a2b1c0d",
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );

        // 击退抗性 +10% × 等级
        this.addAttributeModifier(
                Attributes.KNOCKBACK_RESISTANCE,
                "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0d",
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );

        // 护甲提高 10% × 等级
        this.addAttributeModifier(
                Attributes.ARMOR,
                "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0a",
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );

        // 韧性提高 10% × 等级
        this.addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0c",
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    /**
     * 岩石剑状态下无法跳跃
     */
    @SubscribeEvent
    public void onLivingUpdate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (entity.hasEffect(this)) {
            EntityLivingUtil.setJumped(entity);
        }
    }
}