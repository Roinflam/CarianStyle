// 文件：CarianStyle.java
// 路径：src/main/java/pers/roinflam/carianstyle/CarianStyle.java
package pers.roinflam.carianstyle;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
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
 *
 * @author RoinFlam
 * @version 1.0
 */
@Mod(
        modid = Reference.MOD_ID,
        useMetadata = true,
        guiFactory = "pers.roinflam.carianstyle.gui.ConfigGuiFactory"
)
public class CarianStyle {

    /**
     * 模组实例
     */
    @Mod.Instance
    public static CarianStyle instance;

    /**
     * 代理实例，用于处理客户端和服务端的差异
     */
    @SidedProxy(
            clientSide = Reference.CLIENT_PROXY_CLASS,
            serverSide = Reference.COMMON_PROXY_CLASS
    )
    public static CommonProxy proxy;

    /**
     * 模组预初始化阶段
     * <p>
     * 在此阶段进行：
     * 1. 网络通信注册
     * 2. 实体渲染器注册
     * 3. 创造模式标签页设置
     * 4. 配置文件加载验证
     * </p>
     *
     * @param evt 预初始化事件
     */
    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        LogUtil.separator();
        LogUtil.info("开始预初始化阶段...");
        LogUtil.debug("当前运行环境：%s", evt.getSide().name());

