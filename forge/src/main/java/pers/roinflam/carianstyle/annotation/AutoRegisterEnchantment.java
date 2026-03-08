package pers.roinflam.carianstyle.annotation;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 附魔自动注册注解
 * <p>
 * 在附魔类上添加此注解后，系统会自动完成注册等操作
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoRegisterEnchantment {

    /**
     * 附魔的唯一标识符
     */
    String id();

    /**
     * 附魔所属的类别（自定义分类）
     * ⚠️ 注意：这是模组自定义的EnchantmentCategory，不是原版的
     */
    pers.roinflam.carianstyle.annotation.EnchantmentCategory category();

    /**
     * 附魔的稀有度等级
     */
    EnchantmentRarity rarity();

    /**
     * 附魔可应用的附魔类型（原版）
     * <p>
     * 1.20.1使用原版的EnchantmentCategory
     * 默认值为 BREAKABLE，适用于大多数可损坏物品
     * </p>
     * <p>
     * ⚠️ 如果需要使用自定义类型（如SHIELD、ARMS、PICKAXE等），
     * 请使用 customType() 参数，不要使用此参数
     * </p>
     */
    EnchantmentCategory type() default EnchantmentCategory.BREAKABLE;

    /**
     * 自定义附魔类型的字符串标识符
     * <p>
     * 用于指定在 CarianStyleEnchantments 中定义的自定义附魔类型
     * </p>
     * <p>
     * 可选值：
     * <ul>
     *   <li>"SHIELD" - 盾牌类型（对应 CarianStyleEnchantments.SHIELD）</li>
     *   <li>"ARMS" - 武器类型，剑+弓（对应 CarianStyleEnchantments.ARMS）</li>
     *   <li>"PICKAXE" - 镐子类型（对应 CarianStyleEnchantments.PICKAXE）</li>
     *   <li>"" (空字符串) - 不使用自定义类型，使用 type() 参数指定的原版类型</li>
     * </ul>
     * </p>
     * <p>
     * ⚠️ 注意：customType 和 type 只能二选一使用
     * <ul>
     *   <li>如果 customType 不为空，则使用自定义类型，忽略 type 参数</li>
     *   <li>如果 customType 为空，则使用 type 参数指定的原版类型</li>
     * </ul>
     * </p>
     */
    String customType() default "";

    /**
     * 附魔可应用的装备槽位
     * <p>
     * 1.20.1使用EquipmentSlot代替EntityEquipmentSlot
     * </p>
     */
    EquipmentSlot[] slots() default {EquipmentSlot.MAINHAND};

    /**
     * 与此附魔冲突的附魔类列表
     */
    Class<?>[] conflictsWith() default {};

    /**
     * 允许共存的附魔类列表（例外规则）
     */
    Class<?>[] allowWith() default {};

    /**
     * 自定义的附魔能力基础值
     */
    int baseEnchantability() default -1;

    /**
     * 自定义的附魔能力等级倍率
     */
    int levelMultiplier() default 10;

    /**
     * 此附魔监听的事件类型和优先级配置
     */
    EventHandler[] events() default {};

    /**
     * 是否强制设置为宝藏附魔
     */
    boolean forceTreasure() default false;

    /**
     * 是否为诅咒附魔
     */
    boolean isCurse() default false;

    /**
     * 事件处理器配置
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface EventHandler {
        /**
         * 事件类型
         */
        EnchantmentEventType type();

        /**
         * 事件优先级
         */
        EventPriority priority() default EventPriority.NORMAL;
    }
}