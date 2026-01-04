package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体生物工具类
 * <p>
 * 提供对EntityLivingBase的常用操作，包括攻击冷却、跳跃控制等
 * 使用缓存反射提升性能
 * </p>
 */
@Mod.EventBusSubscriber
public class EntityLivingUtil {

    /** 当前tick的攻击冷却 */
    private static final Map<UUID, Float> NOW_TICKS_SINCE_LAST_SWING = new ConcurrentHashMap<>();

    /** 上一tick的攻击冷却 */
    private static final Map<UUID, Float> LAST_TICKS_SINCE_LAST_SWING = new ConcurrentHashMap<>();

    // ==================== 缓存的反射对象 ====================

    /** ticksSinceLastSwing字段 */
    private static Field ticksSinceLastSwingField = null;

    /** jumpTicks字段 */
    private static Field jumpTicksField = null;

    /** updateActiveHand方法 */
    private static Method updateActiveHandMethod = null;

    /** 反射初始化标记 */
    private static boolean reflectionInitialized = false;

    /**
     * 初始化反射对象（线程安全）
     */
    private static synchronized void initReflection() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;

        try {
            // ticksSinceLastSwing字段
            try {
                ticksSinceLastSwingField = EntityLivingBase.class.getDeclaredField("ticksSinceLastSwing");
                ticksSinceLastSwingField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                try {
                    ticksSinceLastSwingField = ObfuscationReflectionHelper.findField(
                            EntityLivingBase.class, "field_184617_aD");
                } catch (Exception e1) {
                    LogUtil.debug("无法获取ticksSinceLastSwing字段");
                }
            }

            // jumpTicks字段
            try {
                jumpTicksField = EntityLivingBase.class.getDeclaredField("jumpTicks");
                jumpTicksField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                try {
                    jumpTicksField = ObfuscationReflectionHelper.findField(
                            EntityLivingBase.class, "field_70773_bE");
                } catch (Exception e1) {
                    LogUtil.debug("无法获取jumpTicks字段");
                }
            }

            // updateActiveHand方法
            try {
                updateActiveHandMethod = EntityLivingBase.class.getDeclaredMethod("updateActiveHand");
                updateActiveHandMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                try {
                    // 1.12.2混淆名，返回类型void，无参数
                    updateActiveHandMethod = ReflectionHelper.findMethod(
                            EntityLivingBase.class,
                            "updateActiveHand",
                            "func_184608_ct"
                    );
                } catch (Exception e1) {
                    LogUtil.debug("无法获取updateActiveHand方法");
                }
            }

        } catch (Exception e) {
            LogUtil.error("EntityLivingUtil反射初始化失败", e);
        }
    }

    /**
     * 玩家tick事件：记录攻击冷却
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        UUID uuid = evt.player.getUniqueID();
        LAST_TICKS_SINCE_LAST_SWING.put(uuid, NOW_TICKS_SINCE_LAST_SWING.getOrDefault(uuid, 1f));
        NOW_TICKS_SINCE_LAST_SWING.put(uuid, evt.player.getCooledAttackStrength(0));
    }

    /**
     * 获取玩家上一tick的攻击冷却
     *
     * @param player 玩家
     * @return 攻击冷却（0-1）
     */
    public static float getTicksSinceLastSwing(@Nonnull EntityPlayer player) {
        return LAST_TICKS_SINCE_LAST_SWING.getOrDefault(player.getUniqueID(), 1f);
    }

    /**
     * 设置实体的攻击冷却计时器
     *
     * @param entity              实体
     * @param ticksSinceLastSwing 冷却时间（tick）
     */
    public static void setTicksSinceLastSwing(@Nonnull EntityLivingBase entity, int ticksSinceLastSwing) {
        if (!reflectionInitialized) {
            initReflection();
        }

        if (ticksSinceLastSwingField != null) {
            try {
                ticksSinceLastSwingField.setInt(entity, ticksSinceLastSwing);
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    /**
     * 禁止实体跳跃（设置跳跃冷却）
     *
     * @param entity 实体
     */
    public static void setJumped(@Nonnull EntityLivingBase entity) {
        if (!reflectionInitialized) {
            initReflection();
        }

        if (jumpTicksField != null) {
            try {
                jumpTicksField.setInt(entity, 10);
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    /**
     * 更新实体的手持物品使用状态
     *
     * @param entity 实体
     */
    public static void updateHeld(@Nonnull EntityLivingBase entity) {
        if (!reflectionInitialized) {
            initReflection();
        }

        if (updateActiveHandMethod != null) {
            try {
                updateActiveHandMethod.invoke(entity);
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    /**
     * 强制击杀实体
     *
     * @param entity       实体
     * @param damageSource 伤害来源
     */
    public static void kill(@Nullable EntityLivingBase entity, @Nonnull DamageSource damageSource) {
        if (entity == null || entity.isDead) {
            return;
        }

        entity.attackEntityFrom(damageSource, entity.getMaxHealth() * 100);

        if (entity.isEntityAlive()) {
            entity.onDeath(damageSource);
            entity.setHealth(0);
        }
    }

    /**
     * 清理玩家数据
     *
     * @param uuid 玩家UUID
     */
    public static void cleanup(@Nonnull UUID uuid) {
        NOW_TICKS_SINCE_LAST_SWING.remove(uuid);
        LAST_TICKS_SINCE_LAST_SWING.remove(uuid);
    }
}