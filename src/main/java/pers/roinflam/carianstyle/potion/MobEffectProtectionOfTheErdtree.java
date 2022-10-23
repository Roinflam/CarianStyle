package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;


public class MobEffectProtectionOfTheErdtree extends IconBase {

    public MobEffectProtectionOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "protection_of_the_erdtree");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative() && !damageSource.isDamageAbsolute()) {
                if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    if (hurter.isPotionActive(this)) {
                        int amplifier = hurter.getActivePotionEffect(this).getAmplifier();
                        evt.setAmount(evt.getAmount() - evt.getAmount() * (amplifier + 1) * 0.2f);
                    }
                }
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/protection_of_the_erdtree.png");
    }
}
