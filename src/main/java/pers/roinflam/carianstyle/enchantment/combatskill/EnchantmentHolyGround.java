package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>圣地附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "holy_ground",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        customType = "SHIELD",
        slots = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
)
public class EnchantmentHolyGround extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "holy_ground";

    /**
     * 每级减伤比例（上限受等级上限约束）
     * <p>默认 0.05，允许范围 0.0 ~ 0.2。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_reduction_per_level",
                    0.05D, 0.0D, 0.2D);

    /**
     * 光环结算间隔（tick）
     * <p>默认 60，允许范围 5 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle PULSE_INTERVAL_TICKS =
            EnchantmentValues.define(VALUE_ID, "pulse_interval_ticks",
                    60, 5, 1200);

    /**
     * 每次结算每级回复的最大生命占比
     * <p>默认 0.015，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.015D, 0.0D, 0.5D);

    /**
     * 每次结算每级提供的伤害吸收占比
     * <p>默认 0.03，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "absorption_per_level",
                    0.03D, 0.0D, 0.5D);

    public EnchantmentHolyGround() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
