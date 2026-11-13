package pers.roinflam.carianstyle.bus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在订阅类上：类里的 static {@link SubscribeEvent} 方法由平台入口按构建时生成的订阅清单登记到 {@link CarianBus}。
 * 运行期不扫注解，清单生成任务读的就是这个注解和 {@link #side()}。
 * <p>
 * 仅客户端的订阅类只由客户端入口登记：Fabric 专用服上根本不加载这些类，
 * 否则取方法签名时就会碰到服务端没有的客户端类。
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {

    Side side() default Side.BOTH;

    enum Side {
        /** 双端都登记。监听参数不许是 {@code event.client} 包下的客户端事件，登记时检查。 */
        BOTH,
        /** 只在客户端登记。 */
        CLIENT
    }
}
