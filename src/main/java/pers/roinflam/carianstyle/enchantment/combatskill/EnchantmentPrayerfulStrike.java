package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.util.UUID;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>祈祷一击附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "prayerful_strike",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {EnchantmentScarletCorruption.class}
)
public class EnchantmentPrayerfulStrike extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "prayerful_strike";

    /**
     * 蓄力所需的时长（tick）
     * <p>默认 160，允许范围 1 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle CHARGING_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "charging_cooldown",
                    160, 1, 12000);

    /**
     * 未就绪状态的时长（tick）。应小于 charging_cooldown，二者共同决定可用节奏
     * <p>默认 80，允许范围 1 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle NOT_READY_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "not_ready_cooldown",
                    80, 1, 12000);

    /**
     * 按自身最大生命计算的额外伤害比例
     * <p>默认 0.025，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_health_ratio",
                    0.025D, 0.0D, 2.0D);

    /**
     * 按目标当前生命计算的额外伤害比例
     * <p>默认 0.075，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "victim_health_ratio",
                    0.075D, 0.0D, 2.0D);

    /**
     * 额外伤害的上限（占目标最大生命的比例）
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_cap_ratio",
                    0.1D, 0.0D, 2.0D);

    /**
     * 额外伤害转化为治疗的除数（2 表示一半）
     * <p>默认 2.0，允许范围 0.1 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "heal_divisor",
                    2.0D, 0.1D, 100.0D);

    /**
     * 单次治疗量的最大生命占比上限
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.05D, 0.0D, 2.0D);

    /**
     * 就绪提示音的结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");

    private static final String MAX_HEALTH_MODIFIER_NAME = "enchantment.prayerful_strike";

    private static final String CHARGING_COOLDOWN_KEY = "prayerful_strike_charging";

    private static final String NOT_READY_COOLDOWN_KEY = "prayerful_strike_not_ready";

    public EnchantmentPrayerfulStrike() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
