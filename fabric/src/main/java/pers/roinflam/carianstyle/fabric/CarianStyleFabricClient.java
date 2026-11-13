package pers.roinflam.carianstyle.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric 客户端入口（{@code fabric.mod.json} 的 {@code client}），专用服不会调。
 * <p>
 * 客户端订阅、渲染、按键、HUD 的初始化以后都放这里；目前只把客户端事件的转发挂上。
 */
public final class CarianStyleFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientEvents.init();
    }
}
