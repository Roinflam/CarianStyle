package pers.roinflam.carianstyle.annotation;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 附魔自动注册注解
 * <p>
 * 在附魔类上添加此注解后，系统会自动完成以下操作：
 * 1. 注册附魔到游戏系统
 * 2. 添加到对应的分类集合（COMBAT_SKILL、RECOLLECT、LAW、DEAD）
 * 3. 生成静态引用到CarianStyleEnchantments类
 * 4. 自动处理冲突规则
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
     * <p>
     * 用于：
     * 1. 注册到游戏系统的注册名
     * 2. 国际化文件的键值（enchantment.{modid}.{id}）
     * 3. 内部引用标识
     * </p>
     *
     * @return 附魔ID，必须唯一且符合Minecraft命名规范（小写字母、数字、下划线）
     */
    String id();

    /**
     * 附魔所属的类别
     * <p>
     * 决定附魔会被添加到哪个分类集合，同时自动处理同类互斥规则
     * </p>
     *
     * @return 附魔类别
     */
    EnchantmentCategory category();

    /**
     * 附魔的稀有度等级
     * <p>
     * 决定：
     * 1. 默认的最大等级（UNCOMMON=5, RARE=3, VERY_RARE=1）
     * 2. 默认的附魔能力公式
     * 3. 是否为宝藏附魔（根据配置文件）
     * </p>
     *
     * @return 稀有度等级
     */
    EnchantmentRarity rarity();

    /**
     * 附魔可应用的附魔类型
     * <p>
     * 默认为WEAPON，表示只能附魔到武器上
     * </p>
     *
     * @return 附魔类型
     */
    EnumEnchantmentType type() default EnumEnchantmentType.WEAPON;

    /**
     * 附魔可应用的装备槽位
     * <p>
     * 默认为主手槽位
     * </p>
     *
     * @return 装备槽位数组
     */
    EntityEquipmentSlot[] slots() default {EntityEquipmentSlot.MAINHAND};

    /**
     * 与此附魔冲突的附魔类列表
     * <p>
     * 列表中的附魔无法与此附魔同时存在于同一装备上
     * 注意：同类别的附魔会自动冲突，无需在此处重复声明
     * </p>
     *
     * @return 冲突的附魔类数组
     */
    Class<?>[] conflictsWith() default {};

    /**
     * 允许共存的附魔类列表（例外规则）
     * <p>
     * 即使某个附魔在conflictsWith中或属于同一类别，
     * 如果它在allowWith列表中，仍然允许共存
     * </p>
     *
     * @return 允许共存的附魔类数组
     */
    Class<?>[] allowWith() default {};

    /**
     * 自定义的附魔能力基础值
     * <p>
     * 如果设置为-1（默认值），则使用稀有度对应的默认公式
     * 如果设置为其他值，则使用：baseEnchantability + (level - 1) * levelMultiplier
     * </p>
     *
     * @return 基础附魔能力值，-1表示使用默认值
     */
    int baseEnchantability() default -1;

    /**
     * 自定义的附魔能力等级倍率
     * <p>
     * 配合baseEnchantability使用，计算公式为：
     * baseEnchantability + (level - 1) * levelMultiplier
     * </p>
     *
     * @return 等级倍率，默认为10
     */
    int levelMultiplier() default 10;

    /**
     * 此附魔监听的事件类型和优先级配置
     * <p>
     * 如果为空数组，则附魔不会自动监听任何事件，需要手动添加事件处理器
     * </p>
     *
     * @return 事件处理配置数组
     */
    EventHandler[] events() default {};

    /**
     * 是否强制设置为宝藏附魔
     * <p>
     * 如果设置为true，无论配置文件如何设置都会是宝藏附魔
     * 如果设置为false，则根据稀有度和配置文件决定
     * </p>
     *
     * @return 是否强制宝藏附魔
     */
    boolean forceTreasure() default false;

    /**
     * 是否为诅咒附魔
     * <p>
     * 诅咒附魔无法通过砂轮移除
     * </p>
     *
     * @return 是否为诅咒附魔
     */
    boolean isCurse() default false;

    /**
     * 事件处理器配置
     * <p>
     * 用于声明附魔需要监听哪个事件以及使用什么优先级
     * </p>
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface EventHandler {
        /**
         * 事件类型
         *
         * @return 事件类型枚举
         */
        EnchantmentEventType type();

        /**
         * 事件优先级
         *
         * @return 优先级，默认为NORMAL
         */
        EventPriority priority() default EventPriority.NORMAL;
    }
}