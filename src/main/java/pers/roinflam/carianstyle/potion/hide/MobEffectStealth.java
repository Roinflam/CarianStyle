package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;

@Mod.EventBusSubscriber
public class MobEffectStealth extends HideBase {

    public MobEffectStealth(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "stealth");
    }

    public static Potion getPotion() {
        return CarianStylePotion.STEALTH;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLiving(RenderLivingEvent.Pre evt) {
        EntityLivingBase entityLivingBase = evt.getEntity();
        if (entityLivingBase.isPotionActive(getPotion())) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingSetAttackTarget(LivingSetAttackTargetEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getTarget() != null) {
                if (evt.getEntityLiving() instanceof EntityLiving) {
                    EntityLivingBase entityLivingBase = evt.getTarget();
                    EntityLiving entityLiving = (EntityLiving) evt.getEntityLiving();
                    if (entityLivingBase.isPotionActive(getPotion())) {
                        entityLiving.setAttackTarget(null);
                    }
                }
            }
        }
    }

}
