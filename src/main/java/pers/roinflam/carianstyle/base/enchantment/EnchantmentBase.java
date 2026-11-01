package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;

import javax.annotation.Nonnull;

/**
 * PORT-STUB(B1)
 * <p>
 * 只有配置烘焙要调的静态方法；方法名桥接、事件分发、禁用判断等全部由附魔框架批次实装。
 * </p>
 */
@Deprecated
public abstract class EnchantmentBase extends Enchantment {

    protected EnchantmentBase(@Nonnull Rarity rarity, @Nonnull EnumEnchantmentType type, @Nonnull EntityEquipmentSlot[] slots) {
        super(rarity, type, slots);
    }

    /**
     * 配置重新烘焙后清掉所有附魔缓存的禁用状态（桩：还没有缓存，什么都不做）
     */
    public static void invalidateAllDisabledCaches() {
        // 桩
    }
}
