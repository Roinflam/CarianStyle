package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

@Mod.EventBusSubscriber
public class MobEffectSleep extends IconBase {

    public MobEffectSleep(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "sleep");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "5d59080b-eda9-f5b7-1b3c-51568e5b6682", -1, 2);
    }

    public static Potion getPotion() {
        return CarianStylePotion.SLEEP;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingSetAttackTarget(LivingSetAttackTargetEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getTarget() != null) {
                if (evt.getEntityLiving() instanceof EntityLiving) {
                    EntityLiving entityLiving = (EntityLiving) evt.getEntityLiving();
                    if (entityLiving.isPotionActive(getPotion())) {
                        entityLiving.setAttackTarget(null);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                if (attacker.isPotionActive(getPotion())) {
                    evt.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (hurter.isPotionActive(getPotion())) {
                    evt.setAmount((float) (evt.getAmount() * 2 + evt.getAmount() * hurter.getActivePotionEffect(getPotion()).getAmplifier() * 0.25));
                    hurter.removePotionEffect(getPotion());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            EntityLivingBase entityLiving = evt.getEntityLiving();
            if (entityLiving.isPotionActive(getPotion())) {
                EntityLivingUtil.setJumped(entityLiving);
            }
        }
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        if (entityLivingBaseIn instanceof EntityLiving) {
            EntityLiving entityLiving = (EntityLiving) entityLivingBaseIn;
            entityLiving.setAttackTarget(null);
        }
        entityLivingBaseIn.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 21));
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/sleep.png");
    }
}
