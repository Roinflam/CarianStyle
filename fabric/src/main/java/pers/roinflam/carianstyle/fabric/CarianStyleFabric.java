package pers.roinflam.carianstyle.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig;
import pers.roinflam.carianstyle.CarianContent;
import pers.roinflam.carianstyle.CarianStyleCommon;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.fabric.network.FabricNetwork;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.scan.CarianSubscribers;
import pers.roinflam.carianstyle.scan.SubscriberSide;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

/**
 * Fabric 主入口（{@code fabric.mod.json} 的 {@code main}），客户端和专用服都会调。
 * <p>
 * 先后照 NeoForge 主类构造器：事件转发 → 网络 → 配置 → 注册内容 → 附魔行为对象 → 事件订阅类 → 通用设置。
 * 和 NeoForge 的差别：
 * </p>
 * <ul>
 *   <li>Fabric 当场注册，没有注册事件可等，内容必须在这里声明完；</li>
 *   <li>没有「通用设置阶段」，{@link CarianStyleCommon#setup} 放在最后直接调（入口在主线程上逐个模组执行，不用排队）；</li>
 *   <li>内置数据包不在这里挂，由 {@code mixin/pack} 在服务端数据包仓库建好时加上；</li>
 *   <li>没有「加载完成」事件。专用服上本模组的监听到这里都登记完了，直接在入口末尾检查转发表；
 *       客户端还有客户端入口要声明客户端事件，检查放在客户端入口末尾。</li>
 * </ul>
 */
public final class CarianStyleFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // 平台事件转发进本模组总线，要在任何本模组监听登记之前装好
        FabricLogicEvents.init();

        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始加载模组");

        try {
            // 网络包类型两端都登记；客户端的收包处理器和握手回包在客户端入口挂
            LogUtil.info("卡利亚式附魔 - 正在注册网络通信处理器");
            FabricNetwork.register();
            LogUtil.info("卡利亚式附魔 - 网络通信处理器注册成功");

            registerConfig();

            // ==================== 注册内容 ====================
            LogUtil.info("卡利亚式附魔 - 正在注册游戏内容");
            CarianContent.bootstrap();
            LogUtil.info("卡利亚式附魔 - 游戏内容注册完成");

            // 建附魔行为对象：内置数据包、配置都要用到这张表
            LogUtil.info("卡利亚式附魔 - 正在建立附魔行为对象");
            EnchantmentRegistry.loadFromIndex();
            LogUtil.info("卡利亚式附魔 - 附魔行为对象建立完成");

            // 本模组自己发的事件（生物 tick 由 common 的 mixin 发，伤害三段由 mixin/damage 发）和逻辑类转发的事件声明给总线
            CarianBus.provide(LivingTickEvent.class, LivingAttackEvent.class, LivingHurtEvent.class,
                    LivingDamageEvent.class);
            CarianBus.provide(FabricLogicEvents.forwardedEvents().toArray(new Class<?>[0]));

            // 照构建时生成的订阅清单登记订阅类：先两端的，客户端再登记仅客户端的（同 NeoForge，登记在主入口里做）
            LogUtil.info("卡利亚式附魔 - 正在登记事件订阅类");
            int subscribers = CarianSubscribers.register(SubscriberSide.BOTH, CarianBus::register).size();
            if (Services.platform().isClient()) {
                subscribers += CarianSubscribers.register(SubscriberSide.CLIENT, CarianBus::register).size();
            }
            LogUtil.info("卡利亚式附魔 - 事件订阅类登记完成，共 %d 个", subscribers);

            CarianStyleCommon.setup();

            if (!Services.platform().isClient()) {
                CarianBus.verifyProvided();
            }

            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 模组初始化发生错误", e);
            throw new RuntimeException("卡利亚式附魔加载失败", e);
        }
    }

    /**
     * 登记 COMMON 配置（文件名和 NeoForge 版、1.20.1 版一样）。
     * <p>
     * 先挂加载 / 重载回调再登记：Forge Config API Port 在登记时当场读文件，回调挂晚了第一次加载就收不到，
     * 配置的版本号一直停在 0。重载回调可能在文件监视线程上执行，{@link ConfigLoader#onLoaded} 本身按这个前提写。
     * </p>
     */
    private static void registerConfig() {
        // 回调按模组 id 分开，本模组只登记这一份配置
        NeoForgeModConfigEvents.loading(Reference.MOD_ID).register(config -> ConfigLoader.onLoaded());
        NeoForgeModConfigEvents.reloading(Reference.MOD_ID).register(config -> ConfigLoader.onLoaded());
        NeoForgeConfigRegistry.INSTANCE.register(Reference.MOD_ID, ModConfig.Type.COMMON, ConfigLoader.COMMON_CONFIG,
                "carianstyle-common.toml");
    }
}
