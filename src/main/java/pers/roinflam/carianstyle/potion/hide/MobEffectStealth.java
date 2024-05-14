package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.NetworkBase;

import javax.annotation.Nonnull;


public class MobEffectStealth extends NetworkBase {

    public MobEffectStealth(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "stealth");
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderPlayer(@Nonnull RenderPlayerEvent.Pre evt) {
        EntityPlayer entityPlayer = evt.getEntityPlayer();
        if (isAction(entityPlayer.getEntityId())) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingSetAttackTarget(@Nonnull LivingSetAttackTargetEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getTarget() != null) {
                if (evt.getEntityLiving() instanceof EntityLiving) {
                    EntityLivingBase entityLivingBase = evt.getTarget();
                    EntityLiving entityLiving = (EntityLiving) evt.getEntityLiving();
                    if (entityLivingBase.isPotionActive(this)) {
                        entityLiving.setAttackTarget(null);
                    }
                }
            }
        }
    }

    @Override
    public int getSerialNumber() {
        return 3;
    }

}
