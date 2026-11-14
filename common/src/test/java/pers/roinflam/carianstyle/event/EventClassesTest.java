package pers.roinflam.carianstyle.event;

import org.junit.jupiter.api.Test;
import pers.roinflam.carianstyle.bus.CarianEventBus;
import pers.roinflam.carianstyle.bus.EventBusSubscriber;
import pers.roinflam.carianstyle.bus.ICancellableEvent;
import pers.roinflam.carianstyle.bus.SubscribeEvent;
import pers.roinflam.carianstyle.event.client.FakeClientEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台事件类的结构、双端 / 仅客户端订阅类的参数检查。
 * <p>
 * 单测的类路径上没有原版类，带原版类型的字段型实现在测试台的总线场景里、真服务端上验；
 * 这里只按类名加载（加载类不解析方法签名里的原版类型）。
 * </p>
 */
class EventClassesTest {

    private static Class<?> c(String simple) throws ClassNotFoundException {
        return Class.forName("pers.roinflam.carianstyle.event." + simple, false, EventClassesTest.class.getClassLoader());
    }

    @Test
    void onlyDeclaredLogicEventsAreCancellable() throws Exception {
        for (String s : new String[]{"LivingDeathEvent", "LivingHealEvent", "LivingChangeTargetEvent", "BlockEvent$BreakEvent"}) {
            assertTrue(ICancellableEvent.class.isAssignableFrom(c(s)), s);
        }
        for (String s : new String[]{"EntityJoinLevelEvent", "EntityLeaveLevelEvent", "ProjectileImpactEvent", "PlayerTickEvent$Pre",
                "ServerTickEvent$Pre", "MobEffectEvent$Remove", "CriticalHitEvent", "LivingKnockBackEvent",
                "LivingExperienceDropEvent", "BlockDropsEvent", "PlayerEvent$Clone"}) {
            assertFalse(ICancellableEvent.class.isAssignableFrom(c(s)), s);
        }
    }

    @Test
    void hierarchyFollowsNeoForge() throws Exception {
        assertTrue(c("LivingEvent").isAssignableFrom(c("LivingDeathEvent")));
        assertTrue(c("PlayerEvent").isAssignableFrom(c("CriticalHitEvent")));
        assertTrue(c("PlayerEvent").isAssignableFrom(c("PlayerTickEvent$Pre")));
        assertTrue(c("PlayerEvent").isAssignableFrom(c("PlayerEvent$PlayerLoggedInEvent")));
        assertTrue(c("EntityEvent").isAssignableFrom(c("EntityJoinLevelEvent")));
        assertTrue(c("BlockEvent").isAssignableFrom(c("BlockDropsEvent")));
    }

    @EventBusSubscriber
    public static class BothSidesListensClient {
        @SubscribeEvent
        public static void x(FakeClientEvent e) {
        }
    }

    public static class UnannotatedListensClient {
        @SubscribeEvent
        public static void x(FakeClientEvent e) {
        }
    }

    @EventBusSubscriber(side = EventBusSubscriber.Side.CLIENT)
    public static class ClientListensClient {
        @SubscribeEvent
        public static void x(FakeClientEvent e) {
        }
    }

    @Test
    void bothSidesSubscriberMayNotListenToClientEvents() {
        CarianEventBus bus = new CarianEventBus();
        assertThrows(IllegalArgumentException.class, () -> bus.register(BothSidesListensClient.class));
        assertThrows(IllegalArgumentException.class, () -> bus.register(UnannotatedListensClient.class));
        bus.register(ClientListensClient.class);
        assertEquals(1, bus.dump(FakeClientEvent.class).size());
    }
}
