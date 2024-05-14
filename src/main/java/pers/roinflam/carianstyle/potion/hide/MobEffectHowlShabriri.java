package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;

import javax.annotation.Nonnull;


public class MobEffectHowlShabriri extends HideBase {

    public MobEffectHowlShabriri(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "howl_shabriri");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "55fb160e-958d-4962-9dcf-086634ca0699", -0.15, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "915e48f6-3049-a15e-e892-035e2d5a7ca1", -0.15, 2);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                evt.setAmount(evt.getAmount() - evt.getAmount() * (healer.getActivePotionEffect(this).getAmplifier() + 1) * 0.1f);
            }
        }
    }

}
