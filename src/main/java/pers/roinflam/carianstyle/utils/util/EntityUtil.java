package pers.roinflam.carianstyle.utils.util;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;

public class EntityUtil {

    public static <T extends Entity> List<T> getNearbyEntities(Class<? extends T> clazz, Entity entity, double range) {
        return getNearbyEntities(clazz, entity, range, range, range, null);
    }

    public static <T extends Entity> List<T> getNearbyEntities(Class<? extends T> clazz, Entity entity, double range, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, range, range, range, predicate);
    }

    public static <T extends Entity> List<T> getNearbyEntities(Class<? extends T> clazz, Entity entity, double width, double height, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, width, width, height, predicate);
    }

    public static <T extends Entity> List<T> getNearbyEntities(Class<? extends T> clazz, Entity entity, double x, double z, double y, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity.world, entity.getPosition(), x, z, y, predicate);
    }

    public static <T extends Entity> List<T> getNearbyEntities(Class<? extends T> clazz, World world, BlockPos blockPos, double x, double z, double y, @Nullable Predicate<? super T> predicate) {
        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z, blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
        return world.getEntitiesWithinAABB(clazz, axisAlignedBB, predicate);
    }

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
