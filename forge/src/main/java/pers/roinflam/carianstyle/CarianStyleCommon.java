package pers.roinflam.carianstyle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import pers.roinflam.carianstyle.visual.CarianStyleCombatStateDisplay;
import pers.roinflam.carianstyle.visual.CarianStyleConditionDisplay;
import pers.roinflam.carianstyle.visual.CarianStyleFireStyleDisplay;
import pers.roinflam.carianstyle.visual.CarianStyleStackDisplays;
import pers.roinflam.carianstyle.visual.CarianStyleTimeReversalDisplay;

import java.util.Arrays;

/**
 * 通用设置：注册内容、配置都就绪之后、进世界之前做一次的初始化（客户端和服务端都做）
 * <p>
 * 平台入口在自己的「通用设置」时机调 {@link #setup()}，要在主线程上调：叠层显示的登记表是普通集合，不能并发写。
 * </p>
 * <p>顺序固定：</p>
 * <ol>
 *   <li>叠层显示 {@link CarianStyleStackDisplays}；</li>
 *   <li>核对配置（含禁用名单里对不上附魔的条目）、统计注册内容、最终自检（自检不过直接抛异常，模组加载失败）；</li>
 *   <li>四个单独成类的叠层显示项；和上面那批的登记先后不影响显示（客户端按档位和序列号排）；</li>
 *   <li>最后加载附魔数值表 {@link EnchantmentValues#reload()}：要排在附魔注册之后，所有数值项都已声明，写回的文件才不缺键。</li>
 * </ol>
 */
public final class CarianStyleCommon {

    private static boolean done;

    private CarianStyleCommon() {
    }

    /**
     * 做一次通用设置。只许调一次。
     */
    public static synchronized void setup() {
        if (done) {
            throw new IllegalStateException("通用设置已经做过了");
        }
        done = true;

        LogUtil.info("卡利亚式附魔 - 开始通用设置阶段");

        // 注册叠层显示读取器（HUD 数据源，双端都需要；默认无读取器，接入后才显示）
        LogUtil.info("卡利亚式附魔 - 正在注册叠层显示");
        CarianStyleStackDisplays.init();
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

        // 单独成类的四组叠层显示
        CarianStyleCombatStateDisplay.register();
        CarianStyleConditionDisplay.register();
        CarianStyleFireStyleDisplay.register();
        CarianStyleTimeReversalDisplay.register();

        // 附魔数值表：读文件、和代码默认值合并后写回
        EnchantmentValues.reload();
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

            // 对不上任何附魔的条目不会报错，只会悄悄不生效（例如填了显示名、拼错了 id），逐条点名
            for (String entry : ConfigLoader.uninstallEnchantment) {
                boolean known = false;
                for (ResourceLocation key : BuiltInRegistries.ENCHANTMENT.keySet()) {
                    if (Reference.MOD_ID.equals(key.getNamespace()) && ConfigLoader.entryNamesEnchantment(entry, key.getPath())) {
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
    private static void logRegisteredContent() {
        LogUtil.info("卡利亚式附魔 - 正在统计已注册内容");

        // 统计附魔
        int enchantmentCount = CarianStyleEnchantments.getRegisteredCount();
        LogUtil.info("卡利亚式附魔 - 已注册附魔数量：%d", enchantmentCount);

        // 统计药水效果
        int potionCount = CarianContent.declaredCount(Registries.MOB_EFFECT);
        LogUtil.info("卡利亚式附魔 - 已注册药水效果数量：%d", potionCount);

        // 统计方块
        int blockCount = CarianContent.declaredCount(Registries.BLOCK);
        LogUtil.info("卡利亚式附魔 - 已注册方块数量：%d", blockCount);

        // 统计物品
        int itemCount = CarianContent.declaredCount(Registries.ITEM);
        LogUtil.info("卡利亚式附魔 - 已注册物品数量：%d", itemCount);

        // 统计实体
        int entityCount = CarianContent.declaredCount(Registries.ENTITY_TYPE);
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

        if (CarianStyleEnchantments.getRegisteredCount() == 0) {
            LogUtil.error("卡利亚式附魔 - 错误：没有附魔被注册");
            isValid = false;
        }

        if (!CarianContent.validate()) {
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
