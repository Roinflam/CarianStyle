package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>黄金树的庇佑附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "protection_of_the_erdtree",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET},
        forceTreasure = true
)
public class EnchantmentProtectionOfTheErdtree extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "protection_of_the_erdtree";

    /**
     * 受击方参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "victim_level_cap",
                    10, 1, 100);

    /**
     * 攻击方参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle ATTACKER_LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "attacker_level_cap",
                    10, 1, 100);

    /**
     * 攻击方效果等级的上限
     * <p>默认 5，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle ATTACKER_AMPLIFIER_CAP =
            EnchantmentValues.define(VALUE_ID, "attacker_amplifier_cap",
                    5, 1, 100);

    /**
     * 受击方效果等级的上限。5 级减免已是 100%，再高没有意义
     * <p>默认 5，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_AMPLIFIER_CAP =
            EnchantmentValues.define(VALUE_ID, "victim_amplifier_cap",
                    5, 1, 100);

    /**
     * 每级的效果持续秒数
     * <p>默认 2.5，允许范围 0.1 ~ 120.0。</p>
     */
    private static final EnchantmentValues.Handle DURATION_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "duration_seconds_per_level",
                    2.5D, 0.1D, 120.0D);

    public EnchantmentProtectionOfTheErdtree() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}
