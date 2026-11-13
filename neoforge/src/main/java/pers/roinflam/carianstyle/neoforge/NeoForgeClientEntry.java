package pers.roinflam.carianstyle.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import pers.roinflam.carianstyle.client.CarianStyleClientInit;
import pers.roinflam.carianstyle.client.ClientContent;
import pers.roinflam.carianstyle.config.ClothConfigScreen;
import pers.roinflam.carianstyle.neoforge.platform.NeoForgeClientPlatform;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

/**
 * 卡利亚式附魔在 NeoForge 上的客户端入口：只在客户端实例化。
 * <p>
 * <b>为什么单独一个类：</b>这里引用的配置界面、按键、HUD、渲染器都是客户端类。
 * 写进主类的话，主类的常量池就带上了客户端类，专用服加载主类时有被连带解析的风险。
 * NeoForge 允许同一个 modid 有多个入口类，{@code dist = Dist.CLIENT} 的入口只在客户端实例化，
 * 专用服根本不会碰这个类。
 * </p>
 * <p>
 * 做的事：
 * </p>
 * <ol>
 *   <li>登记模组列表里的「配置」按钮（{@link IConfigScreenFactory}，界面本身是 {@link ClothConfigScreen}）；</li>
 *   <li>把客户端平台实现挂到模组总线，再把按键和 HUD 层交给它，到 NeoForge 的注册事件里才真正登记；</li>
 *   <li>客户端设置阶段在主线程上跑 {@link CarianStyleClientInit#setup}（本机视觉配置、实体渲染器、方块渲染层、光环）。</li>
 * </ol>
 * <p>
 * 仅客户端的事件订阅类由主类按订阅清单登记，不在这里。
 * </p>
 */
@Mod(value = Reference.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntry {

    public NeoForgeClientEntry(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> ClothConfigScreen.createConfigScreen(parent));

        NeoForgeClientPlatform.attach(modEventBus);
        ClientPlatform platform = Services.client();
        ClientContent.registerKeyMappings(platform);
        ClientContent.registerHudLayers(platform);

        modEventBus.addListener(NeoForgeClientEntry::clientSetup);
    }

    /**
     * 客户端设置阶段。
     */
    private static void clientSetup(final FMLClientSetupEvent event) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始客户端设置阶段");

        try {
            event.enqueueWork(() -> CarianStyleClientInit.setup(Services.client()));
            LogUtil.info("卡利亚式附魔 - 客户端设置阶段完成");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 客户端设置阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔客户端设置失败", e);
        }
    }
}
