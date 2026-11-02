package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.BossInfoServer;
import pers.roinflam.carianstyle.mc112.util.Reflect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 判断一个生物算不算 BOSS（给「对 BOSS 效果减半」这类数值用）。
 * <p>
 * 1.20.1 依次看实体标签 {@code carianstyle:not_bosses}、{@code forge:bosses} / {@code carianstyle:bosses}、
 * 身上有没有正在显示的服务端 BOSS 血条。1.12.2 版没有实体标签，换成能表达的部分：
 * </p>
 * <ol>
 *     <li>1.20.1 的 {@code not_bosses} 默认是空表，这里不对应；</li>
 *     <li>{@code !entity.isNonBoss()}：末影龙、凋灵，以及覆写了它的模组生物（对应 1.20.1 的 BOSS 标签）；</li>
 *     <li>身上有没有<b>正在显示</b>的服务端 BOSS 血条（{@link BossInfoServer} 类型的实例字段，含父类）。
 *     兜住没覆写 {@code isNonBoss} 的模组——它们的 BOSS 大多挂着血条。</li>
 * </ol>
 * <p>
 * 血条要看<b>可见</b>，不能只看字段在不在：有的 NPC 模组每个 NPC 都带一条 BOSS 血条，默认隐藏，
 * 只有服主在 NPC 设置里打开显示（或设成战斗时显示且正在战斗）才可见，这时它确实是被当 BOSS 用的。
 * 1.12.2 版：{@code BossInfoServer.visible} 是私有字段、没有 getter，经 {@link Reflect} 读。
 * </p>
 * <p>
 * 只在服务端用。反射拿不到的字段直接跳过，不影响前面的判断。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
public final class BossUtil {

    /** {@code BossInfoServer.visible}；拿不到时只要有血条字段就算（第一期的口径） */
    @Nullable
    private static final Field VISIBLE = Reflect.fieldOrNull(BossInfoServer.class, "visible", "field_186764_j");

    /** 每个实体类里（含父类）类型为 BossInfoServer 的实例字段 */
    private static final ClassValue<List<Field>> BOSS_EVENT_FIELDS = new ClassValue<List<Field>>() {
        @Override
        protected List<Field> computeValue(@Nonnull Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null && EntityLivingBase.class.isAssignableFrom(current);
                 current = current.getSuperclass()) {
                Field[] declared;
                try {
                    declared = current.getDeclaredFields();
                } catch (LinkageError | RuntimeException e) {
                    // 字段类型引用了没装的模组类之类，整个类跳过
                    continue;
                }
                for (Field field : declared) {
                    if (Modifier.isStatic(field.getModifiers())
                            || !BossInfoServer.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (LinkageError | RuntimeException ignored) {
                        // 拿不到就不看这个字段
                    }
                }
            }
            return Collections.unmodifiableList(fields);
        }
    };

    private BossUtil() {
    }

    /**
     * 是否算 BOSS
     *
     * @param entity 生物
     * @return 算 BOSS 返回 true
     */
    public static boolean isBoss(@Nonnull EntityLivingBase entity) {
        if (!entity.isNonBoss()) {
            return true;
        }
        return hasVisibleBossBar(entity);
    }

    /**
     * 身上有没有正在显示的服务端 BOSS 血条
     *
     * @param entity 生物
     * @return 有返回 true
     */
    public static boolean hasVisibleBossBar(@Nonnull EntityLivingBase entity) {
        for (Field field : BOSS_EVENT_FIELDS.get(entity.getClass())) {
            try {
                Object value = field.get(entity);
                if (value instanceof BossInfoServer && isVisible((BossInfoServer) value)) {
                    return true;
                }
            } catch (IllegalAccessException | LinkageError | RuntimeException ignored) {
                // 同上
            }
        }
        return false;
    }

    private static boolean isVisible(@Nonnull BossInfoServer bossInfo) throws IllegalAccessException {
        return VISIBLE == null || VISIBLE.getBoolean(bossInfo);
    }
}
