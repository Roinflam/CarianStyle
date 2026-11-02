package pers.roinflam.carianstyle.utils.helper.task;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 同步任务调度器
 * Synchronous task scheduler
 *
 * 用于在服务器tick中延迟执行或周期执行任务。
 * Used for delayed or periodic task execution in server ticks.
 * 使用单一全局监听器管理所有任务，避免每个任务单独注册事件总线的性能开销。
 * Uses a single global listener to manage all tasks, avoiding performance overhead of registering each task separately.
 *
 * <p>使用示例 / Usage examples:</p>
 * <pre>
 * // 延迟20tick后执行一次
 * new SynchronizationTask(20) {
 *     &#064;Override
 *     public void run() {
 *         // 执行逻辑
 *     }
 * }.start();
 *
 * // 延迟10tick后开始，每5tick执行一次（周期任务）
 * new SynchronizationTask(10, 5) {
 *     &#064;Override
 *     public void run() {
 *         // 周期执行逻辑
 *     }
 * }.start();
 * </pre>
 *
 * <p>
 * 性能优化记录 v3.0：
 * 核心问题：出血(MobEffectHemorrhage)、切割(MobEffectIncision)等药水效果
 * 每tick通过 new SynchronizationTask(1) 创建一次性延迟任务，
 * 50人战斗时每秒产生数百个 ConcurrentHashMap.put() + .remove() 操作。
 *
 * 优化方案：
 * 1. 新增 nextTickQueue（ConcurrentLinkedQueue）快速通道：
 *    initialDelay <= 1 且非周期任务直接入队，跳过 ConcurrentHashMap。
 *    Queue.offer() 比 ConcurrentHashMap.put() 快一个数量级，
 *    且不需要后续的 remove() 操作（poll后自动释放）。
 * 2. 超限只打印警告不丢弃任务，保证功能完整性。
 * 3. 保留 v2.1 的 Iterator.remove() 优化。
 * </p>
 *
 * @version 3.0
 */
public abstract class SynchronizationTask implements Runnable {

    // ==================== 常量 ====================

    /** 警告阈值：长期任务超过此数量时打印一次日志 */
    private static final int WARN_THRESHOLD = 1500;

    /** 是否已输出过警告（避免刷屏） */
    private static volatile boolean warnPrinted = false;

    // ==================== 全局管理器 ====================

    /** 全局任务管理器（单例） */
    private static final TaskManager MANAGER = new TaskManager();

    /** 任务ID生成器 */
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    // ==================== 实例字段 ====================

    /** 任务唯一ID */
    protected final int taskId;

    /** 是否为周期任务 */
    protected final boolean cycle;

    /** 初始延迟（tick） */
    protected final int initialDelay;

    /** 周期间隔（tick），-1表示一次性任务 */
    protected final int delay;

    /** 是否已启动 */
    protected volatile boolean started = false;

    /** 是否为首次执行前 */
    protected boolean first = true;

    /** 当前计时器 */
    protected int tick = 0;

    /**
     * 创建立即执行的一次性任务
     */
    public SynchronizationTask() {
        this(0);
    }

    /**
     * 创建延迟执行的一次性任务
     *
     * @param initialDelay 初始延迟（tick），0表示下一tick执行
     */
    public SynchronizationTask(int initialDelay) {
        this(initialDelay, -1);
    }

    /**
     * 创建延迟执行的周期任务
     *
     * @param initialDelay 初始延迟（tick）
     * @param delay 周期间隔（tick），>=0表示周期任务，-1表示一次性任务
     */
    public SynchronizationTask(int initialDelay, int delay) {
        this.taskId = ID_GENERATOR.incrementAndGet();
        this.cycle = delay >= 0;
        this.initialDelay = initialDelay;
        this.delay = delay;
    }

    /**
     * 根据任务ID取消任务（静态方法）
     *
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public static boolean cancel(int taskId) {
        return MANAGER.cancelTask(taskId);
    }

    /**
     * 获取任务ID
     *
     * @return 任务ID
     */
    public int getTaskId() {
        return taskId;
    }

    /**
     * 启动任务
     * <p>
     * v3.0优化：短延迟一次性任务走 nextTickQueue 快速通道
     * </p>
     */
    public synchronized void start() {
        if (!started) {
            started = true;
            MANAGER.addTask(this);
        }
    }

    /**
     * 取消任务
     * <p>
     * 可在run()方法内调用以停止周期任务。
     * 注意：nextTickQueue中的任务无法从队列移除，但执行前会检查started标记。
     * </p>
     */
    public synchronized void cancel() {
        if (started) {
            started = false;
            MANAGER.removeTask(taskId);
        }
    }

