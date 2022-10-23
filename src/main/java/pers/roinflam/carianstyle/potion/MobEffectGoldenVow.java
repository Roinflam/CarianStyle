package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


public class MobEffectGoldenVow extends IconBase {

    public MobEffectGoldenVow(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "golden_vow");
    }

    @SubscribeEvent
    public void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (hurter.isPotionActive(this)) {
                    int amplifier = hurter.getActivePotionEffect(this).getAmplifier();
                    evt.setAmount(evt.getAmount() - evt.getAmount() * (amplifier + 1) * 0.1f);
                }
                if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                    @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                    if (attacker.isPotionActive(this)) {
                        int amplifier = attacker.getActivePotionEffect(this).getAmplifier();
                        evt.setAmount(evt.getAmount() + evt.getAmount() * (amplifier + 1) * 0.15f);
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
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/golden_vow.png");
    }
}
