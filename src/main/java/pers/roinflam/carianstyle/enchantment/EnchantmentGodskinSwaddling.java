package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>神皮襁褓附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "godskin_swaddling",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentGodskinSwaddling extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "godskin_swaddling";

    /**
     * 攻击计数的保持时长（tick）
     * <p>默认 6000，允许范围 20 ~ 72000。</p>
     */
    private static final EnchantmentValues.Handle COUNTER_EXPIRY =
            EnchantmentValues.define(VALUE_ID, "counter_expiry",
                    6000, 20, 72000);

    /**
     * 触发所需的攻击次数
     * <p>默认 3，允许范围 1 ~ 50。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_COUNT =
            EnchantmentValues.define(VALUE_ID, "trigger_count",
                    3, 1, 50);

    /**
     * 一级时回复的最大生命占比
     * <p>默认 0.03，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "base_heal_ratio",
                    0.03D, 0.0D, 1.0D);

    /**
     * 每超过一级额外回复的最大生命占比
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_EXTRA_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_extra_level",
                    0.01D, 0.0D, 1.0D);

    private static final String ATTACK_COUNTER = "godskin_swaddling_attack";

    public EnchantmentGodskinSwaddling() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
