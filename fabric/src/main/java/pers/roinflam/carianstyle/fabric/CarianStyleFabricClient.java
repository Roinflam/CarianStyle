package pers.roinflam.carianstyle.fabric;

import net.fabricmc.api.ClientModInitializer;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.client.CarianStyleClientInit;
import pers.roinflam.carianstyle.client.ClientContent;
import pers.roinflam.carianstyle.fabric.client.FabricClientNetwork;
import pers.roinflam.carianstyle.fabric.client.FabricCreativeTabFilter;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.util.LogUtil;

/**
 * Fabric 客户端入口（{@code fabric.mod.json} 的 {@code client}），专用服不会调。
 * <p>
 * Loader 先跑完所有模组的主入口才跑客户端入口，所以到这里网络包类型、注册内容、配置、
 * 两端和仅客户端的订阅类都已由 {@link CarianStyleFabric} 登记好。这里按 NeoForge 客户端入口的顺序做：
 * </p>
 * <ol>
 *   <li>客户端事件转发进本模组总线；</li>
 *   <li>收包处理器和版本握手的回包；</li>
 *   <li>创造模式物品栏去掉被禁用附魔的书；</li>
 *   <li>按键、HUD 层（Fabric 上当场登记，NeoForge 上要先记下、到注册事件里再交）；</li>
 *   <li>本机视觉配置、实体渲染器、方块渲染层、光环（{@link CarianStyleClientInit#setup}）。
 *       NeoForge 在客户端设置阶段排到主线程执行；Fabric 的入口本来就在客户端主线程上，直接调；</li>
 *   <li>最后检查转发表：主入口在客户端上特意不查，要等这里把客户端事件也声明完，否则客户端事件的监听会被误报没人发。</li>
 * </ol>
 * <p>
 * 配置界面的入口不在这里：Fabric 上模组列表由 Mod Menu 提供，入口是 {@link ModMenuIntegration}。
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
            FabricCreativeTabFilter.register();

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
