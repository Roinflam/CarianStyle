package pers.roinflam.kaliastyle.utils.util;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.List;

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

    public static List<Entity> getNearbyEntities(Entity entity, double radius, double height, Predicate<? super Entity> predicate) {
        return getNearbyEntities(entity.world, entity.getPosition(), radius, height, predicate);
    }

    public static List<Entity> getNearbyEntities(World world, BlockPos blockPos, double radius, double height, Predicate<? super Entity> predicate) {
        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(
                blockPos.getX() - radius,
                blockPos.getY() - height,
                blockPos.getZ() - radius,
                blockPos.getX() + radius,
                blockPos.getY() + height,
                blockPos.getZ() + radius
        );
        return world.getEntitiesWithinAABB(Entity.class, axisAlignedBB, predicate);
    }

}
