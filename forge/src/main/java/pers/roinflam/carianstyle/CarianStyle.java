package pers.roinflam.carianstyle;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.forge.ForgeClientEntry;
import pers.roinflam.carianstyle.forge.ForgeModEvents;
import pers.roinflam.carianstyle.forge.forward.ForgeForwarding;
import pers.roinflam.carianstyle.forge.platform.ForgePlatformRegistry;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.scan.CarianSubscribers;
import pers.roinflam.carianstyle.scan.SubscriberSide;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

/**
 * 卡利亚式附魔模组主类
 */
@Mod(Reference.MOD_ID)
public class CarianStyle {

    public static CarianStyle instance;

    public CarianStyle() {
        instance = this;

        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始加载模组");

        try {
            LogUtil.info("卡利亚式附魔 - 正在注册网络通信处理器");
            // 主通道和可视化通道（叠层 HUD、自绘特效）都照 CarianNetwork 的表登记
            ForgeModEvents.registerNetwork();
            LogUtil.info("卡利亚式附魔 - 网络通信处理器注册成功");
            LogUtil.info("卡利亚式附魔 - 可视化网络通道注册成功");

            // 获取模组事件总线
            IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
            ForgeForwarding.install(FMLEnvironment.dist, modEventBus);

            // 注册配置：回调先挂再登记
            ForgeModEvents.register(modEventBus);
            ModLoadingContext.get().registerConfig(
                    net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                    ConfigLoader.COMMON_CONFIG,
                    "carianstyle-common.toml"
            );

            LogUtil.info("卡利亚式附魔 - 正在注册游戏内容");
            CarianContent.bootstrap();

            // 附魔必须在构造函数里声明完，平台的注册器挂上总线之后就不收新条目了
            LogUtil.info("卡利亚式附魔 - 正在按清单注册附魔");
            EnchantmentRegistry.loadFromIndex();
            LogUtil.info("卡利亚式附魔 - 附魔注册完成");

            ForgePlatformRegistry.attach(modEventBus);
            LogUtil.info("卡利亚式附魔 - 游戏内容注册器挂载完成");

            // 注册设置事件
            modEventBus.addListener(this::commonSetup);

            // 照构建时生成的订阅清单把订阅类登记到本模组总线：先两端的，客户端再登记仅客户端的。
            // 放在构造器最后，和平台自动登记订阅类的时机一样（模组实例建好之后），订阅类的静态初始化照旧晚于内容注册
            LogUtil.info("卡利亚式附魔 - 正在登记事件订阅类");
            int subscribers = CarianSubscribers.register(SubscriberSide.BOTH, CarianBus::register).size();
            if (Services.platform().isClient()) {
                subscribers += CarianSubscribers.register(SubscriberSide.CLIENT, CarianBus::register).size();
            }
            LogUtil.info("卡利亚式附魔 - 事件订阅类登记完成，共 %d 个", subscribers);

            // 配置界面、按键、HUD 层和客户端设置阶段
            if (Services.platform().isClient()) {
                ForgeClientEntry.init(modEventBus);
            }

            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 模组构造函数发生错误", e);
            throw new RuntimeException("卡利亚式附魔加载失败", e);
        }
    }

    /**
     * 通用设置阶段（客户端和服务端都执行）
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LogUtil.separator();

        try {
            // 在主线程上做：叠层显示的登记表是普通集合，通用设置的监听可能并行跑
            event.enqueueWork(CarianStyleCommon::setup);

            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 通用设置阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔通用设置失败", e);
        }
    }
}
