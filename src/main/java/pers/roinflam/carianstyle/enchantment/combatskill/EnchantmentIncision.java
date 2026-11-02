package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>切割附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "incision",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
public class EnchantmentIncision extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "incision";

    /**
     * 触发所需的剩余生命比例下限
     * <p>默认 0.75，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.75D, 0.0D, 1.0D);

    /**
     * 触发时消耗的最大生命占比。必须小于 trigger_health_ratio，否则会当场自杀
     * <p>默认 0.5，允许范围 0.0 ~ 0.95。</p>
     */
    private static final EnchantmentValues.Handle SELF_COST_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_cost_ratio",
                    0.5D, 0.0D, 0.95D);

    /**
     * 自身切割状态的持续时间（tick）；同时也是续期的时长上限
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle SELF_EFFECT_DURATION =
            EnchantmentValues.define(VALUE_ID, "self_effect_duration",
                    200, 20, 6000);

    /**
     * 按本次伤害回复生命的比例
     * <p>默认 0.25，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.25D, 0.0D, 2.0D);

    /**
     * 单次治疗量的最大生命占比上限
     * <p>默认 0.25，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.25D, 0.0D, 2.0D);

    /**
     * 施加给目标的出血持续时间（tick）
     * <p>默认 30，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle HEMORRHAGE_DURATION =
            EnchantmentValues.define(VALUE_ID, "hemorrhage_duration",
                    30, 1, 1200);

    /**
     * 击杀时回复已损失生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle KILL_HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "kill_heal_ratio",
                    0.1D, 0.0D, 2.0D);

    /**
     * 击杀时为自身切割状态续期的时长（tick，总时长不超过 self_effect_duration）
     * <p>默认 100，允许范围 0 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle KILL_DURATION_BONUS =
            EnchantmentValues.define(VALUE_ID, "kill_duration_bonus",
                    100, 0, 6000);

    public EnchantmentIncision() {
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
