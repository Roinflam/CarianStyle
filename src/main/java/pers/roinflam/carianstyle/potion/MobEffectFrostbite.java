package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 冻伤药水效果
 * <p>
 * 效果：
 * - 移动速度降低7.5%×等级
 * - 每0.5秒造成最大生命值×0.25%×(等级+1)的冻伤伤害
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - UUID从"5d59080b-eda9-f5b7-1b3c-51568e5b6682"改为新UUID
 *   原UUID与MobEffectSleep的移速修改器完全相同，导致：
 *   1. 同时拥有冻伤和睡眠时后者覆盖前者的修改器
 *   2. 先移除的效果会把另一个的修改器也删掉
 * </p>
 */
public class MobEffectFrostbite extends IconBase {

    public MobEffectFrostbite(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        // v2.1修复：换用独立UUID，避免与MobEffectSleep的移速修改器冲突
        // 原UUID "5d59080b-eda9-f5b7-1b3c-51568e5b6682" 与Sleep共用
        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                "a3e7f012-8b9c-4d6e-b1f0-3c7829d5a4e1",
                -0.075,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        float damage = entityLivingBaseIn.getMaxHealth() * 0.0025f;
        damage += damage * amplifier;
        entityLivingBaseIn.hurt(NewDamageSource.frostbite(entityLivingBaseIn.level()), damage);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/frostbite.png");
    }
}
