package pers.roinflam.carianstyle.init;

import pers.roinflam.carianstyle.annotation.EnchantTarget;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 模组附魔分类
 * <p>
 * 统一管理所有自定义附魔的分类
 * </p>
 *
 * <h3>v2.0：1.21.1 不再在这里注册</h3>
 * <p>
 * 1.21.1 的附魔注册表是数据包注册表，原来的 {@code DeferredRegister<Enchantment>} 和三个
 * {@code EnchantmentCategory.create} 自定义类别都没有了：附魔条目由内置数据包生成，
 * 「能上哪类物品」改成 {@link EnchantTarget} + 生成的物品标签。这里只剩分组集合和追忆类的附魔能力常量。
 * </p>
 *
 * <h3>v1.1 修复：补上缺失的 PICKAXE 自定义类型</h3>
 * <p>
 * {@link #getCustomEnchantmentCategory(String)} 的文档注释里写明支持 {@code "PICKAXE"}，
 * 但 switch 分支里原来<b>从来没有实现</b>，传入 "PICKAXE" 会静默返回 null，
 * 从构造函数走的附魔一调 {@code canEnchant} 就 NPE。补上后判定为只认镐子，
 * 供 {@code EnchantmentRockBlaster} 使用（该附魔的实际逻辑本来就只认镐子）。
 * </p>
 *
 * @version 2.0
 */
public class CarianStyleEnchantments {

    // ==================== 附魔能力常量 ====================

    /**
     * 追忆类附魔的附魔能力值
     * <p>
     * 追忆类附魔是最强大的附魔，需要很高的附魔能力才能获得。
     * 注意：14 个追忆附魔类各自声明了同名的本类常量（35），只有写全了
     * {@code CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY} 的两个（噩兆、命定之死）用的是这里的 38。
     * 花费已经写进各附魔的注解，这个常量只留着给读数值的地方。
     * </p>
     */
    public static final int RECOLLECT_ENCHANTABILITY = 38;

    // ==================== 附魔列表 ====================

    /**
     * 所有附魔的总列表（附魔基类的构造器里加进来）
     */
    public static final List<EnchantmentBase> ENCHANTMENTS = new ArrayList<>();

    /**
     * 追忆类附魔集合
     * <p>
     * 代表强大的记忆力量，包含最强大的附魔效果
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<EnchantmentBase> RECOLLECT = new HashSet<>();

    /**
     * 战技类附魔集合
     * <p>
     * 包含各种战斗技能类附魔，如盾击、狮子斩等
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<EnchantmentBase> COMBAT_SKILL = new HashSet<>();

    /**
     * 律法类附魔集合
     * <p>
     * 体现不同的法则之力
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<EnchantmentBase> LAW = new HashSet<>();

    /**
     * 死亡类附魔集合
     * <p>
     * 在生死边缘发挥作用的附魔
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<EnchantmentBase> DEAD = new HashSet<>();

    /**
     * 获取所有已注册的附魔数量（扫出来的行为对象个数；注册表条目由内置数据包按同一张表生成）
     *
     * @return 已注册附魔数量
     */
    public static int getRegisteredCount() {
        return EnchantmentRegistry.size();
    }

    // ==================== 自定义类型映射 ====================

    /**
     * 根据字符串标识符获取对应的自定义类别
     * <p>
     * 附魔类构造器里 {@code super(getCustomEnchantmentCategory("SHIELD"), ...)} 这种写法照旧可用，
     * 返回值从原来的 {@code EnchantmentCategory} 换成了 {@link EnchantTarget}。
     * </p>
     *
     * @param customType 自定义类型标识符（大小写不敏感）
     *                   <ul>
     *                     <li>"SHIELD" - 盾牌类型</li>
     *                     <li>"ARMS" - 武器类型（剑+弓+弩）</li>
     *                     <li>"PICKAXE" - 镐子类型（v1.1 新增实现）</li>
     *                   </ul>
     * @return 对应的类别，如果标识符无效或为空则返回 null
     */
    public static EnchantTarget getCustomEnchantmentCategory(String customType) {
        // 空字符串或 null 表示不使用自定义类型
        if (customType == null || customType.isEmpty()) {
            return null;
        }

        // 转换为大写进行匹配（大小写不敏感）
        switch (customType.toUpperCase(Locale.ROOT)) {
            case "SHIELD":
                return EnchantTarget.SHIELD;
            case "ARMS":
                return EnchantTarget.ARMS;
            case "PICKAXE":
                // v1.1：补上此前只写在文档里、从未实现的分支
                return EnchantTarget.PICKAXE;
            default:
                // 未知的自定义类型，返回 null
                // 调用者应该处理这种情况（抛出异常或使用默认值）
                return null;
        }
    }
}
