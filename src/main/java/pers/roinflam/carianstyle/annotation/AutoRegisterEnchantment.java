package pers.roinflam.carianstyle.annotation;

import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.EventPriority;

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
 * <h3>1.21.1 的变化</h3>
 * <p>
 * 1.21.1 的附魔是数据包注册表里的条目，代码里不再注册 {@code Enchantment} 对象。
 * 这个注解现在是内置数据包生成附魔 JSON 的数据来源：花费、类别、槽位、宝藏、诅咒、互斥都从这里和
 * 附魔类的 {@code checkCompatibility} 读出来，所以它必须完整、准确：
 * </p>
 * <ul>
 *   <li>{@link #type()} 换成本模组的 {@link EnchantTarget}（原版 {@code EnchantmentCategory} 已删），
 *       原来的 {@code customType} 字符串（SHIELD / ARMS / PICKAXE）并进了 {@link EnchantTarget}；</li>
 *   <li>{@link #baseEnchantability()} / {@link #levelMultiplier()} 是附魔台花费，原来写在每个附魔类的
 *       {@code getMinCost} 覆写里，现在一律写在注解上（固定花费的 {@code levelMultiplier = 0}）。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoRegisterEnchantment {

    /**
     * 附魔的唯一标识符
     * <p>
     * 也是注册表 id 的路径部分（{@code carianstyle:<id>}）和翻译键的后缀，存档里的物品靠它认附魔，不能改。
     * </p>
     */
    String id();

    /**
     * 附魔所属的类别（自定义分类）
     * 注意：这是模组自定义的EnchantmentCategory，不是原版的
     */
    pers.roinflam.carianstyle.annotation.EnchantmentCategory category();

    /**
     * 附魔的稀有度等级
     */
    EnchantmentRarity rarity();

    /**
     * 附魔能上哪类物品
     * <p>
     * 和附魔类构造器传给 {@code super} 的类别必须一致（1.20.1 真正生效的是构造器那一份）。
     * 没有默认值：每个附魔都写明。
     * </p>
     */
    EnchantTarget type();

    /**
     * 附魔可应用的装备槽位
     * <p>
     * 和构造器传给 {@code super} 的槽位一致；生成附魔 JSON 时换算成 1.21.1 的槽位组
     * （主手 {@code mainhand}、主副手 {@code hand}、四个护甲槽 {@code armor} 等）。
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
     * 附魔台花费的基础值 B（1 级时的最低附魔能力）
     * <p>
     * 每级最低花费 = B + (等级 - 1) × M，再乘附魔难度 {@code enchantingDifficulty}；最高花费 = 最低花费 + 50。
     * -1 表示按稀有度的默认值算（{@link EnchantmentRarity#calculateEnchantability}）。
     * </p>
     */
    int baseEnchantability() default -1;

    /**
     * 附魔台花费的每级增量 M，固定花费的附魔写 0
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
