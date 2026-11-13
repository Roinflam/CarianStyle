package pers.roinflam.carianstyle;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.render.GlintbladesRender;
import pers.roinflam.carianstyle.init.*;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import java.util.Arrays;

/**
 * 卡利亚式附魔模组主类
 * <p>
 * 1.21.1（NeoForge）：模组总线和 {@link ModContainer} 由构造器参数传进来，不再从全局上下文取。
 * 附魔不再走 {@code DeferredRegister}：1.21.1 的附魔是数据包注册表，本模组的附魔类只是行为对象，
 * 由 {@link EnchantmentRegistry#scanAndRegister} 扫注解建出来，注册表条目由内置数据包提供。
 * 配置界面入口放在只在客户端加载的 {@link CarianStyleClient} 里，主类常量池不带客户端类。
 * </p>
 */
@Mod(Reference.MOD_ID)
public class CarianStyle {

    public static CarianStyle instance;

    public CarianStyle(IEventBus modEventBus, ModContainer container) {
        instance = this;

        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始加载模组");

        try {
            // 网络包在 MOD 总线的 RegisterPayloadHandlersEvent 里登记（NeoForge 的唯一入口）
            LogUtil.info("卡利亚式附魔 - 正在注册网络通信处理器");
            modEventBus.addListener(pers.roinflam.carianstyle.network.NetworkHandler::register);
            LogUtil.info("卡利亚式附魔 - 网络通信处理器注册成功");

            // 注册可视化系统专用网络通道（叠层 HUD 同步用，与现有 NetworkHandler 独立，版本号也各自独立）
            modEventBus.addListener(pers.roinflam.carianstyle.network.VisualNetwork::register);
            LogUtil.info("卡利亚式附魔 - 可视化网络通道注册成功");

            // 注册配置（文件名和 1.20.1 一样，老配置直接沿用）
            container.registerConfig(
                    ModConfig.Type.COMMON,
                    ConfigLoader.COMMON_CONFIG,
                    "carianstyle-common.toml"
            );

            // ==================== 注册 DeferredRegister ====================
            LogUtil.info("卡利亚式附魔 - 正在注册游戏内容");

            // 注册方块
            CarianStyleBlocks.BLOCKS.register(modEventBus);
            LogUtil.info("卡利亚式附魔 - 方块注册器已挂载");

            // 注册方块实体
            CarianStyleBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
            LogUtil.info("卡利亚式附魔 - 方块实体注册器已挂载");

            // 注册实体类型
            CarianStyleEntity.ENTITY_TYPES.register(modEventBus);
            LogUtil.info("卡利亚式附魔 - 实体类型注册器已挂载");

            // 注册物品
            CarianStyleItem.ITEMS.register(modEventBus);
            LogUtil.info("卡利亚式附魔 - 物品注册器已挂载");

            // 注册药水效果
            CarianStylePotion.MOB_EFFECTS.register(modEventBus);
            LogUtil.info("卡利亚式附魔 - 药水效果注册器已挂载");

            // 扫描附魔行为对象（必须在构造函数中完成：内置数据包、配置都要用到这张表）
            LogUtil.info("卡利亚式附魔 - 正在扫描并注册附魔");
            EnchantmentRegistry.scanAndRegister("pers.roinflam.carianstyle.enchantment");
            LogUtil.info("卡利亚式附魔 - 附魔扫描注册完成");

            LogUtil.info("卡利亚式附魔 - 游戏内容注册器挂载完成");

            // 注册设置事件
            modEventBus.addListener(this::commonSetup);
            modEventBus.addListener(this::clientSetup);

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
        LogUtil.info("卡利亚式附魔 - 开始通用设置阶段");

        try {
            event.enqueueWork(() -> {
                // 注册数据管理器到事件总线
                LogUtil.info("卡利亚式附魔 - 正在注册数据管理器");
                NeoForge.EVENT_BUS.register(EnchantmentDataManager.class);
                LogUtil.debug("卡利亚式附魔 - 数据管理器注册成功");

                // 注册叠层显示读取器（HUD 数据源，双端都需要；默认无读取器，接入后才显示）
                LogUtil.info("卡利亚式附魔 - 正在注册叠层显示");
                pers.roinflam.carianstyle.visual.CarianStyleStackDisplays.init();
                LogUtil.debug("卡利亚式附魔 - 叠层显示注册完成");

                // 加载并验证配置
                LogUtil.info("卡利亚式附魔 - 正在验证配置文件");
                loadAndValidateConfig();
                LogUtil.debug("卡利亚式附魔 - 配置文件验证完成");

                // 记录注册内容统计
                logRegisteredContent();

                // 执行最终验证
                performFinalValidation();

                LogUtil.info("卡利亚式附魔 - 通用设置阶段完成");
            });

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

    /**
     * 加载并验证配置文件
     */
    private void loadAndValidateConfig() {
        LogUtil.debug("卡利亚式附魔 - 正在验证配置文件");

        // 检查禁用的附魔
        if (ConfigLoader.uninstallEnchantment != null && ConfigLoader.uninstallEnchantment.length > 0) {
            LogUtil.warn("卡利亚式附魔 - 检测到 %d 个被禁用的附魔", ConfigLoader.uninstallEnchantment.length);
            LogUtil.debug("卡利亚式附魔 - 禁用的附魔列表：%s", Arrays.toString(ConfigLoader.uninstallEnchantment));
            // 逐条核对 id 的检查挪到 EnchantmentRegistry：1.21.1 的附魔注册表要等世界加载才有，
            // 这里（通用设置阶段）还查不到，本模组的 id 在扫完注解后核对，别的模组的在服务器启动后核对
        }

        // 检查碎岩者范围
        if (ConfigLoader.rockBlasterMaxRange > 15) {
            LogUtil.warn("卡利亚式附魔 - 碎岩者最大范围设置过大（%d），可能导致性能问题",
                    ConfigLoader.rockBlasterMaxRange);
        }
        LogUtil.debug("卡利亚式附魔 - 碎岩者最大范围：%d", ConfigLoader.rockBlasterMaxRange);

        // 检查祈祷打击
        LogUtil.debug("卡利亚式附魔 - 祈祷打击最大生命值：%d", ConfigLoader.prayerfulStrikeMaxHealth);

        // 检查附魔难度
        if (ConfigLoader.enchantingDifficulty != 1.0) {
            LogUtil.info("卡利亚式附魔 - 附魔难度倍率已设置为：%.2f", ConfigLoader.enchantingDifficulty);
        }
        LogUtil.debug("卡利亚式附魔 - 附魔难度倍率：%.2f", ConfigLoader.enchantingDifficulty);

        // 检查宝藏附魔配置
        if (ConfigLoader.isTreasureVeryRaryEnchantment) {
            LogUtil.info("卡利亚式附魔 - 超稀有(Very Rare)级别附魔已变为宝藏附魔");
        }
        if (ConfigLoader.isTreasureRaryEnchantment) {
            LogUtil.info("卡利亚式附魔 - 稀有(Rare)级别附魔已变为宝藏附魔");
        }
        if (ConfigLoader.isTreasureUncommonEnchantment) {
            LogUtil.info("卡利亚式附魔 - 罕见(Uncommon)级别附魔已变为宝藏附魔");
        }

        // 检查等级限制
        if (ConfigLoader.levelLimit) {
            LogUtil.info("卡利亚式附魔 - 附魔等级限制已启用");
        }
        LogUtil.debug("卡利亚式附魔 - 附魔等级限制：%s", ConfigLoader.levelLimit ? "启用" : "禁用");

        // 检查详细日志
        if (ConfigLoader.enableDetailedLogging) {
            LogUtil.info("卡利亚式附魔 - 详细日志已启用");
        }
        LogUtil.debug("卡利亚式附魔 - 详细日志状态：%s", ConfigLoader.enableDetailedLogging ? "启用" : "禁用");
    }

    /**
     * 记录已注册内容的统计信息
     */
    private void logRegisteredContent() {
        LogUtil.info("卡利亚式附魔 - 正在统计已注册内容");

        // 统计附魔
        int enchantmentCount = CarianStyleEnchantments.getRegisteredCount();
        LogUtil.info("卡利亚式附魔 - 已注册附魔数量：%d", enchantmentCount);

        // 统计药水效果
        int potionCount = CarianStylePotion.MOB_EFFECTS.getEntries().size();
        LogUtil.info("卡利亚式附魔 - 已注册药水效果数量：%d", potionCount);

        // 统计方块
        int blockCount = CarianStyleBlocks.BLOCKS.getEntries().size();
        LogUtil.info("卡利亚式附魔 - 已注册方块数量：%d", blockCount);

        // 统计物品
        int itemCount = CarianStyleItem.ITEMS.getEntries().size();
        LogUtil.info("卡利亚式附魔 - 已注册物品数量：%d", itemCount);

        // 统计实体
        int entityCount = CarianStyleEntity.ENTITY_TYPES.getEntries().size();
        LogUtil.info("卡利亚式附魔 - 已注册实体数量：%d", entityCount);

        // 总计
        int totalCount = enchantmentCount + potionCount + blockCount + itemCount + entityCount;
        LogUtil.info("卡利亚式附魔 - 已注册内容总计：%d", totalCount);
    }

    /**
     * 执行最终验证
     */
    private void performFinalValidation() {
        LogUtil.debug("卡利亚式附魔 - 正在执行最终验证");

        boolean isValid = true;

        if (CarianStyleEnchantments.getRegisteredCount() == 0) {
            LogUtil.error("卡利亚式附魔 - 错误：没有附魔被注册");
            isValid = false;
        }

        if (CarianStylePotion.MOB_EFFECTS.getEntries().isEmpty()) {
            LogUtil.error("卡利亚式附魔 - 错误：没有药水效果被注册");
            isValid = false;
        }

        if (CarianStyleEntity.ENTITY_TYPES.getEntries().isEmpty()) {
            LogUtil.error("卡利亚式附魔 - 错误：没有实体被注册");
            isValid = false;
        }

        if (isValid) {
            LogUtil.debug("卡利亚式附魔 - 最终验证通过，所有组件已正确初始化");
            LogUtil.info("卡利亚式附魔 - 模组加载完成，祝你游戏愉快！");
        } else {
            LogUtil.error("卡利亚式附魔 - 最终验证失败，部分组件未正确初始化");
            throw new RuntimeException("卡利亚式附魔验证失败");
        }
    }
}
