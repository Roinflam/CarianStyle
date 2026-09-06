package pers.roinflam.carianstyle.codex;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
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
 * 之所以不在 {@code FMLCommonSetupEvent} 里预先建好，是因为搜索索引需要
 * <b>语言文件已加载</b>（{@code Component.translatable().getString()} 才有真实文本），
 * 而语言表在客户端资源重载后才就绪，比 setup 阶段晚。
 * </p>
 * <p>
 * 语言切换或资源重载后调用 {@link #invalidate()} 即可让下次访问重建，
 * 界面里的重载按钮走的就是这条路。
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
 * @author FlameForge
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
        //   1. 整类互斥。EnchantmentBase.checkCompatibility 里有一条
        //      「同类别互斥（GENERAL 除外）」——25 个战技之间两两互斥、
        //      14 个回忆之间两两互斥，这些一条注解都没写。
        //      结果「卡利亚式奉还」在百科里显示「冲突 2」，实际是 26。
        //   2. 各附魔自己覆写的 checkCompatibility，比如重力与原版「击退」互斥。
        //
        // 现在改为直接调 isCompatibleWith 实测——那是附魔台与铁砧真正使用的判定，
        // 一次覆盖注解、整类互斥、allowWith 白名单和所有覆写，
        // 而且以后规则怎么改，百科都自动跟上，不需要在这里复刻一份判断逻辑。
        //
        // 开销：113 × (113 + 原版约 40) ≈ 1.7 万次调用，只在首次打开界面时跑一次。
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
}
