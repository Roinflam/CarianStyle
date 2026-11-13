package pers.roinflam.carianstyle.scan;

/**
 * 订阅类在哪一端登记，对应订阅注解上的 {@code side}。
 * <p>
 * 清单里按常量名原样写（{@code BOTH} / {@code CLIENT}），改名会让旧清单读不出来，只许追加。
 * </p>
 */
public enum SubscriberSide {
    /** 两端都登记（服务端逻辑、双端都要的监听） */
    BOTH,
    /** 只在客户端登记：监听里用到客户端类，专用服上不能加载 */
    CLIENT
}