        try {
            // 注册网络通信处理器
            LogUtil.info("正在注册网络通信处理器...");
            NetworkRegistryHandler.register();
            LogUtil.debug("网络通信处理器注册成功");

            // 注册实体渲染器
            LogUtil.info("正在注册实体渲染器...");
            proxy.registerEntityRenderer();
            LogUtil.debug("实体渲染器注册成功");

            // 设置创造模式标签页的附魔类型
            LogUtil.info("正在配置创造模式标签页...");
            setupCreativeTabEnchantmentTypes();
            LogUtil.debug("创造模式标签页配置完成");

            // 加载并验证配置文件
            LogUtil.info("正在加载配置文件...");
            loadAndValidateConfig();
            LogUtil.debug("配置文件加载并验证完成");

            LogUtil.info("预初始化阶段完成！");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("预初始化阶段发生错误！", e);
            throw new RuntimeException("卡利亚式附魔模组预初始化失败", e);
        }
    }

    /**
     * 模组初始化阶段
     * <p>
     * 在此阶段可以进行模组间的交互和额外的初始化工作
     * </p>
     *
     * @param evt 初始化事件
     */
    @Mod.EventHandler
    public static void init(@Nonnull FMLInitializationEvent evt) {
        LogUtil.separator();
        LogUtil.info("开始初始化阶段...");
        LogUtil.debug("当前运行环境：%s", evt.getSide().name());

        try {
            // 在此处添加初始化逻辑
            LogUtil.debug("执行自定义初始化逻辑...");

            // 统计已注册的内容
            logRegisteredContent();

            LogUtil.info("初始化阶段完成！");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("初始化阶段发生错误！", e);
            throw new RuntimeException("卡利亚式附魔模组初始化失败", e);
        }
    }

    /**
     * 模组后初始化阶段
     * <p>
     * 在此阶段所有模组都已加载完成，可以进行最终的配置和优化
     * </p>
     *
     * @param evt 后初始化事件
     */
    @Mod.EventHandler
    public static void postInit(@Nonnull FMLPostInitializationEvent evt) {
        LogUtil.separator();
        LogUtil.info("开始后初始化阶段...");
        LogUtil.debug("当前运行环境：%s", evt.getSide().name());

        try {
            // 在此处添加后初始化逻辑
            LogUtil.debug("执行自定义后初始化逻辑...");

            // 最终验证
            performFinalValidation();

            LogUtil.info("后初始化阶段完成！");
            LogUtil.info("卡利亚式附魔模组加载完成，祝你游戏愉快！");
            LogUtil.separator();

        } catch (Exception e) {
            LogUtil.error("后初始化阶段发生错误！", e);
            throw new RuntimeException("卡利亚式附魔模组后初始化失败", e);
        }
    }

    /**
     * 设置创造模式标签页的附魔类型
     * <p>
     * 将自定义的附魔类型添加到对应的创造模式标签页中
     * </p>
     */
    private static void setupCreativeTabEnchantmentTypes() {
        LogUtil.debug("正在设置战斗标签页的附魔类型...");

        // 设置战斗标签页的附魔类型
        @Nonnull List<EnumEnchantmentType> combatTypeList = new ArrayList<>(
                Arrays.asList(CreativeTabs.COMBAT.getRelevantEnchantmentTypes())
        );

        if (CarianStyleEnchantments.ARMS != null) {
            combatTypeList.add(CarianStyleEnchantments.ARMS);
            LogUtil.debug("已添加武器附魔类型到战斗标签页");
        }

        if (CarianStyleEnchantments.SHIELD != null) {
            combatTypeList.add(CarianStyleEnchantments.SHIELD);
            LogUtil.debug("已添加盾牌附魔类型到战斗标签页");
        }

        CreativeTabs.COMBAT.setRelevantEnchantmentTypes(
                combatTypeList.toArray(new EnumEnchantmentType[0])
        );

        LogUtil.debug("正在设置工具标签页的附魔类型...");

        // 设置工具标签页的附魔类型
        @Nonnull List<EnumEnchantmentType> toolTypeList = new ArrayList<>(
                Arrays.asList(CreativeTabs.TOOLS.getRelevantEnchantmentTypes())
        );

        if (CarianStyleEnchantments.PICKAEX != null) {
            toolTypeList.add(CarianStyleEnchantments.PICKAEX);
            LogUtil.debug("已添加镐子附魔类型到工具标签页");
        }

        CreativeTabs.TOOLS.setRelevantEnchantmentTypes(
                toolTypeList.toArray(new EnumEnchantmentType[0])
        );
    }

    /**
     * 加载并验证配置文件
     * <p>
     * 检查配置文件的合法性并输出配置信息
     * </p>
     */
    private static void loadAndValidateConfig() {
        LogUtil.debug("正在验证配置文件...");

        // 验证附魔禁用列表
        if (ConfigLoader.uninstallEnchantment != null && ConfigLoader.uninstallEnchantment.length > 0) {
            LogUtil.warn("检测到 %d 个被禁用的附魔", ConfigLoader.uninstallEnchantment.length);
            LogUtil.debug("禁用的附魔列表：%s", Arrays.toString(ConfigLoader.uninstallEnchantment));
        }

        // 验证碎岩者范围
        if (ConfigLoader.rockBlasterMaxRange > 15) {
            LogUtil.warn("碎岩者最大范围设置过大（%d），可能导致性能问题",
                    ConfigLoader.rockBlasterMaxRange);
        }
        LogUtil.debug("碎岩者最大范围：%d", ConfigLoader.rockBlasterMaxRange);

        // 验证祈祷打击生命值上限
        LogUtil.debug("祈祷打击最大生命值：%d", ConfigLoader.prayerfulStrikeMaxHealth);

        // 验证附魔难度
        if (ConfigLoader.enchantingDifficulty != 1.0) {
            LogUtil.info("附魔难度倍率已设置为：%.2f", ConfigLoader.enchantingDifficulty);
        }
        LogUtil.debug("附魔难度倍率：%.2f", ConfigLoader.enchantingDifficulty);

        // 验证宝藏附魔设置
        if (ConfigLoader.isTreasureVeryRaryEnchantment) {
            LogUtil.warn("所有附魔已设置为宝藏级超稀有");
        } else if (ConfigLoader.isTreasureRaryEnchantment) {
            LogUtil.warn("所有附魔已设置为宝藏级稀有");
        } else if (ConfigLoader.isTreasureUncommonEnchantment) {
            LogUtil.warn("所有附魔已设置为宝藏级罕见");
        }

        // 验证等级限制
        if (ConfigLoader.levelLimit) {
            LogUtil.info("附魔等级限制已启用");
        }
        LogUtil.debug("附魔等级限制：%s", ConfigLoader.levelLimit ? "启用" : "禁用");

        // 验证详细日志
        if (ConfigLoader.enableDetailedLogging) {
            LogUtil.info("详细日志已启用");
        }
        LogUtil.debug("详细日志状态：%s", ConfigLoader.enableDetailedLogging ? "启用" : "禁用");
    }

    /**
     * 输出已注册内容的统计信息
     */
    private static void logRegisteredContent() {
        LogUtil.info("正在统计已注册内容...");

        // 统计附魔
        int enchantmentCount = CarianStyleEnchantments.ENCHANTMENTS.size();
        LogUtil.info("已注册附魔数量：%d", enchantmentCount);
        LogUtil.debug("附魔详细信息：");
        LogUtil.debug("  - 回忆类附魔：%d", CarianStyleEnchantments.RECOLLECT.size());
        LogUtil.debug("  - 战技类附魔：%d", CarianStyleEnchantments.COMBAT_SKILL.size());
        LogUtil.debug("  - 律法类附魔：%d", CarianStyleEnchantments.LAW.size());
        LogUtil.debug("  - 死亡类附魔：%d", CarianStyleEnchantments.DEAD.size());

        // 统计药水效果
        int potionCount = CarianStylePotion.POTIONS.size();
        LogUtil.info("已注册药水效果数量：%d", potionCount);

        // 统计方块
        int blockCount = CarianStyleBlocks.BLOCKS.size();
        LogUtil.info("已注册方块数量：%d", blockCount);

        // 统计物品
        int itemCount = CarianStyleItem.ITEMS.size();
        LogUtil.info("已注册物品数量：%d", itemCount);

        // 统计实体
        int entityCount = CarianStyleEntity.ENTITY_ENTRIES.size();
        LogUtil.info("已注册实体数量：%d", entityCount);

        // 总计
        int totalCount = enchantmentCount + potionCount + blockCount + itemCount + entityCount;
        LogUtil.info("已注册内容总计：%d", totalCount);
    }

    /**
     * 执行最终验证
     * <p>
     * 在所有模组加载完成后进行最终的完整性检查
     * </p>
     */
    private static void performFinalValidation() {
        LogUtil.debug("正在执行最终验证...");

        // 验证关键组件是否正确初始化
        boolean isValid = true;

        if (CarianStyleEnchantments.ENCHANTMENTS.isEmpty()) {
            LogUtil.error("错误：没有附魔被注册！");
            isValid = false;
        }

        if (CarianStylePotion.POTIONS.isEmpty()) {
            LogUtil.error("错误：没有药水效果被注册！");
            isValid = false;
        }

        if (CarianStyleEntity.ENTITY_ENTRIES.isEmpty()) {
            LogUtil.error("错误：没有实体被注册！");
            isValid = false;
        }

        if (isValid) {
            LogUtil.debug("最终验证通过，所有组件已正确初始化");
        } else {
            LogUtil.error("最终验证失败，部分组件未正确初始化！");
            throw new RuntimeException("卡利亚式附魔模组验证失败");
        }
    }

}