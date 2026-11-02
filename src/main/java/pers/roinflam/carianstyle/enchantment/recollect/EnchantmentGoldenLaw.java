package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>黄金律法附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "golden_law", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentGoldenLaw extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "golden_law";

    /**
     * 固定的额外伤害比例
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_BONUS =
            EnchantmentValues.define(VALUE_ID, "base_bonus",
                    0.15D, 0.0D, 5.0D);

    /**
     * 按已损失生命比例提供的额外伤害系数
     * <p>默认 0.45，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MISSING_HEALTH_BONUS =
            EnchantmentValues.define(VALUE_ID, "missing_health_bonus",
                    0.45D, 0.0D, 5.0D);

    /**
     * 受到伤害的倍率
     * <p>默认 0.85，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.85D, 0.0D, 2.0D);

    /**
     * 触发免疫所需的伤害占当前生命比例上限
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle IMMUNITY_THRESHOLD =
            EnchantmentValues.define(VALUE_ID, "immunity_threshold",
                    0.15D, 0.0D, 1.0D);

    /**
     * 免疫的冷却时间（tick）
     * <p>默认 100，允许范围 1 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle IMMUNITY_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "immunity_cooldown",
                    100, 1, 12000);

    private static final String IMMUNITY_COOLDOWN_KEY = "golden_law_immunity";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGoldenLaw() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override protected boolean checkCompatibility(Enchantment ench) {
        if (packageNameOf(ench).contains("law") && !ench.equals(this)) return false;
        return super.checkCompatibility(ench);
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
