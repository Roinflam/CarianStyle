package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;


public class MobEffectHemorrhage extends IconBase {

    public MobEffectHemorrhage(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "hemorrhage");
    }

    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        float damage = entityLivingBaseIn.getMaxHealth() * (0.07f + 0.01f * amplifier) / 30;
        new SynchronizationTask(1) {

            @Override
            public void run() {
                if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
                    entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
                } else {
                    EntityLivingUtil.kill(entityLivingBaseIn, NewDamageSource.HEMORRHAGE);
                }
            }

        }.start();
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/hemorrhage.png");
    }
}
