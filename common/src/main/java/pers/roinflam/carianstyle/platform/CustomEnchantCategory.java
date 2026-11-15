package pers.roinflam.carianstyle.platform;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * 本模组自己的三种附魔适用类型，附魔注解里用 {@code customType} 的字符串指定。
 * <p>
 * 原版的 {@code EnchantmentCategory} 是枚举，只有 Forge 能往里扩展。判定写在这里，两个平台共用；
 * 附魔对象的 {@code category} 字段挂什么由 {@link Platform#hostCategory} 决定。
 * </p>
 */
public enum CustomEnchantCategory {

    SHIELD("cs_shield", item -> item instanceof ShieldItem),
    ARMS("cs_arms", item -> item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem),
    // 原版 DIGGER 连斧、锹、锄也算，碎岩只对镐子有用
    PICKAXE("cs_pickaxe", item -> item instanceof PickaxeItem);

    // Forge 上扩展出来的枚举常量用这个名字，和以前一样，别改
    private final String hostName;
    private final Predicate<Item> test;

    CustomEnchantCategory(String hostName, Predicate<Item> test) {
        this.hostName = hostName;
        this.test = test;
    }

    public String hostName() {
        return hostName;
    }

    public boolean canEnchant(Item item) {
        return test.test(item);
    }

    /**
     * 按注解里的 {@code customType} 找类型，大小写不敏感；空串、null、认不出的都返回 null。
     */
    public static CustomEnchantCategory byName(String customType) {
        if (customType == null || customType.isEmpty()) {
            return null;
        }
        String upper = customType.toUpperCase(Locale.ROOT);
        for (CustomEnchantCategory type : values()) {
            if (type.name().equals(upper)) {
                return type;
            }
        }
        return null;
    }
}
