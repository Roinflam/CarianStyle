package pers.roinflam.carianstyle.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在监听方法上：方法必须是 public、只有一个参数，参数类型是 {@link Event} 的子类（可以是抽象的父类，
 * 父类上的监听也收得到子类事件）。用 {@link CarianBus#register(Class)} 登记时方法要是 static，
 * 用 {@link CarianBus#register(Object)} 登记时要是实例方法，和 NeoForge 总线的规矩一样。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {

    EventPriority priority() default EventPriority.NORMAL;

    /** 事件已被取消时还要不要收到。只对 {@link ICancellableEvent} 有意义。 */
    boolean receiveCanceled() default false;

    /**
     * 同一档内的排序值，从小到大执行，相同的按登记先后（静态监听按订阅清单的类名顺序、同一类里按方法名）。
     * <p>
     * 平台总线上同一档里本模组各监听的先后原本由加载器遍历类的顺序决定，没有保证；
     * 改成「排序值 + 登记先后」之后两个平台的先后一致。只有确实依赖先后的监听才设这个值。
     * </p>
     */
    int order() default 0;
}
