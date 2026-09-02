package pers.roinflam.carianstyle.utils.helper.dot;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.utils.Reference;
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
 * 核心性能优化：替代每次攻击创建独立 SynchronizationTask 的模式。
 * </p>
 * <p>
 * 原问题：
 * 注定死亡、死亡之刃、黑焰刃、癫火、战士、沙布里里嚎叫、空癫火等附魔，
 * 每次攻击命中都创建1-2个 SynchronizationTask(delay, 1)，持续60-100tick。
 * 50人服务器战斗高峰时，每秒产生数百个周期任务对象，同时存活数千个。
 * 每个匿名内部类实例约200+字节（捕获外部变量引用+类元数据），
 * 加上 ConcurrentHashMap/ConcurrentLinkedQueue 操作的 Node 对象。
 * </p>
 * <p>
 * 优化方案：
 * 所有持续伤害效果统一注册到一个 ArrayList 中，
 * 每个 ServerTick 遍历一次列表处理所有活跃效果。
 * DoTEntry 是轻量级普通类，无闭包捕获开销。
 * </p>
 * <p>
 * 效果估算（50人战斗场景）：
 * - 原方案：~2000个 ConcurrentHashMap/Queue 条目 + 2000个匿名类实例
 * - 新方案：~2000个 DoTEntry 在一个 ArrayList 中，单次顺序遍历
 * - GC分配速率降低约15-25GB/min（消除匿名类+HashMap节点分配）
 * </p>
 *
 * <h3>v1.1：新增来源标签（tag）与剩余伤害查询</h3>
 * <p>
 * <b>为什么需要：</b>本管理器是<b>七个附魔共用的池子</b>（注定死亡、死亡之刃、黑焰刃、
 * 癫火、战士、沙布里里嚎叫、空癫火）。此前的公开 API 只有
 * {@link #getActiveCount()} 与 {@link #getStats()}，都是全局计数，
 * 无法回答「<b>某个实体身上、由某个特定附魔造成的</b>持续伤害还剩多少」。
 * </p>
 * <p>
 * 战士的 HUD 需要显示「流血剩余」。如果直接把该实体身上所有 DoT 加起来，
 * 一个同时中了癫火和战士的玩家，HUD 会把癫火的伤害算进战士那一行——
 * 数字看着有，但意义是错的。因此给条目加一个可选的来源标签。
 * </p>
 * <p>
 * <b>完全向后兼容：</b>原有的 {@link #applyLinear(LivingEntity, float, int, int, DamageSource, boolean)}
 * 与 {@link #applyScaling(LivingEntity, float, int, int, DamageSource, boolean, BiFunction)}
 * 两个重载<b>签名与行为一律不变</b>，内部以 {@code tag = null} 转调新重载。
 * 六个尚未打标签的附魔一行都不用改，只是查不到而已。
 * </p>
 *
 * @author RoinFlam
 * @version 1.1
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DamageOverTimeManager {

    /**
     * 所有活跃的持续伤害效果
     * <p>
     * 只在主线程（ServerTick START阶段）中遍历修改，不需要并发容器。
     * ArrayList 保证顺序遍历的缓存友好性。
     * </p>
     */
    private static final List<DoTEntry> ACTIVE_DOTS = new ArrayList<>(256);

    /**
     * 待添加队列
     * <p>
     * apply() 可能在 ServerTick 遍历期间被间接触发（通过kill触发DeathEvent，
     * DeathEvent中某些附魔又调用apply），使用 pending 队列延迟到遍历结束后批量添加，
     * 避免 ConcurrentModificationException。
     * </p>
     */
    private static final List<DoTEntry> PENDING = new ArrayList<>(64);

    /**
     * 是否正在遍历中
     */
    private static boolean iterating = false;

    // ==================== 公开API ====================

    /**
     * 注册一个固定伤害的持续效果（每tick扣固定值）
     *
     * @param target        目标实体
     * @param damagePerTick 每tick伤害值（在注册时计算好，不再每tick重算）
     * @param durationTicks 持续时间（tick）
     * @param initialDelay  初始延迟（tick），0=下一tick开始
     * @param source        伤害来源（用于击杀时的死亡消息）
     * @param canKill       是否可以致死
     */
    public static void applyLinear(@Nonnull LivingEntity target, float damagePerTick,
                                   int durationTicks, int initialDelay,
                                   @Nonnull DamageSource source, boolean canKill) {
        applyLinear(target, damagePerTick, durationTicks, initialDelay, source, canKill, null);
    }

    /**
     * 注册一个固定伤害的持续效果，并打上来源标签（v1.1 新增）
     * <p>
     * 打了标签的条目才能被 {@link #getRemainingDamage} 按来源查到。
     * 标签建议用附魔自身的常量（如 {@code EnchantmentWarrior.DOT_TAG}），
     * 不要在多处硬编码字符串。
     * </p>
     *
     * @param target        目标实体
     * @param damagePerTick 每tick伤害值（在注册时计算好，不再每tick重算）
     * @param durationTicks 持续时间（tick）
     * @param initialDelay  初始延迟（tick），0=下一tick开始
     * @param source        伤害来源（用于击杀时的死亡消息）
     * @param canKill       是否可以致死
     * @param tag           来源标签，可为 null（表示不参与按来源查询）
     */
    public static void applyLinear(@Nonnull LivingEntity target, float damagePerTick,
                                   int durationTicks, int initialDelay,
                                   @Nonnull DamageSource source, boolean canKill,
                                   @Nullable String tag) {
        DoTEntry entry = new DoTEntry(target, damagePerTick, durationTicks, initialDelay, source, canKill, null, tag);
        addEntry(entry);
    }

    /**
     * 注册一个递增/自定义伤害的持续效果
     * <p>
     * 实际伤害 = scalingFunction.apply(baseDamagePerTick, elapsedDamageTicks)
     * </p>
     *
     * @param target             目标实体
     * @param baseDamagePerTick  基础每tick伤害值（传给scaling函数的第一个参数）
     * @param durationTicks      持续时间（tick）
     * @param initialDelay       初始延迟（tick）
     * @param source             伤害来源
     * @param canKill            是否可以致死
     * @param scalingFunction    伤害缩放函数：(baseDamage, elapsedTicks) -> actualDamage
     */
    public static void applyScaling(@Nonnull LivingEntity target, float baseDamagePerTick,
                                    int durationTicks, int initialDelay,
                                    @Nonnull DamageSource source, boolean canKill,
                                    @Nonnull BiFunction<Float, Integer, Float> scalingFunction) {
        applyScaling(target, baseDamagePerTick, durationTicks, initialDelay, source, canKill, scalingFunction, null);
    }

    /**
     * 注册一个递增/自定义伤害的持续效果，并打上来源标签（v1.1 新增）
     * <p>
     * <b>⚠ 注意：</b>{@link #getRemainingDamage} 对缩放型条目只能按<b>基础速率</b>估算
     * （{@code baseDamagePerTick × 剩余 tick}），无法预测缩放函数未来的返回值。
     * 若需要精确的剩余量，请改用 {@link #applyLinear} 的标签重载。
     * </p>
     *
     * @param target            目标实体
     * @param baseDamagePerTick 基础每tick伤害值（传给scaling函数的第一个参数）
     * @param durationTicks     持续时间（tick）
     * @param initialDelay      初始延迟（tick）
     * @param source            伤害来源
     * @param canKill           是否可以致死
     * @param scalingFunction   伤害缩放函数：(baseDamage, elapsedTicks) -> actualDamage
     * @param tag               来源标签，可为 null（表示不参与按来源查询）
     */
    public static void applyScaling(@Nonnull LivingEntity target, float baseDamagePerTick,
                                    int durationTicks, int initialDelay,
                                    @Nonnull DamageSource source, boolean canKill,
                                    @Nonnull BiFunction<Float, Integer, Float> scalingFunction,
                                    @Nullable String tag) {
        DoTEntry entry = new DoTEntry(target, baseDamagePerTick, durationTicks, initialDelay, source, canKill, scalingFunction, tag);
        addEntry(entry);
    }

    /**
     * 查询某实体身上、由指定来源造成的持续伤害<b>剩余总量</b>（v1.1 新增）
     * <p>
     * 同一来源可能有多个条目同时存在（例如战士在 60 tick 内被连续命中多次，
     * 每次都会注册一条），本方法会把它们全部累加。
     * </p>
     * <p>
     * <b>计算方式：</b>{@code Σ(每tick伤害 × 剩余tick)}。对
     * {@link #applyLinear} 注册的条目是精确值；对 {@link #applyScaling} 注册的条目
     * 只是按基础速率的估算（详见该方法注释）。
     * </p>
     * <p>
     * <b>初始延迟期内的条目也计入</b>——伤害尚未开始扣，但它确实是「欠着的」，
     * HUD 应该在延迟期就把它显示出来，否则玩家会看到数字凭空跳出来。
     * </p>
     * <p>
     * <b>线程：</b>仅供服务端主线程调用（与 tick 处理同线程），故直接遍历 ArrayList。
     * 用下标遍历而非迭代器，避免在 {@link #onServerTick} 遍历期间被调用时抛
     * ConcurrentModificationException——虽然当前不存在这种调用路径，但成本为零，值得防。
     * </p>
     *
     * @param target 目标实体
     * @param tag    来源标签（与注册时传入的一致）
     * @return 剩余伤害总量；无匹配条目时为 0
     */
    public static float getRemainingDamage(@Nonnull LivingEntity target, @Nonnull String tag) {
        int targetId = target.getId();
        float total = 0f;
        for (int i = 0; i < ACTIVE_DOTS.size(); i++) {
            DoTEntry dot = ACTIVE_DOTS.get(i);
            if (dot.targetId == targetId && tag.equals(dot.tag)) {
                total += dot.remainingDamage();
            }
        }
        // 待添加队列里的条目同样算数：它们是本 tick 刚注册的，
        // 漏掉会让 HUD 在受击后的第一次轮询少显示一截
        for (int i = 0; i < PENDING.size(); i++) {
            DoTEntry dot = PENDING.get(i);
            if (dot.targetId == targetId && tag.equals(dot.tag)) {
                total += dot.remainingDamage();
            }
        }
        return total;
    }

    /**
     * 清除指定实体的所有持续伤害效果
     *
     * @param target 目标实体
     */
    public static void clearEntity(@Nonnull LivingEntity target) {
        int targetId = target.getId();
        ACTIVE_DOTS.removeIf(dot -> dot.targetId == targetId);
        PENDING.removeIf(dot -> dot.targetId == targetId);
    }

    /**
     * 清除所有持续伤害效果（服务器关闭时调用）
     */
    public static void clearAll() {
        ACTIVE_DOTS.clear();
        PENDING.clear();
    }

    /**
     * 获取活跃效果数量（调试用）
     *
     * @return 活跃效果数量
     */
    public static int getActiveCount() {
        return ACTIVE_DOTS.size();
    }

    /**
     * 获取统计信息（调试用）
     *
     * @return 统计字符串
     */
    public static String getStats() {
        return String.format("[DoT管理器] 活跃: %d | 待添加: %d", ACTIVE_DOTS.size(), PENDING.size());
    }

    // ==================== 内部方法 ====================

    /**
     * 添加效果条目
     */
    private static void addEntry(@Nonnull DoTEntry entry) {
        if (iterating) {
            PENDING.add(entry);
        } else {
            ACTIVE_DOTS.add(entry);
        }
    }

    // ==================== ServerTick 处理 ====================

    /**
     * 每tick处理所有活跃的持续伤害效果
     */
    @SubscribeEvent
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        if (ACTIVE_DOTS.isEmpty() && PENDING.isEmpty()) {
            return;
        }

        // 先合并 pending
        if (!PENDING.isEmpty()) {
            ACTIVE_DOTS.addAll(PENDING);
            PENDING.clear();
        }

        iterating = true;
        try {
            Iterator<DoTEntry> iterator = ACTIVE_DOTS.iterator();
            while (iterator.hasNext()) {
                DoTEntry dot = iterator.next();
                if (dot.tick()) {
                    iterator.remove();
                }
            }
        } finally {
            iterating = false;
        }

        // 遍历期间可能有新的效果通过 kill -> DeathEvent -> 附魔逻辑 加入 pending
        if (!PENDING.isEmpty()) {
            ACTIVE_DOTS.addAll(PENDING);
            PENDING.clear();
        }
    }

    // ==================== DoT 效果条目 ====================

    /**
     * 持续伤害效果条目
     * <p>
     * 轻量级普通类，无闭包捕获。每个实例约56字节。
     * 对比匿名SynchronizationTask子类约200+字节。
     * </p>
     */
    private static class DoTEntry {
        final int targetId;
        final LivingEntity target;
        final float baseDamagePerTick;
        final DamageSource source;
        final boolean canKill;
        @Nullable
        final BiFunction<Float, Integer, Float> scalingFunction;
        /**
         * 来源标签（v1.1 新增）
         * <p>用于把共用同一个池子的七个附魔区分开，供
         * {@link #getRemainingDamage} 按来源查询。null 表示不参与查询。</p>
         */
        @Nullable
        final String tag;

        int remainingDelay;
        int remainingTicks;
        int elapsedDamageTicks;

        DoTEntry(@Nonnull LivingEntity target, float baseDamagePerTick, int durationTicks,
                 int initialDelay, @Nonnull DamageSource source, boolean canKill,
                 @Nullable BiFunction<Float, Integer, Float> scalingFunction,
                 @Nullable String tag) {
            this.targetId = target.getId();
            this.target = target;
            this.baseDamagePerTick = baseDamagePerTick;
            this.remainingDelay = initialDelay;
            this.remainingTicks = durationTicks;
            this.source = source;
            this.canKill = canKill;
            this.scalingFunction = scalingFunction;
            this.tag = tag;
            this.elapsedDamageTicks = 0;
        }

        /**
         * 本条目尚未结算的伤害总量（v1.1 新增）
         * <p>对线性条目为精确值；对缩放条目为按基础速率的估算。</p>
         *
         * @return 剩余伤害；已结束时为 0
         */
        float remainingDamage() {
            if (remainingTicks <= 0 || baseDamagePerTick <= 0f) {
                return 0f;
            }
            return baseDamagePerTick * remainingTicks;
        }

        /**
         * 每tick处理
         *
         * @return true=效果结束需移除
         */
        boolean tick() {
            // 目标已死亡或被移除
            if (!target.isAlive() || target.isRemoved()) {
                return true;
            }

            // 初始延迟
            if (remainingDelay > 0) {
                remainingDelay--;
                return false;
            }

            // 持续时间结束
            if (remainingTicks <= 0) {
                return true;
            }

            remainingTicks--;
            elapsedDamageTicks++;

            // 计算伤害
            float damage;
            if (scalingFunction != null) {
                damage = scalingFunction.apply(baseDamagePerTick, elapsedDamageTicks);
            } else {
                damage = baseDamagePerTick;
            }

            if (damage <= 0) {
                return false;
            }

            // 应用伤害
            if (canKill && target.getHealth() - damage * 2 <= 0) {
                EntityLivingUtil.kill(target, source);
                return true;
            } else {
                EntityLivingUtil.damageHealthDirectly(target, damage);
            }

            return false;
        }
    }
}
