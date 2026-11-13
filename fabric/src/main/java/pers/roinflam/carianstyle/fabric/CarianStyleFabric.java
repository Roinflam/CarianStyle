package pers.roinflam.carianstyle.fabric;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric 服务端 / 通用入口（{@code fabric.mod.json} 的 {@code main}）。
 * <p>
 * 内容注册、配置、网络、通用初始化等业务代码挪进 common 之后在这里按顺序调；
 * 目前只把逻辑事件的转发挂上。
 */
public final class CarianStyleFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLogicEvents.init();
    }
}
