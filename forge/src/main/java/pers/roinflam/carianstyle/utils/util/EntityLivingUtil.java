package pers.roinflam.carianstyle.utils.util;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.ReflectionCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体生物工具类
 * Entity Living Utility Class
 *
 * 提供跳跃控制、物品使用加速、真伤扣血等功能
 * Provides jump control, item use acceleration, true damage, etc.
 */
public class EntityLivingUtil {

    // ========== 真伤系统的缓存 ==========

    /**
     * 缓存：实体类 -> 血量处理信息
     */
    private static final Map<Class<?>, HealthFieldInfo> HEALTH_FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 血量处理方式枚举
     */
    private enum DamageMethod {
        /** 普通setHealth方法有效 */
        SET_HEALTH,
        /** 需要使用原版DATA_HEALTH_ID字段（预留） */
        VANILLA_FIELD,
        /** 需要使用暴力查找的字段 */
        CUSTOM_FIELD
    }

    /**
     * 血量字段信息
     */
    private static class HealthFieldInfo {
        final DamageMethod method;
        final EntityDataAccessor<?> accessor;
        final boolean isFloat;

        /** 构造函数 - SET_HEALTH方式 */
        HealthFieldInfo() {
            this.method = DamageMethod.SET_HEALTH;
            this.accessor = null;
            this.isFloat = false;
        }

        /** 构造函数 - VANILLA_FIELD或CUSTOM_FIELD方式 */
        HealthFieldInfo(DamageMethod method, EntityDataAccessor<?> accessor, boolean isFloat) {
            this.method = method;
            this.accessor = accessor;
            this.isFloat = isFloat;
        }
    }

    // ========== 跳跃 / 物品使用 ==========

    /**
     * 设置实体已跳跃状态（阻止连续跳跃）
     *
     * @param livingEntity 生物实体
     */
    public static void setJumped(@Nonnull LivingEntity livingEntity) {
        ReflectionCache.setJumpTicks(livingEntity, 10);
    }

    /**
     * 加速物品使用进度
     *
     * @param livingEntity 生物实体
     * @param ticks        要加速的tick数
     */
    public static void accelerateItemUse(@Nonnull LivingEntity livingEntity, int ticks) {
        ReflectionCache.reduceUseItemRemaining(livingEntity, ticks);
    }

    /**
     * 更新手持物品使用状态
     *
     * @param livingEntity 生物实体
     */
    public static void updateHeld(@Nonnull LivingEntity livingEntity) {
        ReflectionCache.invokeUpdatingUsingItem(livingEntity);
    }

    /**
     * 强制杀死实体
     *
     * @param livingEntity 要杀死的实体
     * @param damageSource 伤害源
     */
    public static void kill(@Nullable LivingEntity livingEntity, @Nonnull DamageSource damageSource) {
        if (livingEntity != null && livingEntity.isAlive()) {
            damageHealthDirectly(livingEntity, livingEntity.getHealth());
            livingEntity.die(damageSource);
        }
    }

    // ========== 真伤系统 ==========

    /**
     * 真伤扣血 - 三步优化策略
     *
     * 步骤1：优先尝试setHealth，检查是否生效
     * 步骤2：如果setHealth无效，暴力查找SynchedEntityData中真实血量字段
     * 步骤3：所有方法失败则降级回setHealth
     *
     * 结果按实体类缓存，同类实体第二次起直接走缓存
     *
     * @param entity 目标实体
     * @param damage 伤害值
     */
    @SuppressWarnings("unchecked")
    public static void damageHealthDirectly(LivingEntity entity, float damage) {
        if (entity.isDeadOrDying()) {
            return;
        }

        // 计算目标血量
        float currentHealth = entity.getHealth();
        float targetHealth = Math.max(0.0F, currentHealth - damage);

        // 配置关闭时直接用setHealth
        if (!ConfigLoader.enableTrueDamage) {
            entity.setHealth(targetHealth);
            return;
        }

        // 检查缓存
        HealthFieldInfo cachedInfo = HEALTH_FIELD_CACHE.get(entity.getClass());

        if (cachedInfo != null) {
            applyDamageWithCachedInfo(entity, targetHealth, cachedInfo);
            return;
        }

        // 无缓存，开始发现流程
        HealthFieldInfo discoveredInfo = discoverHealthHandlingMethod(entity, currentHealth, targetHealth);
        HEALTH_FIELD_CACHE.put(entity.getClass(), discoveredInfo);
        applyDamageWithCachedInfo(entity, targetHealth, discoveredInfo);
    }

    /**
     * 真伤扣血（带DamageSource，血量归零时触发die）
     *
     * @param entity 目标实体
     * @param damage 伤害值
     * @param source 伤害源
     */
    public static void damageHealthDirectly(@Nonnull LivingEntity entity, float damage, @Nonnull DamageSource source) {
        damageHealthDirectly(entity, damage);

        if (entity.getHealth() <= 0.0F && !entity.isDeadOrDying()) {
            entity.die(source);
        }
    }

    /**
     * 使用缓存的信息应用伤害
     *
     * @param entity       目标实体
     * @param targetHealth 目标血量
     * @param info         缓存的血量处理信息
     */
    @SuppressWarnings("unchecked")
    private static void applyDamageWithCachedInfo(LivingEntity entity, float targetHealth, HealthFieldInfo info) {
        try {
            switch (info.method) {
                case SET_HEALTH:
                    entity.setHealth(targetHealth);
                    break;

                case VANILLA_FIELD:
                case CUSTOM_FIELD:
                    SynchedEntityData entityData = entity.getEntityData();
                    EntityDataAccessor accessor = info.accessor;

                    if (info.isFloat) {
                        entityData.set(accessor, targetHealth);
                    } else {
                        entityData.set(accessor, (double) targetHealth);
                    }
                    break;
            }
        } catch (Exception e) {
            LogUtil.error("应用伤害失败: " + entity.getName().getString() + " (方式: " + info.method + ")", e);
            // 降级处理
            entity.setHealth(targetHealth);
        }
    }

