package pers.roinflam.carianstyle.fabric;

import net.fabricmc.api.ClientModInitializer;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.client.CarianStyleClientInit;
import pers.roinflam.carianstyle.client.ClientContent;
import pers.roinflam.carianstyle.fabric.client.FabricClientNetwork;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.util.LogUtil;

/**
 * Fabric 客户端入口，专用服不会调。
 * <p>
 * Loader 先跑完所有模组的主入口才跑这里，网络、注册内容、配置和订阅类都已由 {@link CarianStyleFabric} 登记好。
 * 顺序照 Forge 客户端入口：客户端事件转发、收包和握手回包、按键和 HUD 层、{@link CarianStyleClientInit#setup}。
 * 入口本来就在客户端主线程上，setup 直接调。转发表最后才查：主入口在客户端上特意不查，
 * 要等这里把客户端事件也声明完，否则客户端事件的监听会被误报没人发。
 * </p>
 */
public final class CarianStyleFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始客户端设置阶段");

        try {
            FabricClientEvents.init();
            FabricClientNetwork.register();

            ClientPlatform platform = Services.client();
            ClientContent.registerKeyMappings(platform);
            ClientContent.registerHudLayers(platform);

            CarianStyleClientInit.setup(platform);

            CarianBus.verifyProvided();

            LogUtil.info("卡利亚式附魔 - 客户端设置阶段完成");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 客户端设置阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔客户端设置失败", e);
        }
    }
}
