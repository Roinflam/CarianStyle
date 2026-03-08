package pers.roinflam.carianstyle.init;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.*;

/**
 * 模组附魔注册类
 * <p>
 * 统一管理所有自定义附魔的实例化和分类
 * </p>
 */
public class CarianStyleEnchantments {

    // ==================== DeferredRegister ====================

    /**
     * 附魔注册器
     * <p>
     * 必须在模组主类中将此注册器挂载到事件总线
     * </p>
     */
    public static final DeferredRegister<Enchantment> ENCHANTMENTS_REGISTER =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Reference.MOD_ID);

    // ==================== 自定义附魔类型 ====================

    /**
     * 盾牌附魔类型
     * <p>
     * 1.20.1 中无法使用 EnumHelper，改为直接创建 EnchantmentCategory
     * </p>
     */
    public static final EnchantmentCategory SHIELD = EnchantmentCategory.create(
            "cs_shield",
            item -> item instanceof ShieldItem
    );

    /**
     * 武器附魔类型（剑+弓）
     */
    public static final EnchantmentCategory ARMS = EnchantmentCategory.create(
            "cs_arms",
            item -> item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem
    );

    // ==================== 附魔能力常量 ====================

    /**
     * 追忆类附魔的附魔能力值
     * <p>
     * 追忆类附魔是最强大的附魔，需要很高的附魔能力才能获得
     * </p>
     */
    public static final int RECOLLECT_ENCHANTABILITY = 38;

    // ==================== 附魔列表 ====================

    /**
     * 所有附魔的总列表（用于分类管理）
     * <p>
     * 注意：这个列表只用于逻辑分类，真正的注册由 ENCHANTMENTS_REGISTER 完成
     * </p>
     */
    public static final List<Enchantment> ENCHANTMENTS = new ArrayList<>();

    /**
     * 追忆类附魔集合
     * <p>
     * 代表强大的记忆力量，包含最强大的附魔效果
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<Enchantment> RECOLLECT = new HashSet<>();

    /**
     * 战技类附魔集合
     * <p>
     * 包含各种战斗技能类附魔，如盾击、狮子斩等
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<Enchantment> COMBAT_SKILL = new HashSet<>();

    /**
     * 律法类附魔集合
     * <p>
     * 体现不同的法则之力
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<Enchantment> LAW = new HashSet<>();

    /**
     * 死亡类附魔集合
     * <p>
     * 在生死边缘发挥作用的附魔
     * 同类别的附魔默认互斥
     * </p>
     */
    public static final Set<Enchantment> DEAD = new HashSet<>();

    // ==================== 动态注册存储 ====================

    /**
     * 存储所有动态注册的附魔
     * <p>
     * 键：附魔ID，值：RegistryObject
     * </p>
     */
    private static final List<RegistryObject<Enchantment>> REGISTERED_ENCHANTMENTS = new ArrayList<>();

    // ==================== 注册方法 ====================

    /**
     * 注册单个附魔到 DeferredRegister
     * <p>
     * 此方法由 EnchantmentRegistry 在扫描时调用
     * </p>
     *
     * @param id 附魔ID
     * @param enchantment 附魔实例
     * @return RegistryObject
     */
    public static RegistryObject<Enchantment> registerEnchantment(String id, Enchantment enchantment) {
        RegistryObject<Enchantment> registryObject = ENCHANTMENTS_REGISTER.register(id, () -> enchantment);
        REGISTERED_ENCHANTMENTS.add(registryObject);
        return registryObject;
    }

    /**
     * 获取所有已注册的附魔数量
     *
     * @return 已注册附魔数量
     */
    public static int getRegisteredCount() {
        return REGISTERED_ENCHANTMENTS.size();
    }

    // ==================== 自定义类型映射 ====================

    /**
     * 根据字符串标识符获取对应的自定义 EnchantmentCategory
     * <p>
     * 此方法用于将注解中的 customType 字符串参数映射到实际的 EnchantmentCategory 对象
     * 因为注解参数必须是编译时常量，不能直接使用运行时创建的对象
     * </p>
     *
     * @param customType 自定义类型标识符（大小写不敏感）
     *                   <ul>
     *                     <li>"SHIELD" - 盾牌类型</li>
     *                     <li>"ARMS" - 武器类型（剑+弓）</li>
     *                     <li>"PICKAXE" - 镐子类型</li>
     *                   </ul>
     * @return 对应的 EnchantmentCategory 对象，如果标识符无效或为空则返回 null
     */
    public static EnchantmentCategory getCustomEnchantmentCategory(String customType) {
        // 空字符串或 null 表示不使用自定义类型
        if (customType == null || customType.isEmpty()) {
            return null;
        }

        // 转换为大写进行匹配（大小写不敏感）
        switch (customType.toUpperCase()) {
            case "SHIELD":
                return SHIELD;
            case "ARMS":
                return ARMS;
            default:
                // 未知的自定义类型，返回 null
                // 调用者应该处理这种情况（抛出异常或使用默认值）
                return null;
        }
    }
}