package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import pers.roinflam.carianstyle.bus.EventBusSubscriber;
import pers.roinflam.carianstyle.bus.SubscribeEvent;
import pers.roinflam.carianstyle.event.client.ClientTickEvent;

/**
 * 每个客户端 tick 推进 {@link AoeEffectManager} 里的范围特效。
 * <p>
 * 拿当前世界要用 {@link Minecraft}，所以单独放一个只在客户端登记的类；
 * {@link AoeEffectManager} 本身不碰客户端专有的类，网络包类引用它时两端都能加载。
 * </p>
 */
@EventBusSubscriber(side = EventBusSubscriber.Side.CLIENT)
public final class AoeEffectClientTicker {

    private AoeEffectClientTicker() {
    }

    /**
     * 客户端 tick 末尾：解绑已死亡 / 已卸载的绑定实体，移除到期特效；离开世界时清空。
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        AoeEffectManager.tick(Minecraft.getInstance().level);
    }
}
