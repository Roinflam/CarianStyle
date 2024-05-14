package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;


public class MobEffectBadOmen extends IconBase {

    public MobEffectBadOmen(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "bad_omen");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE, "5154bf3b-743a-cee6-cf4f-2a62cf832d25", -0.2, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "d545b2e6-98a0-c8c7-a7f3-345df7ec14dc", -0.2, 2);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (hurter.isPotionActive(this)) {
                evt.setAmount(evt.getAmount() * 1.25f);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                evt.setAmount(evt.getAmount() * 0.5f);
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/bad_omen.png");
    }

}
