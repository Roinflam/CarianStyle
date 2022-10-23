package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;


public class MobEffectGravitas extends IconBase {

    public MobEffectGravitas(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "gravitas");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "64dd94d7-8d83-122b-82be-0c52223463ca", -0.01, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "53878b9c-1134-c379-59c4-391599537f5e", -0.01, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.FLYING_SPEED, "710bd865-953f-ed1a-facf-eba7de0ce330", -0.01, 2);
    }

    @SubscribeEvent
    public void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLiving = evt.getEntityLiving();
        if (entityLiving.isPotionActive(this)) {
            EntityLivingUtil.setJumped(entityLiving);
        }
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/gravitas.png");
    }
}
