package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;


public class MobEffectBlessingOfTheErdtree extends IconBase {

    public MobEffectBlessingOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "blessing_of_the_erdtree");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MAX_HEALTH, "c407bffa-97df-adf8-51db-5681fdef4b8c", 0.15, 2);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 60 == 0;
    }

    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        entityLivingBaseIn.heal(entityLivingBaseIn.getMaxHealth() * (amplifier + 1) * 0.04f);
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/blessing_of_the_erdtree.png");
    }

}
