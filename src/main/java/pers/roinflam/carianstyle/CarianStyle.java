package pers.roinflam.carianstyle;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.*;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.proxy.CommonProxy;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 卡利亚式附魔模组主类
 * <p>
 * 这是模组的核心入口类，负责协调模组的初始化流程
 * </p>
 */
@Mod(
        modid = Reference.MOD_ID,
        useMetadata = true,
        guiFactory = "pers.roinflam.carianstyle.gui.ConfigGuiFactory"
)
public class CarianStyle {

    @Mod.Instance
    public static CarianStyle instance;

    @SidedProxy(
            clientSide = Reference.CLIENT_PROXY_CLASS,
            serverSide = Reference.COMMON_PROXY_CLASS
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始预初始化阶段");
        LogUtil.debug("卡利亚式附魔 - 当前运行环境：%s", evt.getSide().name());

        try {
            // 注册EnchantmentBase到事件总线
            LogUtil.info("卡利亚式附魔 - 正在注册附魔事件处理器");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    pers.roinflam.carianstyle.base.enchantment.EnchantmentBase.class
            );
            LogUtil.debug("卡利亚式附魔 - 附魔事件处理器注册成功");

            // 注册数据管理器到事件总线
            LogUtil.info("卡利亚式附魔 - 正在注册数据管理器");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager.class
            );
            LogUtil.debug("卡利亚式附魔 - 数据管理器注册成功");

            // 扫描并注册所有带注解的附魔
            LogUtil.info("卡利亚式附魔 - 正在扫描并注册附魔");
            EnchantmentRegistry.scanAndRegister(evt.getAsmData());
            LogUtil.debug("卡利亚式附魔 - 附魔扫描注册完成");

            // 注册网络通信处理器
            LogUtil.info("卡利亚式附魔 - 正在注册网络通信处理器");
            NetworkRegistryHandler.register();
            LogUtil.debug("卡利亚式附魔 - 网络通信处理器注册成功");

            // 注册实体渲染器
            LogUtil.info("卡利亚式附魔 - 正在注册实体渲染器");
            proxy.registerEntityRenderer();
            LogUtil.debug("卡利亚式附魔 - 实体渲染器注册成功");

            // 设置创造模式标签页
            LogUtil.info("卡利亚式附魔 - 正在配置创造模式标签页");
            setupCreativeTabEnchantmentTypes();
            LogUtil.debug("卡利亚式附魔 - 创造模式标签页配置完成");

            // 加载并验证配置
            LogUtil.info("卡利亚式附魔 - 正在加载配置文件");
            loadAndValidateConfig();
            LogUtil.debug("卡利亚式附魔 - 配置文件加载并验证完成");

