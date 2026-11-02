package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.mc112.math.Mth;
import pers.roinflam.carianstyle.mc112.util.Reflect;
import pers.roinflam.carianstyle.source.NewDamageSource;
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

    /**
     * 代价致死一击的伤害倍率（× 最大生命）。
     * <p>与出血 / 切腹的致死路径取同一个值：足以压过吸收与百分比减伤，又不会在后续乘算里溢出。</p>
     */
    private static final float LETHAL_DAMAGE_MULTIPLIER = 10.0f;

    /**
     * 血量探测的比较容差（绝对值上限）。
     * <p>只用来吸收浮点误差，探测量必须明显大于它，否则「没变化」也会被判成「对上了」。</p>
     */
    private static final float HEALTH_PROBE_TOLERANCE = 0.01f;

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
        final DataParameter<?> accessor;
        final boolean isFloat;

        /** 构造函数 - SET_HEALTH方式 */
        HealthFieldInfo() {
            this.method = DamageMethod.SET_HEALTH;
            this.accessor = null;
            this.isFloat = false;
        }

        /** 构造函数 - VANILLA_FIELD或CUSTOM_FIELD方式 */
        HealthFieldInfo(DamageMethod method, DataParameter<?> accessor, boolean isFloat) {
            this.method = method;
            this.accessor = accessor;
            this.isFloat = isFloat;
        }
    }

    // ========== 击退 ==========

    /**
     * 水平方向长度平方的下限，对应 1.20.1 {@code Vec3.normalize} 的长度下限 1.0E-4（平方即 1.0E-8）。
     */
    private static final double KNOCKBACK_MIN_RATIO_SQR = 1.0E-8D;

    /**
     * 击退（对应 1.20.1 的 {@code LivingEntity.knockback}）
     * <p>
     * 1.12.2 的 {@code EntityLivingBase.knockBack} 直接用 (x, z) 除以它自己的长度，没有判零：
     * 两个实体水平坐标完全重合（最常见的是骑乘，坐骑和乘客的 x / z 一模一样）时算出 NaN 速度，
     * 生物会卡死在原地、NaN 还会写进存档，骑在上面的玩家随后发 NaN 移动包被服务端踢掉。
     * 原版自己的受击击退在 {@code attackEntityFrom} 里另有随机抖动兜底，模组直接调就没有这层保护。
     * </p>
     * <p>
     * 1.20.1 遇到零向量时归一化得零，只把水平速度减半、在地面上时往上弹一下；强度不大于 0 时什么都不做。
     * 这里方向和强度都正常时照常调 {@code knockBack}（保留实体自己的覆写），否则按 1.20.1 的结果手动处理：
     * 先发一次 {@code LivingKnockBackEvent}，强度不大于 0 就停，击退抗性照 1.12.2 的写法掷骰。
     * </p>
     *
     * @param target   被击退的实体
     * @param attacker 击退来源（事件里用）
     * @param strength 强度
     * @param x        方向 x（会被取反，和原版一样）
     * @param z        方向 z
     */
    public static void knockBack(@Nonnull EntityLivingBase target, @Nullable Entity attacker,
                                 float strength, double x, double z) {
        if (strength > 0.0F && x * x + z * z >= KNOCKBACK_MIN_RATIO_SQR) {
            target.knockBack(attacker, strength, x, z);
            return;
        }
        LivingKnockBackEvent event = ForgeHooks.onLivingKnockBack(target, attacker, strength, x, z);
        if (event.isCanceled()) {
            return;
        }
        float s = event.getStrength();
        double ex = event.getRatioX();
        double ez = event.getRatioZ();
        if (s <= 0.0F) {
            return;
        }
        if (target.getRNG().nextDouble() < target.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).getAttributeValue()) {
            return;
        }
        target.isAirBorne = true;
        target.motionX /= 2.0D;
        target.motionZ /= 2.0D;
        // 监听器可能改了方向，改成非零的照常推
        double lenSqr = ex * ex + ez * ez;
        if (lenSqr >= KNOCKBACK_MIN_RATIO_SQR) {
            double len = Math.sqrt(lenSqr);
            target.motionX -= ex / len * s;
            target.motionZ -= ez / len * s;
        }
        if (target.onGround) {
            target.motionY = Math.min(0.4D, target.motionY / 2.0D + s);
        }
    }

    // ========== 跳跃 / 物品使用 ==========

    /**
     * 设置实体已跳跃状态（阻止连续跳跃）
     *
     * @param livingEntity 生物实体
     */
    public static void setJumped(@Nonnull EntityLivingBase livingEntity) {
        ReflectionCache.setJumpTicks(livingEntity, 10);
    }

    /**
     * 加速物品使用进度
     *
     * @param livingEntity 生物实体
     * @param ticks        要加速的tick数
     */
    public static void accelerateItemUse(@Nonnull EntityLivingBase livingEntity, int ticks) {
        ReflectionCache.reduceUseItemRemaining(livingEntity, ticks);
    }

    /**
     * 更新手持物品使用状态
     *
     * @param livingEntity 生物实体
     */
    public static void updateHeld(@Nonnull EntityLivingBase livingEntity) {
        ReflectionCache.invokeUpdatingUsingItem(livingEntity);
    }

    /**
     * 强制杀死实体
     * <p>
     * 血量真的归零才调 {@code onDeath()}。模组怪可能覆写 {@code setHealth} 或把血量存在别处，
     * 扣血没生效时直接 {@code onDeath()} 会留下「dead=true 但血量大于 0」的半死实体：不走死亡流程，
     * 也打不死、清不掉。这种情况走一次原版 {@code /kill} 的伤害兜底，还活着就算了，不强行标记死亡。
     * </p>
     *
     * @param livingEntity 要杀死的实体
     * @param damageSource 伤害源
     */
    public static void kill(@Nullable EntityLivingBase livingEntity, @Nonnull DamageSource damageSource) {
        if (livingEntity != null && livingEntity.isEntityAlive()) {
            damageHealthDirectly(livingEntity, livingEntity.getHealth());
            if (livingEntity.getHealth() <= 0.0F) {
                livingEntity.onDeath(damageSource);
            } else {
                // 1.12.2 版：没有 genericKill，用虚空伤害（无视护甲、能伤创造模式），和第一期一致
                livingEntity.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
            }
        }
    }

    /**
     * 支付生命值代价（血刃、切腹这类主动技的自损）
     * <p>
     * 血量够付就直接扣，不走伤害管线；不够付时不能把血量写成 0 就算完——那样不触发死亡，
     * 实体停在 0 血、事件和图腾都没走到。这里和出血的致死路径一样走 {@code attackEntityFrom()}，
     * 正常触发死亡事件，不死图腾和免死类附魔都拦得住。
     * </p>
     * <p>
     * 普拉顿桑克斯的回溯期间不扣：回溯无敌，而且这一击的 10 倍最大生命会被算进储存伤害。
     * </p>
     *
     * @param entity 付代价的实体
     * @param cost   代价（生命值）
     */
    public static void payHealthCost(@Nonnull EntityLivingBase entity, float cost) {
        if (EnchantmentTimeReversal.isReversing(entity)) {
            return;
        }
        float remaining = entity.getHealth() - cost;
        if (remaining > 0.0F) {
            entity.setHealth(remaining);
        } else {
            entity.attackEntityFrom(NewDamageSource.hemorrhage(entity.world), entity.getMaxHealth() * LETHAL_DAMAGE_MULTIPLIER);
        }
    }

    // ========== 真伤系统 ==========

    /**
     * 真伤扣血 - 三步优化策略
     *
     * 步骤1：用半血探测setHealth是否生效（探完恢复）
     * 步骤2：如果setHealth无效，暴力查找EntityDataManager中真实血量字段
     * 步骤3：所有方法失败则降级回setHealth
     *
     * 结果按实体类缓存，同类实体第二次起直接走缓存
     *
     * @param entity 目标实体
     * @param damage 伤害值
     */
    @SuppressWarnings("unchecked")
    public static void damageHealthDirectly(EntityLivingBase entity, float damage) {
        if (entity.getHealth() <= 0.0F) {
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

        // 无缓存，开始发现流程（探测完血量已恢复，下面再按真实目标血量扣）
        HealthFieldInfo discoveredInfo = discoverHealthHandlingMethod(entity, currentHealth);
        HEALTH_FIELD_CACHE.put(entity.getClass(), discoveredInfo);
        applyDamageWithCachedInfo(entity, targetHealth, discoveredInfo);
    }

    /**
     * 真伤扣血（带DamageSource，血量归零时触发die）
     * <p>
     * 只有这一次把活实体扣到 0 才 {@code onDeath()}：{@code onDeath()} 开头就发 {@code LivingDeathEvent}，
     * 对已经死了的实体再调一次，事件会重发一遍。
     * </p>
     *
     * @param entity 目标实体
     * @param damage 伤害值
     * @param source 伤害源
     */
    public static void damageHealthDirectly(@Nonnull EntityLivingBase entity, float damage, @Nonnull DamageSource source) {
        boolean wasAlive = entity.isEntityAlive();
        damageHealthDirectly(entity, damage);

        if (wasAlive && entity.getHealth() <= 0.0F) {
            entity.onDeath(source);
        }
    }

    /**
     * 这个实体的真伤扣血是否就是一次普通的 {@code setHealth}
     * <p>
     * 是的话，连续几次 {@link #damageHealthDirectly(EntityLivingBase, float)} 可以先按
     * {@code clamp(max(0, 血量 - 伤害), 0, 最大生命)} 逐次算好，最后只 {@code setHealth} 一次，结果逐位相同。
     * 这个类还没走过发现流程（没有缓存）时返回 false，调用方照旧逐次扣，扣过一次缓存就有了。
     * </p>
     *
     * @param entity 目标实体
     * @return 真伤关闭，或该类已确认用 setHealth 扣血时为 true
     */
    public static boolean isPlainSetHealth(@Nonnull EntityLivingBase entity) {
        if (!ConfigLoader.enableTrueDamage) {
            return true;
        }
        HealthFieldInfo info = HEALTH_FIELD_CACHE.get(entity.getClass());
        return info != null && info.method == DamageMethod.SET_HEALTH;
    }

    /**
     * 使用缓存的信息应用伤害
     *
     * @param entity       目标实体
     * @param targetHealth 目标血量
     * @param info         缓存的血量处理信息
     */
    @SuppressWarnings("unchecked")
    private static void applyDamageWithCachedInfo(EntityLivingBase entity, float targetHealth, HealthFieldInfo info) {
        try {
            switch (info.method) {
                case SET_HEALTH:
                    entity.setHealth(targetHealth);
                    break;

                case VANILLA_FIELD:
                case CUSTOM_FIELD:
                    EntityDataManager entityData = entity.getDataManager();
                    DataParameter accessor = info.accessor;

                    if (info.isFloat) {
                        entityData.set(accessor, targetHealth);
                    } else {
                        entityData.set(accessor, (double) targetHealth);
                    }
                    break;
            }
        } catch (Exception e) {
            LogUtil.error("应用伤害失败: " + entity.getName() + " (方式: " + info.method + ")", e);
            // 降级处理
            entity.setHealth(targetHealth);
        }
    }

    /**
     * 三步测试：发现实体的血量处理方式
     * <p>
     * 探测用当前血量的一半，试完恢复，不用本次真实伤害：
     * </p>
     * <ul>
     *     <li>每 tick 伤害可能小于比较容差，这时 {@code setHealth} 是空操作的实体也会被判成有效；</li>
     *     <li>命定之死先降最大生命，血量会高于上限，{@code setHealth} 被压到上限，
     *         比较时要按压过之后的值算，不然明明生效了却被判成无效。</li>
     * </ul>
     *
     * @param entity        实体
     * @param currentHealth 当前血量
     * @return 血量处理信息
     */
    private static HealthFieldInfo discoverHealthHandlingMethod(EntityLivingBase entity, float currentHealth) {

        String entityName = entity.getClass().getSimpleName();

        // 步骤1：测试setHealth
        LogUtil.debug("[真伤系统] 步骤1：测试 " + entityName + " 的 setHealth 方法");

        try {
            float probeHealth = currentHealth * 0.5f;
            float expectedHealth = Mth.clamp(probeHealth, 0.0F, entity.getMaxHealth());
            float tolerance = probeTolerance(currentHealth);

            entity.setHealth(probeHealth);
            float actualHealth = entity.getHealth();
            entity.setHealth(currentHealth);

            if (Math.abs(actualHealth - expectedHealth) < tolerance
                    && Math.abs(actualHealth - currentHealth) >= tolerance) {
                LogUtil.info("[真伤系统] " + entityName + " 使用 SET_HEALTH 方式");
                return new HealthFieldInfo();
            }

            LogUtil.debug("[真伤系统] " + entityName + " 的 setHealth 无效，进入步骤2");
        } catch (Exception e) {
            LogUtil.error("[真伤系统] " + entityName + " 测试 setHealth 时出错", e);
        }

        // 步骤2：暴力查找
        LogUtil.debug("[真伤系统] 步骤2：暴力查找 " + entityName + " 的真实血量字段");

        HealthFieldInfo customFieldInfo = bruteForceFindHealthField(entity, currentHealth);

        if (customFieldInfo != null && customFieldInfo.method == DamageMethod.CUSTOM_FIELD) {
            LogUtil.info("[真伤系统] " + entityName + " 使用 CUSTOM_FIELD 方式");
            return customFieldInfo;
        }

        // 步骤3：所有方法失败，降级
        LogUtil.warn("[真伤系统] " + entityName + " 所有方法都失败，降级为 SET_HEALTH");
        return new HealthFieldInfo();
    }

    /**
     * 比较血量时的容差。血量极低时跟着缩小，保证它始终明显小于探测量（当前血量的一半）。
     *
     * @param currentHealth 当前血量
     * @return 容差
     */
    private static float probeTolerance(float currentHealth) {
        return Math.min(HEALTH_PROBE_TOLERANCE, currentHealth * 0.1f);
    }

    /**
     * 暴力查找实体真正的血量字段
     *
     * 反射拿到EntityDataManager内部的entries映射，
     * 遍历所有Float/Double类型字段，匹配当前血量值，
     * 再通过临时赋值验证getHealth()是否同步，确认后缓存该accessor
     * <p>
     * 数值恰好等于血量的字段不止血量一个（模组怪身上常有护盾、阶段阈值之类的同步字段），
     * 所以试探量必须明显大于容差：写入后 {@code getHealth()} 要跟着变，恢复后要变回去，两头都对才算。
     * 判错的后果很重——结果按实体类缓存，之后这类怪的真伤全写到错误字段上，再也烧不死。
     * </p>
     *
     * @param entity        实体
     * @param currentHealth 当前血量
     * @return 血量处理信息，失败返回null
     */
    @SuppressWarnings("unchecked")
    private static HealthFieldInfo bruteForceFindHealthField(EntityLivingBase entity, float currentHealth) {
        try {
            EntityDataManager entityData = entity.getDataManager();

            // 反射获取同步数据表（1.12.2：entries，SRG field_187234_c）
            Field itemsByIdField = Reflect.field(EntityDataManager.class, "entries", "field_187234_c");
            Map<Integer, EntityDataManager.DataEntry<?>> itemsById =
                    (Map<Integer, EntityDataManager.DataEntry<?>>) itemsByIdField.get(entityData);

            float tolerance = probeTolerance(currentHealth);

            for (EntityDataManager.DataEntry<?> item : itemsById.values()) {
                Object value = item.getValue();

                // 只检查Float和Double类型
                if (!(value instanceof Float || value instanceof Double)) {
                    continue;
                }

                float fieldValue = value instanceof Float ? (Float) value : ((Double) value).floatValue();

                // 值匹配当前血量
                if (Math.abs(fieldValue - currentHealth) < tolerance) {
                    float testValue = currentHealth * 0.5f;
                    Object originalValue = value;
                    boolean isFloat = value instanceof Float;

                    try {
                        // 临时修改，验证getHealth()是否同步
                        EntityDataManager.DataEntry dataItem = item;
                        if (isFloat) {
                            dataItem.setValue(testValue);
                        } else {
                            dataItem.setValue((double) testValue);
                        }

                        boolean followed = Math.abs(entity.getHealth() - testValue) < tolerance;

                        // 恢复原值
                        dataItem.setValue(originalValue);

                        boolean restored = Math.abs(entity.getHealth() - currentHealth) < tolerance;

                        if (followed && restored) {
                            return new HealthFieldInfo(DamageMethod.CUSTOM_FIELD, item.getKey(), isFloat);
                        }
                    } catch (Exception e) {
                        // 恢复原值后继续
                        try {
                            ((EntityDataManager.DataEntry) item).setValue(originalValue);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

        } catch (Exception e) {
            LogUtil.error("暴力查找血量字段失败: " + entity.getName(), e);
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
                case SET_HEALTH:
                    setHealthCount++;
                    break;
                case VANILLA_FIELD:
                    vanillaFieldCount++;
                    break;
                case CUSTOM_FIELD:
                    customFieldCount++;
                    break;
                default:
                    break;
            }
        }

        return String.format(
                "[真伤系统缓存] 总计: %d | SET_HEALTH: %d | VANILLA_FIELD: %d | CUSTOM_FIELD: %d",
                HEALTH_FIELD_CACHE.size(), setHealthCount, vanillaFieldCount, customFieldCount
        );
    }
}