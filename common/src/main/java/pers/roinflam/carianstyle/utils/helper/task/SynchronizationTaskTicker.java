package pers.roinflam.carianstyle.utils.helper.task;

import pers.roinflam.carianstyle.bus.EventBusSubscriber;
import pers.roinflam.carianstyle.bus.SubscribeEvent;
import pers.roinflam.carianstyle.event.ServerTickEvent;

import javax.annotation.Nonnull;

/**
 * {@link SynchronizationTask} 的服务器 tick 监听。
 * <p>
 * 同档的先后按类名排：本类要排在 {@code DamageOverTimeManager} 之后，和原来监听写在 {@code SynchronizationTask} 里时的位置一样，改名别挪出这个包。
 * </p>
 */
@EventBusSubscriber
public final class SynchronizationTaskTicker {

    private SynchronizationTaskTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(@Nonnull ServerTickEvent.Pre evt) {
        SynchronizationTask.onServerTick(evt);
    }
}
