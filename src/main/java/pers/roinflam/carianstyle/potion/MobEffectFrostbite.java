package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;



public class MobEffectFrostbite extends IconBase {

    public MobEffectFrostbite(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "frostbite");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "5d59080b-eda9-f5b7-1b3c-51568e5b6682", -0.075, 2);
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
