package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 互斥判断里的「另一个附魔」
 *
 * <h3>为什么要有</h3>
 * <p>
 * 1.20.1 的 {@code checkCompatibility(Enchantment other)} 拿到的是附魔对象本身：原版附魔用
 * {@code other == Enchantments.SHARPNESS} 比，本模组附魔用 {@code instanceof} / {@code getClass()} 比。
 * 1.21.1 的附魔是数据包注册表里的条目，{@code Enchantments.X} 只是 {@code ResourceKey}，
 * 本模组的附魔类也不再继承 {@code Enchantment}（它们只是行为对象）。
 * </p>
 * <p>
 * 这个类把两边包在一起：注册表键 + 本模组的行为对象（不是本模组的附魔为 null）。
 * 32 个 {@code checkCompatibility} 覆写只做机械替换（{@code ==} 换 {@link #is}、{@code instanceof X} 换
 * {@link #isOf}、{@code getClass()} 换 {@link #enchantClass()}），判断逻辑一字不动。
 * </p>
 * <p>
 * 互斥关系在 1.21.1 写进附魔 JSON 的 {@code exclusive_set}，由内置数据包生成时对
 * 「本模组 113 个 × (本模组 113 个 + 原版全部附魔键)」逐对求出来，运行期不再调用判断方法。
 * </p>
 */
public final class EnchantKey {

    private final ResourceKey<Enchantment> key;
    @Nullable
    private final EnchantmentBase base;

    private EnchantKey(@Nonnull ResourceKey<Enchantment> key, @Nullable EnchantmentBase base) {
        this.key = key;
        this.base = base;
    }

    /**
     * 按注册表键构造；是本模组的附魔时顺带找出行为对象。
     */
    @Nonnull
    public static EnchantKey of(@Nonnull ResourceKey<Enchantment> key) {
        return new EnchantKey(key, EnchantmentRegistry.byKey(key));
    }

    /**
     * 本模组附魔的键。
     */
    @Nonnull
    public static EnchantKey of(@Nonnull EnchantmentBase base) {
        return new EnchantKey(base.getKey(), base);
    }

    /**
     * 注册表键
     */
    @Nonnull
    public ResourceKey<Enchantment> key() {
        return key;
    }

    /**
     * 是不是这个附魔（原版附魔常量 {@code Enchantments.X} 就是键）
     */
    public boolean is(@Nonnull ResourceKey<Enchantment> other) {
        return key.equals(other);
    }

    /**
     * 本模组的行为对象；不是本模组的附魔返回 null
     */
    @Nullable
    public EnchantmentBase base() {
        return base;
    }

    /**
     * 是不是某个本模组附魔类（或它的子类）的附魔，对应 1.20.1 的 {@code clazz.isInstance(other)}。
     * 原版和别的模组的附魔一律 false。
     */
    public boolean isOf(@Nonnull Class<?> clazz) {
        return base != null && clazz.isInstance(base);
    }

    /**
     * 对应 1.20.1 的 {@code other.getClass()}：本模组附魔返回行为类；别的附魔返回 {@link Enchantment}，
     * 它的包名（{@code net.minecraft.world.item.enchantment}）和 1.20.1 原版附魔类的包名一样，
     * 按包名判断分类的规则（追忆、律法）结果不变。
     */
    @Nonnull
    public Class<?> enchantClass() {
        return base != null ? base.getClass() : Enchantment.class;
    }

    @Override
    public String toString() {
        return key.location().toString();
    }
}
