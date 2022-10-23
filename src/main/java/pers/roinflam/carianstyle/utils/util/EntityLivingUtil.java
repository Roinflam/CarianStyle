package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EntityLivingUtil {
    private final static HashMap<UUID, Float> NOW_TICKS_SINCE_LAST_SWING_LIST = new HashMap<>();
    private final static HashMap<UUID, Float> LAST_TICKS_SINCE_LAST_SWING_LIST = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (evt.phase.equals(TickEvent.Phase.START)) {
            LAST_TICKS_SINCE_LAST_SWING_LIST.put(evt.player.getUniqueID(), NOW_TICKS_SINCE_LAST_SWING_LIST.getOrDefault(evt.player.getUniqueID(), 1f));
            NOW_TICKS_SINCE_LAST_SWING_LIST.put(evt.player.getUniqueID(), evt.player.getCooledAttackStrength(0));
        }
    }

    public static float getTicksSinceLastSwing(EntityPlayer entityPlayer) {
        return LAST_TICKS_SINCE_LAST_SWING_LIST.getOrDefault(entityPlayer.getUniqueID(), 1f);
    }


    public static void setJumped(EntityLivingBase entityLivingBase) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, entityLivingBase, 10, "jumpTicks");
        } catch (Exception exception) {
            ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, entityLivingBase, 10, "field_70773_bE");
            exception.printStackTrace();
        }
    }

    public static void updateHeld(EntityLivingBase entityLivingBase) {
        try {
            ReflectionHelper.findMethod(EntityLivingBase.class, "updateActiveHand", "func_184608_ct").invoke(entityLivingBase);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public static void kill(EntityLivingBase entityLivingBase, DamageSource damageSource) {
        if (entityLivingBase != null) {
            entityLivingBase.attackEntityFrom(damageSource, entityLivingBase.getMaxHealth());
            if (entityLivingBase.isEntityAlive()) {
                entityLivingBase.onDeath(damageSource);
                entityLivingBase.setHealth(0);
            }
        }
    }

}
