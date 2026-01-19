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
            item -> item instanceof SwordItem || item instanceof BowItem
    );

    /**
     * 镐子附魔类型
     */
    public static final EnchantmentCategory PICKAXE = EnchantmentCategory.create(
            "cs_pickaxe",
            item -> item instanceof PickaxeItem
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
}