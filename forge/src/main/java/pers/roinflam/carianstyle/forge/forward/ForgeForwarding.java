package pers.roinflam.carianstyle.forge.forward;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.bus.Event;
import pers.roinflam.carianstyle.bus.EventPriority;
import pers.roinflam.carianstyle.bus.PhasedForwarder;
import pers.roinflam.carianstyle.forge.forward.client.ForgeClientForwards;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Forge 侧把平台事件转发进本模组总线 {@link CarianBus}。
 * <p>
 * 每种 Forge 事件在 {@link MinecraftForge#EVENT_BUS} 的五档上各挂一个 {@code receiveCanceled = true} 的监听，
 * 每一档只把这一档转给本模组总线（{@link PhasedForwarder}），所以本模组监听和别的模组监听之间的先后、改值、取消
 * 都和直接挂在 Forge 总线上一样。五档全挂，不按「现在有没有监听」省，否则以后新监听用了没挂的档位会静默不触发。
 * </p>
 * <p>
 * 加载完成时调 {@link CarianBus#verifyProvided()}：监听了这里没转发的事件就报错，不会悄悄不触发。
 * 在模组构造器里尽量早装，转发监听在同档里的位置和原来本模组直接挂在 Forge 总线上的监听差不多。
 * </p>
 */
public final class ForgeForwarding {

    private static boolean installed;

    private ForgeForwarding() {
    }

    public static synchronized void install(Dist dist, IEventBus modEventBus) {
        if (installed) {
            return;
        }
        installed = true;
        ForgeLogicForwards.install();
        if (dist.isClient()) {
            ForgeClientForwards.install();
        }
        modEventBus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false,
                FMLLoadCompleteEvent.class, e -> CarianBus.verifyProvided());
    }

    /**
     * 转发一种 Forge 事件：五档各挂一个监听，并把 {@code busType} 登记进转发表。
     * Forge 总线把子类事件也分发给父类的监听，所以 {@code platformType} 的子类事件同样经过这里。
     */
    public static <P extends net.minecraftforge.eventbus.api.Event, W extends Event> void forward(
            Class<P> platformType, Class<? extends Event> busType, Class<W> wrapperType, Function<P, W> wrap) {
        Target<P> target = target(busType, wrapperType, wrap);
        // 有的服务端的事件总线在五档之外另加优先级，按名字对，多出来的不挂
        for (EventPriority phase : EventPriority.values()) {
            net.minecraftforge.eventbus.api.EventPriority p = net.minecraftforge.eventbus.api.EventPriority.valueOf(phase.name());
            MinecraftForge.EVENT_BUS.addListener(p, true, platformType, e -> target.forwarder.phase(e, phase));
        }
        CarianBus.provide(busType);
    }

    /**
     * 转发 Forge 的 tick 事件：同一个类按 {@code phase} 分成 START / END 两次发，分别对到模组的 Pre / Post。
     * 没有对应模组事件的那一相位传 null，不转发。START 和 END 是两个不同的事件对象，各走各的 {@link PhasedForwarder}。
     */
    public static <P extends TickEvent> void forwardTick(Class<P> platformType, @Nullable Target<P> start, @Nullable Target<P> end) {
        for (EventPriority phase : EventPriority.values()) {
            net.minecraftforge.eventbus.api.EventPriority p = net.minecraftforge.eventbus.api.EventPriority.valueOf(phase.name());
            MinecraftForge.EVENT_BUS.addListener(p, true, platformType, e -> {
                Target<P> t = e.phase == TickEvent.Phase.START ? start : end;
                if (t != null) {
                    t.forwarder.phase(e, phase);
                }
            });
        }
        if (start != null) {
            CarianBus.provide(start.busType);
        }
        if (end != null) {
            CarianBus.provide(end.busType);
        }
    }

    public static <P, W extends Event> Target<P> target(Class<? extends Event> busType, Class<W> wrapperType, Function<P, W> wrap) {
        if (!busType.isAssignableFrom(wrapperType)) {
            throw new IllegalArgumentException(wrapperType.getName() + " 不是 " + busType.getName() + " 的子类");
        }
        return new Target<>(busType, new PhasedForwarder<>(CarianBus.get(), wrapperType, wrap));
    }

    /** 一种模组事件的转发目标：登记用的事件类和分档分发器。 */
    public static final class Target<P> {
        final Class<? extends Event> busType;
        final PhasedForwarder<P, ?> forwarder;

        private Target(Class<? extends Event> busType, PhasedForwarder<P, ?> forwarder) {
            this.busType = busType;
            this.forwarder = forwarder;
        }
    }
}
