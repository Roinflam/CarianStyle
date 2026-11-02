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
 * <p>精准落雷附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "precise_lightning", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnumEnchantmentType.ARMOR, slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}, conflictsWith = {EnchantmentCausalityPrinciple.class})
public class EnchantmentPreciseLightning extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "precise_lightning";

    /**
     * 反击雷电伤害占原伤害的比例
     * <p>默认 0.3，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.3D, 0.0D, 5.0D);

    /**
     * 反击的击退强度
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "knockback_strength",
                    0.2D, 0.0D, 5.0D);

    /**
     * 高倍率档的伤害放大倍数
     * <p>默认 4，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle MAGNIFICATION_HIGH =
            EnchantmentValues.define(VALUE_ID, "magnification_high",
                    4, 1, 100);

    /**
     * 中倍率档的伤害放大倍数
     * <p>默认 2，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle MAGNIFICATION_MID =
            EnchantmentValues.define(VALUE_ID, "magnification_mid",
                    2, 1, 100);

    public EnchantmentPreciseLightning() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((30 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
