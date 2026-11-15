package pers.roinflam.carianstyle.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.carianstyle.client.CarianStyleClientInit;
import pers.roinflam.carianstyle.client.ClientContent;
import pers.roinflam.carianstyle.config.ClothConfigScreen;
import pers.roinflam.carianstyle.forge.platform.ForgeClientPlatform;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;

/**
 * Forge 客户端入口：配置界面、按键、HUD 层和客户端设置阶段。
 * <p>
 * Forge 1.20.1 一个模组只能有一个 {@code @Mod} 类，所以由主类构造器在客户端上调 {@link #init}；
 * 专用服上不调，本类和它引用的客户端类都不会被加载。
 * </p>
 */
public final class ForgeClientEntry {

    private static boolean done;

    private ForgeClientEntry() {
    }

    /**
     * 只许调一次，在主类构造器里调（按键、HUD 层要赶在各自的注册事件之前记下）。
     *
     * @param modEventBus 本模组的模组总线
     */
    public static void init(@Nonnull IEventBus modEventBus) {
        if (done) {
            throw new IllegalStateException("Forge 客户端入口已经初始化过了");
        }
        done = true;

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, screen) -> ClothConfigScreen.createConfigScreen(screen)
                )
        );

        ForgeClientPlatform.attach(modEventBus);
        ClientPlatform platform = Services.client();
        ClientContent.registerKeyMappings(platform);
        ClientContent.registerHudLayers(platform);

        modEventBus.addListener(ForgeClientEntry::clientSetup);
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