            LogUtil.info("卡利亚式附魔 - 预初始化阶段完成");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 预初始化阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔预初始化失败", e);
        }
    }

    @Mod.EventHandler
    public static void init(@Nonnull FMLInitializationEvent evt) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始初始化阶段");
        LogUtil.debug("卡利亚式附魔 - 当前运行环境：%s", evt.getSide().name());

        try {
            LogUtil.debug("卡利亚式附魔 - 执行自定义初始化逻辑");
            logRegisteredContent();

            LogUtil.info("卡利亚式附魔 - 初始化阶段完成");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 初始化阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔初始化失败", e);
        }
    }

    @Mod.EventHandler
    public static void postInit(@Nonnull FMLPostInitializationEvent evt) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始后初始化阶段");
        LogUtil.debug("卡利亚式附魔 - 当前运行环境：%s", evt.getSide().name());

        try {
            LogUtil.debug("卡利亚式附魔 - 执行自定义后初始化逻辑");
            performFinalValidation();

            LogUtil.info("卡利亚式附魔 - 后初始化阶段完成");
            LogUtil.info("卡利亚式附魔 - 模组加载完成，祝你游戏愉快！");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 后初始化阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔后初始化失败", e);
        }
    }

    /**
     * 设置创造模式标签页的附魔类型
     */
    private static void setupCreativeTabEnchantmentTypes() {
        LogUtil.debug("卡利亚式附魔 - 正在设置战斗标签页的附魔类型");

        @Nonnull List<EnumEnchantmentType> combatTypeList = new ArrayList<>(
                Arrays.asList(CreativeTabs.COMBAT.getRelevantEnchantmentTypes())
        );

        if (CarianStyleEnchantments.ARMS != null) {
            combatTypeList.add(CarianStyleEnchantments.ARMS);
            LogUtil.debug("卡利亚式附魔 - 已添加武器附魔类型到战斗标签页");
        }

        CreativeTabs.COMBAT.setRelevantEnchantmentTypes(
                combatTypeList.toArray(new EnumEnchantmentType[0])
        );

        LogUtil.debug("卡利亚式附魔 - 正在设置工具标签页的附魔类型");

        @Nonnull List<EnumEnchantmentType> toolTypeList = new ArrayList<>(
                Arrays.asList(CreativeTabs.TOOLS.getRelevantEnchantmentTypes())
        );

        if (CarianStyleEnchantments.PICKAEX != null) {
            toolTypeList.add(CarianStyleEnchantments.PICKAEX);
            LogUtil.debug("卡利亚式附魔 - 已添加镐子附魔类型到工具标签页");
        }

        CreativeTabs.TOOLS.setRelevantEnchantmentTypes(
                toolTypeList.toArray(new EnumEnchantmentType[0])
        );
    }

    /**
     * 加载并验证配置文件
     */
    private static void loadAndValidateConfig() {
        LogUtil.debug("卡利亚式附魔 - 正在验证配置文件");

        // 检查禁用的附魔
        if (ConfigLoader.uninstallEnchantment != null && ConfigLoader.uninstallEnchantment.length > 0) {
            LogUtil.warn("卡利亚式附魔 - 检测到 %d 个被禁用的附魔", ConfigLoader.uninstallEnchantment.length);
            LogUtil.debug("卡利亚式附魔 - 禁用的附魔列表：%s", Arrays.toString(ConfigLoader.uninstallEnchantment));
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
    private static void logRegisteredContent() {
        LogUtil.info("卡利亚式附魔 - 正在统计已注册内容");

        // 统计附魔
        int enchantmentCount = CarianStyleEnchantments.ENCHANTMENTS.size();
        LogUtil.info("卡利亚式附魔 - 已注册附魔数量：%d", enchantmentCount);

        // 统计药水效果
        int potionCount = CarianStylePotion.POTIONS.size();
        LogUtil.info("卡利亚式附魔 - 已注册药水效果数量：%d", potionCount);

        // 统计方块
        int blockCount = CarianStyleBlocks.BLOCKS.size();
        LogUtil.info("卡利亚式附魔 - 已注册方块数量：%d", blockCount);

        // 统计物品
        int itemCount = CarianStyleItem.ITEMS.size();
        LogUtil.info("卡利亚式附魔 - 已注册物品数量：%d", itemCount);

        // 统计实体
        int entityCount = CarianStyleEntity.ENTITY_ENTRIES.size();
        LogUtil.info("卡利亚式附魔 - 已注册实体数量：%d", entityCount);

        // 总计
        int totalCount = enchantmentCount + potionCount + blockCount + itemCount + entityCount;
        LogUtil.info("卡利亚式附魔 - 已注册内容总计：%d", totalCount);
    }

    /**
     * 执行最终验证
     */
    private static void performFinalValidation() {
        LogUtil.debug("卡利亚式附魔 - 正在执行最终验证");

        boolean isValid = true;

        if (CarianStyleEnchantments.ENCHANTMENTS.isEmpty()) {
            LogUtil.error("卡利亚式附魔 - 错误：没有附魔被注册");
            isValid = false;
        }

        if (CarianStylePotion.POTIONS.isEmpty()) {
            LogUtil.error("卡利亚式附魔 - 错误：没有药水效果被注册");
            isValid = false;
        }

        if (CarianStyleEntity.ENTITY_ENTRIES.isEmpty()) {
            LogUtil.error("卡利亚式附魔 - 错误：没有实体被注册");
            isValid = false;
        }

        if (isValid) {
            LogUtil.debug("卡利亚式附魔 - 最终验证通过，所有组件已正确初始化");
        } else {
            LogUtil.error("卡利亚式附魔 - 最终验证失败，部分组件未正确初始化");
            throw new RuntimeException("卡利亚式附魔验证失败");
        }
    }
}