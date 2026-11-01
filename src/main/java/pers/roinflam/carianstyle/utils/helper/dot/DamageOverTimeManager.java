package pers.roinflam.carianstyle.utils.helper.dot;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 持续伤害（DoT）集中管理器
 * <p>
 * 命定之死、死亡刀刃、黑焰刀刃、癫火等附魔的持续灼烧统一登记到这里，每个服务端 tick 遍历一次，
 * 不再每次命中各开一个 {@code SynchronizationTask}。
 * </p>
 *
 * <h3>扣血与致死</h3>
 * <p>
 * 每 tick 直接扣血、不走伤害管线（一秒 20 次走 {@code attackEntityFrom} 会让受击类附魔各触发 20 次）。
 * 扣完会剩不到两跳的量时直接处决（{@link EntityLivingUtil#kill}），抗性提升、护甲都拦不住，
 * 这是「烧不死」的根本修法：以前各附魔按「抗性和护甲减免之后」的伤害算每跳伤害，
 * 抗性提升 V 下那一下是 0，命定之死又按当前血量的百分比烧，越烧越少，永远到不了致死线。
 * 现在各附魔改成按护甲前的伤害和最大生命计算，致死判定统一在这里做。
 * </p>
 *
 * <h3>自损</h3>
 * <p>
 * 癫火、空癫火对持有者自己的反噬，致死那一下改走 {@code attackEntityFrom}（伤害源副本 + 绝对伤害 + 无视护甲，
 * 伤害 10 倍最大生命），死亡消息、死因、伤害事件链和正常死亡一样；没走到死亡判定（被取消 / 免疫）
 * 时退回直接处决。对敌的持续伤害仍直接处决。
 * </p>
 *
 * <h3>回溯</h3>
 * <p>普拉顿桑克斯的回溯期间不扣血，持续时间照走。</p>
 */
@Mod.EventBusSubscriber
public class DamageOverTimeManager {

    /** 活跃的持续伤害条目 */
    private static final List<DoTEntry> ACTIVE_DOTS = new ArrayList<>(256);

    /** 遍历期间新登记的条目，遍历完再并进去 */
    private static final List<DoTEntry> PENDING = new ArrayList<>(64);

    /** 是否正在遍历 */
    private static boolean iterating = false;

    /** 自损致死一击的伤害（倍最大生命） */
    private static final float SELF_LETHAL_DAMAGE_MULTIPLIER = 10.0F;

    /** 正在吃自损致死一击的实体 */
    @Nullable
    private static EntityLivingBase lethalTarget = null;

    /** 这一击有没有走到死亡判定 */
    private static boolean lethalDeathPosted = false;

    /** 这一击用的伤害源副本 */
    @Nullable
    private static DamageSource lethalSource = null;

    private DamageOverTimeManager() {
    }

    // ==================== 公开API ====================

    /**
     * 每 tick 固定伤害
     *
     * @param target        目标
     * @param damagePerTick 每 tick 伤害
     * @param durationTicks 持续 tick 数
     * @param initialDelay  起始延迟
     * @param source        伤害源（处决时的死因）
     * @param canKill       能否致死
     */
    public static void applyLinear(@Nonnull EntityLivingBase target, float damagePerTick,
                                   int durationTicks, int initialDelay,
                                   @Nonnull DamageSource source, boolean canKill) {
        addEntry(new DoTEntry(target, damagePerTick, durationTicks, initialDelay, source, canKill, null, false));
    }

    /**
     * 持有者对自己的反噬（癫火、空癫火），致死一击走伤害管线
     *
     * @param holder        持有者
     * @param damagePerTick 每 tick 伤害
     * @param durationTicks 持续 tick 数
     * @param initialDelay  起始延迟
     * @param source        伤害源
     */
    public static void applySelfLinear(@Nonnull EntityLivingBase holder, float damagePerTick,
                                       int durationTicks, int initialDelay,
                                       @Nonnull DamageSource source) {
        addEntry(new DoTEntry(holder, damagePerTick, durationTicks, initialDelay, source, true, null, true));
    }

    /**
     * 每 tick 伤害随时间变化
     *
     * @param target            目标
     * @param baseDamagePerTick 基础每 tick 伤害
     * @param durationTicks     持续 tick 数
     * @param initialDelay      起始延迟
     * @param source            伤害源
     * @param canKill           能否致死
     * @param scalingFunction   (基础伤害, 已结算的 tick 数，从 1 起) → 本 tick 伤害
     */
    public static void applyScaling(@Nonnull EntityLivingBase target, float baseDamagePerTick,
                                    int durationTicks, int initialDelay,
                                    @Nonnull DamageSource source, boolean canKill,
                                    @Nonnull BiFunction<Float, Integer, Float> scalingFunction) {
        addEntry(new DoTEntry(target, baseDamagePerTick, durationTicks, initialDelay, source, canKill, scalingFunction, false));
    }

    /**
     * 清掉某个实体身上的全部持续伤害
     *
     * @param target 目标
     */
    public static void clearEntity(@Nonnull EntityLivingBase target) {
        ACTIVE_DOTS.removeIf(dot -> dot.target == target);
        PENDING.removeIf(dot -> dot.target == target);
    }

    /**
     * 活跃条目数
     *
     * @return 条目数
     */
    public static int getActiveCount() {
        return ACTIVE_DOTS.size() + PENDING.size();
    }

    private static void addEntry(@Nonnull DoTEntry entry) {
        if (iterating) {
            PENDING.add(entry);
        } else {
            ACTIVE_DOTS.add(entry);
        }
    }

    // ==================== ServerTick 处理 ====================

    /**
     * 每 tick 处理所有活跃的持续伤害
     *
     * @param event 服务端 tick
     */
    @SubscribeEvent
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!PENDING.isEmpty()) {
            ACTIVE_DOTS.addAll(PENDING);
            PENDING.clear();
        }
        if (ACTIVE_DOTS.isEmpty()) {
            return;
        }

        iterating = true;
        try {
            Iterator<DoTEntry> iterator = ACTIVE_DOTS.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().tick()) {
                    iterator.remove();
                }
            }
        } finally {
            iterating = false;
        }

        // 处决 → 死亡事件 → 附魔逻辑，可能在遍历期间登记了新条目
        if (!PENDING.isEmpty()) {
            ACTIVE_DOTS.addAll(PENDING);
            PENDING.clear();
        }
    }

    // ==================== 自损致死 ====================

    /**
     * 记录自损致命一击有没有走到死亡判定
     * <p>最低优先级 + 接收已取消事件：复活类监听取消了死亡也照样记上，被复活的实体不会再被补刀。</p>
     *
     * @param event 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(@Nonnull LivingDeathEvent event) {
        if (lethalTarget != null && event.getEntityLiving() == lethalTarget) {
            lethalDeathPosted = true;
        }
    }

    /**
     * 当前是不是自损持续伤害的致命一击
     * <p>
     * 这一击是绝对伤害，免伤类附魔管不到；死亡触发类附魔要用这个认出它，别把它当成 /kill 跳过。
     * </p>
     *
     * @param source 死亡事件里的伤害源
     * @return 是这一击的伤害源副本时为 true
     */
    public static boolean isSelfLethalBlow(@Nullable DamageSource source) {
        return source != null && source == lethalSource;
    }

    /**
     * 自损致死一击：走 attackEntityFrom，拦不住时退回直接处决
     *
     * @param target 目标
     * @param source 条目登记时的伤害源
     */
    private static void killThroughDamagePipeline(@Nonnull EntityLivingBase target, @Nonnull DamageSource source) {
        DamageSource lethal = copyAsAbsolute(source);

        lethalTarget = target;
        lethalSource = lethal;
        lethalDeathPosted = false;
        try {
            // 无敌时间内伤害要比上一下高才算数，10 倍最大生命足够越过去
            target.attackEntityFrom(lethal, target.getMaxHealth() * SELF_LETHAL_DAMAGE_MULTIPLIER);
        } finally {
            lethalTarget = null;
            lethalSource = null;
        }

        if (!lethalDeathPosted && target.isEntityAlive()) {
            EntityLivingUtil.kill(target, source);
        }
    }

    /**
     * 复制一份伤害源，挂上无视护甲 + 绝对伤害（不吃抗性提升和保护附魔）
     * <p>1.12.2 的伤害源是可变对象、有的还是全局共享的静态实例，不能直接在原对象上改标记。</p>
     *
     * @param source 原伤害源
     * @return 副本
     */
    @Nonnull
    private static DamageSource copyAsAbsolute(@Nonnull DamageSource source) {
        DamageSource copy;
        if (source instanceof EntityDamageSourceIndirect) {
            copy = new EntityDamageSourceIndirect(source.getDamageType(), source.getImmediateSource(), source.getTrueSource());
        } else if (source instanceof EntityDamageSource) {
            copy = new EntityDamageSource(source.getDamageType(), source.getTrueSource());
        } else {
            copy = new DamageSource(source.getDamageType());
        }
        if (source.isFireDamage()) {
            copy.setFireDamage();
        }
        if (source.isMagicDamage()) {
            copy.setMagicDamage();
        }
        return copy.setDamageBypassesArmor().setDamageIsAbsolute();
    }

    // ==================== 条目 ====================

    private static class DoTEntry {
        final EntityLivingBase target;
        final float baseDamagePerTick;
        final DamageSource source;
        final boolean canKill;
        @Nullable
        final BiFunction<Float, Integer, Float> scalingFunction;
        final boolean selfInflicted;

        int remainingDelay;
        int remainingTicks;
        int elapsedDamageTicks = 0;

        DoTEntry(@Nonnull EntityLivingBase target, float baseDamagePerTick, int durationTicks,
                 int initialDelay, @Nonnull DamageSource source, boolean canKill,
                 @Nullable BiFunction<Float, Integer, Float> scalingFunction, boolean selfInflicted) {
            this.target = target;
            this.baseDamagePerTick = baseDamagePerTick;
            this.remainingTicks = durationTicks;
            this.remainingDelay = initialDelay;
            this.source = source;
            this.canKill = canKill;
            this.scalingFunction = scalingFunction;
            this.selfInflicted = selfInflicted;
        }

        /**
         * @return true = 结束，移除
         */
        boolean tick() {
            if (!target.isEntityAlive()) {
                return true;
            }
            if (remainingDelay > 0) {
                remainingDelay--;
                return false;
            }
            if (remainingTicks <= 0) {
                return true;
            }
            remainingTicks--;
            elapsedDamageTicks++;

            // 回溯期间不扣血，时间照走
            if (EnchantmentTimeReversal.isReversing(target)) {
                return false;
            }

            float damage = scalingFunction != null
                    ? scalingFunction.apply(baseDamagePerTick, elapsedDamageTicks)
                    : baseDamagePerTick;
            if (damage <= 0) {
                return false;
            }

            if (canKill && target.getHealth() - damage * 2 <= 0) {
                if (selfInflicted) {
                    killThroughDamagePipeline(target, source);
                } else {
                    EntityLivingUtil.kill(target, source);
                }
                return true;
            }
            EntityLivingUtil.damageHealthDirectly(target, damage);
            return false;
        }
    }
}
