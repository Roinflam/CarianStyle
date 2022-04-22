package pers.roinflam.kaliastyle.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

public class EntityLivingUtil {

    public static void setJumped(EntityLivingBase entityLivingBase) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, entityLivingBase, 10, "jumpTicks");
        } catch (Exception exception) {
            ObfuscationReflectionHelper.setPrivateValue(EntityLivingBase.class, entityLivingBase, 10, "field_70773_bE");
        }
    }

}
