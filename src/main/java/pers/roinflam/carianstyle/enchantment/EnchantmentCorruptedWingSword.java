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
 * <p>腐败翼剑附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "corrupted_wing_sword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentCorruptedWingSword extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "corrupted_wing_sword";

    /**
     * 连击层数上限
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_COMBO =
            EnchantmentValues.define(VALUE_ID, "max_combo",
                    20, 1, 200);

    /**
     * 连击开始衰减前的延迟（tick）
     * <p>默认 300，允许范围 20 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle DECAY_DELAY =
            EnchantmentValues.define(VALUE_ID, "decay_delay",
                    300, 20, 12000);

    /**
     * 连击层数的换算除数（越小同层数增伤越高）
     * <p>默认 4.0，允许范围 0.5 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle COMBO_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "combo_divisor",
                    4.0D, 0.5D, 100.0D);

    /**
     * 每级每档连击提供的额外伤害倍率
     * <p>默认 0.03，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.03D, 0.0D, 2.0D);

    private static final String COMBO_COUNTER_KEY = "corrupted_wing_sword_combo";

    public EnchantmentCorruptedWingSword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
