package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>忍耐附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "patience",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentPatience extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "patience";

    /**
     * 每级可累积伤害的最大生命占比上限
     * <p>默认 0.4，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MAX_ACCUMULATED_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "max_accumulated_per_level",
                    0.4D, 0.0D, 5.0D);

    /**
     * 每级把受到伤害转为累积值的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ACCUMULATE_RATE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "accumulate_rate_per_level",
                    0.1D, 0.0D, 2.0D);

    private static final String PATIENCE_DATA_KEY = "patience_accumulated";

    public EnchantmentPatience() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
