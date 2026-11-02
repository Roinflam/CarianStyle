package pers.roinflam.carianstyle.mc112.util;

import net.minecraft.entity.Entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * 读实体的剩余燃烧时间
 * <p>
 * 1.20.1 有 {@code Entity.getRemainingFireTicks()}；1.12.2 只有私有字段 {@code fire}（{@code field_190534_ay}），
 * 没有 getter，只能反射读。几个火焰类附魔和猩红腐败都要用，集中放这里，调用处写法和 1.20.1 对得上。
 * </p>
 */
public final class EntityFireAccess {

    /** {@code Entity.fire}；找不到时为 null，退回按 {@code isBurning()} 估 */
    @Nullable
    private static final Field FIRE = Reflect.fieldOrNull(Entity.class, "fire", "field_190534_ay");

    private EntityFireAccess() {
    }

    /**
     * 剩余燃烧 tick，对应 1.20.1 的 {@code getRemainingFireTicks()}。
     *
     * @param entity 实体
     * @return 剩余燃烧 tick；字段读不到时着火算 1、没着火算 0
     */
    public static int getRemainingFireTicks(@Nonnull Entity entity) {
        if (FIRE != null) {
            try {
                return FIRE.getInt(entity);
            } catch (IllegalAccessException ignored) {
                // 退回下面的估算
            }
        }
        return entity.isBurning() ? 1 : 0;
    }
}
