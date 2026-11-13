package pers.roinflam.carianstyle;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.scan.CarianSubscribers;
import pers.roinflam.carianstyle.scan.SubscriberSide;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.neoforge.NeoForgeModEvents;
import pers.roinflam.carianstyle.neoforge.platform.NeoForgePlatformRegistry;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

/**
 * 卡利亚式附魔模组主类
 * <p>
 * 1.21.1（NeoForge）：模组总线和 {@link ModContainer} 由构造器参数传进来，不再从全局上下文取。
 * 附魔不再走 {@code DeferredRegister}：1.21.1 的附魔是数据包注册表，本模组的附魔类只是行为对象，
 * 由 {@link EnchantmentRegistry#loadFromIndex} 照构建时生成的清单建出来，注册表条目由内置数据包提供。
 * 配置界面入口放在只在客户端加载的 {@link CarianStyleClient} 里，主类常量池不带客户端类。
 * </p>
 * <p>
 * 这里只做 NeoForge 特有的接线：挂模组总线、登记配置文件、把平台事件转给通用代码。
 * 注册内容由 {@link CarianContent} 声明，通用设置由 {@link CarianStyleCommon} 做，两边和平台无关。
 * </p>
 */
@Mod(Reference.MOD_ID)
public class CarianStyle {

    public static CarianStyle instance;

    public CarianStyle(IEventBus modEventBus, ModContainer container) {
        instance = this;

        // 平台事件转发进本模组总线，要在任何本模组监听登记之前装好（见 NeoForwarding）
        pers.roinflam.carianstyle.neoforge.forward.NeoForwarding.install(net.neoforged.fml.loading.FMLEnvironment.dist, modEventBus);

        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始加载模组");

        try {
            // 网络包在 MOD 总线的 RegisterPayloadHandlersEvent 里照 CarianNetwork.PACKETS 登记（NeoForge 的唯一入口），
            // 主通道和可视化通道两组版本号各自独立
            LogUtil.info("卡利亚式附魔 - 正在注册网络通信处理器");
            NeoForgeModEvents.registerNetwork(modEventBus);
            LogUtil.info("卡利亚式附魔 - 网络通信处理器注册成功");
            LogUtil.info("卡利亚式附魔 - 可视化网络通道注册成功");

            // 配置加载 / 重载、挂内置数据包、创造物品栏过滤这几个平台事件先挂上，再登记配置（回调先于登记，不怕漏第一次加载）
            NeoForgeModEvents.register(modEventBus);

            // 注册配置（文件名和 1.20.1 一样，老配置直接沿用）
            container.registerConfig(
                    ModConfig.Type.COMMON,
                    ConfigLoader.COMMON_CONFIG,
                    "carianstyle-common.toml"
            );

            // ==================== 注册内容 ====================
            LogUtil.info("卡利亚式附魔 - 正在注册游戏内容");

            // 声明方块、方块实体、实体、物品、药水效果，再把平台的注册器挂到模组总线
            CarianContent.bootstrap();
            NeoForgePlatformRegistry.attach(modEventBus);
            LogUtil.info("卡利亚式附魔 - 游戏内容注册器挂载完成");

            // 建附魔行为对象（必须在构造函数中完成：内置数据包、配置都要用到这张表）
            LogUtil.info("卡利亚式附魔 - 正在建立附魔行为对象");
            EnchantmentRegistry.loadFromIndex();
            LogUtil.info("卡利亚式附魔 - 附魔行为对象建立完成");

            // 注册设置事件
            modEventBus.addListener(this::commonSetup);
            modEventBus.addListener(this::clientSetup);

            // 本模组自己发的事件（生物 tick、伤害三段，由 mixin 发出）声明给总线，要在加载完成的检查之前
            CarianBus.provide(LivingTickEvent.class, LivingAttackEvent.class, LivingHurtEvent.class,
                    LivingDamageEvent.class);

            // 照构建时生成的订阅清单把订阅类登记到本模组总线：先两端的，客户端再登记仅客户端的。
            // 放在构造器最后，和平台自动登记订阅类的时机一样（模组实例建好之后），订阅类的静态初始化照旧晚于内容注册
            LogUtil.info("卡利亚式附魔 - 正在登记事件订阅类");
            int subscribers = CarianSubscribers.register(SubscriberSide.BOTH, CarianBus::register).size();
            if (Services.platform().isClient()) {
                subscribers += CarianSubscribers.register(SubscriberSide.CLIENT, CarianBus::register).size();
            }
            LogUtil.info("卡利亚式附魔 - 事件订阅类登记完成，共 %d 个", subscribers);

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

    /**
     * 客户端设置阶段
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始客户端设置阶段");

        try {
            // 使用客户端处理器处理客户端设置
            ClientSetupHandler.onClientSetup(event);
            LogUtil.info("卡利亚式附魔 - 客户端设置阶段完成");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 客户端设置阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔客户端设置失败", e);
        }
    }
}
