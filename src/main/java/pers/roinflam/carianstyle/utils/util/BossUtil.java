package pers.roinflam.carianstyle.utils.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.Tags;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 判断一个生物算不算 BOSS（给「对 BOSS 效果减半」这类数值用）。
 * <p>
 * 依次看：
 * </p>
 * <ol>
 *     <li>实体标签 {@code carianstyle:not_bosses}：服主用数据包排除误判，命中就不算；</li>
 *     <li>实体标签 {@code forge:bosses}（Forge 自带末影龙、凋灵，
 *     大多数 BOSS 模组也往里加）或 {@code carianstyle:bosses}（本模组自带的这份只引用
 *     {@code c:bosses}，服主可用数据包追加）；</li>
 *     <li>身上有没有<b>正在显示</b>的服务端 BOSS 血条（{@link ServerBossEvent} 类型的实例字段，含父类）。
 *     兜住没打标签、或标签放错路径的模组——例如有的模组把标签放在 1.21 才认的 {@code tags/entity_type/}，
 *     在 1.20.1 上不生效，但它的 BOSS 都挂着血条。</li>
 * </ol>
 * <p>
 * 血条要看<b>可见</b>，不能只看字段在不在：有的 NPC 模组每个 NPC 都带一条 BOSS 血条，默认隐藏，
 * 只有服主在 NPC 设置里打开显示（或设成战斗时显示且正在战斗）才可见，这时它确实是被当 BOSS 用的。
 * </p>
 * <p>
 * 只在服务端用。反射拿不到的字段直接跳过，不影响前两条判断。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
public final class BossUtil {

    /** 额外算作 BOSS 的实体（数据包追加） */
    public static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Reference.MOD_ID, "bosses"));

    /** 不算 BOSS 的实体（数据包排除误判，优先级最高） */
    public static final TagKey<EntityType<?>> NOT_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Reference.MOD_ID, "not_bosses"));

    /** 每个实体类里（含父类）类型为 ServerBossEvent 的实例字段 */
    private static final ClassValue<List<Field>> BOSS_EVENT_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(@Nonnull Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null && LivingEntity.class.isAssignableFrom(current);
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
                            || !ServerBossEvent.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    try {
                        if (field.trySetAccessible()) {
                            fields.add(field);
                        }
                    } catch (LinkageError | RuntimeException ignored) {
                        // 拿不到就不看这个字段
                    }
                }
            }
            return List.copyOf(fields);
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
    public static boolean isBoss(@Nonnull LivingEntity entity) {
        EntityType<?> type = entity.getType();
        if (type.is(NOT_BOSSES)) {
            return false;
        }
        if (type.is(Tags.EntityTypes.BOSSES) || type.is(BOSSES)) {
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
    public static boolean hasVisibleBossBar(@Nonnull LivingEntity entity) {
        for (Field field : BOSS_EVENT_FIELDS.get(entity.getClass())) {
            try {
                if (field.get(entity) instanceof ServerBossEvent bossEvent && bossEvent.isVisible()) {
                    return true;
                }
            } catch (IllegalAccessException | LinkageError | RuntimeException ignored) {
                // 同上
            }
        }
        return false;
    }
}
