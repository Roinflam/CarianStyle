package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
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
 */
public class MobEffectFrostbite extends IconBase {

    public MobEffectFrostbite(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "frostbite");

        // 移速修正器用自己的 UUID：以前和睡眠共用一个，原版按 UUID 先删后加，
        // 两个效果同时在身上时，后加的会顶掉先加的（睡着还能走），先结束的会把另一个的减速一起删掉
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.MOVEMENT_SPEED,
                "a3e7f012-8b9c-4d6e-b1f0-3c7829d5a4e1",
                -0.075,
                2
        );
    }

    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        float damage = entityLivingBaseIn.getMaxHealth() * 0.0025f;
        damage += damage * amplifier;
        entityLivingBaseIn.attackEntityFrom(NewDamageSource.FROSTBITE, damage);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/frostbite.png");
    }
}