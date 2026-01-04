package pers.roinflam.carianstyle.base.enchantment.rarity;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

/**
 * 极其稀有附魔基类（兼容旧代码）
 * <p>
 * 此类仅用于向后兼容，新附魔应该直接继承EnchantmentBase并使用注解
 * </p>
 * <p>
 * 特性：
 * - 最大等级：1
 * - 默认公式：RECOLLECT_ENCHANTABILITY（固定值38）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 * @deprecated 推荐使用 {@link EnchantmentBase} 配合 {@link AutoRegisterEnchantment} 注解
 */
@Deprecated
public abstract class VeryRaryBase extends EnchantmentBase {

    /**
     * 构造函数
     *
     * @param typeIn 附魔类型
     * @param slots 装备槽位
     * @param name 附魔名称
     */
    protected VeryRaryBase(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots, String name) {
        super(Rarity.VERY_RARE, typeIn, slots, name);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        // VeryRaryBase使用固定的RECOLLECT_ENCHANTABILITY
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY *
                pers.roinflam.carianstyle.config.ConfigLoader.enchantingDifficulty);
    }
}