    /**
     * 内部tick处理（由TaskManager调用，仅用于长期任务）
     *
     * @return true表示任务已完成需要移除
     */
    boolean onTick() {
        if (!started) {
            return true;
        }

        tick++;

        if (first) {
            if (tick >= initialDelay) {
                first = false;
                tick = 0;

                try {
                    this.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (!cycle) {
                    started = false;
                    return true;
                }
            }
        } else {
            if (cycle && tick >= delay) {
                tick = 0;

                try {
                    this.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return !started;
    }

    // ==================== 统计方法（调试用） ====================

    /**
     * 获取当前活跃任务数量
     *
     * @return 长期任务 + nextTick队列
     */
    public static int getActiveTaskCount() {
        return MANAGER.getActiveCount();
    }

    /**
     * 获取统计摘要字符串
     *
     * @return 统计摘要
     */
    public static String getStats() {
        return MANAGER.getStats();
    }

    /**
     * 全局任务管理器
     * <p>
     * v3.0核心优化：新增 nextTickQueue 快速通道
     * - 出血、切割等每tick创建的 SynchronizationTask(1) 全部走 ConcurrentLinkedQueue
     * - ConcurrentLinkedQueue.offer() 是无锁CAS操作，比 ConcurrentHashMap.put() 快得多
     * - 不需要后续的 remove() 操作（poll后节点自动GC）
     * - 长期任务（周期+延迟>1）仍走 ConcurrentHashMap
     * </p>
     *
     * @version 3.0
     */
    private static class TaskManager {

        /** 长期任务列表（周期任务 + delay>1的一次性任务） */
        private final Map<Integer, SynchronizationTask> tasks = new ConcurrentHashMap<>();

        /**
         * nextTick快速通道队列
         * <p>
         * 存放 initialDelay<=1 且非周期的一次性任务。
         * 出血/切割等效果每tick创建的 SynchronizationTask(1) 全部走这条通道。
         * </p>
         */
        private final Queue<SynchronizationTask> nextTickQueue = new ConcurrentLinkedQueue<>();

        /** 是否已注册到事件总线 */
        private volatile boolean registered = false;

        /** 累计处理的nextTick任务数（统计用） */
        private volatile long totalNextTickProcessed = 0;

        /**
         * 添加任务（自动路由到快速通道或长期任务表）
         *
         * @param task 要添加的任务
         */
        void addTask(@Nonnull SynchronizationTask task) {
            ensureRegistered();

            // v3.0：短延迟一次性任务走快速通道
            if (!task.cycle && task.initialDelay <= 1) {
                nextTickQueue.offer(task);
                return;
            }

            // 长期任务：超限时打印警告但不丢弃，保证功能完整
            int currentSize = tasks.size();
            if (currentSize >= WARN_THRESHOLD && !warnPrinted) {
                warnPrinted = true;
                System.err.println("[卡利亚式附魔] 警告：同步任务数量已达 " + currentSize
                        + "，可能存在任务泄漏，请检查。");
            }

            tasks.put(task.getTaskId(), task);
        }

        /**
         * 移除长期任务
         */
        void removeTask(int taskId) {
            tasks.remove(taskId);
        }

        /**
         * 根据ID取消长期任务
         */
        boolean cancelTask(int taskId) {
            SynchronizationTask task = tasks.remove(taskId);
            if (task != null) {
                task.started = false;
                return true;
            }
            return false;
        }

        /**
         * 确保已注册到事件总线（只注册一次）
         */
        private synchronized void ensureRegistered() {
            if (!registered) {
                registered = true;
                MinecraftForge.EVENT_BUS.register(this);
            }
        }

        /**
         * 服务器tick事件处理
         * <p>
         * v3.0优化：先drain nextTickQueue，再处理长期任务。
         * nextTickQueue中的任务执行后自动释放，不需要额外的remove操作。
         * </p>
         */
        @SubscribeEvent
        public void onServerTick(@Nonnull TickEvent.ServerTickEvent evt) {
            if (evt.phase != TickEvent.Phase.START) {
                return;
            }

            // ========== 阶段1：处理 nextTickQueue 快速通道 ==========
            int nextTickCount = 0;
            SynchronizationTask fastTask;
            while ((fastTask = nextTickQueue.poll()) != null) {
                // 检查是否已被取消（cancel()会设置started=false）
                if (!fastTask.started) {
                    continue;
                }

                try {
                    fastTask.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                fastTask.started = false;
                nextTickCount++;
            }
            if (nextTickCount > 0) {
                totalNextTickProcessed += nextTickCount;
            }

            // ========== 阶段2：处理长期任务 ==========
            if (tasks.isEmpty()) {
                // 任务清空后重置警告标记
                if (warnPrinted) {
                    warnPrinted = false;
                }
                return;
            }

            // v2.1优化保留：使用 Iterator.remove() 替代临时列表
            Iterator<Map.Entry<Integer, SynchronizationTask>> iterator = tasks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, SynchronizationTask> entry = iterator.next();
                SynchronizationTask task = entry.getValue();

                if (task.onTick()) {
                    iterator.remove();
                }
            }
        }

        // ========== 统计方法 ==========

        int getActiveCount() {
            return tasks.size() + nextTickQueue.size();
        }

        String getStats() {
            return String.format("[SynchronizationTask] 长期任务: %d | nextTick队列: %d | 累计处理nextTick: %d",
                    tasks.size(), nextTickQueue.size(), totalNextTickProcessed);
        }
    }
}
