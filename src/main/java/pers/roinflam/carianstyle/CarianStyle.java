package pers.roinflam.carianstyle;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.*;
import pers.roinflam.carianstyle.mc112.hook.CreativeTabHook;
import pers.roinflam.carianstyle.mc112.migration.LegacyConfigMigration;
import pers.roinflam.carianstyle.mc112.util.ServerLifecycle;
import pers.roinflam.carianstyle.network.NetworkHandler;
import pers.roinflam.carianstyle.network.VisualNetwork;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import pers.roinflam.carianstyle.visual.CarianStyleCombatStateDisplay;
import pers.roinflam.carianstyle.visual.CarianStyleConditionDisplay;
import pers.roinflam.carianstyle.visual.CarianStyleFireStyleDisplay;
import pers.roinflam.carianstyle.visual.CarianStyleStackDisplays;
import pers.roinflam.carianstyle.visual.CarianStyleTimeReversalDisplay;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Map;

/**
 * 卡利亚式附魔模组主类
 * <p>
 * 1.12.2 版：生命周期照 1.20.1 拆到 preInit / init / postInit 三步（1.20.1 是模组构造器 + {@code FMLCommonSetupEvent}）。
 * 客户端专用的初始化放在 {@link ClientSetupHandler}，这里只在客户端调用它，不用 {@code @SidedProxy}。
 * 握手时的版本检查见 {@link #checkRemote}。
 * </p>
 */
@Mod(
        modid = Reference.MOD_ID,
        version = Reference.VERSION,
        useMetadata = true,
        acceptedMinecraftVersions = "[1.12.2]",
        guiFactory = "pers.roinflam.carianstyle.config.ConfigGuiFactory"
)
public class CarianStyle {

    @Mod.Instance(Reference.MOD_ID)
    public static CarianStyle instance;

    public CarianStyle() {
        // 旧版 carianstyle.cfg 迁移到 carianstyle-common.cfg：要赶在 FML 构造完模组后第一次同步 @Config 之前
        LegacyConfigMigration.run();
    }

    /**
     * 握手时检查对方的卡利亚式附魔版本
     * <p>
     * FML 默认的检查器只有服务端会拒绝客户端（客户端从来不拒绝服务端），以后改了协议，
     * 新客户端进旧服会连上、包格式对不上。这里换掉默认检查器，两边都查：
     * </p>
     * <ul>
     *   <li>对方没装：对方是服务端就放行（装了本模组的客户端可以进没装的服务器，这时收不到任何包，数值按本地文件）；
     *       对方是客户端就拒绝（注册表要同步）；</li>
     *   <li>对方装了：版本不低于 {@link NetworkHandler#PROTOCOL_SINCE} 才放行。</li>
     * </ul>
     * <p>拒绝时 FML 给玩家的提示不够具体，所以日志里自己打一行。</p>
     *
     * @param mods       对方的模组列表（modid → 版本）
     * @param remoteSide 对方是哪一侧
     * @return 能不能连
     */
    @NetworkCheckHandler
    public boolean checkRemote(Map<String, String> mods, Side remoteSide) {
        String v = mods.get(Reference.MOD_ID);
        if (v == null) {
            // 服务端没装：放行；客户端没装：拒绝
            return remoteSide == Side.SERVER;
        }
        boolean ok = new DefaultArtifactVersion(v)
                .compareTo(new DefaultArtifactVersion(NetworkHandler.PROTOCOL_SINCE)) >= 0;
        if (!ok) {
            LogUtil.warn("对方的卡利亚式附魔版本是 " + v + "，至少要 " + NetworkHandler.PROTOCOL_SINCE);
        }
        return ok;
    }

    @Mod.EventHandler
    public void preInit(@Nonnull FMLPreInitializationEvent event) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始加载模组");

