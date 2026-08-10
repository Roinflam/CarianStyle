package pers.roinflam.carianstyle.utils.util;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * AOE 安全遍历工具（服务端）。
 * <p>
 * 把本模组反复出现的两条防护措施收敛成默认行为，供<b>新增附魔</b>直接使用，
 * 免得每次都得记着手写一遍：
 * </p>
 * <ol>
 *     <li><b>半径封顶</b>——防止「等级直接当半径」在高等级时扫过整个区块区域。
 *         历史上已出现过 {@code level*4}（100 级 = 400 格）、{@code level*3}（300 格）
 *         这类写法，被迫逐个补 {@code MAX_SEARCH_RADIUS}；</li>
 *     <li><b>命中数封顶</b>——防止密集怪物场景下单次触发引发数千次 {@code hurt}
 *         事件链，进而突破 Watchdog 阈值崩服。</li>
 * </ol>
 * <p>
 * 另附 {@link ReentrancyGuard}，用于阻断「AOE 造成的伤害再次触发同一个 AOE」
 * 的自我喂养式级联——因果律、时间逆转、学者盾都各自手写过一份完全相同的
 * ThreadLocal 逻辑，这里统一提供。
 * </p>
 *
 * <h3>典型用法</h3>
 * <pre>
 * // 半径 = 等级×3、封顶 8 格，最多命中 16 个目标
 * AoeHelper.forEachNearby(
 *         victim, LivingEntity.class,
 *         level * 3, 8, 16,
 *         target -&gt; !target.equals(victim),
 *         target -&gt; target.hurt(source, damage));
 * </pre>
 *
 * <h3>与既有附魔的关系</h3>
 * <p>
 * <b>既有附魔无需改造。</b>它们已各自补齐了 {@code MAX_SEARCH_RADIUS} 与
 * {@code MAX_TARGETS}，行为与本工具等价；本类的价值在于让<b>以后新加的附魔</b>
 * 一开始就带上防护，而不是等崩服后再回头补。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class AoeHelper {

    /**
     * 推荐的默认命中上限。
     * <p>取 20 与模组内多数附魔现有的 {@code MAX_TARGETS} 一致；
     * 若单个目标的处理成本较高（如为每个目标创建持续任务），应传更小的值。</p>
     */
    public static final int DEFAULT_MAX_TARGETS = 20;

    /**
     * 推荐的默认半径上限（格）。
     * <p>取 10 与模组内多数 AOE 附魔一致。视觉类特效半径可以更大（不产生事件），
     * 但<b>伤害判定半径</b>不建议超过此值。</p>
     */
    public static final double DEFAULT_MAX_RADIUS = 10.0;

    private AoeHelper() {
    }

    /**
     * 遍历中心实体附近的目标并执行动作，自动应用半径封顶与命中数封顶。
     * <p>
     * 内部走 {@link EntityUtil#getNearbyEntities(Class, net.minecraft.world.entity.Entity, double, Predicate)}
     * 的圆柱判定（水平圆 + 垂直 ±R），与本模组其余 AOE 的范围口径完全一致。
     * </p>
     *
     * @param center       中心实体（不会自动排除自身，需要排除请在 {@code filter} 里写）
     * @param clazz        目标实体类型
     * @param desiredRadius 期望半径（格），通常是「等级 × 系数」算出来的值
     * @param maxRadius    半径上限（格），超出部分会被夹取
     * @param maxTargets   最大命中数，达到即停止遍历
     * @param filter       目标过滤器；可为 {@code null} 表示不过滤
     * @param action       对每个命中目标执行的动作
     * @param <T>          目标实体类型
     * @return 实际命中的目标数量
     */
    public static <T extends LivingEntity> int forEachNearby(
            @Nonnull LivingEntity center,
            @Nonnull Class<T> clazz,
            double desiredRadius,
            double maxRadius,
            int maxTargets,
            @Nullable Predicate<? super T> filter,
            @Nonnull Consumer<? super T> action) {

        double radius = clampRadius(desiredRadius, maxRadius);
        if (radius <= 0 || maxTargets <= 0) {
            return 0;
        }

        List<T> targets = EntityUtil.getNearbyEntities(clazz, center, radius, filter);
        if (targets.isEmpty()) {
            return 0;
        }

        int hitCount = 0;
        for (T target : targets) {
            if (hitCount >= maxTargets) {
                break;
            }
            action.accept(target);
            hitCount++;
        }
        return hitCount;
    }

    /**
     * 遍历中心实体附近的目标（使用默认上限）。
     *
     * @param center        中心实体
     * @param clazz         目标实体类型
     * @param desiredRadius 期望半径（格）
     * @param filter        目标过滤器；可为 {@code null}
     * @param action        对每个命中目标执行的动作
     * @param <T>           目标实体类型
     * @return 实际命中的目标数量
     */
    public static <T extends LivingEntity> int forEachNearby(
            @Nonnull LivingEntity center,
            @Nonnull Class<T> clazz,
            double desiredRadius,
            @Nullable Predicate<? super T> filter,
            @Nonnull Consumer<? super T> action) {
        return forEachNearby(center, clazz, desiredRadius,
                DEFAULT_MAX_RADIUS, DEFAULT_MAX_TARGETS, filter, action);
    }

    /**
     * 半径夹取。
     * <p>建议把「等级 × 系数」的结果先过一遍本方法，再同时用于<b>伤害判定与特效半径</b>，
     * 保证「看到多大就打多大」。</p>
     *
     * @param desired 期望半径（格）
     * @param max     上限（格）
     * @return 夹取后的半径，最小为 0
     */
    public static double clampRadius(double desired, double max) {
        if (desired <= 0) {
            return 0;
        }
        return Math.min(desired, max);
    }

    // ==================== 重入保护 ====================

    /**
     * 线程级重入保护。
     * <p>
     * 用于阻断「AOE 反击造成的伤害再次进入同一个事件监听器」形成的自我喂养式级联。
     * 每个附魔应持有<b>自己独立的一份</b>（静态 final 字段），
     * 不同附魔之间互不影响。
     * </p>
     *
     * <h3>用法</h3>
     * <pre>
     * private static final AoeHelper.ReentrancyGuard GUARD = new AoeHelper.ReentrancyGuard();
     *
     * public static void onLivingDamage(LivingDamageEvent evt) {
     *     if (GUARD.isActive()) {
     *         return;          // 本次伤害由自身 AOE 造成，直接跳过
     *     }
     *     ...
     *     GUARD.run(() -&gt; {
     *         for (target : targets) {
     *             target.hurt(source, damage);
     *         }
     *     });
     * }
     * </pre>
     * <p>
     * {@link #run} 内部用 try-finally 复位标记，即使 {@code hurt} 抛出异常
     * 也不会让标记滞留、导致该附魔永久失效——这正是手写版本最容易漏掉的地方。
     * </p>
     */
    public static final class ReentrancyGuard {

        /** 当前线程是否正处在被保护的代码块内 */
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> Boolean.FALSE);

        /**
         * 判断当前线程是否正处在被保护的代码块内。
         *
         * @return 处于保护块内返回 true，此时调用方应直接返回、不再触发效果
         */
        public boolean isActive() {
            return active.get();
        }

        /**
         * 在保护标记置位的状态下执行动作，结束后可靠复位。
         *
         * @param action 待执行动作（通常是对多个目标造成伤害的循环）
         */
        public void run(@Nonnull Runnable action) {
            if (active.get()) {
                // 已在保护块内：拒绝嵌套执行，避免逻辑意外递归
                return;
            }
            active.set(Boolean.TRUE);
            try {
                action.run();
            } finally {
                active.set(Boolean.FALSE);
            }
        }
    }
}
