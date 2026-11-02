package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import javax.annotation.Nonnull;

/**
 * PORT-STUB(B2a)
 *
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
@Deprecated
public class MobEffectFrostbite extends IconBase {
    public MobEffectFrostbite(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
    @Nonnull @OnlyIn(Dist.CLIENT) protected ResourceLocation getIconTexture() {
        return null;
    }
}