        try {
            ConfigLoader.bake();

            // ==================== 注册 DeferredRegister ====================
            LogUtil.info("卡利亚式附魔 - 正在注册游戏内容");

            CarianStyleBlocks.BLOCKS.register(MinecraftForge.EVENT_BUS);
            LogUtil.info("卡利亚式附魔 - 方块注册器已挂载");

            CarianStyleEntity.ENTITY_TYPES.register(MinecraftForge.EVENT_BUS);
            LogUtil.info("卡利亚式附魔 - 实体类型注册器已挂载");

            CarianStyleItem.ITEMS.register(MinecraftForge.EVENT_BUS);
            LogUtil.info("卡利亚式附魔 - 物品注册器已挂载");

            CarianStylePotion.MOB_EFFECTS.register(MinecraftForge.EVENT_BUS);
            LogUtil.info("卡利亚式附魔 - 药水效果注册器已挂载");

            CarianStyleEnchantments.ENCHANTMENTS_REGISTER.register(MinecraftForge.EVENT_BUS);
            LogUtil.info("卡利亚式附魔 - 附魔注册器已挂载");

            // 扫描并登记所有附魔（注册事件在 preInit 之后才发，这里登记就来得及）
            LogUtil.info("卡利亚式附魔 - 正在扫描并注册附魔");
            EnchantmentRegistry.scanAndRegister(event.getAsmData(), "pers.roinflam.carianstyle.enchantment");
            LogUtil.info("卡利亚式附魔 - 附魔扫描注册完成");

            LogUtil.info("卡利亚式附魔 - 正在注册网络通信处理器");
            NetworkHandler.register();
            // 可视化系统专用网络通道（叠层 HUD、演出包，与 NetworkHandler 独立）
            VisualNetwork.register();
            LogUtil.info("卡利亚式附魔 - 网络通信处理器注册成功");

            // 方块实体（1.12.2 没有注册表，直接登记）
            CarianStyleBlockEntities.register();

            if (event.getSide().isClient()) {
                ClientSetupHandler.preInit();
            }

            LogUtil.separator();
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 预初始化阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔加载失败", e);
        }
    }

    @Mod.EventHandler
    public void init(@Nonnull FMLInitializationEvent event) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始初始化阶段");

        try {
            // 注册叠层显示读取器（HUD 数据源，双端都需要；默认无读取器，接入后才显示）
            LogUtil.info("卡利亚式附魔 - 正在注册叠层显示");
            CarianStyleStackDisplays.init();
            CarianStyleCombatStateDisplay.init();
            CarianStyleConditionDisplay.init();
            CarianStyleFireStyleDisplay.init();
            CarianStyleTimeReversalDisplay.init();
            LogUtil.debug("卡利亚式附魔 - 叠层显示注册完成");

            CreativeTabHook.install();

            if (event.getSide().isClient()) {
                ClientSetupHandler.init();
            }

            LogUtil.info("卡利亚式附魔 - 初始化阶段完成");
            LogUtil.separator();
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 初始化阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔初始化失败", e);
        }
    }

    @Mod.EventHandler
    public void postInit(@Nonnull FMLPostInitializationEvent event) {
        LogUtil.separator();
        LogUtil.info("卡利亚式附魔 - 开始后初始化阶段");

        try {
            // 所有附魔类的静态块都已经跑过，define 句柄齐了，这时读数值文件不会缺键
            EnchantmentValues.reload();

            // 加载并验证配置
            LogUtil.info("卡利亚式附魔 - 正在验证配置文件");
            loadAndValidateConfig();
            LogUtil.debug("卡利亚式附魔 - 配置文件验证完成");

            // 记录注册内容统计
            logRegisteredContent();

            // 执行最终验证
            performFinalValidation();

            LogUtil.info("卡利亚式附魔 - 后初始化阶段完成");
            LogUtil.separator();
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 后初始化阶段发生错误", e);
            throw new RuntimeException("卡利亚式附魔后初始化失败", e);
        }
    }

    /**
     * 服务器停止之后：清掉服务端那一侧的静态表（1.12.2 的停服事件只发给主类）
     */
    @Mod.EventHandler
    public void serverStopped(@Nonnull FMLServerStoppedEvent event) {
        ServerLifecycle.onServerStopped(event);
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

            // 对不上任何附魔的条目不会报错，只会悄悄不生效（例如填了显示名、拼错了 id），逐条点名
            for (String entry : ConfigLoader.uninstallEnchantment) {
                boolean known = false;
                for (ResourceLocation key : ForgeRegistries.ENCHANTMENTS.getKeys()) {
                    if (Reference.MOD_ID.equals(key.getResourceDomain()) && ConfigLoader.entryNamesEnchantment(entry, key.getResourcePath())) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    LogUtil.warn("卡利亚式附魔 - uninstallEnchantment 中的「%s」没有对应的附魔，这一项不会生效（应填附魔 id，例如 prayerful_strike）", entry);
                }
            }
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
     * <p>
     * 药水、实体缺了直接报错退出；附魔一个都没有时只记一条错误日志，不中断加载
     * （禁用、扫描出错都只影响附魔本身，方块、药水、网络照常可用，方便排查）。
     * </p>
     */
    private void performFinalValidation() {
        LogUtil.debug("卡利亚式附魔 - 正在执行最终验证");

        boolean isValid = true;

        if (CarianStyleEnchantments.getRegisteredCount() == 0) {
            LogUtil.error("卡利亚式附魔 - 错误：没有附魔被注册");
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
