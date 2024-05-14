package pers.roinflam.carianstyle.utils.helper.task;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Random;

public abstract class SynchronizationTask implements Runnable {
    private final static HashMap<Integer, SynchronizationTask> TASK_LIST = new HashMap<Integer, SynchronizationTask>();
    protected final int TASKID;
    protected final boolean CYCLE;
    protected final int INITIALDELAY;
    protected final int DELAY;
    protected boolean start;
    protected boolean first;
    protected int tick;

    public SynchronizationTask() {
        this(0);
    }

    public SynchronizationTask(int initialDelay) {
        this(initialDelay, -1);
    }

    public SynchronizationTask(int initialDelay, int delay) {
        this.TASKID = new Random().nextInt(Short.MAX_VALUE);
        this.first = true;
        this.CYCLE = delay >= 0;
        this.tick = 0;
        this.INITIALDELAY = initialDelay;
        this.DELAY = delay;
    }

    public synchronized static boolean cancel(int taskID) {
        if (TASK_LIST.containsKey(taskID)) {
            TASK_LIST.get(taskID).cancel();
            return true;
        } else {
            return false;
        }
    }

    @SubscribeEvent
    public void onTick(@Nonnull TickEvent.ServerTickEvent evt) {
        if (evt.phase.equals(TickEvent.Phase.START)) {
            tick++;
            if (first) {
                if (tick >= INITIALDELAY) {
                    first = false;
                    tick = 0;
                    this.run();
                }
            } else {
                if (CYCLE) {
                    if (tick >= DELAY) {
                        tick = 0;
                        this.run();
                    }
                } else {
                    cancel();
                }
            }
        }
    }

    public int getTaskID() {
        return TASKID;
    }

    public synchronized void start() {
        if (!start) {
            start = true;
            TASK_LIST.put(getTaskID(), this);
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    public synchronized void cancel() {
        start = false;
        TASK_LIST.remove(this.getTaskID());
        MinecraftForge.EVENT_BUS.unregister(this);
    }

}