    /**
     * 三步测试：发现实体的血量处理方式
     *
     * @param entity        实体
     * @param currentHealth 当前血量
     * @param targetHealth  目标血量
     * @return 血量处理信息
     */
    private static HealthFieldInfo discoverHealthHandlingMethod(
            LivingEntity entity, float currentHealth, float targetHealth) {

        String entityName = entity.getClass().getSimpleName();

        // 步骤1：测试setHealth
        LogUtil.debug("[真伤系统] 步骤1：测试 " + entityName + " 的 setHealth 方法");

        try {
            entity.setHealth(targetHealth);
            float actualHealth = entity.getHealth();

            if (Math.abs(actualHealth - targetHealth) < 0.01f) {
                LogUtil.info("✓ [真伤系统] " + entityName + " 使用 SET_HEALTH 方式");
                return new HealthFieldInfo();
            }

            // setHealth无效，恢复血量
            entity.setHealth(currentHealth);
            LogUtil.debug("[真伤系统] " + entityName + " 的 setHealth 无效，进入步骤2");
        } catch (Exception e) {
            LogUtil.error("[真伤系统] " + entityName + " 测试 setHealth 时出错", e);
        }

        // 步骤2：暴力查找
        LogUtil.debug("[真伤系统] 步骤2：暴力查找 " + entityName + " 的真实血量字段");

        HealthFieldInfo customFieldInfo = bruteForceFindHealthField(entity, currentHealth);

        if (customFieldInfo != null && customFieldInfo.method == DamageMethod.CUSTOM_FIELD) {
            LogUtil.info("✓ [真伤系统] " + entityName + " 使用 CUSTOM_FIELD 方式");
            return customFieldInfo;
        }

        // 步骤3：所有方法失败，降级
        LogUtil.warn("[真伤系统] " + entityName + " 所有方法都失败，降级为 SET_HEALTH");
        return new HealthFieldInfo();
    }

    /**
     * 暴力查找实体真正的血量字段
     *
     * 反射拿到SynchedEntityData内部的itemsById映射，
     * 遍历所有Float/Double类型字段，匹配当前血量值，
     * 再通过临时赋值验证getHealth()是否同步，确认后缓存该accessor
     *
     * @param entity        实体
     * @param currentHealth 当前血量
     * @return 血量处理信息，失败返回null
     */
    @SuppressWarnings("unchecked")
    private static HealthFieldInfo bruteForceFindHealthField(LivingEntity entity, float currentHealth) {
        try {
            SynchedEntityData entityData = entity.getEntityData();

            // 反射获取itemsById字段
            Field itemsByIdField = ObfuscationReflectionHelper.findField(
                    SynchedEntityData.class,
                    "f_135345_"
            );
            itemsByIdField.setAccessible(true);
            Map<Integer, SynchedEntityData.DataItem<?>> itemsById =
                    (Map<Integer, SynchedEntityData.DataItem<?>>) itemsByIdField.get(entityData);

            for (SynchedEntityData.DataItem<?> item : itemsById.values()) {
                Object value = item.getValue();

                // 只检查Float和Double类型
                if (!(value instanceof Float || value instanceof Double)) {
                    continue;
                }

                float fieldValue = value instanceof Float ? (Float) value : ((Double) value).floatValue();

                // 值匹配当前血量
                if (Math.abs(fieldValue - currentHealth) < 0.01f) {
                    float testValue = currentHealth - 0.001f;
                    Object originalValue = value;
                    boolean isFloat = value instanceof Float;

                    try {
                        // 临时修改，验证getHealth()是否同步
                        SynchedEntityData.DataItem dataItem = item;
                        if (isFloat) {
                            dataItem.setValue(testValue);
                        } else {
                            dataItem.setValue((double) testValue);
                        }

                        float newHealthValue = entity.getHealth();
                        boolean matched = Math.abs(newHealthValue - testValue) < 0.01f;

                        // 恢复原值
                        dataItem.setValue(originalValue);

                        if (matched) {
                            return new HealthFieldInfo(DamageMethod.CUSTOM_FIELD, item.getAccessor(), isFloat);
                        }
                    } catch (Exception e) {
                        // 恢复原值后继续
                        try {
                            ((SynchedEntityData.DataItem) item).setValue(originalValue);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

        } catch (Exception e) {
            LogUtil.error("暴力查找血量字段失败: " + entity.getName().getString(), e);
        }

        return null;
    }

    // ========== 调试工具 ==========

    /**
     * 清空血量处理缓存（用于调试）
     */
    public static void clearHealthCache() {
        HEALTH_FIELD_CACHE.clear();
        LogUtil.info("[真伤系统] 已清空血量处理缓存");
    }

    /**
     * 获取缓存统计信息（用于调试）
     *
     * @return 统计信息字符串
     */
    public static String getCacheStats() {
        int setHealthCount = 0;
        int vanillaFieldCount = 0;
        int customFieldCount = 0;

        for (HealthFieldInfo info : HEALTH_FIELD_CACHE.values()) {
            switch (info.method) {
                case SET_HEALTH -> setHealthCount++;
                case VANILLA_FIELD -> vanillaFieldCount++;
                case CUSTOM_FIELD -> customFieldCount++;
            }
        }

        return String.format(
                "[真伤系统缓存] 总计: %d | SET_HEALTH: %d | VANILLA_FIELD: %d | CUSTOM_FIELD: %d",
                HEALTH_FIELD_CACHE.size(), setHealthCount, vanillaFieldCount, customFieldCount
        );
    }
}