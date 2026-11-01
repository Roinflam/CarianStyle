package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.BossInfoServer;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOSS 判断
 * <p>
 * 原版的凋灵、末影龙 {@code isNonBoss()} 返回 false；模组 BOSS 多半没覆写它，
 * 但都会在字段里放一个 {@link BossInfoServer} 显示血条，所以再看一眼字段里有没有服务端 BOSS 血条
 * （1.12.2 的 BossInfoServer 读不到「是否显示」，挂着就算）。
 * 对 BOSS 打折的持续伤害（命定之死、死亡刀刃）按这里判断。
 * </p>
 */
public class BossUtil {

    /** 每个类里类型是 BossInfoServer 的字段（含父类），按类缓存 */
    private static final Map<Class<?>, List<Field>> BOSS_INFO_FIELDS = new ConcurrentHashMap<>();

    private BossUtil() {
    }

    /**
     * 是不是 BOSS
     *
     * @param entity 生物
     * @return 是返回 true
     */
    public static boolean isBoss(@Nonnull EntityLivingBase entity) {
        if (!entity.isNonBoss()) {
            return true;
        }
        for (Field field : BOSS_INFO_FIELDS.computeIfAbsent(entity.getClass(), BossUtil::findBossInfoFields)) {
            try {
                Object value = field.get(entity);
                if (value instanceof BossInfoServer) {
                    return true;
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                // 读不到就当没有
            }
        }
        return false;
    }

    @Nonnull
    private static List<Field> findBossInfoFields(@Nonnull Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Field field : c.getDeclaredFields()) {
                    if (BossInfoServer.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
            } catch (LinkageError | RuntimeException ignored) {
                // 某些模组类字段类型在服务端加载不了，跳过
            }
        }
        return fields;
    }
}
