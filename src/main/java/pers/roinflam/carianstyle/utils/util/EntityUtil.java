package pers.roinflam.carianstyle.utils.util;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;

public class EntityUtil {

    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double range) {
        return getNearbyEntities(clazz, entity, range, range, range, null);
    }

    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double range, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, range, range, range, predicate);
    }

    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double width, double height, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity, width, width, height, predicate);
    }

    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull Entity entity, double x, double z, double y, @Nullable Predicate<? super T> predicate) {
        return getNearbyEntities(clazz, entity.world, entity.getPosition(), x, z, y, predicate);
    }

    @Nonnull
    public static <T extends Entity> List<T> getNearbyEntities(@Nonnull Class<? extends T> clazz, @Nonnull World world, @Nonnull BlockPos blockPos, double x, double z, double y, @Nullable Predicate<? super T> predicate) {
        @Nonnull AxisAlignedBB axisAlignedBB = new AxisAlignedBB(blockPos.getX() - x, blockPos.getY() - y, blockPos.getZ() - z, blockPos.getX() + x, blockPos.getY() + y, blockPos.getZ() + z);
        return world.getEntitiesWithinAABB(clazz, axisAlignedBB, predicate);
    }

    public static int getFire(Entity entity) {
        try {
            @Nonnull Field field = Entity.class.getDeclaredField("fire");
            field.setAccessible(true);
            return (int) field.get(entity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            try {
                @Nonnull Field field = Entity.class.getDeclaredField("field_190534_ay");
                field.setAccessible(true);
                return (int) field.get(entity);
            } catch (NoSuchFieldException | IllegalAccessException e1) {
                e.printStackTrace();
            }
        }
        return -999;
    }

}
