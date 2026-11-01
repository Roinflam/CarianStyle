package pers.roinflam.carianstyle.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.LevelDecay;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 卡利亚风格模组配置类
 * Carian Style Mod Configuration Class
 *
 * 统一管理所有配置项烘焙后的值
 * 配置文件位置:config/carianstyle-common.cfg
 * Config file location: config/carianstyle-common.cfg
 *
 * <p>
 * 1.12.2 版：配置规格（分类、键、默认值、范围、注释）在 {@link ConfigSpec}（Forge {@code @Config}），
 * 这里只留附魔代码读的静态字段和 {@link #bake()}。字段名照 1.20.1，包括拼错的
 * {@code isTreasureVeryRaryEnchantment} / {@code isTreasureRaryEnchantment}（附魔代码引用的就是这两个名字）。
 * </p>
 *
 * <p>
 * v2.2修复：bake() 末尾调用 EnchantmentBase.invalidateAllDisabledCaches()，
 * 确保配置重载后 uninstallEnchantment 黑名单立即生效。
 * </p>
 *
 * <p>
 * v2.3新增：怪物附魔触发开关
 * <ul>
 *   <li>{@link #allowMobTriggerEnchantments}：是否允许非玩家生物（怪物等）触发卡利亚式附魔，默认开启。
 *       关闭后可大幅降低存在大量附魔生物时的服务器开销。</li>
 *   <li>{@link #allowMobTriggerDeathEnchantments}：是否允许非玩家生物触发死亡/濒死类附魔
 *       （包括 DEAD 分类的全部附魔，以及 RECOLLECT 中的 满月/死诞者/时间逆转），默认关闭。
 *       建议保持禁用，避免精英怪/Boss获得不公平的复活和范围反伤能力。</li>
 * </ul>
 * </p>
 *
 * <p>
 * v2.4新增：附魔获取途径平衡开关
 * <ul>
 *   <li>{@link #useVanillaRarityWeight}：是否把模组稀有度映射回原版权重体系，默认开启（即修复生效）。</li>
 *   <li>{@link #allowVillagerBookTrade}：是否允许模组附魔进入村民附魔书交易池，默认关闭（即修复生效）。</li>
 * </ul>
 * 两项的背景与影响详见 {@code EnchantmentBase} 的类注释。
 * </p>
 *
 * <p>
 * v2.5新增（模组 2.2.0）：隐身碰撞箱屏蔽开关
 * <ul>
 *   <li>{@link #hideStealthHitbox}：处于卡利亚式隐身的玩家，其碰撞箱在所有客户端的 F3+B 中都不显示，默认开启。
 *       由服务端决定并逐实体下发，多人游戏中客户端本地的这一项不起作用。
 *       机制详见 {@code StealthHitboxSyncHelper} 的类注释。</li>
 * </ul>
 * </p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ConfigLoader {

    // ==================== 便捷访问属性 ====================

    /** 是否启用详细日志 */
    public static boolean enableDetailedLogging = false;

    /** 禁用的附魔ID列表 */
    public static String[] uninstallEnchantment = new String[0];

    /**
     * 配置版本号：每次 {@link #bake()} 同步完静态字段后加一，0 表示配置还没加载过。
     * {@code EnchantmentBase#isDisabled()} 用它判断自己缓存的禁用状态是不是过期了。
     */
    private static final AtomicInteger BAKE_GENERATION = new AtomicInteger();

    /** 碎岩者最大范围 */
    public static int rockBlasterMaxRange = 10;

    /** 碎岩者是否抑制常见石头掉落物 */
    public static boolean rockBlasterSuppressCommonDrops = false;

    /** 祈祷打击最大生命值 */
    public static int prayerfulStrikeMaxHealth = 1000;

    /** 是否启用高级真伤系统 */
    public static boolean enableTrueDamage = true;

    /** 超稀有附魔是否为宝藏 */
    public static boolean isTreasureVeryRaryEnchantment = false;

    /** 稀有附魔是否为宝藏 */
    public static boolean isTreasureRaryEnchantment = false;

    /** 罕见附魔是否为宝藏 */
    public static boolean isTreasureUncommonEnchantment = false;

    /** 是否启用等级限制 */
    public static boolean levelLimit = false;

    /** 附魔难度倍率 */
    public static double enchantingDifficulty = 1.0;

    /**
     * 是否把模组稀有度映射到原版权重体系（v2.4新增）
     * <p>
     * 默认 true。原版附魔台按 {@code getRarity().getWeight()} 加权抽取候选
     * （COMMON=10、UNCOMMON=5、RARE=2、VERY_RARE=1），
     * 而 {@code EnchantmentBase} 的主构造函数只能在 super() 阶段传占位值 COMMON，
     * 若不做映射，全部模组附魔都是权重 10，会把原版附魔挤出候选池。
     * </p>
     * <p>
     * 副作用：铁砧合成花费同样读取稀有度，Rare / Very Rare 档会变贵。
     * 设为 false 可恢复旧行为。
     * </p>
     */
    public static boolean useVanillaRarityWeight = true;

    /**
     * 是否允许模组附魔进入村民附魔书交易池（v2.4新增）
     * <p>
     * 默认 false。1.12.2 的 {@code EntityVillager.ListEnchantedBookForEmeralds}
     * 是从所有 {@code isTradeable()} 为 true 的附魔里<b>等概率</b>抽取，不看权重。
     * 一百多个模组附魔全部可交易会把原版约 40 个的池子撑到 150+，
     * 导致「经验修补」等原版关键附魔的出现概率掉到原来的四分之一左右。
     * </p>
     * <p>
     * 不影响附魔台与战利品箱的获取途径。
     * </p>
     */
    public static boolean allowVillagerBookTrade = false;

    /**
     * 是否允许非玩家生物触发卡利亚式附魔
     * <p>
     * 默认 true（允许）。关闭后可大幅降低存在大量附魔怪物时的服务器开销，
     * 因为 EnchantmentEventHandler 会在伤害事件扫描入口直接跳过非玩家持有者。
     * </p>
     * <p>
     * 注意：仅作用于走中央事件处理器路径的附魔。部分使用独立 @SubscribeEvent
     * 监听器的附魔不受此开关影响。
     * </p>
     */
    public static boolean allowMobTriggerEnchantments = true;

    /**
     * 是否允许非玩家生物触发死亡/濒死类附魔
     * <p>
     * 默认 false（拦截）。覆盖范围：
     * <ul>
     *   <li>所有 DEAD 分类附魔（猩红艾奥尼亚、发狂扩散、巨剑阵、古龙雷击）</li>
     *   <li>RECOLLECT 分类中的濒死触发型附魔（满月、死诞者、时间逆转）</li>
     * </ul>
     * </p>
     * <p>
     * 建议保持禁用，避免精英怪/Boss获得复活、范围反伤等过强能力，防止玩家"被打破防"。
     * </p>
     * <p>
     * 仅当 {@link #allowMobTriggerEnchantments} 为 true 时才会被检查；
     * 若通用开关为 false，则死亡类附魔自然也不会触发。
     * </p>
     */
    public static boolean allowMobTriggerDeathEnchantments = false;

    /**
     * 是否对所有客户端隐藏隐身玩家的碰撞箱（v2.5新增）
     * <p>
     * 默认 true。只在服务端读取：由 {@code StealthHitboxSyncHelper} 据此决定
     * 是否给隐身玩家挂上碰撞箱屏蔽序列号，客户端只认序列号、不读本字段。
     * </p>
     */
    public static boolean hideStealthHitbox = true;

    /**
     * 从配置规格同步到静态字段
     * <p>
     * 在配置加载或修改后调用。
     * v2.2修复：末尾清除所有附魔的禁用状态缓存，确保黑名单变更立即生效。
     * v2.3新增：同步两个怪物附魔触发开关。
     * v2.4新增：同步权重映射与村民交易两个开关。
     * v2.5新增：同步隐身碰撞箱屏蔽开关。
     * </p>
     * <p>
     * 1.12.2 版：值从 {@link ConfigSpec} 读（Forge 在模组构造时已经把文件同步进去了），
     * 由主类在 preInit 调一次，配置界面改动后由 {@link #onConfigChanged} 再调。
     * </p>
     */
    public static void bake() {
        enableDetailedLogging = ConfigSpec.logging.enableDetailedLogging;

        String[] enchList = ConfigSpec.enchantment.uninstallEnchantment;
        uninstallEnchantment = enchList == null ? new String[0] : enchList.clone();

        rockBlasterMaxRange = ConfigSpec.enchantment.rockBlasterMaxRange;
        rockBlasterSuppressCommonDrops = ConfigSpec.enchantment.rockBlasterSuppressCommonDrops;
        prayerfulStrikeMaxHealth = ConfigSpec.enchantment.prayerfulStrikeMaxHealth;

        enableTrueDamage = ConfigSpec.trueDamage.enableTrueDamage;

        isTreasureVeryRaryEnchantment = ConfigSpec.treasure.isTreasureVeryRareEnchantment;
        isTreasureRaryEnchantment = ConfigSpec.treasure.isTreasureRareEnchantment;
        isTreasureUncommonEnchantment = ConfigSpec.treasure.isTreasureUncommonEnchantment;

        levelLimit = ConfigSpec.balance.levelLimit;
        enchantingDifficulty = ConfigSpec.balance.enchantingDifficulty;

        String[] curve = ConfigSpec.levelDecay.curve;
        LevelDecay.bake(ConfigSpec.levelDecay.enabled, ConfigSpec.levelDecay.threshold,
                ConfigSpec.levelDecay.byMultiple, ConfigSpec.levelDecay.maxLevel,
                curve == null ? Collections.<String>emptyList() : Arrays.asList(curve),
                ConfigSpec.levelDecay.showInName);

        // v2.4新增：同步附魔获取途径平衡开关
        useVanillaRarityWeight = ConfigSpec.balance.useVanillaRarityWeight;
        allowVillagerBookTrade = ConfigSpec.balance.allowVillagerBookTrade;

        // v2.3新增：同步怪物附魔触发开关
        allowMobTriggerEnchantments = ConfigSpec.mobTrigger.allowMobTriggerEnchantments;
        allowMobTriggerDeathEnchantments = ConfigSpec.mobTrigger.allowMobTriggerDeathEnchantments;

        // v2.5新增：同步隐身碰撞箱屏蔽开关
        hideStealthHitbox = ConfigSpec.stealth.hideStealthHitbox;

        // 静态字段全部换完再递增版本号：isDisabled() 读到新版本号时一定能看到新的 uninstallEnchantment
        BAKE_GENERATION.incrementAndGet();

        // v2.2修复：清除所有附魔的禁用状态缓存
        // 确保 uninstallEnchantment 配置变更后，isDisabled() 会重新计算
        EnchantmentBase.invalidateAllDisabledCaches();
    }

    /**
     * 配置版本号，0 表示配置还没加载过（详见 {@link #BAKE_GENERATION}）。
     */
    public static int bakeGeneration() {
        return BAKE_GENERATION.get();
    }

    /**
     * uninstallEnchantment 里有没有写到这个附魔。
     * <p>
     * <b>问题：</b>以前是拿注解 id 和配置项做 {@code equals}，只认一模一样的 {@code prayerful_strike}。
     * 下面这些写法都会悄无声息地不生效，「禁用」的附魔照样能从战利品、附魔台里出来，日志里也没有任何提示：
     * <ul>
     *   <li>带命名空间：{@code carianstyle:prayerful_strike}（/enchant、/give 指令里写的，物品 NBT 里存的，都是这种）</li>
     *   <li>大小写不同、首尾多了空格</li>
     *   <li>照着示例 {@code ["scarlet_rot", "doomed_death"]}，把引号、方括号一起填进了配置界面的一格，
     *       或者在一格里用逗号写了好几个</li>
     *   <li>照着注释去 en_us.lang 里找，抄成了键名 {@code enchantment.carianstyle.prayerful_strike}</li>
     * </ul>
     * </p>
     * <p>
     * <b>现在：</b>每一项按逗号拆开，去掉首尾的空白、引号、方括号，转小写，
     * 再去掉 {@code carianstyle:} 或 {@code enchantment.carianstyle.} 前缀后比较。
     * 附魔 id 只由小写字母、数字和下划线组成，不会含这些字符，所以放宽之后不会误伤别的附魔；
     * 别的命名空间（如 {@code minecraft:xxx}）仍然对不上。
     * </p>
     *
     * @param id 附魔注解里的 id（即注册名的路径部分），如 {@code prayerful_strike}
     */
    public static boolean isEnchantmentUninstalled(String id) {
        String[] list = uninstallEnchantment;
        if (list == null || list.length == 0 || id == null) {
            return false;
        }
        for (String entry : list) {
            if (entryNamesEnchantment(entry, id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * uninstallEnchantment 的某一项是否写到了这个附魔，规则同 {@link #isEnchantmentUninstalled(String)}。
     */
    public static boolean entryNamesEnchantment(String entry, String id) {
        if (entry == null || id == null) {
            return false;
        }
        for (String part : entry.split("[,，]")) {
            if (id.equals(normalizeEnchantmentId(part))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeEnchantmentId(String raw) {
        int begin = 0;
        int end = raw.length();
        while (begin < end && isIdWrapper(raw.charAt(begin))) {
            begin++;
        }
        while (end > begin && isIdWrapper(raw.charAt(end - 1))) {
            end--;
        }
        String id = raw.substring(begin, end).toLowerCase(Locale.ROOT);
        String namespace = Reference.MOD_ID + ":";
        String langKey = "enchantment." + Reference.MOD_ID + ".";
        if (id.startsWith(namespace)) {
            return id.substring(namespace.length());
        }
        if (id.startsWith(langKey)) {
            return id.substring(langKey.length());
        }
        return id;
    }

    private static boolean isIdWrapper(char c) {
        return Character.isWhitespace(c) || c == '"' || c == '\'' || c == '[' || c == ']' || c == '“' || c == '”';
    }

    /**
     * 游戏内配置界面改了本模组的配置
     * <p>
     * 1.12.2 版：对应 1.20.1 的 {@code ModConfigEvent.Reloading}。界面改的是 {@code GuiConfig} 里的属性，
     * 先 {@code ConfigManager.sync} 把它写回 {@link ConfigSpec} 字段并保存文件，再烘焙。
     * 只有界面会触发这个事件；手改 {@code .cfg} 要重启才生效。
     * </p>
     */
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Reference.MOD_ID.equals(event.getModID())) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
            bake();
        }
    }

    // 私有构造函数，防止实例化
    private ConfigLoader() {
    }
}
