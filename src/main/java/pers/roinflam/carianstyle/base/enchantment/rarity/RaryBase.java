package pers.roinflam.carianstyle.base.enchantment.rarity;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

/**
 * 稀有附魔基类（兼容旧代码）
 * <p>
 * 此类仅用于向后兼容，新附魔应该直接继承EnchantmentBase并使用注解
 * </p>
 * <p>
 * 特性：
 * - 最大等级：3
 * - 默认公式：10 + (level - 1) * 15
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 * @deprecated 推荐使用 {@link EnchantmentBase} 配合 {@link AutoRegisterEnchantment} 注解
 */
@Deprecated
public abstract class RaryBase extends EnchantmentBase {

    /**
     * 构造函数
     *
     * @param typeIn 附魔类型
     * @param slots 装备槽位
     * @param name 附魔名称
     */
    protected RaryBase(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(Rarity.RARE, typeIn, slots, name);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        // RaryBase的默认公式：10 + (level - 1) * 15
        return enchantmentRarity.calculateEnchantability(enchantmentLevel,
                pers.roinflam.carianstyle.config.ConfigLoader.enchantingDifficulty);
    }
}