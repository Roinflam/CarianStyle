package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>死亡之刃附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "death_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentDeathBlade extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "death_blade";

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 100，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "dot_duration",
                    100, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 1，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    1, 0, 200);

    /**
     * 转为持续伤害的比例（以本次伤害为基数）
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DOT_RATIO =
            EnchantmentValues.define(VALUE_ID, "dot_ratio",
                    0.5D, 0.0D, 5.0D);

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

    /**
     * 直接伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DIRECT_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "direct_multiplier",
                    0.5D, 0.0D, 5.0D);

    /**
     * 死亡燃烧视觉的持续秒数
     * <p>默认 5，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle BURNING_SECONDS =
            EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    5, 1, 120);

    /**
     * 死亡标记（最大生命降低）的持续秒数
     * <p>默认 45（原作命定之死、死亡刀刃都是 45 秒），允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DOOM_SECONDS =
            EnchantmentValues.define(VALUE_ID, "doom_seconds",
                    45, 1, 120);

    public EnchantmentDeathBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (50 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
