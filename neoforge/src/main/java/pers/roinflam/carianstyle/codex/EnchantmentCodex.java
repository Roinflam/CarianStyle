package pers.roinflam.carianstyle.codex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
 * <h3>数据来自客户端的世界注册表</h3>
 * <p>
 * 1.21.1 的附魔定义（花费、权重、互斥、能上哪些物品）和获取途径标签都在世界的注册表里，
 * 本模组那部分是服务器按它自己的配置生成内置数据包、进世界时同步到客户端的。
 * 百科读的就是客户端手上这一份，也就是这台服务器的真实情况，不读客户端本地配置。
 * </p>
 * <p>
 * 注册表跟着世界走：换一台服务器、重进单人存档都是新的一份。所以索引记下自己是对着哪一份注册表建的，
 * 拿到的注册表换了就重建，不跨世界复用里面的 Holder。还没进世界（没有注册表）时索引是空的。
 * </p>
 *
 * <h3>冲突关系的双向补全</h3>
 * <p>
 * 注解里的 {@code conflictsWith} 是<b>单向</b>声明：A 写了「与 B 冲突」，
 * B 往往没写「与 A 冲突」。但原版的 {@code Enchantment.areCompatible}
 * 在附魔台/铁砧上是<b>双向</b>生效的（两边的互斥集合任一方含对方即不能共存）。
 * 若百科只照搬注解，玩家在 B 的页面上会看不到这条冲突，然后在铁砧上撞墙。
 * </p>
 * <p>
 * 因此本类在建索引时直接用 {@code areCompatible} 实测每一对，
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
 * @version 1.1
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

    /** 索引是对着哪一份附魔注册表建的；注册表换了（换世界）就重建。弱引用，不拖住旧世界 */
    private static WeakReference<Registry<Enchantment>> builtFor = new WeakReference<>(null);

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
     * 当前世界的附魔注册表（客户端的那一份，服务器同步下来的）。
     *
     * @return 注册表；还没进世界时为 null
     */
    @Nullable
    static Registry<Enchantment> currentRegistry() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
    }

    /**
     * 确保索引已构建（惰性，双重检查）；注册表换了（换了世界）也重建。
     */
    private static void ensureBuilt() {
        Registry<Enchantment> registry = currentRegistry();
        if (built && builtFor.get() == registry) {
            return;
        }
        synchronized (BUILD_LOCK) {
            if (built && builtFor.get() == registry) {
                return;
            }
            if (registry == null) {
                byId = Collections.emptyMap();
                ordered = Collections.emptyList();
            } else {
                build(registry);
            }
            builtFor = new WeakReference<>(registry);
            built = true;
        }
    }

    /**
     * 扫描注册表构建索引。
     * <p>
     * 数据源：
     * <ul>
     *   <li>{@code CarianStyleEnchantments.ENCHANTMENTS} —— 全部附魔行为对象</li>
     *   <li>{@code base.getAnnotation()} —— 注解元数据</li>
     *   <li>世界注册表 —— 附魔定义与标签（按 {@code base.getKey()} 取）</li>
     *   <li>{@code RECOLLECT / COMBAT_SKILL / LAW / DEAD} 四个 Set —— 分组归属</li>
     * </ul>
     * </p>
     *
     * @param registry 当前世界的附魔注册表
     */
    private static void build(@Nonnull Registry<Enchantment> registry) {
        long start = System.currentTimeMillis();

        Map<String, EnchantmentMeta> idMap = new HashMap<>();
        List<EnchantmentMeta> list = new ArrayList<>();
        int missing = 0;

        for (EnchantmentBase enchantment : CarianStyleEnchantments.ENCHANTMENTS) {
            AutoRegisterEnchantment annotation = enchantment.getAnnotation();
            if (annotation == null || enchantment.getKey() == null) {
                // 没有注解的附魔无法确定 id，也不在注册表里，进不了百科
                continue;
            }
            // 内置数据包每个附魔都生成（禁用的也生成），正常不会缺；
            // 缺了说明被别的数据包删掉了，这时游戏里本来就没有它，百科也不列
            Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(enchantment.getKey());
            if (holder.isEmpty()) {
                missing++;
                continue;
            }

            EnchantmentMeta meta = new EnchantmentMeta(enchantment, holder.get(), annotation,
                    resolveGroup(enchantment));
            meta.buildSearchIndex();

            idMap.put(meta.getId(), meta);
            list.add(meta);
        }
        if (missing > 0) {
            LogUtil.warn("卡利亚式附魔 - 百科：有 %d 个附魔不在当前世界的注册表里（被数据包删掉了？），没有收录", missing);
        }

        // ==================== 冲突关系：实测而非读注解 ====================
        //
        // 早先这里只遍历 annotation.conflictsWith() 再做双向补全，漏掉了两大类：
        //
        //   1. 整类互斥。EnchantmentBase.checkCompatibility 里有一条
        //      「同类别互斥（GENERAL 除外）」——25 个战技之间两两互斥、
        //      14 个追忆之间两两互斥，这些一条注解都没写。
        //      结果「卡利亚式奉还」在百科里显示「冲突 2」，实际是 26。
        //   2. 各附魔自己覆写的 checkCompatibility，比如重力与原版「击退」互斥。
        //
        // 现在改为直接调 Enchantment.areCompatible 实测——那是附魔台与铁砧真正使用的判定。
        // 1.21.1 的互斥在注册表的 exclusive_set 里，本模组那份由内置数据包按注解、整类互斥、
        // allowWith 白名单和所有覆写逐对算出来，数据包作者还能改；读它就一次全覆盖，
        // 以后规则怎么改，百科都自动跟上，不需要在这里复刻一份判断逻辑。
        //
        // 开销：113 × (113 + 原版约 40) ≈ 1.7 万次调用，只在首次打开界面时跑一次。
        List<Holder.Reference<Enchantment>> foreigners = new ArrayList<>();
        registry.holders().forEach(holder -> {
            if (!isOurs(holder)) {
                foreigners.add(holder);
            }
        });
        for (EnchantmentMeta meta : list) {
            Holder<Enchantment> self = meta.getEnchantment();

            // 与本模组其它附魔
            for (EnchantmentMeta other : list) {
                if (other == meta) {
                    continue;
                }
                if (!Enchantment.areCompatible(self, other.getEnchantment())) {
                    meta.addConflict(other.getId());
                }
            }

            // 与原版及其它模组的附魔
            for (Holder.Reference<Enchantment> foreign : foreigners) {
                if (!Enchantment.areCompatible(self, foreign)) {
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
     * 注册表里的这个附魔是不是本模组的（归「附魔百科」标签页，不算外部附魔）。
     * <p>
     * 按行为对象表查，查不到再看命名空间：万一有人把本模组的附魔写到别的命名空间下，
     * 行为对象表仍然准确；本命名空间下多出来的（数据包加的）也不当外部附魔。
     * </p>
     *
     * @param holder 注册表里的附魔
     * @return 是否本模组的
     */
    static boolean isOurs(@Nonnull Holder.Reference<Enchantment> holder) {
        return EnchantmentRegistry.byKey(holder.key()) != null
                || Reference.MOD_ID.equals(holder.key().location().getNamespace());
    }

    /**
     * 判断附魔属于哪个自定义分组。
     *
     * @param enchantment 附魔行为对象
     * @return 分组；不在任何专用集合中时归为 {@link EnchantmentMeta.CodexGroup#NORMAL}
     */
    @Nonnull
    private static EnchantmentMeta.CodexGroup resolveGroup(@Nonnull EnchantmentBase enchantment) {
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
