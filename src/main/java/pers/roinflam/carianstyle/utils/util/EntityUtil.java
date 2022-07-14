package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.Entity;

import java.lang.reflect.Field;

public class EntityUtil {

    public static int getFire(Entity entity) {
        try {
            Field field = Entity.class.getDeclaredField("fire");
            field.setAccessible(true);
            return (int) field.get(entity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            try {
                Field field = Entity.class.getDeclaredField("field_190534_ay");
                field.setAccessible(true);
                return (int) field.get(entity);
            } catch (NoSuchFieldException | IllegalAccessException e1) {
                e.printStackTrace();
            }
        }
        return -999;
    }

}
