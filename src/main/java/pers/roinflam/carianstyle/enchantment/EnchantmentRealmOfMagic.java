package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>魔法领域附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "realm_of_magic",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET},
        conflictsWith = {EnchantmentToppsStand.class},
        forceTreasure = true
)
public class EnchantmentRealmOfMagic extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "realm_of_magic";

    /**
     * 友方搜索半径（格）
     * <p>默认 6，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle ALLY_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "ally_search_radius",
                    6, 1, 64);

    /**
     * 附近有同类携带者时的额外伤害比例
     * <p>默认 0.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_BONUS =
            EnchantmentValues.define(VALUE_ID, "damage_bonus",
                    0.5D, 0.0D, 10.0D);

    public EnchantmentRealmOfMagic() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
