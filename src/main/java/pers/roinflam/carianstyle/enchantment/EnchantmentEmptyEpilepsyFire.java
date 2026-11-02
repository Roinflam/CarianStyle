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
 * <p>空癫火附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "empty_epilepsy_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentEmptyEpilepsyFire extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "empty_epilepsy_fire";

    /**
     * 燃烧视觉效果的持续时间（tick）
     * <p>默认 65，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle BURN_VISUAL_DURATION =
            EnchantmentValues.define(VALUE_ID, "burn_visual_duration",
                    65, 1, 1200);

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 60，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_TICKS =
            EnchantmentValues.define(VALUE_ID, "damage_ticks",
                    60, 1, 1200);

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
     * 自身承受的总伤害占最大生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.1D, 0.0D, 1.0D);

    /**
     * 每级施加给目标的伤害倍率（以自身承受量为基数）
     * <p>默认 0.2，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "victim_damage_per_level",
                    0.2D, 0.0D, 2.0D);

    public EnchantmentEmptyEpilepsyFire() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
