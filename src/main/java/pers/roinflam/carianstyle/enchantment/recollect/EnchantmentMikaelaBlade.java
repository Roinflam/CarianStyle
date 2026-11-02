package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>米凯拉之刃附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "mikaela_blade", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentMikaelaBlade extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "mikaela_blade";

    /**
     * 连击起始时的伤害倍率
     * <p>默认 0.4，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "base_multiplier",
                    0.4D, 0.0D, 5.0D);

    /**
     * 每层连击增加的伤害倍率
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MULTIPLIER_PER_COMBO =
            EnchantmentValues.define(VALUE_ID, "multiplier_per_combo",
                    0.2D, 0.0D, 5.0D);

    /**
     * 连击层数的保持时长（tick）
     * <p>默认 40，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle COMBO_DURATION =
            EnchantmentValues.define(VALUE_ID, "combo_duration",
                    40, 1, 1200);

    /**
     * 目标每层连击时自身受到的额外伤害比例
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_COMBO_BONUS =
            EnchantmentValues.define(VALUE_ID, "victim_combo_bonus",
                    0.1D, 0.0D, 5.0D);

    private static final String COMBO_COUNT_KEY = "mikaela_blade_combo";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentMikaelaBlade() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
