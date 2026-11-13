package pers.roinflam.carianstyle.scan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 照清单登记：按端过滤、按清单顺序、先初始化类、每端只许一次、类不存在就抛异常。 */
class CarianSubscribersTest {

    static boolean bothAInitialized;
    static boolean clientInitialized;

    public static class BothA {
        static {
            bothAInitialized = true;
        }
    }

    public static class BothB {
    }

    public static class ClientOnly {
        static {
            clientInitialized = true;
        }
    }

    private static final String P = CarianSubscribersTest.class.getName() + "$";

    private static List<ScanIndex.Subscriber> entries() {
        return List.of(
                new ScanIndex.Subscriber(SubscriberSide.BOTH, P + "BothA"),
                new ScanIndex.Subscriber(SubscriberSide.BOTH, P + "BothB"),
                new ScanIndex.Subscriber(SubscriberSide.CLIENT, P + "ClientOnly"));
    }

    @BeforeEach
    @AfterEach
    void reset() {
        CarianSubscribers.resetForTest();
    }

    @Test
    void registersOnlyRequestedSideInListOrder() {
        List<Class<?>> got = new ArrayList<>();
        List<Class<?>> returned = CarianSubscribers.register(SubscriberSide.BOTH, entries(), got::add);
        assertEquals(List.of(BothA.class, BothB.class), got);
        assertEquals(got, returned);
        assertTrue(bothAInitialized, "登记前要先初始化类");
        assertFalse(clientInitialized, "只登记两端的时候不许碰仅客户端的类");

        List<Class<?>> client = new ArrayList<>();
        CarianSubscribers.register(SubscriberSide.CLIENT, entries(), client::add);
        assertEquals(List.of(ClientOnly.class), client);
        assertTrue(clientInitialized);
    }

    @Test
    void sameSideTwiceThrows() {
        CarianSubscribers.register(SubscriberSide.BOTH, entries(), c -> {
        });
        assertThrows(IllegalStateException.class, () -> CarianSubscribers.register(SubscriberSide.BOTH, entries(), c -> {
        }));
    }

    @Test
    void missingClassThrows() {
        List<ScanIndex.Subscriber> broken = List.of(new ScanIndex.Subscriber(SubscriberSide.BOTH, P + "Gone"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CarianSubscribers.register(SubscriberSide.BOTH, broken, c -> {
                }));
        assertTrue(e.getMessage().contains("Gone"), e.getMessage());
        // 失败的那一端不算登记过，重来还是报真正的原因
        e = assertThrows(IllegalStateException.class, () -> CarianSubscribers.register(SubscriberSide.BOTH, broken, c -> {
        }));
        assertTrue(e.getMessage().contains("Gone"), e.getMessage());
    }
}
