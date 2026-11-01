package pers.roinflam.carianstyle.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.source.NewDamageSource;

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

    /** 代价付不起时致死一击的伤害（倍最大生命） */
    private static final float LETHAL_DAMAGE_MULTIPLIER = 10.0F;

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
     * <p>
     * 血量清零后直接走死亡流程，不经过伤害管线：抗性提升、护甲、无敌时间、不死图腾都拦不住。
     * 以前先用 {@code attackEntityFrom(来源, 最大生命×100)} 试一下再补 {@code onDeath}，
     * 伤害被抗性提升 V 减成 0 时会以「还活着」的状态先发一次死亡事件、再把血量设 0；
     * 复活类监听取消了死亡事件也照样设 0，实体变成没有死亡事件的半死状态。
     * 现在照 setHealth → 血量真的归零才 onDeath；血量没归零（setHealth 被模组生物改写）时
     * 退回用虚空伤害打一下，至少让它走一遍正常死亡。
     * </p>
     *
     * @param entity       实体
     * @param damageSource 伤害来源（死亡消息、击杀归属用）
     */
    public static void kill(@Nullable EntityLivingBase entity, @Nonnull DamageSource damageSource) {
        if (entity == null || entity.isDead || entity.getHealth() <= 0.0F) {
            return;
        }

        entity.setHealth(0.0F);
        if (entity.getHealth() <= 0.0F) {
            entity.onDeath(damageSource);
        } else {
            entity.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
        }
    }

    /**
     * 直接扣血，不走伤害管线
     *
     * @param entity 实体
     * @param damage 扣多少
     */
    public static void damageHealthDirectly(@Nonnull EntityLivingBase entity, float damage) {
        if (entity.isDead || entity.getHealth() <= 0.0F) {
            return;
        }
        entity.setHealth(Math.max(0.0F, entity.getHealth() - damage));
    }

    /**
     * 直接扣血，这一下扣死了就以给定来源走死亡流程
     * <p>
     * 只在「扣之前活着、扣完血量归零」时 onDeath，同一个目标连扣两次不会发两次死亡事件。
     * </p>
     *
     * @param entity 实体
     * @param damage 扣多少
     * @param source 死亡来源
     */
    public static void damageHealthDirectly(@Nonnull EntityLivingBase entity, float damage, @Nonnull DamageSource source) {
        boolean wasAlive = !entity.isDead && entity.getHealth() > 0.0F;
        damageHealthDirectly(entity, damage);

        if (wasAlive && entity.getHealth() <= 0.0F) {
            entity.onDeath(source);
        }
    }

    /**
     * 支付以生命值计的代价（血刃、切腹、鲜血斩击等）
     * <p>
     * 血量够付就直接扣；不够付时走伤害管线正常死亡（出血伤害、10 倍最大生命），
     * 不死图腾挡得住。以前直接 setHealth 到 0 以下会被夹成 0，实体卡在 0 血不死也不活。
     * 回溯期间不付代价，否则这一下会被算进储存伤害。
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
            entity.attackEntityFrom(NewDamageSource.HEMORRHAGE, entity.getMaxHealth() * LETHAL_DAMAGE_MULTIPLIER);
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