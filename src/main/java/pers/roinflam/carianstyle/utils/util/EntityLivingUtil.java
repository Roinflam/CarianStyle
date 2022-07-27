package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import pers.roinflam.carianstyle.source.NewDamageSource;

import java.lang.reflect.InvocationTargetException;

public class EntityLivingUtil {

    public static void setJumped(EntityLivingBase entityLivingBase) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, entityLivingBase, 10, "jumpTicks");
        } catch (Exception exception) {
            ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, entityLivingBase, 10, "field_70773_bE");
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
                entityLivingBase.onDeath(NewDamageSource.EPILEPSY_FIRE);
                entityLivingBase.setHealth(0);
            }
        }
    }

}
