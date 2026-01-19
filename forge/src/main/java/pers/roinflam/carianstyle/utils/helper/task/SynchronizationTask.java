package pers.roinflam.carianstyle.utils.helper.task;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * // Execute once after 20 ticks
 * new SynchronizationTask(20) {
 *     @Override
 *     public void run() {
 *         // 执行逻辑 / execution logic
 *     }
 * }.start();
 *
 * // 延迟10tick后开始，每5tick执行一次（周期任务）
 * // Start after 10 ticks, execute every 5 ticks (periodic task)
 * new SynchronizationTask(10, 5) {
 *     @Override
 *     public void run() {
 *         // 周期执行逻辑 / periodic execution logic
 *     }
 * }.start();
 *
 * // 立即开始，每1tick执行一次（持续伤害等）
 * // Start immediately, execute every tick (continuous damage, etc.)
 * new SynchronizationTask(0, 1) {
 *     @Override
 *     public void run() {
 *         if (条件不满足) {
 *             this.cancel();
 *             return;
 *         }
 *         // 持续伤害逻辑 / continuous damage logic
 *     }
 * }.start();
 * </pre>
 */
public abstract class SynchronizationTask implements Runnable {

    /** 全局任务管理器（单例）/ Global task manager (singleton) */
    private static final TaskManager MANAGER = new TaskManager();

    /** 任务ID生成器 / Task ID generator */
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    /** 任务唯一ID / Task unique ID */
    protected final int taskId;

    /** 是否为周期任务 / Whether it's a periodic task */
    protected final boolean cycle;

    /** 初始延迟（tick）/ Initial delay (ticks) */
    protected final int initialDelay;

    /** 周期间隔（tick），-1表示一次性任务 / Period interval (ticks), -1 for one-time task */
    protected final int delay;

    /** 是否已启动 / Whether started */
    protected volatile boolean started = false;

    /** 是否为首次执行前 / Whether before first execution */
    protected boolean first = true;

    /** 当前计时器 / Current timer */
    protected int tick = 0;

    /**
     * 创建立即执行的一次性任务
     * Create an immediately executing one-time task
     */
    public SynchronizationTask() {
        this(0);
    }

    /**
     * 创建延迟执行的一次性任务
     * Create a delayed one-time task
     *
     * @param initialDelay 初始延迟（tick），0表示下一tick执行 / initial delay (ticks), 0 means execute next tick
     */
    public SynchronizationTask(int initialDelay) {
        this(initialDelay, -1);
    }

    /**
     * 创建延迟执行的周期任务
     * Create a delayed periodic task
     *
     * @param initialDelay 初始延迟（tick）/ initial delay (ticks)
     * @param delay 周期间隔（tick），>=0表示周期任务，-1表示一次性任务 / period interval (ticks), >=0 for periodic task, -1 for one-time
     */
    public SynchronizationTask(int initialDelay, int delay) {
        this.taskId = ID_GENERATOR.incrementAndGet();
        this.cycle = delay >= 0;
        this.initialDelay = initialDelay;
        this.delay = delay;
    }

    /**
     * 根据任务ID取消任务（静态方法）
     * Cancel task by task ID (static method)
     *
     * @param taskId 任务ID / task ID
     * @return 是否成功取消 / whether successfully cancelled
     */
    public static boolean cancel(int taskId) {
        return MANAGER.cancelTask(taskId);
    }

    /**
     * 获取任务ID
     * Get task ID
     *
     * @return 任务ID / task ID
     */
    public int getTaskId() {
        return taskId;
    }

    /**
     * 启动任务
     * Start task
     *
     * 任务启动后会在服务器tick中执行
     * Task will execute in server ticks after being started
     */
    public synchronized void start() {
        if (!started) {
            started = true;
            MANAGER.addTask(this);
        }
    }

    /**
     * 取消任务
     * Cancel task
     *
     * 可在run()方法内调用以停止周期任务
     * Can be called inside run() method to stop periodic task
     */
    public synchronized void cancel() {
        if (started) {
            started = false;
            MANAGER.removeTask(taskId);
        }
    }

    /**
     * 内部tick处理（由TaskManager调用）
     * Internal tick processing (called by TaskManager)
     *
     * @return true表示任务已完成需要移除，false表示继续执行 / true if task completed and should be removed, false to continue
     */
    boolean onTick() {
        // 任务已被取消
        if (!started) {
            return true;
        }

        tick++;

        if (first) {
            // 首次执行前：等待初始延迟
            if (tick >= initialDelay) {
                first = false;
                tick = 0;

                // 执行任务
                try {
                    this.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 一次性任务执行后结束
                if (!cycle) {
                    started = false;
                    return true;
                }
            }
        } else {
            // 周期执行
            if (cycle && tick >= delay) {
                tick = 0;

                try {
                    this.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 检查是否在run()中被取消
        return !started;
    }

    /**
     * 全局任务管理器
     * Global task manager
     *
     * 使用单一事件监听器管理所有任务，相比每个任务单独注册：
     * Uses single event listener to manage all tasks, compared to individual registration:
     * - 100个任务只有1个监听器，而不是100个 / 100 tasks with 1 listener instead of 100
     * - 减少事件总线的遍历开销 / Reduces event bus traversal overhead
     * - 线程安全 / Thread-safe
     */
    @Mod.EventBusSubscriber
    private static class TaskManager {

        /** 任务列表（线程安全）/ Task list (thread-safe) */
        private final Map<Integer, SynchronizationTask> tasks = new ConcurrentHashMap<>();

        /** 是否已注册到事件总线 / Whether registered to event bus */
        private volatile boolean registered = false;

        /**
         * 添加任务
         * Add task
         */
        void addTask(@Nonnull SynchronizationTask task) {
            tasks.put(task.getTaskId(), task);
            ensureRegistered();
        }

        /**
         * 移除任务
         * Remove task
         */
        void removeTask(int taskId) {
            tasks.remove(taskId);
        }

        /**
         * 根据ID取消任务
         * Cancel task by ID
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
         * Ensure registered to event bus (register only once)
         */
        private synchronized void ensureRegistered() {
            if (!registered) {
                registered = true;
                MinecraftForge.EVENT_BUS.register(this);
            }
        }

        /**
         * 服务器tick事件处理
         * Server tick event handling
         *
         * 每个服务器tick遍历所有任务并执行
         * Traverse and execute all tasks each server tick
         */
        @SubscribeEvent
        public void onServerTick(@Nonnull TickEvent.ServerTickEvent evt) {
            // 只在START阶段处理（与原版一致）
            if (evt.phase != TickEvent.Phase.START) {
                return;
            }

            // 无任务时跳过
            if (tasks.isEmpty()) {
                return;
            }

            // 收集需要移除的任务（避免在遍历时修改）
            List<Integer> toRemove = new ArrayList<>();

            for (Map.Entry<Integer, SynchronizationTask> entry : tasks.entrySet()) {
                SynchronizationTask task = entry.getValue();

                // onTick()返回true表示任务已完成
                if (task.onTick()) {
                    toRemove.add(entry.getKey());
                }
            }

            // 批量移除已完成的任务
            for (Integer taskId : toRemove) {
                tasks.remove(taskId);
            }
        }
    }
}