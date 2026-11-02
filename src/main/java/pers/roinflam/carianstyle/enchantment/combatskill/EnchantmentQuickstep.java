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
 * <p>快步附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "quickstep",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.ARMOR_FEET,
        slots = {EntityEquipmentSlot.FEET}
)
public class EnchantmentQuickstep extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "quickstep";

    /**
     * 速度重算间隔（tick）；调大可降低服务端开销
     * <p>默认 4，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle CHECK_INTERVAL_TICKS =
            EnchantmentValues.define(VALUE_ID, "check_interval_ticks",
                    4, 1, 100);

    /**
     * 速度等级除数：等级 = 已损失生命百分比 × 100 ÷ 本值 × 附魔等级
     * <p>默认 5.0，允许范围 1.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle SPEED_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "speed_divisor",
                    5.0D, 1.0D, 100.0D);

    /**
     * 每次施加的速度持续时间（tick）
     * <p>默认 6，允许范围 1 ~ 200。</p>
     * <p>
     * <b>必须大于 {@code check_interval_ticks}</b>，否则两次结算之间会出现速度断档，
     * 玩家会感到一顿一顿的。默认值 6 覆盖默认间隔 4 正是这个道理；
     * 若把间隔调大，请同步把本值调到比它更大。
     * </p>
     */
    private static final EnchantmentValues.Handle SPEED_DURATION_TICKS =
            EnchantmentValues.define(VALUE_ID, "speed_duration_ticks",
                    6, 1, 200);

    public EnchantmentQuickstep() {
        super(EnumEnchantmentType.ARMOR_FEET, new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
