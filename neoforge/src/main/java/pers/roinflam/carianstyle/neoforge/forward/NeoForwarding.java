package pers.roinflam.carianstyle.neoforge.forward;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.bus.Event;
import pers.roinflam.carianstyle.bus.EventPriority;
import pers.roinflam.carianstyle.bus.PhasedForwarder;
import pers.roinflam.carianstyle.neoforge.forward.client.NeoClientForwards;

import java.util.function.Function;

/**
 * NeoForge 侧把平台事件转发进本模组总线 {@link CarianBus}。
 * <p>
 * 做法：每种平台事件在 NeoForge 总线的五档上各挂一个 {@code receiveCanceled = true} 的监听，
 * 每一档只把这一档转给本模组总线（{@link PhasedForwarder}）。这样本模组的监听和别的模组的监听之间的先后、
 * 改值、取消，和直接挂在 NeoForge 总线上完全一样：别的模组在 HIGH 取消，本模组 NORMAL 的普通监听照样跳过；
 * 本模组 HIGHEST 改的值，别的模组 HIGH 读得到。五档全挂，不按「现在有没有监听」省，
 * 否则以后新监听用了没挂的档位会静默不触发。
 * </p>
 * <p>
 * 包装对象是直通的：读写、取消都落在平台事件上。平台事件可取消、但本模组的事件类没声明取消的（如实体加入世界），
 * 包装类照样实现取消接口，平台上别的模组取消之后本模组后面的普通监听照样跳过。
 * </p>
 * <p>
 * 每种转发了的事件类都登记进总线的转发表（{@link CarianBus#provide}）；加载完成（{@link FMLLoadCompleteEvent}）时
 * 调 {@link CarianBus#verifyProvided()}，检查到那时为止登记的全部监听，之后再登记的当场检查：
 * 谁监听了这里没转发的事件就报错，不会悄悄不触发。
 * 模组自己发的事件（生物 tick、伤害三段）由发出它们的代码自己登记，最晚在加载完成之前（放在模组构造器里），
 * 不能放在事件类的静态初始化里——类第一次被用到可能已经是进世界以后了。
 * </p>
 * <p>
 * 在模组构造器里最先装：这时平台总线上别的模组的监听也大多在构造期挂上，本模组的转发监听在同档里的位置
 * 和原来本模组直接挂在平台总线上的监听差不多。总线上还没有本模组监听时，转发监听只查一次表就返回，平台事件照常往下走。
 * </p>
 */
public final class NeoForwarding {

    private static boolean installed;

    private NeoForwarding() {
    }

    /** 装全部转发。只装一次，再调什么都不做。客户端事件只在客户端装。 */
    public static synchronized void install(Dist dist, IEventBus modEventBus) {
        if (installed) {
            return;
        }
        installed = true;
        NeoLogicForwards.install();
        if (dist.isClient()) {
            NeoClientForwards.install();
        }
        modEventBus.addListener(FMLLoadCompleteEvent.class, e -> CarianBus.verifyProvided());
    }

    /**
     * 转发一种平台事件：五档各挂一个监听，并把 {@code busType} 登记进转发表。
     *
     * @param platformType NeoForge 的事件类（它的子类事件也会经过这里，NeoForge 总线就是这么分发的）
     * @param busType      本模组总线上对应的事件类（抽象类，监听写的就是它）
     * @param wrapperType  直通包装的具体类
     * @param wrap         从平台事件建包装
     */
    public static <P extends net.neoforged.bus.api.Event, W extends Event> void forward(
            Class<P> platformType, Class<? extends Event> busType, Class<W> wrapperType, Function<P, W> wrap) {
        if (!busType.isAssignableFrom(wrapperType)) {
            throw new IllegalArgumentException(wrapperType.getName() + " 不是 " + busType.getName() + " 的子类");
        }
        PhasedForwarder<P, W> forwarder = new PhasedForwarder<>(CarianBus.get(), wrapperType, wrap);
        EventPriority[] mine = EventPriority.values();
        for (net.neoforged.bus.api.EventPriority p : net.neoforged.bus.api.EventPriority.values()) {
            EventPriority phase = mine[p.ordinal()];
            NeoForge.EVENT_BUS.addListener(p, true, platformType, e -> forwarder.phase(e, phase));
        }
        CarianBus.provide(busType);
    }
}
