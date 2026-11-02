package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>注定死亡附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "doomed_death",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentDoomedDeath extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "doomed_death";

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 40，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "dot_duration",
                    40, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 死亡燃烧视觉的持续秒数
     * <p>默认 2（与持续伤害同时结束），允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle BURNING_SECONDS =
            EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    2, 1, 120);

    /**
     * 死亡标记（最大生命降低）的持续秒数
     * <p>默认 45（原作命定之死、死亡刀刃都是 45 秒），允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DOOM_SECONDS =
            EnchantmentValues.define(VALUE_ID, "doom_seconds",
                    45, 1, 120);

    /**
     * 按本次伤害计算的持续伤害基数比例
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.2D, 0.0D, 5.0D);

    /**
     * 按目标最大生命计算的持续伤害基数比例（v3.1 起取代按当前生命的 victim_health_ratio）
     * <p>默认 0.02，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_MAX_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "victim_max_health_ratio",
                    0.02D, 0.0D, 1.0D);

    /**
     * 最大生命降低比例
     * <p>默认 0.1，允许范围 0.0 ~ 0.9。</p>
     */
    private static final EnchantmentValues.Handle MAX_HEALTH_REDUCTION =
            EnchantmentValues.define(VALUE_ID, "max_health_reduction",
                    0.1D, 0.0D, 0.9D);

    /**
     * 目标是 BOSS 时持续伤害的倍率（最大生命降低不受影响）
     * <p>默认 0.5，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle BOSS_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "boss_multiplier",
                    0.5D, 0.0D, 1.0D);

    public EnchantmentDoomedDeath() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
