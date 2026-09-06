package pers.roinflam.carianstyle.codex;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 原版及其它模组附魔的百科索引。
 *
 * <h3>与 {@link EnchantmentCodex} 的关系</h3>
 * <p>
 * 两者是<b>并列</b>的两份索引，各自服务一个标签页；冲突关系则是<b>互通</b>的：
 * 本模组附魔的详情里能点开原版的「击退」，原版「击退」的详情里也能点回「重力」。
 * </p>
 * <p>
 * 互通靠的是同一套判定——{@code isCompatibleWith}。这一点很重要：
 * 如果两边各自维护一份冲突表，迟早会出现「A 说与 B 冲突、B 说不冲突」的矛盾，
 * 而玩家会以为是百科出错。共用同一个判定源就不存在这个问题。
 * </p>
 *
 * <h3>构建开销</h3>
 * <p>
 * 设外部附魔 N 个（原版 40 左右，装了附魔类模组可能到几百）：
 * 两两互测是 N²，加上与本模组 113 个的互测。N=200 时约 6.2 万次
 * {@code isCompatibleWith}，在客户端首次打开界面时跑一次，几十毫秒量级。
 * </p>
 * <p>
 * 之所以敢这么算，是因为它<b>只在客户端、只跑一次</b>，
 * 而换来的是完全准确的冲突关系——手写规则表在装了未知模组时必然出错。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class ForeignCodex {

    /** 构建锁 */
    private static final Object BUILD_LOCK = new Object();

    /** 键 -> 元数据 */
    private static Map<String, ForeignMeta> byKey = Collections.emptyMap();

    /** 排好序的完整列表 */
    private static List<ForeignMeta> ordered = Collections.emptyList();

    /** 是否已构建 */
    private static volatile boolean built = false;

    private ForeignCodex() {
    }

    /**
     * 取全部外部附魔（已排序）。
     *
     * @return 不可修改的列表
     */
    @Nonnull
    public static List<ForeignMeta> getAll() {
        ensureBuilt();
        return ordered;
    }

    /**
     * 按键精确查找。
     *
     * @param key {@code 命名空间:路径}
     * @return 元数据；不存在时返回 null
     */
    @Nullable
    public static ForeignMeta get(@Nullable String key) {
        if (key == null) {
            return null;
        }
        ensureBuilt();
        return byKey.get(key);
    }

    /**
     * 按关键字筛选。
     *
     * @param keyword 关键字，可为 null 或空串
     * @return 匹配结果
     */
    @Nonnull
    public static List<ForeignMeta> search(@Nullable String keyword) {
        ensureBuilt();
        String lower = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<ForeignMeta> result = new ArrayList<>(ordered.size());
        for (ForeignMeta meta : ordered) {
            if (meta.matches(lower)) {
                result.add(meta);
            }
        }
        return result;
    }

    /**
     * 让索引失效，下次访问时重建。
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
     */
    private static void build() {
        long start = System.currentTimeMillis();

        // 本模组的附魔实例集合，用于把它们从「外部」里排除掉
        Set<Enchantment> ours = new HashSet<>(CarianStyleEnchantments.ENCHANTMENTS);

        Map<String, ForeignMeta> keyMap = new HashMap<>();
        List<ForeignMeta> list = new ArrayList<>();

        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry
                : ForgeRegistries.ENCHANTMENTS.getEntries()) {

            Enchantment enchantment = entry.getValue();
            ResourceLocation id = entry.getKey().location();

            // 本模组的附魔归「附魔百科」标签页，这里只收其余的。
            // 用实例集合判断而不是比较命名空间——万一有人把本模组的附魔
            // 注册到别的命名空间下，实例判断仍然准确
            if (ours.contains(enchantment) || Reference.MOD_ID.equals(id.getNamespace())) {
                continue;
            }

            ForeignMeta meta = new ForeignMeta(enchantment, id);
            meta.buildSearchIndex();
            keyMap.put(meta.getKey(), meta);
            list.add(meta);
        }

        buildConflicts(list);

        // 排序：原版在最前，其余模组按命名空间字母序；组内稀有度由低到高，再按名称
        list.sort((a, b) -> {
            int groupCmp = compareNamespace(a.getGroupKey(), b.getGroupKey());
            if (groupCmp != 0) {
                return groupCmp;
            }
            int rarityCmp = Integer.compare(a.getRarityOrder(), b.getRarityOrder());
            if (rarityCmp != 0) {
                return rarityCmp;
            }
            return a.getDisplayName().getString().compareTo(b.getDisplayName().getString());
        });

        byKey = Collections.unmodifiableMap(keyMap);
        ordered = Collections.unmodifiableList(list);

        LogUtil.info("卡利亚式附魔 - 外部附魔索引构建完成，收录 %d 个，耗时 %d 毫秒",
                list.size(), System.currentTimeMillis() - start);
    }

    /**
     * 实测并记录冲突关系。
     *
     * @param list 全部外部附魔
     */
    private static void buildConflicts(@Nonnull List<ForeignMeta> list) {
        // 外部附魔之间
        for (ForeignMeta meta : list) {
            Enchantment self = meta.getEnchantment();
            for (ForeignMeta other : list) {
                if (other == meta) {
                    continue;
                }
                if (!self.isCompatibleWith(other.getEnchantment())) {
                    meta.addForeignConflict(other.getKey());
                }
            }
        }

        // 与本模组的附魔。这一份是双向的入口：
        // EnchantmentCodex 那边已经记录了「本模组 -> 外部」，
        // 这里记录「外部 -> 本模组」，两个标签页才能互相跳转
        for (EnchantmentMeta ours : EnchantmentCodex.getAll()) {
            Enchantment enchantment = ours.getEnchantment();
            for (ForeignMeta meta : list) {
                if (!enchantment.isCompatibleWith(meta.getEnchantment())) {
                    meta.addCarianConflict(ours.getId());
                }
            }
        }
    }

    /**
     * 命名空间排序：原版永远在最前。
     *
     * @param a 命名空间 A
     * @param b 命名空间 B
     * @return 比较结果
     */
    private static int compareNamespace(@Nonnull String a, @Nonnull String b) {
        boolean va = "minecraft".equals(a);
        boolean vb = "minecraft".equals(b);
        if (va != vb) {
            return va ? -1 : 1;
        }
        return a.compareTo(b);
    }
}
