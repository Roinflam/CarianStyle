package pers.roinflam.carianstyle.utils.util;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
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

    // ========== 真伤系统的缓存 / True Damage System Cache ==========

    /**
     * 缓存：实体类 -> 血量处理信息
     * Cache: Entity Class -> Health Handling Info
     */
    private static final Map<Class<?>, HealthFieldInfo> HEALTH_FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 血量处理方式枚举
     * Health Handling Method Enum
     */
    private enum DamageMethod {
        /**
         * 普通setHealth方法有效
         * Standard setHealth method works
         */
        SET_HEALTH,

        /**
         * 需要使用原版DATA_HEALTH_ID字段
         * Needs to use vanilla DATA_HEALTH_ID field
         */
        VANILLA_FIELD,

        /**
         * 需要使用自定义查找的字段
         * Needs to use custom-found field
         */
        CUSTOM_FIELD
    }

    /**
     * 血量字段信息
     * Health Field Information
     */
    private static class HealthFieldInfo {
        final DamageMethod method;
        final EntityDataAccessor<?> accessor;
        final boolean isFloat;

        /**
         * 构造函数 - SET_HEALTH方式
         * Constructor for SET_HEALTH method
         */
        HealthFieldInfo() {
            this.method = DamageMethod.SET_HEALTH;
            this.accessor = null;
            this.isFloat = false;
        }

        /**
         * 构造函数 - VANILLA_FIELD或CUSTOM_FIELD方式
         * Constructor for VANILLA_FIELD or CUSTOM_FIELD method
         */
        HealthFieldInfo(DamageMethod method, EntityDataAccessor<?> accessor, boolean isFloat) {
            this.method = method;
            this.accessor = accessor;
            this.isFloat = isFloat;
        }
    }

    /**
     * 设置实体已跳跃状态（阻止连续跳跃）
     * Set entity jumped state (prevent continuous jumping)
     *
     * @param livingEntity 生物实体 / Living entity
     */
    public static void setJumped(@Nonnull LivingEntity livingEntity) {
        ReflectionCache.setJumpTicks(livingEntity, 10);
    }

    /**
     * 加速物品使用进度
     * Accelerate item use progress
     *
     * @param livingEntity 生物实体 / Living entity
     * @param ticks        要加速的tick数 / Ticks to accelerate
     */
    public static void accelerateItemUse(@Nonnull LivingEntity livingEntity, int ticks) {
        ReflectionCache.reduceUseItemRemaining(livingEntity, ticks);
    }

    /**
     * 更新手持物品使用状态
     * Update held item use state
     *
     * @param livingEntity 生物实体 / Living entity
     */
    public static void updateHeld(@Nonnull LivingEntity livingEntity) {
        ReflectionCache.invokeUpdatingUsingItem(livingEntity);
    }

    /**
     * 强制杀死实体
     * Force kill entity
     *
     * @param livingEntity 要杀死的实体 / Entity to kill
     * @param damageSource 伤害源 / Damage source
     */
    public static void kill(@Nullable LivingEntity livingEntity, @Nonnull DamageSource damageSource) {
        if (livingEntity != null && livingEntity.isAlive()) {
            damageHealthDirectly(livingEntity, livingEntity.getHealth());
            livingEntity.die(damageSource);
        }
    }

    /**
     * 真伤扣血 - 三步优化策略
     * True damage - Three-step optimization strategy
     *
     * 步骤1：优先尝试setHealth，检查是否生效
     * Step 1: Try setHealth first, check if effective
     *
     * 步骤2：如果原版字段也无效，暴力查找所有字段
     * Step 2: If vanilla field fails, brute-force search all fields
     *
     * @param entity 目标实体 / Target entity
     * @param damage 伤害值 / Damage amount
     */
    @SuppressWarnings("unchecked")
    public static void damageHealthDirectly(LivingEntity entity, float damage) {
        if (entity.isDeadOrDying()) {
            return;
        }

//        // 检查配置开关 / Check config toggle
//        if (!ModConfig.KUVA_LICH.enableTrueDamage.get()) {
//            float newHealth = Math.max(0.0F, entity.getHealth() - damage);
//            entity.setHealth(newHealth);
//            return;
//        }

        // 计算目标血量 / Calculate target health
        float currentHealth = entity.getHealth();
        float targetHealth = Math.max(0.0F, currentHealth - damage);

        // 检查缓存 / Check cache
        HealthFieldInfo cachedInfo = HEALTH_FIELD_CACHE.get(entity.getClass());

        if (cachedInfo != null) {
            // 有缓存，直接使用缓存的方式 / Cache exists, use cached method
            applyDamageWithCachedInfo(entity, targetHealth, cachedInfo);
            return;
        }

        // 无缓存，开始三步测试 / No cache, start three-step testing
        HealthFieldInfo discoveredInfo = discoverHealthHandlingMethod(entity, currentHealth, targetHealth);

        // 缓存结果 / Cache result
        HEALTH_FIELD_CACHE.put(entity.getClass(), discoveredInfo);

        // 应用伤害 / Apply damage
        applyDamageWithCachedInfo(entity, targetHealth, discoveredInfo);
    }

    /**
     * 真伤扣血（带DamageSource）
     * True damage with DamageSource
     */
    public static void damageHealthDirectly(@Nonnull LivingEntity entity, float damage, @Nonnull DamageSource source) {
        damageHealthDirectly(entity, damage);

        if (entity.getHealth() <= 0.0F && !entity.isDeadOrDying()) {
            entity.die(source);
        }
    }

    /**
     * 使用缓存的信息应用伤害
     * Apply damage using cached information
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
            // 降级处理 / Fallback
            entity.setHealth(targetHealth);
        }
    }

    /**
     * 三步测试：发现实体的血量处理方式
     * Three-step testing: Discover entity's health handling method
     *
     * @param entity        实体 / Entity
     * @param currentHealth 当前血量 / Current health
     * @param targetHealth  目标血量 / Target health
     * @return 血量处理信息 / Health handling info
     */
    @SuppressWarnings("unchecked")
    private static HealthFieldInfo discoverHealthHandlingMethod(
            LivingEntity entity, float currentHealth, float targetHealth) {

        String entityName = entity.getClass().getSimpleName();

        // ========== 步骤1：测试setHealth / Step 1: Test setHealth ==========
        LogUtil.debug("[真伤系统] 步骤1：测试 " + entityName + " 的 setHealth 方法");

        try {
            entity.setHealth(targetHealth);
            float actualHealth = entity.getHealth();

            if (Math.abs(actualHealth - targetHealth) < 0.01f) {
                LogUtil.info("✓ [真伤系统] " + entityName + " 使用 SET_HEALTH 方式");
                return new HealthFieldInfo();
            }

            // setHealth无效，恢复血量 / setHealth failed, restore health
            entity.setHealth(currentHealth);
            LogUtil.debug("[真伤系统] " + entityName + " 的 setHealth 无效，进入步骤2");
        } catch (Exception e) {
            LogUtil.error("[真伤系统] " + entityName + " 测试 setHealth 时出错", e);
        }

        // ========== 步骤2：暴力查找 / Step 2: Brute-force search ==========
        LogUtil.debug("[真伤系统] 步骤2：暴力查找 " + entityName + " 的真实血量字段");

        HealthFieldInfo customFieldInfo = bruteForceFindHealthField(entity, currentHealth);

        if (customFieldInfo != null && customFieldInfo.method == DamageMethod.CUSTOM_FIELD) {
            LogUtil.info("✓ [真伤系统] " + entityName + " 使用 CUSTOM_FIELD 方式");
            return customFieldInfo;
        }

        // 所有方法都失败，降级为SET_HEALTH / All methods failed, fallback to SET_HEALTH
        LogUtil.warn("[真伤系统] " + entityName + " 所有方法都失败，降级为 SET_HEALTH");
        return new HealthFieldInfo();
    }

    /**
     * 暴力查找实体真正的血量字段
     * Brute-force search for entity's real health field
     */
    @SuppressWarnings("unchecked")
    private static HealthFieldInfo bruteForceFindHealthField(LivingEntity entity, float currentHealth) {
        try {
            SynchedEntityData entityData = entity.getEntityData();

            // 反射获取所有EntityData字段 / Get all EntityData fields via reflection
            Field itemsByIdField = ObfuscationReflectionHelper.findField(
                    SynchedEntityData.class,
                    "f_135345_"
            );
            itemsByIdField.setAccessible(true);
            Map<Integer, SynchedEntityData.DataItem<?>> itemsById =
                    (Map<Integer, SynchedEntityData.DataItem<?>>) itemsByIdField.get(entityData);

            // 遍历所有字段 / Iterate all fields
            for (SynchedEntityData.DataItem<?> item : itemsById.values()) {
                Object value = item.getValue();

                // 只检查Float和Double类型 / Only check Float and Double types
                if (!(value instanceof Float || value instanceof Double)) {
                    continue;
                }

                float fieldValue = value instanceof Float ? (Float) value : ((Double) value).floatValue();

                // 值匹配当前血量？/ Value matches current health?
                if (Math.abs(fieldValue - currentHealth) < 0.01f) {
                    // 验证测试 / Validation test
                    float testValue = currentHealth - 0.001f;
                    Object originalValue = value;
                    boolean isFloat = value instanceof Float;

                    try {
                        // 临时修改 / Temporary modification
                        SynchedEntityData.DataItem dataItem = item;
                        if (isFloat) {
                            dataItem.setValue(testValue);
                        } else {
                            dataItem.setValue((double) testValue);
                        }

                        // 检查getHealth()是否同步变化 / Check if getHealth() changes
                        float newHealthValue = entity.getHealth();
                        boolean matched = Math.abs(newHealthValue - testValue) < 0.01f;

                        // 恢复原值 / Restore original value
                        dataItem.setValue(originalValue);

                        // 如果匹配，说明找到了真正的血量字段 / If matched, found the real field
                        if (matched) {
                            return new HealthFieldInfo(DamageMethod.CUSTOM_FIELD, item.getAccessor(), isFloat);
                        }
                    } catch (Exception e) {
                        // 恢复原值后继续 / Restore and continue
                        try {
                            SynchedEntityData.DataItem dataItem = item;
                            dataItem.setValue(originalValue);
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

    /**
     * 清空血量处理缓存（用于调试）
     * Clear health handling cache (for debugging)
     */
    public static void clearHealthCache() {
        HEALTH_FIELD_CACHE.clear();
        LogUtil.info("[真伤系统] 已清空血量处理缓存");
    }

    /**
     * 获取缓存统计信息（用于调试）
     * Get cache statistics (for debugging)
     */
    public static String getCacheStats() {
        int setHealthCount = 0;
        int vanillaFieldCount = 0;
        int customFieldCount = 0;

        for (HealthFieldInfo info : HEALTH_FIELD_CACHE.values()) {
            switch (info.method) {
                case SET_HEALTH:
                    setHealthCount++;
                    break;
                case VANILLA_FIELD:
                    vanillaFieldCount++;
                    break;
                case CUSTOM_FIELD:
                    customFieldCount++;
                    break;
            }
        }

        return String.format(
                "[真伤系统缓存] 总计: %d | SET_HEALTH: %d | VANILLA_FIELD: %d | CUSTOM_FIELD: %d",
                HEALTH_FIELD_CACHE.size(), setHealthCount, vanillaFieldCount, customFieldCount
        );
    }
}