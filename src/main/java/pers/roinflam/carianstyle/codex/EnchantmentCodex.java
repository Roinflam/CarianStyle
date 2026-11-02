package pers.roinflam.carianstyle.codex;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.client.gui.DescriptionText;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.mc112.gui.Font;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 附魔百科索引：把注册表里的附魔转成可供界面直接消费的元数据集合。
 *
 * <h3>构建时机</h3>
 * <p>
 * 惰性构建——首次调用 {@link #getAll()} 或 {@link #get(String)} 时才扫描。
 * 之所以不在 postInit 里预先建好，是因为搜索索引需要
 * <b>语言文件已加载</b>（{@code Component.translatable().getString()} 才有真实文本），
 * 而语言表在客户端资源重载后才就绪，比 setup 阶段晚。
 * </p>
 * <p>
 * 语言切换或资源重载后调用 {@link #invalidate()} 即可让下次访问重建，
 * 界面里的重载按钮走的就是这条路。
 * </p>
 * <p>
 * 1.12.2 版：整个 {@code codex} 包只在客户端用（显示名、搜索索引都要客户端的语言表），
 * 服务端不会加载这些类；自检入口见 {@link DebugDump}。
 * </p>
 *
 * <h3>冲突关系的双向补全</h3>
 * <p>
 * 注解里的 {@code conflictsWith} 是<b>单向</b>声明：A 写了「与 B 冲突」，
 * B 往往没写「与 A 冲突」。但原版的 {@code Enchantment.isCompatibleWith}
 * 在附魔台/铁砧上是<b>双向</b>生效的（任一方拒绝即不能共存）。
 * 若百科只照搬注解，玩家在 B 的页面上会看不到这条冲突，然后在铁砧上撞墙。
 * </p>
 * <p>
 * 因此本类在建索引时把每条 A→B 同时写进 B 的冲突列表，
 * 让百科展示的关系与游戏内实际行为一致。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>
 * 只在客户端渲染线程访问（界面打开时）。构建过程用 synchronized 保护，
 * 防止极端情况下的重复构建；构建完成后的集合是只读的，无需加锁。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
public final class EnchantmentCodex {

    /** 构建锁：只保护构建过程本身 */
    private static final Object BUILD_LOCK = new Object();

    /** id → 元数据 */
    private static Map<String, EnchantmentMeta> byId = Collections.emptyMap();

    /** 按分组、再按显示名排序后的完整列表 */
    private static List<EnchantmentMeta> ordered = Collections.emptyList();

    /** 索引是否已构建 */
    private static volatile boolean built = false;

    private EnchantmentCodex() {
    }

    /**
     * 取全部附魔元数据（已排序）。
     *
     * @return 不可修改的元数据列表
     */
    @Nonnull
    public static List<EnchantmentMeta> getAll() {
        ensureBuilt();
        return ordered;
    }

    /**
     * 按 id 精确查找。
     *
     * @param id 附魔注册 id
     * @return 元数据；不存在时返回 null
     */
    @Nullable
    public static EnchantmentMeta get(@Nullable String id) {
        if (id == null) {
            return null;
        }
        ensureBuilt();
        return byId.get(id);
    }

    /**
     * 按关键字筛选。
     *
     * @param keyword 关键字，可为 null 或空串（表示不筛选）
     * @param theme   限定主题，null 表示不限
     * @return 匹配结果（新建列表，调用方可自由持有）
     */
    @Nonnull
    public static List<EnchantmentMeta> search(@Nullable String keyword,
                                               @Nullable CodexTheme theme) {
        ensureBuilt();
        String lower = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<EnchantmentMeta> result = new ArrayList<>(ordered.size());
        for (EnchantmentMeta meta : ordered) {
            if (theme != null && meta.getTheme() != theme) {
                continue;
            }
            if (meta.matches(lower)) {
                result.add(meta);
            }
        }
        return result;
    }

    /**
     * 让索引失效，下次访问时重建。
     * <p>切换语言或重载资源包后调用。</p>
     */
    public static void invalidate() {
        synchronized (BUILD_LOCK) {
            built = false;
        }
    }

    /**
     * 确保索引已构建（惰性，双重检查）。
     */
    private static void ensureBuilt() {
        if (built) {
            return;
        }
        synchronized (BUILD_LOCK) {
            if (built) {
                return;
            }
            build();
            built = true;
        }
    }

    /**
     * 扫描注册表构建索引。
     * <p>
     * 数据源全部是现有的公开成员，不需要改动 {@code EnchantmentRegistry}：
     * <ul>
     *   <li>{@code CarianStyleEnchantments.ENCHANTMENTS} —— 全部已注册附魔</li>
     *   <li>{@code enchantment.getClass().getAnnotation(...)} —— 注解元数据</li>
     *   <li>{@code RECOLLECT / COMBAT_SKILL / LAW / DEAD} 四个 Set —— 分组归属</li>
     * </ul>
     * </p>
     */
    private static void build() {
        long start = System.currentTimeMillis();

        Map<String, EnchantmentMeta> idMap = new HashMap<>();
        Map<Class<?>, EnchantmentMeta> classMap = new HashMap<>();
        List<EnchantmentMeta> list = new ArrayList<>();

        for (Enchantment enchantment : CarianStyleEnchantments.ENCHANTMENTS) {
            AutoRegisterEnchantment annotation =
                    enchantment.getClass().getAnnotation(AutoRegisterEnchantment.class);
            if (annotation == null) {
                // 走传统构造函数注册的附魔没有注解，无法进百科（也无法确定 id）
                continue;
            }

            EnchantmentMeta meta = new EnchantmentMeta(enchantment, annotation, resolveGroup(enchantment));
            meta.buildSearchIndex();

            idMap.put(meta.getId(), meta);
            classMap.put(enchantment.getClass(), meta);
            list.add(meta);
        }

        // ==================== 冲突关系：实测而非读注解 ====================
        //
        // 早先这里只遍历 annotation.conflictsWith() 再做双向补全，漏掉了两大类：
        //
        //   1. 整类互斥。EnchantmentBase.checkCompatibility（1.12.2 由 canApplyTogether 转调）里有一条
        //      「同类别互斥（GENERAL 除外）」——25 个战技之间两两互斥、
        //      14 个追忆之间两两互斥，这些一条注解都没写。
        //      结果「卡利亚式奉还」在百科里显示「冲突 2」，实际是 26。
        //   2. 各附魔自己覆写的 checkCompatibility，比如重力与原版「击退」互斥。
        //
        // 现在改为直接调 isCompatibleWith 实测——那是附魔台与铁砧真正使用的判定，
        // 一次覆盖注解、整类互斥、allowWith 白名单和所有覆写，
        // 而且以后规则怎么改，百科都自动跟上，不需要在这里复刻一份判断逻辑。
        //
        // 开销：113 × (113 + 原版约 30) ≈ 1.6 万次调用，只在首次打开界面时跑一次。
        for (EnchantmentMeta meta : list) {
            Enchantment self = meta.getEnchantment();

            // 与本模组其它附魔
            for (EnchantmentMeta other : list) {
                if (other == meta) {
                    continue;
                }
                if (!self.isCompatibleWith(other.getEnchantment())) {
                    meta.addConflict(other.getId());
                }
            }

            // 与原版及其它模组的附魔
            for (Enchantment foreign : ForgeRegistries.ENCHANTMENTS) {
                if (foreign == self || classMap.containsKey(foreign.getClass())) {
                    continue;
                }
                if (!self.isCompatibleWith(foreign)) {
                    meta.addForeignConflict(foreign);
                }
            }
        }

        // 标记「与同分类全部成员互斥」，供界面折叠展示
        Map<EnchantmentMeta.CodexGroup, List<EnchantmentMeta>> byGroup = new HashMap<>();
        for (EnchantmentMeta meta : list) {
            byGroup.computeIfAbsent(meta.getGroup(), k -> new ArrayList<>()).add(meta);
        }
        for (Map.Entry<EnchantmentMeta.CodexGroup, List<EnchantmentMeta>> entry : byGroup.entrySet()) {
            List<EnchantmentMeta> members = entry.getValue();
            // 单成员分类（例如律法只有一个）折叠没有意义
            if (members.size() < 2) {
                continue;
            }
            for (EnchantmentMeta meta : members) {
                boolean all = true;
                for (EnchantmentMeta other : members) {
                    if (other != meta && !meta.getConflictIds().contains(other.getId())) {
                        all = false;
                        break;
                    }
                }
                meta.markGroupConflict(all, members.size());
            }
        }

        // 排序：主题分组 -> 组内稀有度从低到高 -> 同稀有度按显示名
        //
        // 稀有度升序是刻意的：玩家翻百科通常是在找「我现在能拿到什么」，
        // 普通档排在前面比极稀有排在前面更贴近这个用途；
        // 而且同一主题里稀有度往往对应强度梯度，升序读下来就是一条成长线。
        list.sort((a, b) -> {
            int themeCmp = Integer.compare(a.getTheme().ordinal(), b.getTheme().ordinal());
            if (themeCmp != 0) {
                return themeCmp;
            }
            int rarityCmp = Integer.compare(rarityOrder(a), rarityOrder(b));
            if (rarityCmp != 0) {
                return rarityCmp;
            }
            return a.getDisplayName().getString().compareTo(b.getDisplayName().getString());
        });

        byId = Collections.unmodifiableMap(idMap);
        ordered = Collections.unmodifiableList(list);

        LogUtil.info("卡利亚式附魔 - 百科索引构建完成，收录 %d 个附魔，耗时 %d 毫秒",
                list.size(), System.currentTimeMillis() - start);
    }

    /**
     * 稀有度的排序权重：普通 &lt; 稀有 &lt; 极稀有。
     * <p>
     * 不直接用 {@code EnchantmentRarity} 的 {@code ordinal()}——枚举的声明顺序
     * 是给注册用的，不保证与「由低到高」一致，将来有人调整枚举顺序，
     * 界面排序会跟着悄悄变。这里显式写死语义。
     * </p>
     *
     * @param meta 元数据
     * @return 排序权重，越小越靠前
     */
    private static int rarityOrder(@Nonnull EnchantmentMeta meta) {
        switch (meta.getRarity()) {
            case UNCOMMON:
                return 0;
            case RARE:
                return 1;
            case VERY_RARE:
            default:
                return 2;
        }
    }

    /**
     * 判断附魔属于哪个自定义分组。
     *
     * @param enchantment 附魔实例
     * @return 分组；不在任何专用集合中时归为 {@link EnchantmentMeta.CodexGroup#NORMAL}
     */
    @Nonnull
    private static EnchantmentMeta.CodexGroup resolveGroup(@Nonnull Enchantment enchantment) {
        if (CarianStyleEnchantments.RECOLLECT.contains(enchantment)) {
            return EnchantmentMeta.CodexGroup.RECOLLECT;
        }
        if (CarianStyleEnchantments.COMBAT_SKILL.contains(enchantment)) {
            return EnchantmentMeta.CodexGroup.COMBAT_SKILL;
        }
        if (CarianStyleEnchantments.LAW.contains(enchantment)) {
            return EnchantmentMeta.CodexGroup.LAW;
        }
        if (CarianStyleEnchantments.DEAD.contains(enchantment)) {
            return EnchantmentMeta.CodexGroup.DEAD;
        }
        return EnchantmentMeta.CodexGroup.NORMAL;
    }

    // ==================== 自检输出 ====================

    /**
     * 把百科的全部条目打进日志，核对数据是否齐全。
     * <p>
     * 列出本模组每个条目的主题、实现分类、稀有度、等级上限、冲突数、适配物品数和可调数值句柄，
     * 以及原版和其它模组的每个外部条目（含识别出的模组名和反推的物品种类），
     * 再把每条描述按详情页的宽度排一遍版，最后拿一把钻石剑走一遍 {@link ItemFit}。
     * 条目数和注册表里本模组附魔的个数对不上时会在第一行写明。
     * </p>
     * <p>
     * 只给开发和排查用：由 {@link DebugDump} 在配置里打开详细日志时于主界面输出一次，默认不输出。
     * 必须在客户端、语言表就绪之后调用。
     * </p>
     */
    public static void dumpToLog() {
        List<EnchantmentMeta> all = getAll();

        int registered = 0;
        for (Map.Entry<ResourceLocation, Enchantment> entry : ForgeRegistries.ENCHANTMENTS.getEntries()) {
            if (Reference.MOD_ID.equals(entry.getKey().getResourceDomain())) {
                registered++;
            }
        }
        LogUtil.info("卡利亚式附魔 - [百科自检] 本模组条目 %d 个，注册表里本模组附魔 %d 个（%s）",
                all.size(), registered, all.size() == registered ? "一致" : "不一致");

        int withHandles = 0;
        int handleTotal = 0;
        for (EnchantmentMeta meta : all) {
            List<EnchantmentValues.Handle> handles = EnchantmentValues.handlesOf(meta.getId());
            if (!handles.isEmpty()) {
                withHandles++;
                handleTotal += handles.size();
            }
            StringBuilder values = new StringBuilder();
            for (EnchantmentValues.Handle handle : handles) {
                if (values.length() > 0) {
                    values.append(", ");
                }
                values.append(handle.getKey()).append('=').append(formatValue(handle));
            }
            LogUtil.info("卡利亚式附魔 - [百科自检]   %s「%s」主题=%s 分类=%s 稀有度=%s 最高=%d 冲突=%d%s"
                            + " 适配物品=%d（%s）种类=%s 槽位=%s 数值句柄=%d [%s]",
                    meta.getId(), meta.getDisplayName().getString(), meta.getTheme().getKey(),
                    meta.getGroup().name(), meta.getRarity().name(), meta.getMaxLevel(),
                    meta.getConflictCount(), meta.conflictsWithWholeGroup() ? "（整类互斥）" : "",
                    meta.getApplicableItems().size(), itemNames(meta.getApplicableItems()),
                    meta.getCategoryName().getString(), meta.getSlots(), handles.size(), values);
        }
        LogUtil.info("卡利亚式附魔 - [百科自检] 有可调数值的条目 %d 个，句柄合计 %d 个", withHandles, handleTotal);

        // 描述排版：按详情页大致的宽度排一遍公式档，确认每条都排得出来
        Font font = Font.get();
        int totalLines = 0;
        int longest = 0;
        String longestId = "";
        for (EnchantmentMeta meta : all) {
            int lines = DescriptionText.layout(meta.getDescription().getString(), DescriptionText.FORMULA,
                    font, 220, 10).size();
            totalLines += lines;
            if (lines > longest) {
                longest = lines;
                longestId = meta.getId();
            }
        }
        LogUtil.info("卡利亚式附魔 - [百科自检] 描述排版（宽 220）：%d 条共 %d 行，最长 %s %d 行",
                all.size(), totalLines, longestId, longest);

        List<ForeignMeta> foreign = ForeignCodex.getAll();
        Map<String, Integer> byMod = new HashMap<>();
        for (ForeignMeta meta : foreign) {
            byMod.merge(ModNames.displayName(meta.getGroupKey()), 1, Integer::sum);
        }
        LogUtil.info("卡利亚式附魔 - [百科自检] 外部条目 %d 个，来自 %d 个模组：%s", foreign.size(), byMod.size(), byMod);
        for (ForeignMeta meta : foreign) {
            LogUtil.info("卡利亚式附魔 - [百科自检]   %s「%s」模组=%s 稀有度=%s 最高=%d 冲突=%d（外部 %d / 本模组 %d）"
                            + " 适配物品=%d（%s）种类=%s 描述=%s",
                    meta.getKey(), meta.getDisplayName().getString(), meta.getGroupName().getString(),
                    meta.getRarityName().getString(), meta.getMaxLevel(), meta.getConflictCount(),
                    meta.getForeignConflicts().size(), meta.getCarianConflicts().size(),
                    meta.getApplicableItems().size(), itemNames(meta.getApplicableItems()),
                    ItemCategoryNamer.describe(meta.getApplicableItems()).getString(),
                    !meta.hasDescription() ? "无" : (meta.isDescriptionSupplied() ? "本模组补充" : "自带"));
        }

        // 物品适配：拿一把干净的钻石剑走一遍，各结论的个数
        ItemFit.Context context = ItemFit.prepare(new ItemStack(Items.DIAMOND_SWORD));
        Map<ItemFit.Status, Integer> statuses = new EnumMap<>(ItemFit.Status.class);
        int table = 0;
        int anvilOnly = 0;
        for (EnchantmentMeta meta : all) {
            ItemFit fit = ItemFit.evaluate(meta.getEnchantment(), context);
            statuses.merge(fit.getStatus(), 1, Integer::sum);
            if (fit.getStatus() == ItemFit.Status.FITS) {
                if (fit.isViaTable()) {
                    table++;
                } else {
                    anvilOnly++;
                }
            }
        }
        LogUtil.info("卡利亚式附魔 - [百科自检] 钻石剑适配：%s（能附的里附魔台 %d 个、仅铁砧 %d 个）",
                statuses, table, anvilOnly);
    }

    /**
     * @param handle 数值句柄
     * @return 当前值；整数句柄不带小数，被配置改过的后面标「*」
     */
    @Nonnull
    private static String formatValue(@Nonnull EnchantmentValues.Handle handle) {
        String text = handle.isIntegral() ? String.valueOf(handle.getInt()) : String.valueOf(handle.get());
        return handle.isOverridden() ? text + "*" : text;
    }

    /**
     * @param items 物品
     * @return 注册名的路径部分，逗号分隔
     */
    @Nonnull
    private static String itemNames(@Nonnull List<Item> items) {
        StringBuilder builder = new StringBuilder();
        for (Item item : items) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            ResourceLocation name = item.getRegistryName();
            builder.append(name == null ? "?" : name.getResourcePath());
        }
        return builder.toString();
    }

    /**
     * 自检入口：第一次进主界面时把百科条目打进日志。
     * <p>
     * 只在配置里打开了详细日志（{@code enableDetailedLogging}）时输出，每次启动最多一次；
     * 开发环境也一样，不再默认输出（几百行条目每次启动都刷一遍，盖住了真正要看的日志）。
     * 挑主界面是因为那时语言表、注册表都已就绪，又还没进世界，不会和游戏里的日志搅在一起。
     * </p>
     * <p>
     * 只在客户端注册（{@code value = Side.CLIENT}），专用服不会加载它，也就不会碰到百科用的客户端类。
     * </p>
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
    public static final class DebugDump {

        /** 本次启动是否已经判断过（不管输出没输出，都只判断一次） */
        private static boolean checked = false;

        private DebugDump() {
        }

        /**
         * 第一次打开主界面时按开关输出自检。
         *
         * @param event 界面打开事件
         */
        @SubscribeEvent
        public static void onGuiOpen(GuiOpenEvent event) {
            if (checked || !(event.getGui() instanceof GuiMainMenu)) {
                return;
            }
            checked = true;
            if (!ConfigLoader.enableDetailedLogging) {
                return;
            }
            try {
                dumpToLog();
            } catch (Throwable t) {
                // 自检出错只记日志：它不是玩法的一部分，不能因此进不了游戏
                LogUtil.error("卡利亚式附魔 - [百科自检] 输出失败", t);
            }
        }
    }
}
